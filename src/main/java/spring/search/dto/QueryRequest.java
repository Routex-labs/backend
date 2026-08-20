package spring.search.dto;

/**
 * 목적지·정보 질의 요청 본문.
 *
 * <p>파이썬은 destination과 info의 요청 모델을 따로 선언했지만 모양이 같고 갈라질 이유가 없어
 * 하나로 뒀다.
 *
 * @param currentFloorId 층 라벨("B2")과 내부 id("FL-...") 둘 다 받는다
 */
public record QueryRequest(String text, String buildingId, String currentFloorId) {}
