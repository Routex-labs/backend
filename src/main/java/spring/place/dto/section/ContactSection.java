package spring.place.dto.section;

/**
 * 연락처. 영업시간 바로 아래다 — "지금 여나"와 "물어볼 수 있나"는 같은 판단의 앞뒤이고,
 * 무슨 매장인지 읽기 전에 정하는 것들이다.
 *
 * <p><b>자유 문자열이 아니라 구조체인 이유.</b> 오버레이 스키마는 `keyValue`·`businessInfo`에
 * "전화번호" 라벨을 금지한다. 출처가 없어 확인할 방법이 없고, 번호가 바뀌면 조용히 거짓이 되기
 * 때문이다. 그 금지를 푸는 대신 옆에 검증된 길을 냈다 — 영업시간이 `hours`로 나간 것과 같은
 * 판단이다. {@code source}와 {@code confirmedAt}이 필수라 다시 열어 확인할 수 있다.
 *
 * <p>번호 형식을 서버가 다듬지 않는다. 출처 페이지에 적힌 표기를 그대로 옮기는 것이
 * "그 페이지에서 확인했다"는 말을 지키는 방법이고, 걸기 좋은 형태로 바꾸는 것은 화면 몫이다.
 */
public record ContactSection(String type, String tel, String confirmedAt, String source)
        implements PlaceSection {

    public ContactSection(String tel, String confirmedAt, String source) {
        this("contact", tel, confirmedAt, source);
    }
}
