package spring.common.web;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;

/**
 * Cache-Control/ETag와 If-None-Match 재검증 헬퍼.
 *
 * <p>왜 필요한가 — 벡터 지도는 같은 리소스를 여러 번 요청하는 것이 정상 동작이다. MapLibre는
 * 줌을 오갈 때마다 타일을, 스타일을 다시 로드할 때마다 글리프 범위를 통째로 다시 가져온다.
 * 캐시 헤더가 없으면 그 전부가 서버까지 도달한다. 한글 라벨은 글리프 범위만 40개가 넘고 파일
 * 하나가 170KB대라, 헤더가 없는 것만으로 층 전환마다 수 MB가 다시 흐른다. 서버 쪽 메모리
 * 캐시로는 이 왕복 자체를 없앨 수 없다.
 */
public final class HttpCache {

    private HttpCache() {}

    /**
     * If-None-Match가 주어진 ETag와 맞는지 본다.
     *
     * <p>헤더에는 쉼표로 여러 ETag가 올 수 있고 {@code *}는 아무 표현형과도 맞는다(RFC 9110).
     * 비교는 약한 비교를 쓴다 — 재검증 목적에는 규격상 그쪽이 맞고, {@code W/} 접두사가 붙어
     * 되돌아오는 프록시를 만나도 304를 놓치지 않는다.
     */
    public static boolean matches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        Set<String> candidates = Arrays.stream(ifNoneMatch.split(","))
                .map(String::trim)
                .filter(part -> !part.isEmpty())
                .collect(Collectors.toSet());
        if (candidates.contains("*")) {
            return true;
        }
        return candidates.stream().map(HttpCache::dropWeakPrefix).anyMatch(dropWeakPrefix(etag)::equals);
    }

    /**
     * 캐시 가능한 응답에 붙일 헤더. <b>304에도 같은 헤더를 붙여야</b> 브라우저가 만료 시각을
     * 갱신한다 — 안 붙이면 재검증 직후 곧바로 또 재검증하러 온다.
     *
     * @param immutable <b>URL 자체가 버전일 때만</b> true. 주소가 그대로인 채 내용만 바뀔 수
     *     있는 응답에 붙이면 낡은 데이터가 만료될 때까지 눌러앉는다.
     */
    public static HttpHeaders headers(String etag, long maxAgeSeconds, boolean immutable) {
        HttpHeaders headers = new HttpHeaders();
        String directives = "public, max-age=" + maxAgeSeconds + (immutable ? ", immutable" : "");
        headers.set(HttpHeaders.CACHE_CONTROL, directives);
        headers.set(HttpHeaders.ETAG, etag);
        return headers;
    }

    private static String dropWeakPrefix(String etag) {
        return etag.startsWith("W/") ? etag.substring(2) : etag;
    }
}
