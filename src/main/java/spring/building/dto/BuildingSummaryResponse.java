package spring.building.dto;

import java.util.List;

/**
 * 건물 목록 한 줄. 앱이 건물을 고르고 첫 층을 여는 데 필요한 최소 정보다.
 *
 * <p>{@code floors}는 엘리베이터 버튼판 순서(위층 → 아래층)일 뿐 기본 층이 아니다.
 * 기본 층은 {@code defaultFloor}로 분리해 내려준다 — 예전에 클라이언트가 목록 첫
 * 항목을 초기 층으로 쓰다가 지하층이 생기자 앱이 최상층으로 열린 적이 있다.
 */
public record BuildingSummaryResponse(
        String id, String name, List<String> floors, String defaultFloor) {}
