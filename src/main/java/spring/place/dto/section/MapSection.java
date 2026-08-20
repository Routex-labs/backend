package spring.place.dto.section;

import java.util.List;
import spring.common.geometry.LocalPoint;

/** 매장 폴리곤 미리보기. 폴리곤이 없는 매장(14건)은 이 섹션 자체를 만들지 않는다. */
public record MapSection(String type, List<LocalPoint> polygonLocalM) implements PlaceSection {

    public MapSection(List<LocalPoint> polygonLocalM) {
        this("map", polygonLocalM);
    }
}
