package com.example.demo.ticketing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.example.demo.ticketing.domain.TicketingEvent;
import com.example.demo.ticketing.domain.TicketingReservation;
import com.example.demo.ticketing.dto.TicketingDto.ReserveResponse;
import com.example.demo.ticketing.dto.TicketingDto.ReserveResult;
import com.example.demo.ticketing.dto.TicketingDto.TicketingStatusDto;
import com.example.demo.ticketing.repository.TicketingEventRepository;
import com.example.demo.ticketing.repository.TicketingReservationRepository;

@ExtendWith(MockitoExtension.class)
class TicketingServiceTest {

    @Mock TicketingEventRepository eventRepository;
    @Mock TicketingReservationRepository reservationRepository;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock StringRedisTemplate redisTemplate;
    @Mock ValueOperations<String, String> valueOps;
    @Mock SetOperations<String, String> setOps;

    @InjectMocks TicketingService service;

    private static final Long EVENT_ID = 1L;
    private TicketingEvent event;

    @BeforeEach
    void setUp() {
        event = new TicketingEvent("데모 이벤트", "설명", 100);
        ReflectionTestUtils.setField(event, "id", EVENT_ID);
        ReflectionTestUtils.setField(service, "currentEventId", EVENT_ID);

        given(redisTemplate.opsForValue()).willReturn(valueOps);
        given(redisTemplate.opsForSet()).willReturn(setOps);
    }

    // --- reserve() ---

    @Test
    void reserve_성공() {
        given(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString())).willReturn(99L);
        given(eventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));

        ReserveResponse res = service.reserve("user1");

        assertThat(res.getResult()).isEqualTo(ReserveResult.SUCCESS);
        assertThat(res.getUserId()).isEqualTo("user1");
    }

    @Test
    void reserve_중복_차단() {
        given(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString())).willReturn(-1L);

        ReserveResponse res = service.reserve("user1");

        assertThat(res.getResult()).isEqualTo(ReserveResult.DUPLICATE);
        then(reservationRepository).should(never()).save(any());
    }

    @Test
    void reserve_매진() {
        given(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString())).willReturn(-2L);

        ReserveResponse res = service.reserve("user1");

        assertThat(res.getResult()).isEqualTo(ReserveResult.SOLD_OUT);
        then(reservationRepository).should(never()).save(any());
    }

    @Test
    void reserve_이벤트_미초기화() {
        ReflectionTestUtils.setField(service, "currentEventId", null);

        ReserveResponse res = service.reserve("user1");

        assertThat(res.getResult()).isEqualTo(ReserveResult.ERROR);
    }

    @Test
    void reserve_Redis_null_반환시_중복처리() {
        given(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString())).willReturn(null);

        ReserveResponse res = service.reserve("user1");

        assertThat(res.getResult()).isEqualTo(ReserveResult.DUPLICATE);
    }

    @Test
    void reserve_DB_저장_실패시_Redis_롤백() {
        given(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString())).willReturn(10L);
        given(eventRepository.findById(EVENT_ID)).willThrow(new RuntimeException("DB error"));

        ReserveResponse res = service.reserve("user1");

        assertThat(res.getResult()).isEqualTo(ReserveResult.ERROR);
        then(valueOps).should().increment(anyString());
        then(setOps).should().remove(anyString(), anyString());
    }

    // --- getStatus() ---

    @Test
    void getStatus_정상() {
        given(eventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(reservationRepository.countByEventId(EVENT_ID)).willReturn(30);
        given(valueOps.get(anyString())).willReturn("70");

        TicketingStatusDto status = service.getStatus();

        assertThat(status.name()).isEqualTo("데모 이벤트");
        assertThat(status.totalCapacity()).isEqualTo(100);
        assertThat(status.reservedCount()).isEqualTo(30);
        assertThat(status.remaining()).isEqualTo(70);
    }

    @Test
    void getStatus_이벤트_미초기화() {
        ReflectionTestUtils.setField(service, "currentEventId", null);

        TicketingStatusDto status = service.getStatus();

        assertThat(status.name()).isEqualTo("이벤트 없음");
        assertThat(status.totalCapacity()).isZero();
    }

    @Test
    void getStatus_Redis_재고_음수일때_0으로_표시() {
        given(eventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));
        given(reservationRepository.countByEventId(EVENT_ID)).willReturn(100);
        given(valueOps.get(anyString())).willReturn("-1");

        TicketingStatusDto status = service.getStatus();

        assertThat(status.remaining()).isZero();
    }

    // --- reset() ---

    @Test
    void reset_정상() {
        given(eventRepository.findById(EVENT_ID)).willReturn(Optional.of(event));

        service.reset();

        then(reservationRepository).should().deleteByEventId(EVENT_ID);
        then(redisTemplate).should().delete(anyString());
        then(valueOps).should().set(anyString(), anyString());
    }

    @Test
    void reset_이벤트_미초기화시_조기_종료() {
        ReflectionTestUtils.setField(service, "currentEventId", null);

        service.reset();

        then(reservationRepository).should(never()).deleteByEventId(any());
    }
}
