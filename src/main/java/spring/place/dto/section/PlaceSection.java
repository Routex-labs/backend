package spring.place.dto.section;


/**
 * 상세 시트의 섹션 하나.
 *
 * <p>각 섹션은 자기 렌더링에 필요한 데이터를 전부 들고 있다 — 다른 섹션이나 코어 필드를
 * 참조해야 그릴 수 있는 섹션은 만들지 않는다.
 *
 * <p>JSON의 {@code type} 판별자는 각 레코드의 첫 컴포넌트가 상수로 들고 있다. 이 응답은
 * 내려보내기만 하고 되읽지 않으므로 Jackson 다형 설정이 필요 없다.
 */
public sealed interface PlaceSection
        permits HeroSection,
                NoticeSection,
                HoursSection,
                TagsSection,
                SummarySection,
                MenuSection,
                KeyValueSection,
                DemoInfoSection,
                LinksSection,
                BusinessInfoSection,
                MapSection {

    String type();
}
