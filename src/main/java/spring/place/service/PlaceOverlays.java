package spring.place.service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 매장 상세 오버레이 로더 — 표시용 수작업 데이터를 읽어 온다.
 *
 * <p><b>왜 표시용 값을 DB 컬럼이 아니라 JSON에 두는가:</b> 원본(Studio)에 표시용 정보가 아예
 * 없어 사람이 쓰는 수밖에 없는데, 사람이 쓴 것을 DB에 넣으면 재시드할 때마다 날아가거나 시드
 * 스크립트가 편집을 덮어쓴다.
 *
 * <p><b>조인 키는 항상 id다.</b> 이름은 유일 키가 아니다 — 에스컬레이터 152건·엘리베이터
 * 60건·화장실 19건이 동명이다. 오버레이가 적어 두는 {@code name}은 사람이 diff를 읽기 위한
 * 중복 정보이며 병합에 쓰지 않는다.
 *
 * <p><b>파일이 없어도 빈 맵이다.</b> 상세 API가 먼저 서고 데이터가 나중에 채워지는 순서라,
 * 파일이 하나도 없는 상태에서도 API는 200으로 코어를 반환해야 한다.
 *
 * <p>파이썬은 요청마다 디렉터리를 다시 읽지만 여기서는 기동 시 한 번만 읽는다 — 이 파일들은
 * 배포 산출물 안에 있어 프로세스가 사는 동안 바뀌지 않는다.
 */
@Component
public class PlaceOverlays {

    /** {@code _}로 시작하는 파일은 스키마 선언 같은 메타데이터라 오버레이가 아니다. */
    private static final String META_PREFIX = "_";

    private final Map<String, Map<String, Object>> overlays;

    PlaceOverlays(ObjectMapper objectMapper) {
        this.overlays = load(objectMapper);
    }

    /** 오버레이가 없는 매장은 빈 맵. null을 돌려주지 않는다 — 호출부에 분기가 생긴다. */
    public Map<String, Object> forPlace(String placeId) {
        return overlays.getOrDefault(placeId, Map.of());
    }

    private static Map<String, Map<String, Object>> load(ObjectMapper objectMapper) {
        Map<String, Map<String, Object>> merged = new HashMap<>();
        Resource[] resources;
        try {
            resources = new PathMatchingResourcePatternResolver().getResources("classpath:store_details/*.json");
        } catch (IOException missingDirectory) {
            return Map.of();
        }
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null || filename.startsWith(META_PREFIX)) {
                continue;
            }
            try {
                Map<String, Object> payload = objectMapper.readValue(resource.getContentAsByteArray(), Map.class);
                payload.forEach((placeId, overlay) -> {
                    if (overlay instanceof Map<?, ?> map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> typed = (Map<String, Object>) map;
                        // 같은 id가 두 파일에 있으면 나중 파일이 이긴다. 정상 상태가 아니므로
                        // 검증 스크립트가 따로 잡는다 — 여기서 예외를 던지면 데이터 한 건
                        // 때문에 API 전체가 죽는다.
                        merged.put(placeId, typed);
                    }
                });
            } catch (IOException | RuntimeException unreadable) {
                // 같은 이유로 파일 하나가 깨져도 나머지는 살린다.
                continue;
            }
        }
        return Map.copyOf(merged);
    }
}
