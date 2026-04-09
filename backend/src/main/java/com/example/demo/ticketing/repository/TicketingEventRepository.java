package com.example.demo.ticketing.repository;

import com.example.demo.ticketing.domain.TicketingEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 티켓팅 이벤트 리포지토리
 *
 * 현재 데모에서는 단일 이벤트만 사용하므로,
 * findFirstByOrderByIdAsc()로 첫 번째 이벤트를 가져옵니다.
 */
public interface TicketingEventRepository extends JpaRepository<TicketingEvent, Long> {

    /**
     * ID 오름차순으로 첫 번째 이벤트 조회 (데모용 단일 이벤트)
     */
    Optional<TicketingEvent> findFirstByOrderByIdAsc();
}
