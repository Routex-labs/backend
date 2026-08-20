package spring.place.dto;

import java.util.List;
import spring.place.dto.section.PlaceSection;

/**
 * 상세 응답 본체.
 *
 * <p>{@code sections}가 비어 있어도 이 응답만으로 화면이 성립해야 한다 — 그것이 "정보 없음
 * 카드"를 만들지 않기 위한 조건이다.
 *
 * @param kind store/facility/excluded. 클라이언트가 아이콘과 기본 액션을 고르는 키이자, 상세
 *     시트를 열지 말지 판단하는 근거다.
 * @param subtitle 예: "B2 · 카페·베이커리"
 */
public record PlaceDetailResponse(
        String kind,
        String id,
        String name,
        String subtitle,
        String category,
        String subcategory,
        PlaceLocationResponse location,
        List<PlaceActionResponse> actions,
        List<PlaceSection> sections,
        ProvenanceResponse provenance) {}
