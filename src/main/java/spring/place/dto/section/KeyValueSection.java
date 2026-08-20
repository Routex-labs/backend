package spring.place.dto.section;

import java.util.List;

/** 라벨·값 목록. 값이 빈 항목은 싣지 않는다 — 실으면 클라이언트에 "빈 줄" 분기가 생긴다. */
public record KeyValueSection(String type, List<Item> items) implements PlaceSection {

    public KeyValueSection(List<Item> items) {
        this("keyValue", items);
    }

    public record Item(String label, String value) {}
}
