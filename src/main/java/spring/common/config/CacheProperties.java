package spring.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 캐시 수명(초). 파이썬 {@code app/core/config.py}의 같은 이름 설정과 값이 같다.
 *
 * @param tileMaxAge 버전 토큰 없는 타일. 짧게 둔다 — 재시드하면 내용이 바뀔 수 있다.
 * @param tileVersionedMaxAge {@code ?v=}가 붙은 타일. URL이 곧 버전이라 1년 + immutable이다.
 * @param glyphMaxAge 글리프 범위 파일. 폰트는 배포 전까지 바뀌지 않는다.
 */
@ConfigurationProperties(prefix = "navigation.cache")
public record CacheProperties(long tileMaxAge, long tileVersionedMaxAge, long glyphMaxAge) {

    public CacheProperties {
        tileMaxAge = tileMaxAge == 0 ? 60 : tileMaxAge;
        tileVersionedMaxAge = tileVersionedMaxAge == 0 ? 31_536_000 : tileVersionedMaxAge;
        glyphMaxAge = glyphMaxAge == 0 ? 86_400 : glyphMaxAge;
    }
}
