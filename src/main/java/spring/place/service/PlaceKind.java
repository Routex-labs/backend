package spring.place.service;

import java.util.Set;

/**
 * 소분류 하나로 장소 종류를 정한다. <b>이 클래스가 kind 규칙의 단일 출처다.</b>
 *
 * <p>매장 색인(store-index)도 같은 값을 실어 보내야 한다. 규칙을 그쪽에 베끼면 "상세는 안
 * 열리는데 목록에는 있는" 상태가 조용히 생긴다.
 */
public final class PlaceKind {

    private PlaceKind() {}

    public static final String STORE = "store";
    public static final String FACILITY = "facility";
    public static final String EXCLUDED = "excluded";

    /**
     * 상세 시트를 열지 않는 소분류. 주차 787 + 에스컬레이터 152 + 엘리베이터 68 = 1,007건으로
     * 전체 1,640건의 61%다. 이들은 "설명"이라는 개념이 성립하지 않는다.
     *
     * <p>404로 만들지 않는 이유: 존재하지 않는 것과 상세가 없는 것은 다르고, 클라이언트가
     * kind를 보고 시트를 열지 말지 정하는 편이 id 규칙을 클라이언트에 심는 것보다 낫다.
     */
    private static final Set<String> EXCLUDED_SUBCATEGORIES = Set.of("주차", "에스컬레이터", "엘리베이터");

    /** 사람이 설명을 쓰지 않고 위치 안내만 파생하는 시설. 화장실·락커·교통·생활편의 92건이다. */
    private static final Set<String> FACILITY_SUBCATEGORIES = Set.of("화장실", "생활편의", "교통", "락커");

    /** @param subcategory null이면 일반 매장으로 본다. */
    public static String of(String subcategory) {
        if (subcategory == null) {
            return STORE;
        }
        if (EXCLUDED_SUBCATEGORIES.contains(subcategory)) {
            return EXCLUDED;
        }
        return FACILITY_SUBCATEGORIES.contains(subcategory) ? FACILITY : STORE;
    }
}
