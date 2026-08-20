package spring.common.config;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 교차 출처 허용 정책. <b>운영에서는 절대 와일드카드를 쓰지 않는다.</b>
 *
 * <ul>
 *   <li>{@code navigation.cors.origins}가 있으면(개발·운영 무관) 그 화이트리스트만 허용한다.
 *   <li>없고 개발 환경이면 localhost의 임의 포트만 허용한다 — Flutter 웹은 실행마다 포트가 바뀐다.
 *   <li>없고 운영 환경이면 교차 출처를 전부 막고 경고를 남긴다(동일 출처 요청은 그대로 동작).
 * </ul>
 *
 * <p>{@code *}가 아니라 패턴으로 localhost·127.0.0.1에 한정하는 것이 개발 기본값의 요점이다.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class CorsConfig implements WebMvcConfigurer {

    /** Flutter 웹이 실행마다 다른 포트를 쓰므로 포트만 열어 둔다. 호스트는 고정이다. */
    private static final List<String> DEV_ORIGIN_PATTERNS =
            List.of("http://localhost:[*]", "http://127.0.0.1:[*]", "http://localhost", "http://127.0.0.1");

    private final CorsProperties corsProperties;
    private final Environment environment;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = corsProperties.origins();
        if (origins != null && !origins.isEmpty()) {
            registry.addMapping("/**").allowedOrigins(origins.toArray(String[]::new)).allowedMethods("*").allowedHeaders("*");
            return;
        }
        if (!environment.matchesProfiles("prod")) {
            registry.addMapping("/**")
                    .allowedOriginPatterns(DEV_ORIGIN_PATTERNS.toArray(String[]::new))
                    .allowedMethods("*")
                    .allowedHeaders("*");
            return;
        }
        // 운영인데 origin이 하나도 지정되지 않았다. 교차 출처를 막되(매핑 미등록) 잘못된 배포를
        // 알아채도록 경고를 남긴다.
        log.warn("운영(prod 프로파일)인데 navigation.cors.origins가 비어 있다 — "
                + "교차 출처 요청을 전부 차단한다. Flutter 앱 도메인을 지정하라.");
    }

    /** @param origins 허용할 출처 화이트리스트. 비면 위 규칙으로 떨어진다. */
    @ConfigurationProperties(prefix = "navigation.cors")
    public record CorsProperties(List<String> origins) {}
}
