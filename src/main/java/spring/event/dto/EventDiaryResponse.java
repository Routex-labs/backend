package spring.event.dto;

/**
 * 원본 이슈 다이어리 쪽 한 장. 화면에서는 카드 하나다.
 *
 * <p><b>행사가 아니다.</b> 쪽은 기간도 장소도 갖지 않고 그 안의 행사들이 갖는다. 쪽이 오늘
 * 뜨는지는 자식으로 판정하며, 그 판정은 서버가 아니라 화면이 한다({@link BuildingEventsResponse}).
 *
 * @param key 원본 쪽을 가리키는 값(popup·tasty·shopping). 행사의 {@code diary}가 이걸 가리킨다
 * @param title 원본이 카드에 적어 둔 이름. <b>번역하지 않는다</b> — 대표 사진 안에 같은 글자가
 *     그려져 있어서, 한글로 바꾸면 그림과 글이 다른 이름이 된다
 * @param image 대표 사진. 클라이언트 자산 경로다 — 매장 상세 오버레이와 같은 방식이며, 이유는
 *     {@code PlaceOverlays}에 적혀 있다
 */
public record EventDiaryResponse(String key, String title, String image) {}
