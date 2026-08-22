package spring.event.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * 행사 한 건.
 *
 * <p><b>기간은 문자열(YYYY-MM-DD)로 둔다.</b> 이 형식은 사전순이 곧 날짜순이라 비교에 파싱이
 * 필요 없고, 파싱을 하면 시간대가 끼어들어 "오늘"의 경계가 기기 설정에 따라 흔들린다.
 *
 * @param storeId 안내를 걸 매장 id. null이면 장소 문구만 있고 지도로는 못 간다 — 그래도 목록에서
 *     빼지 않는다. 감추면 사용자는 그 행사가 없는 줄 안다
 * @param details 포스터 아래로 이어지는 본문. <b>타입을 세우지 않는다</b> — 원본이 블록 종류를
 *     늘려도 응답이 통째로 깨지면 안 되고, 그릴 수 없는 것은 화면이 조용히 건너뛴다. 종류를
 *     여기서 못 박으면 새 종류가 오는 날 파싱이 아니라 API가 죽는다
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BuildingEventResponse(
        String title,
        String start,
        String end,
        String place,
        String diary,
        String floor,
        String storeId,
        String image,
        List<Map<String, Object>> details) {}
