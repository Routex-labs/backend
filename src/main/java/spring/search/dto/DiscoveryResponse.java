package spring.search.dto;

import java.util.List;

/**
 * 탐색 질의 응답. {@code mode}가 화면 분기의 유일한 근거다.
 *
 * <ul>
 *   <li>direct — 명확한 목적지 1건, 질문 없음
 *   <li>clarify — 후보가 넓고 구분력 있는 축이 있음. question + options + 초기 후보 3건
 *   <li>results — 충분히 좁혀졌거나 되물을 축이 없음. 이 목록이 곧 최종 답이다
 *   <li>no_match — 후보 없음
 * </ul>
 *
 * @param source 후보를 무엇으로 잡았는가 — light(이름·카테고리·동의어·intent) | semantic(임베딩).
 *     mode와 축이 다르다: mode는 "얼마나 좁혀졌나", source는 "무엇을 근거로 잡았나"다
 */
public record DiscoveryResponse(
        String mode,
        String query,
        String source,
        String question,
        List<DiscoveryOption> options,
        List<DiscoveryMatchResponse> matches) {}
