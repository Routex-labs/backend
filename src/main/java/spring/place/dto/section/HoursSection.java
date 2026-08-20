package spring.place.dto.section;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/**
 * 영업시간. 소개·메뉴보다 위다 — 상세를 연 사람이 가장 먼저 정하는 것은 "지금 갈 수 있나"이고,
 * 그 판단이 무슨 매장인지 읽는 것보다 앞선다.
 *
 * <p><b>판정 문자열을 만들지 않는다.</b> 서버가 "영업 중"을 계산해 넣으면 그 값은 응답을 만든
 * 순간부터 틀리기 시작하고, ETag 캐시가 걸려 있어 더 오래 살아남는다. 서버가 내려보내는 것은
 * 규칙뿐이고 지금 시각과의 비교는 화면이 한다.
 *
 * @param utcOffsetMinutes 매장이 전부 서울에 있어 기본값이 뜻을 갖지만 <b>항상</b> 실어 보낸다 —
 *     클라이언트와 서버가 각자 기본값을 들면 둘이 갈라진다.
 */
public record HoursSection(
        String type,
        Map<String, List<Interval>> weekly,
        List<Exception> exceptions,
        int utcOffsetMinutes,
        String confirmedAt,
        String source)
        implements PlaceSection {

    public HoursSection(
            Map<String, List<Interval>> weekly,
            List<Exception> exceptions,
            int utcOffsetMinutes,
            String confirmedAt,
            String source) {
        this("hours", weekly, exceptions, utcOffsetMinutes, confirmedAt, source);
    }

    public record Interval(String open, String close) {}

    /** {@code note}는 없으면 키 자체를 내보내지 않는다. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Exception(String date, boolean closed, List<Interval> intervals, String note) {}
}
