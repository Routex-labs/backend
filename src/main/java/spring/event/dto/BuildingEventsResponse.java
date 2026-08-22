package spring.event.dto;

import java.util.List;

/**
 * 한 건물의 행사 한 벌.
 *
 * <p><b>"오늘 열리는 것"을 서버가 고르지 않는다.</b> 서버가 날짜로 걸러 내려보내면 그 응답은
 * 만들어진 순간부터 틀리기 시작하고, 캐시가 걸려 있어 더 오래 살아남는다. 서버가 주는 것은
 * 기간이라는 규칙뿐이고 지금 날짜와의 비교는 화면이 한다 — 영업시간(HoursSection)과 같은 판단이다.
 *
 * <p>기간을 기기 로컬 날짜로 재는 것도 같은 이유다. 사용자가 서 있는 곳이 곧 기준이라, 서버의
 * 시간대로 "오늘"을 정하면 자정 근처에서 둘이 갈린다.
 *
 * @param capturedOn 원본을 받아 온 날. <b>자동 갱신이 없다</b> — 화면이 "언제 기준인지" 밝히고,
 *     이 값이 낡으면 목록이 비는 것으로 드러난다
 */
public record BuildingEventsResponse(
        String capturedOn, List<EventDiaryResponse> diaries, List<BuildingEventResponse> events) {}
