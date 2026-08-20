package spring.place.dto.section;

import java.util.List;

/**
 * 영업시간·대표번호처럼 낡는 값. 출처와 확인일을 항목마다 함께 싣는다 — 출처 없는 값을
 * 내보내지 않겠다는 결정을 스키마로 강제하는 장치다.
 */
public record DemoInfoSection(String type, List<Item> items) implements PlaceSection {

    public DemoInfoSection(List<Item> items) {
        this("demoInfo", items);
    }

    public record Item(String label, String value, String source, String confirmedAt) {}
}
