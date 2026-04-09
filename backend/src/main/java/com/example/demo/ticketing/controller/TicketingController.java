package com.example.demo.ticketing.controller;

import com.example.demo.ticketing.dto.TicketingDto.ReserveRequest;
import com.example.demo.ticketing.dto.TicketingDto.ReserveResponse;
import com.example.demo.ticketing.dto.TicketingDto.TicketingStatusDto;
import com.example.demo.ticketing.service.TicketingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 티켓팅 API 컨트롤러
 *
 * 모든 엔드포인트는 인증 없이 접근 가능합니다 (데모용).
 * WebConfig의 excludePathPatterns에 "/api/ticketing/**" 추가 필요.
 *
 * - GET  /api/ticketing/status  → 현재 티켓팅 현황 조회
 * - POST /api/ticketing/reserve → 티켓 예매 요청
 * - POST /api/ticketing/reset   → 티켓팅 리셋 (관리자용)
 */
@RestController
@RequestMapping("/api/ticketing")
@RequiredArgsConstructor
public class TicketingController {

    private final TicketingService ticketingService;

    /**
     * 현재 티켓팅 현황 조회
     *
     * 이벤트 정보, 총 티켓 수, 예매 수, 잔여 수를 반환합니다.
     */
    @GetMapping("/status")
    public ResponseEntity<TicketingStatusDto> getStatus() {
        return ResponseEntity.ok(ticketingService.getStatus());
    }

    /**
     * 티켓 예매 요청
     *
     * 2단계 동시성 제어를 거쳐 예매를 처리합니다:
     * Layer 1 (Redis Lua Script: 중복 차단 + 재고 감산 원자적 실행) → Layer 2 (Semaphore: DB 쓰기 제한) → DB 저장
     *
     * @param request userId를 포함한 예매 요청
     * @return 예매 결과 (SUCCESS / DUPLICATE / SOLD_OUT / TIMEOUT / ERROR)
     */
    @PostMapping("/reserve")
    public ResponseEntity<ReserveResponse> reserve(@RequestBody ReserveRequest request) {
        ReserveResponse response = ticketingService.reserve(request.userId());
        return ResponseEntity.ok(response);
    }

    /**
     * 티켓팅 리셋 (데모용 - 인증 불필요)
     *
     * DB 예약 전체 삭제 + 인메모리 상태 초기화
     */
    @PostMapping("/reset")
    public ResponseEntity<Void> reset() {
        ticketingService.reset();
        return ResponseEntity.ok().build();
    }
}
