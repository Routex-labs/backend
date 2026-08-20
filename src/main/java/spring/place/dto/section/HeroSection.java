package spring.place.dto.section;

import java.util.List;

/** 대표 사진. 순서상 맨 위다 — 사진으로 매장을 알아본 다음 설명을 읽는다. */
public record HeroSection(String type, List<Item> items) implements PlaceSection {

    public HeroSection(List<Item> items) {
        this("hero", items);
    }

    public record Item(String localAsset) {}
}
