package spring.health.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import spring.health.dto.HealthResponse;
import spring.health.dto.ReadinessResponse;

/**
 * liveness와 readiness를 나눈다.
 *
 * <pre>
 *   GET /health       → liveness (하위호환)
 *   GET /health/live  → liveness 별칭
 *   GET /health/ready → readiness. DB가 안 되면 503
 * </pre>
 *
 * <p>Actuator를 쓰지 않는 이유: 이 세 경로의 응답 스키마를 Flutter가 파싱한다.
 * Actuator JSON은 모양이 달라 대체가 안 되고, 넣으면 아무도 안 부르는 두 번째 헬스
 * 시스템이 생긴다.
 */
@RestController
@RequiredArgsConstructor
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    /** liveness는 의존성을 보지 않는다 — "프로세스가 죽었으면 재시작하라"는 신호다. */
    @GetMapping("/health")
    public HealthResponse health() {
        return HealthResponse.ok();
    }

    @GetMapping("/health/live")
    public HealthResponse healthLive() {
        return HealthResponse.ok();
    }

    /** readiness는 "트래픽을 받아도 되냐"라 DB를 실제로 찔러 본다. */
    @GetMapping("/health/ready")
    public ReadinessResponse healthReady() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        } catch (RuntimeException error) {
            // 어떤 DB 오류든 "준비 안 됨"으로 본다. 503이면 로드밸런서가 트래픽에서 뺀다.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Database not ready", error);
        }
        return ReadinessResponse.ready();
    }
}
