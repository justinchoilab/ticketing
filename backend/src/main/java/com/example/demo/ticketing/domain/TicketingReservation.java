package com.example.demo.ticketing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * 티켓 예약 엔티티
 *
 * 예약 성공 시 생성되며, 각 예약은 특정 이벤트(TicketingEvent)와 userId로 구성됩니다.
 * 하나의 userId는 하나의 이벤트에 대해 단 한 번만 예약 가능합니다.
 * (중복 체크는 서비스 레이어의 ConcurrentHashMap이 우선 처리)
 */
@Entity
@Table(name = "ticketing_reservation")
@Getter
@NoArgsConstructor
public class TicketingReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 예약된 이벤트 (ManyToOne: 하나의 이벤트에 여러 예약 가능) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private TicketingEvent event;

    /** 예약한 사용자 ID */
    private String userId;

    /** 예약 시각 (자동 생성) */
    @CreationTimestamp
    private LocalDateTime reservedAt;

    public TicketingReservation(TicketingEvent event, String userId) {
        this.event = event;
        this.userId = userId;
    }
}
