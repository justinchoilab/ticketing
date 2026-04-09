package com.example.demo.ticketing.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.example.demo.ticketing.domain.TicketingEvent;
import com.example.demo.ticketing.domain.TicketingReservation;

@DataJpaTest
class TicketingReservationRepositoryTest {

    @Autowired TestEntityManager em;
    @Autowired TicketingReservationRepository reservationRepository;

    private TicketingEvent event;

    @BeforeEach
    void setUp() {
        event = em.persistAndFlush(new TicketingEvent("테스트 이벤트", "설명", 100));
    }

    @Test
    void countByEventId_예약_수_반환() {
        em.persistAndFlush(new TicketingReservation(event, "user1"));
        em.persistAndFlush(new TicketingReservation(event, "user2"));

        int count = reservationRepository.countByEventId(event.getId());

        assertThat(count).isEqualTo(2);
    }

    @Test
    void countByEventId_예약_없을때_0() {
        int count = reservationRepository.countByEventId(event.getId());

        assertThat(count).isZero();
    }

    @Test
    void deleteByEventId_전체_삭제() {
        em.persistAndFlush(new TicketingReservation(event, "user1"));
        em.persistAndFlush(new TicketingReservation(event, "user2"));

        reservationRepository.deleteByEventId(event.getId());
        em.flush();
        em.clear();

        assertThat(reservationRepository.countByEventId(event.getId())).isZero();
    }

    @Test
    void findFirstByOrderByIdAsc_첫_이벤트_반환() {
        assertThat(em.getEntityManager()
                .createQuery("SELECT e FROM TicketingEvent e ORDER BY e.id ASC", TicketingEvent.class)
                .setMaxResults(1)
                .getResultList())
                .hasSize(1)
                .first()
                .extracting(TicketingEvent::getName)
                .isEqualTo("테스트 이벤트");
    }
}
