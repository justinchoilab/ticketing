package com.example.demo.ticketing.dto;

/**
 * 티켓팅 관련 DTO 모음
 *
 * 모든 DTO를 하나의 파일에 inner static class/record로 모아 관리합니다.
 */
public class TicketingDto {

    /** 예매 요청 DTO */
    public record ReserveRequest(String userId) {}

    /**
     * 예매 결과 열거형
     *
     * - SUCCESS:   정상 예매 완료
     * - DUPLICATE: 이미 예매한 사용자 (Layer 1: Redis Lua Script SADD에서 차단)
     * - SOLD_OUT:  잔여 티켓 없음 (Layer 1: Redis Lua Script DECR에서 차단)
     * - TIMEOUT:   DB 접근 세마포어 획득 실패 - 과부하 상태 (Layer 2: Semaphore에서 차단)
     * - ERROR:     DB 저장 중 예외 발생 (보상 트랜잭션 처리됨)
     */
    public enum ReserveResult {
        SUCCESS, DUPLICATE, SOLD_OUT, TIMEOUT, ERROR
    }

    /** 예매 응답 DTO */
    public static class ReserveResponse {
        private final ReserveResult result;
        private final String userId;
        private final String receivedAt;
        private final String message;

        private ReserveResponse(ReserveResult result, String userId, String receivedAt, String message) {
            this.result = result;
            this.userId = userId;
            this.receivedAt = receivedAt;
            this.message = message;
        }

        public static ReserveResponse of(ReserveResult result, String userId, String receivedAt, String message) {
            return new ReserveResponse(result, userId, receivedAt, message);
        }

        public static ReserveResponse error(String userId, String receivedAt, String message) {
            return new ReserveResponse(ReserveResult.ERROR, userId, receivedAt, message);
        }

        public ReserveResult getResult() { return result; }
        public String getUserId() { return userId; }
        public String getReceivedAt() { return receivedAt; }
        public String getMessage() { return message; }

    }

    /**
     * 티켓팅 현황 DTO
     *
     * 프론트엔드에서 현재 이벤트 상태를 표시하기 위해 사용합니다.
     */
    public record TicketingStatusDto(
            String name,
            int totalCapacity,
            int reservedCount,
            int remaining
    ) {}
}
