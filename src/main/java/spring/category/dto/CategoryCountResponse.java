package spring.category.dto;

/**
 * 카테고리 필터가 쓰는 (층·대분류·소분류)별 매장 수 한 줄.
 *
 * <p>지도 위 pill 목록과 "이 층 N곳" 안내를 이 응답 하나로 만든다. 이게 없을 때 클라이언트는
 * 그 둘을 만들려고 <b>12개 층 지도를 통째로</b> 받았다 — 정작 쓰는 건 세 문자열과 개수뿐인데
 * 매장 폴리곤·좌표·그래프가 전부 따라왔다.
 */
public record CategoryCountResponse(String floor, String category, String subcategory, long count) {}
