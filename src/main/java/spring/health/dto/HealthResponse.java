package spring.health.dto;

/** liveness 응답. 프로세스가 응답을 돌려줄 수 있으면 항상 {@code status="ok"}다. */
public record HealthResponse(String status) {

    public static HealthResponse ok() {
        return new HealthResponse("ok");
    }
}
