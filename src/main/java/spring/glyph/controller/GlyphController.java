package spring.glyph.controller;

import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import spring.common.config.CacheProperties;
import spring.common.web.HttpCache;

/**
 * MapLibre 글리프(SDF 폰트) 서빙.
 *
 * <pre>GET /fonts/{fontstack}/{start}-{end}.pbf → 글리프 범위 파일</pre>
 *
 * <p>MapLibre는 심볼 레이어 텍스트에 필요한 256자 단위 범위를 그때그때 요청한다. 응답이
 * 실패하면 심볼 레이어의 레이아웃이 끝나지 않아 <b>같은 타일의 fill 레이어까지 통째로</b>
 * 렌더링되지 않는다 — 글리프는 지도 표시의 선택 사항이 아니다.
 *
 * <p>파일은 classpath의 {@code fonts/<fontstack>/}에 커밋되어 있다. 타일과 같은 출처에서
 * 내려주므로 외부 폰트 CDN 없이 오프라인·사내망에서도 동작한다.
 */
@RestController
@RequestMapping("/fonts")
@RequiredArgsConstructor
public class GlyphController {

    /** 빈 glyphs 메시지. 내용이 항상 같아 ETag가 상수여도 된다. */
    private static final byte[] EMPTY_GLYPHS = new byte[0];

    private static final String EMPTY_GLYPHS_ETAG = "W/\"empty\"";

    private final CacheProperties cacheProperties;

    @GetMapping("/{fontstack}/{start:\\d+}-{end:\\d+}.pbf")
    public ResponseEntity<byte[]> getGlyphRange(
            @PathVariable String fontstack,
            @PathVariable int start,
            @PathVariable int end,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {

        if (start < 0 || end < start || end > 65535 || end - start != 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid glyph range");
        }

        // fontstack에는 쉼표로 여러 폰트가 올 수 있다(text-font 배열 그대로).
        // 우리가 가진 첫 폰트를 쓰고, 하나도 없으면 빈 응답으로 떨어진다.
        for (String rawName : fontstack.split(",")) {
            String name = rawName.strip();
            // 경로 조작 방지: 디렉터리 이름 한 칸만 허용한다.
            if (name.isEmpty() || name.contains("/") || name.contains("\\") || name.startsWith(".")) {
                continue;
            }
            Resource resource = new ClassPathResource("fonts/%s/%d-%d.pbf".formatted(name, start, end));
            if (!resource.exists()) {
                continue;
            }
            try {
                // 내용 대신 길이로 ETag를 만든다. 재검증을 170KB 읽기 없이 304로 끝내려는 것이다.
                // 약한 ETag인 이유는 TileController와 같다 — 강한 ETag는 gzip을 막는다.
                String etag = "W/\"%s-%d-%d-%x\"".formatted(name, start, end, resource.contentLength());
                if (HttpCache.matches(ifNoneMatch, etag)) {
                    return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                            .headers(HttpCache.headers(etag, cacheProperties.glyphMaxAge(), false))
                            .build();
                }
                return ResponseEntity.ok()
                        .headers(HttpCache.headers(etag, cacheProperties.glyphMaxAge(), false))
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(resource.getContentAsByteArray());
            } catch (IOException readFailure) {
                // 길이나 내용을 못 읽으면 다음 폰트로 넘어간다. 여기서 500을 내면 지도 전체가
                // 안 그려지므로, 최악이어도 빈 글리프로 떨어지는 편이 낫다.
                continue;
            }
        }

        // 없는 범위(한자 등)도 캐시한다 — 안 그러면 "비어 있음"을 확인하려고 매번 다시
        // 물어보게 되고, 요청 수로는 있는 범위와 똑같이 비싸다. 404를 주지 않는 이유는
        // MapLibre가 404를 스타일 오류로 보고 심볼 레이아웃을 멈추기 때문이다.
        if (HttpCache.matches(ifNoneMatch, EMPTY_GLYPHS_ETAG)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .headers(HttpCache.headers(EMPTY_GLYPHS_ETAG, cacheProperties.glyphMaxAge(), false))
                    .build();
        }
        return ResponseEntity.ok()
                .headers(HttpCache.headers(EMPTY_GLYPHS_ETAG, cacheProperties.glyphMaxAge(), false))
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(EMPTY_GLYPHS);
    }
}
