package spring.search.dto;

import java.util.List;

/**
 * 정보 질의 응답. 대표 1건에 "그게 어느 층들에 있는지"를 더한다.
 *
 * @param floors 대상이 존재하는 층 이름들(level 오름차순)
 */
public record InfoResponse(String status, String query, QueryMatchResponse match, List<String> floors) {}
