package spring.search.dto;

import java.util.List;
import java.util.Map;

/**
 * 탐색 질의 요청 본문. 목적지와 계약이 다르다.
 *
 * <p>서버 대화 세션을 만들지 않는다 — 클라이언트가 매 요청에 원문과 현재 선택을 다시 보내는
 * stateless 계약이다.
 *
 * @param selectedFacets 사용자가 clarify 질문에서 고른 값
 * @param showAll clarify에서 "전체 보기"를 눌렀을 때만 true. <b>primitive가 아니라 Boolean이다</b> —
 *     Jackson 3은 없는 필드를 primitive에 넣지 못해 400을 내는데, 클라이언트는 이 키를 보내지 않는다
 */
public record AiRequest(
        String text,
        String buildingId,
        String currentFloorId,
        Map<String, List<String>> selectedFacets,
        Boolean showAll) {}
