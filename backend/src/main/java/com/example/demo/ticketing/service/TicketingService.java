package com.example.demo.ticketing.service;

import com.example.demo.ticketing.domain.TicketingEvent;
import com.example.demo.ticketing.domain.TicketingReservation;
import com.example.demo.ticketing.dto.TicketingDto.ReserveResponse;
import com.example.demo.ticketing.dto.TicketingDto.ReserveResult;
import com.example.demo.ticketing.dto.TicketingDto.TicketingStatusDto;
import com.example.demo.ticketing.repository.TicketingEventRepository;
import com.example.demo.ticketing.repository.TicketingReservationRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class TicketingService {

    private final TicketingEventRepository eventRepository;
    private final TicketingReservationRepository reservationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final StringRedisTemplate redisTemplate;

    private final Semaphore dbWriteSemaphore = new Semaphore(10);

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile ScheduledFuture<?> pendingBroadcast;

    private volatile Long currentEventId = null;

    // Redis 키
    private String stockKey()  { return "ticketing:stock:"  + currentEventId; }
    private String usersKey()  { return "ticketing:users:"  + currentEventId; }

    /**
     * Lua 스크립트 — Layer 1: 중복 차단(SADD) + 재고 감산(DECR) 원자적 처리
     *
     * KEYS[1] = users set key
     * KEYS[2] = stock key
     * ARGV[1] = userId
     *
     * 반환값:
     *   -1 = DUPLICATE
     *   -2 = SOLD_OUT
     *   >= 0 = 남은 재고 (SUCCESS)
     */
    private static final DefaultRedisScript<Long> RESERVE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('SADD', KEYS[1], ARGV[1]) == 0 then
                return -1
            end
            local remaining = tonumber(redis.call('DECR', KEYS[2]))
            if remaining < 0 then
                redis.call('INCR', KEYS[2])
                redis.call('SREM', KEYS[1], ARGV[1])
                return -2
            end
            return remaining
            """, Long.class);

    private void broadcastStatus() {
        if (pendingBroadcast != null) pendingBroadcast.cancel(false);
        pendingBroadcast = scheduler.schedule(() ->
            messagingTemplate.convertAndSend("/topic/ticketing/status", getStatus()),
            300, TimeUnit.MILLISECONDS
        );
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void init() {
        if (eventRepository.findFirstByOrderByIdAsc().isEmpty()) {
            eventRepository.save(new TicketingEvent("티켓팅 데모", "선착순 동시성 제어 데모 이벤트", 100));
            log.info("[Ticketing Init] 기본 이벤트 생성 완료");
        }

        eventRepository.findFirstByOrderByIdAsc().ifPresent(event -> {
            currentEventId = event.getId();
            reservationRepository.deleteByEventId(currentEventId);

            try {
                redisTemplate.delete(usersKey());
                redisTemplate.opsForValue().set(stockKey(), String.valueOf(event.getTotalCapacity()));
                log.info("[Ticketing Init] Event: '{}', reinitialized with {} seats",
                        event.getName(), event.getTotalCapacity());
            } catch (Exception e) {
                log.warn("[Ticketing Init] Redis connection failed - ticketing disabled: {}", e.getMessage());
            }
        });
    }

    public ReserveResponse reserve(String userId) {
        String receivedAt = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));

        if (currentEventId == null) {
            return ReserveResponse.of(ReserveResult.ERROR, userId, receivedAt, "이벤트가 초기화되지 않았습니다.");
        }

        Long result = redisTemplate.execute(
                RESERVE_SCRIPT,
                List.of(usersKey(), stockKey()),
                userId
        );

        if (result == null || result == -1L) {
            log.debug("[Layer1-Duplicate] Blocked duplicate reservation attempt: userId={}", userId);
            return ReserveResponse.of(ReserveResult.DUPLICATE, userId, receivedAt, "이미 예매하셨습니다.");
        }
        if (result == -2L) {
            log.debug("[Layer1-SoldOut] Blocked by stock depletion: userId={}", userId);
            return ReserveResponse.of(ReserveResult.SOLD_OUT, userId, receivedAt, "티켓이 매진되었습니다.");
        }

        boolean acquired;
        try {
            acquired = dbWriteSemaphore.tryAcquire(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            rollbackRedis(userId);
            return ReserveResponse.of(ReserveResult.ERROR, userId, receivedAt, "처리 중 인터럽트가 발생했습니다.");
        }

        if (!acquired) {
            rollbackRedis(userId);
            log.debug("[Layer2-Timeout] Semaphore acquire timed out: userId={}", userId);
            return ReserveResponse.of(ReserveResult.TIMEOUT, userId, receivedAt, "서버가 혼잡합니다. 잠시 후 다시 시도해주세요.");
        }

        try {
            saveReservation(userId);
            broadcastStatus();
            log.debug("[Reservation Success] userId={}, remaining={}", userId, result);
            return ReserveResponse.of(ReserveResult.SUCCESS, userId, receivedAt, "예매가 완료되었습니다.");
        } catch (Exception e) {
            rollbackRedis(userId);
            log.error("[DB Failure + Redis Rollback] userId={}, error={}", userId, e.getMessage());
            return ReserveResponse.error(userId, receivedAt, "예매 처리 중 오류가 발생했습니다: " + e.getMessage());
        } finally {
            dbWriteSemaphore.release();
        }
    }

    private void rollbackRedis(String userId) {
        log.debug("[Redis Rollback] Restoring stock and clearing duplicate check due to failure: userId={}", userId);
        redisTemplate.opsForValue().increment(stockKey());
        redisTemplate.opsForSet().remove(usersKey(), userId);
    }

    @Transactional
    public void saveReservation(String userId) {
        TicketingEvent event = eventRepository.findById(currentEventId)
                .orElseThrow(() -> new IllegalStateException("이벤트를 찾을 수 없습니다."));
        reservationRepository.save(new TicketingReservation(event, userId));
    }

    public TicketingStatusDto getStatus() {
        if (currentEventId == null) {
            return new TicketingStatusDto("이벤트 없음", 0, 0, 0);
        }
        return eventRepository.findById(currentEventId).map(event -> {
            int reserved = reservationRepository.countByEventId(event.getId());
            String stockStr = redisTemplate.opsForValue().get(stockKey());
            int remaining = stockStr != null ? Math.max(0, Integer.parseInt(stockStr)) : 0;
            return new TicketingStatusDto(event.getName(), event.getTotalCapacity(), reserved, remaining);
        }).orElse(new TicketingStatusDto("이벤트 없음", 0, 0, 0));
    }

    @Transactional
    public void reset() {
        if (currentEventId == null) {
            log.warn("[Reset] No current event.");
            return;
        }
        reservationRepository.deleteByEventId(currentEventId);
        redisTemplate.delete(usersKey());
        eventRepository.findById(currentEventId).ifPresent(event -> {
            redisTemplate.opsForValue().set(stockKey(), String.valueOf(event.getTotalCapacity()));
            log.info("[Reset Completed] Event: '{}', reinitialized with {} seats", event.getName(), event.getTotalCapacity());
        });
        broadcastStatus();
    }
}
