package spring.search.dto;

/**
 * 목적지 질의 응답. 안내할 매장 1건만 담는다.
 *
 * @param status ok · ok_no_route · no_match · ambiguous
 * @param match 최적 1건. 확정하지 못하면 null — 클라이언트가 그때 /query/ai 목록 계약으로 이어 간다
 */
public record DestinationResponse(String status, String query, QueryMatchResponse match) {}
