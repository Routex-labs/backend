package spring.health.dto;

/**
 * readiness 응답. liveness와 달리 의존성을 확인한다.
 *
 * <p>{@code embeddingModel}은 {@code /query/ai}의 워밍 상태다. search 패키지를 이식하기
 * 전이라 늘 "unknown"이고, 준비 여부를 막지는 않는다(DB만 필수).
 */
public record ReadinessResponse(String status, String database, String embeddingModel) {

    public static ReadinessResponse ready() {
        return new ReadinessResponse("ready", "ok", "unknown");
    }
}
