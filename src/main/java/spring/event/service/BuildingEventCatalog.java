package spring.event.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import spring.event.dto.BuildingEventResponse;
import spring.event.dto.BuildingEventsResponse;
import spring.event.dto.EventDiaryResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * 행사 스냅샷 로더 — 건물마다 파일 하나를 읽어 둔다.
 *
 * <p><b>왜 DB 테이블이 아니라 JSON인가:</b> 매장 상세 오버레이와 같은 이유다({@code
 * PlaceOverlays}). 원본(Studio)에 행사 정보가 아예 없어 사람이 모으는 수밖에 없는데, 사람이 모은
 * 것을 DB에 넣으면 재시드할 때마다 날아가거나 시드 스크립트가 편집을 덮어쓴다.
 *
 * <p><b>매장 행에는 손대지 않는다.</b> 한 칸에 행사가 동시에 여럿 열리고(B1 식품 행사장에 지금
 * 네 건이 겹친다) 끝나면 되돌려야 하는데, 매장 칸을 덮어쓰면 원래 값을 아무도 모른다.
 *
 * <p><b>파일이 없는 건물은 빈 값이 아니라 없음이다.</b> 행사를 모아 둔 건물이 하나뿐이라,
 * 나머지에 빈 목록을 내려보내면 "행사가 없는 건물"과 "아직 안 모은 건물"이 같은 화면이 된다.
 * 그 구분은 호출부가 404로 옮긴다.
 *
 * <p>기동 시 한 번만 읽는다 — 이 파일들은 배포 산출물 안에 있어 프로세스가 사는 동안 바뀌지 않는다.
 */
@Component
public class BuildingEventCatalog {

    /** 원본 조사 메모처럼 응답이 아닌 값은 {@code _}로 시작한다. */
    private static final String META_PREFIX = "_";

    private static final String CAPTURED_ON = "_captured";

    private final Map<String, BuildingEventsResponse> byBuilding;

    BuildingEventCatalog(ObjectMapper objectMapper) {
        this.byBuilding = load(objectMapper);
    }

    /** 파일 이름이 곧 건물 id다. 없으면 {@link Optional#empty()}. */
    public Optional<BuildingEventsResponse> forBuilding(String buildingId) {
        return Optional.ofNullable(byBuilding.get(buildingId));
    }

    private static Map<String, BuildingEventsResponse> load(ObjectMapper objectMapper) {
        Resource[] resources;
        try {
            resources = new PathMatchingResourcePatternResolver().getResources("classpath:events/*.json");
        } catch (IOException missingDirectory) {
            return Map.of();
        }
        Map<String, BuildingEventsResponse> merged = new HashMap<>();
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null || filename.startsWith(META_PREFIX)) {
                continue;
            }
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = objectMapper.readValue(resource.getContentAsByteArray(), Map.class);
                merged.put(filename.replaceFirst("\\.json$", ""), toResponse(payload));
            } catch (IOException | RuntimeException unreadable) {
                // 파일 한 건이 깨졌다고 API 전체를 죽이지 않는다. 그 건물만 없는 것으로 남고,
                // 형식 검증은 시드 쪽(fastapi 저장소)이 맡는다.
            }
        }
        return Map.copyOf(merged);
    }

    private static BuildingEventsResponse toResponse(Map<String, Object> payload) {
        return new BuildingEventsResponse(
                string(payload, CAPTURED_ON), diaries(list(payload, "diaries")), events(list(payload, "events")));
    }

    private static List<EventDiaryResponse> diaries(List<Map<String, Object>> raw) {
        List<EventDiaryResponse> diaries = new ArrayList<>(raw.size());
        for (Map<String, Object> page : raw) {
            diaries.add(new EventDiaryResponse(string(page, "key"), string(page, "title"), string(page, "image")));
        }
        return List.copyOf(diaries);
    }

    private static List<BuildingEventResponse> events(List<Map<String, Object>> raw) {
        List<BuildingEventResponse> events = new ArrayList<>(raw.size());
        for (Map<String, Object> event : raw) {
            events.add(new BuildingEventResponse(
                    string(event, "title"),
                    string(event, "start"),
                    string(event, "end"),
                    string(event, "place"),
                    string(event, "diary"),
                    string(event, "floor"),
                    string(event, "storeId"),
                    string(event, "image"),
                    list(event, "details")));
        }
        return List.copyOf(events);
    }

    private static String string(Map<String, Object> from, String key) {
        return from.get(key) instanceof String value ? value : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> from, String key) {
        if (!(from.get(key) instanceof List<?> raw)) {
            return List.of();
        }
        List<Map<String, Object>> typed = new ArrayList<>(raw.size());
        for (Object item : raw) {
            if (item instanceof Map<?, ?> map) {
                typed.add((Map<String, Object>) map);
            }
        }
        return List.copyOf(typed);
    }
}
