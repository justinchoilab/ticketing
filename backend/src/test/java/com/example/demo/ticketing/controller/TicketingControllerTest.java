package com.example.demo.ticketing.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.example.demo.ticketing.dto.TicketingDto.ReserveResponse;
import com.example.demo.ticketing.dto.TicketingDto.ReserveResult;
import com.example.demo.ticketing.dto.TicketingDto.TicketingStatusDto;
import com.example.demo.ticketing.service.TicketingService;

@WebMvcTest(TicketingController.class)
class TicketingControllerTest {

    @Autowired MockMvc mockMvc;
    @MockBean TicketingService ticketingService;

    @Test
    void getStatus_현황_반환() throws Exception {
        given(ticketingService.getStatus()).willReturn(
                new TicketingStatusDto("데모 이벤트", 100, 30, 70));

        mockMvc.perform(get("/api/ticketing/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("데모 이벤트"))
                .andExpect(jsonPath("$.totalCapacity").value(100))
                .andExpect(jsonPath("$.reservedCount").value(30))
                .andExpect(jsonPath("$.remaining").value(70));
    }

    @Test
    void reserve_성공() throws Exception {
        given(ticketingService.reserve("user1")).willReturn(
                ReserveResponse.of(ReserveResult.SUCCESS, "user1", "2024-01-01 00:00:00.000", "예매가 완료되었습니다."));

        mockMvc.perform(post("/api/ticketing/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"user1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SUCCESS"))
                .andExpect(jsonPath("$.userId").value("user1"))
                .andExpect(jsonPath("$.message").value("예매가 완료되었습니다."));
    }

    @Test
    void reserve_중복() throws Exception {
        given(ticketingService.reserve("user1")).willReturn(
                ReserveResponse.of(ReserveResult.DUPLICATE, "user1", "2024-01-01 00:00:00.000", "이미 예매하셨습니다."));

        mockMvc.perform(post("/api/ticketing/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"user1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("DUPLICATE"));
    }

    @Test
    void reserve_매진() throws Exception {
        given(ticketingService.reserve("user2")).willReturn(
                ReserveResponse.of(ReserveResult.SOLD_OUT, "user2", "2024-01-01 00:00:00.000", "티켓이 매진되었습니다."));

        mockMvc.perform(post("/api/ticketing/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"user2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("SOLD_OUT"));
    }

    @Test
    void reset_200_반환() throws Exception {
        mockMvc.perform(post("/api/ticketing/reset"))
                .andExpect(status().isOk());

        then(ticketingService).should().reset();
    }
}
