package com.example.demo.ticketing.repository;

import com.example.demo.ticketing.domain.TicketingReservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 티켓 예약 리포지토리
 *
 * - countByEventId: 특정 이벤트의 현재 예약 수 조회 (재고 계산에 사용)
 * - findUserIdsByEventId: 이미 예약한 userId 목록 조회 (서버 재시작 시 ConcurrentHashMap 복원에 사용)
 * - deleteByEventId: 리셋 시 특정 이벤트의 예약 전체 삭제
 */
public interface TicketingReservationRepository extends JpaRepository<TicketingReservation, Long> {

    /**
     * 특정 이벤트에 대한 예약 수 카운트
     */
    int countByEventId(Long eventId);

    /**
     * 특정 이벤트에 예약한 userId 목록만 추출
     * - 서버 재시작 시 ConcurrentHashMap에 복원하여 중복 예약 방지
     */
    @Query("SELECT r.userId FROM TicketingReservation r WHERE r.event.id = :eventId")
    List<String> findUserIdsByEventId(@Param("eventId") Long eventId);

    /**
     * 특정 이벤트의 모든 예약 삭제 (리셋용)
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM TicketingReservation r WHERE r.event.id = :eventId")
    void deleteByEventId(@Param("eventId") Long eventId);
}
