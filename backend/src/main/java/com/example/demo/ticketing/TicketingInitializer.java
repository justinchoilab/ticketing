package com.example.demo.ticketing;

import com.example.demo.ticketing.domain.TicketingEvent;
import com.example.demo.ticketing.repository.TicketingEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 티켓팅 이벤트 초기화
 *
 * 서버 시작 시 이벤트가 없으면 기본 데모 이벤트를 생성합니다.
 * @Profile 없이 항상 실행되므로 로컬/Docker 환경 모두에서 동작합니다.
 *
 * 생성되는 기본 이벤트:
 *   - 이름: "콘서트 티켓팅"
 *   - 설명: "선착순 100명 한정! 동시성 제어 데모입니다."
 *   - 총 티켓: 100석
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TicketingInitializer implements ApplicationRunner {

    private final TicketingEventRepository eventRepository;

    @Override
    public void run(ApplicationArguments args) {
        // 이벤트가 없을 때만 기본 이벤트 생성 (멱등성 보장)
        if (eventRepository.count() == 0) {
            TicketingEvent event = new TicketingEvent(
                    "콘서트 티켓팅",
                    "선착순 100명 한정! 동시성 제어 데모입니다.",
                    100
            );
            eventRepository.save(event);
            log.info("[Ticketing] Default event created: '{}', Total {} seats", event.getName(), event.getTotalCapacity());
        } else {
            log.info("[Ticketing] Existing event found, skipping initialization");
        }
    }
}
