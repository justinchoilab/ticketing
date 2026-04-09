package com.example.demo.ticketing.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 티켓팅 이벤트 엔티티
 *
 * 동시성 제어는 Redis Lua 스크립트(Layer 1: 중복 차단 + 재고 감산 원자적 실행)와
 * Semaphore(Layer 2: DB 접근 스로틀링)로 처리한다.
 * DB 레벨에서는 비관적 락(PESSIMISTIC_WRITE)을 최후 안전망으로 사용한다.
 */
@Entity
@Table(name = "ticketing_event")
@Getter
@NoArgsConstructor
public class TicketingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 이벤트 이름 */
    private String name;

    /** 이벤트 설명 */
    private String description;

    /** 총 티켓 수 */
    private int totalCapacity;

    public TicketingEvent(String name, String description, int totalCapacity) {
        this.name = name;
        this.description = description;
        this.totalCapacity = totalCapacity;
    }
}
