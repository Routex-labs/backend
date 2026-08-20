package spring.tile.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import spring.common.config.CacheProperties;
import spring.common.web.HttpCache;
import spring.tile.service.TileService;

/**
 * 층 벡터 타일.
 *
 * <pre>GET /buildings/{id}/floors/{floor}/tiles/{z}/{x}/{y}.mvt[?v=]</pre>
 *
 * <p>같은 타일을 여러 번 요청하는 것은 MapLibre의 정상 동작이다(줌 전환·층 재방문·부모 타일
 * 프리페치). 캐시 헤더가 없으면 그 전부가 서버까지 온다.
 */
@RestController
@RequestMapping("/buildings")
@RequiredArgsConstructor
public class TileController {

    private static final MediaType MVT = MediaType.parseMediaType("application/vnd.mapbox-vector-tile");

    private final TileService tileService;
    private final CacheProperties cacheProperties;

    /**
     * @param v 버전 토큰({@code /buildings/{id}}의 tile_revision). <b>값 자체는 검사하지 않는다</b> —
     *     서버가 아는 것은 "이 주소로 온 바이트"뿐이고, 낡은 v로 온 요청은 낡은 주소에 현재 내용이
     *     담길 뿐 다음 실행에서 새 주소로 갈아탄다.
     */
    @GetMapping("/{buildingId}/floors/{floorName}/tiles/{z}/{x}/{y}.mvt")
    public ResponseEntity<byte[]> getFloorTile(
            @PathVariable String buildingId,
            @PathVariable String floorName,
            @PathVariable int z,
            @PathVariable int x,
            @PathVariable int y,
            @RequestParam(required = false) String v,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch) {

        byte[] tile;
        try {
            tile = tileService
                    .renderFloorTile(buildingId, floorName, z, x, y)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Floor not found"));
        } catch (IllegalArgumentException badCoordinates) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, badCoordinates.getMessage(), badCoordinates);
        }

        // ETag는 이미 만들어진 바이트에서 뽑으므로 추가 쿼리가 없다.
        //
        // **약한 ETag(W/)를 쓴다.** 강한 ETag는 바이트가 정확히 같다는 뜻이라, Tomcat이
        // 그런 응답을 gzip하지 않는다(맞는 동작이다 — 압축하면 바이트가 달라진다).
        // 타일은 gzip으로 평균 48% 줄어드는 것이 훨씬 큰 이득이고, 재검증에는 약한 비교로
        // 충분하다. 실제로 이 접두사를 붙이기 전까지 타일이 압축되지 않고 나갔다.
        String etag = "W/\"%s\"".formatted(digest(tile));

        // ?v=가 붙어 있으면 URL이 곧 버전이다. 내용이 바뀌면 클라이언트가 새 주소로 오므로 길게
        // 잡고 immutable을 준다 — 재검증조차 안 나가 층을 오갈 때의 304 왕복이 사라진다.
        boolean versioned = v != null && !v.isBlank();
        long maxAge = versioned ? cacheProperties.tileVersionedMaxAge() : cacheProperties.tileMaxAge();

        if (HttpCache.matches(ifNoneMatch, etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .headers(HttpCache.headers(etag, maxAge, versioned))
                    .build();
        }
        return ResponseEntity.ok()
                .headers(HttpCache.headers(etag, maxAge, versioned))
                .contentType(MVT)
                .body(tile);
    }

    /** 파이썬은 blake2b-128을 쓰지만 JDK에 없다. ETag는 불투명 값이라 알고리즘이 달라도 무방하다. */
    private static String digest(byte[] tile) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(tile);
            byte[] truncated = new byte[16];
            System.arraycopy(hash, 0, truncated, 0, 16);
            return HexFormat.of().formatHex(truncated);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
