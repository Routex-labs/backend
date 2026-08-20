package spring.place.dto.section;

import java.util.List;

/**
 * 주소 같은 사업자 정보. <b>맨 아래다</b> — 건물 안에서 길을 찾는 사용자에게 건물 주소는
 * 이미 아는 정보라 위쪽 자리를 쓸 값어치가 없다.
 */
public record BusinessInfoSection(String type, List<Item> items) implements PlaceSection {

    public BusinessInfoSection(List<Item> items) {
        this("businessInfo", items);
    }

    public record Item(String label, String value) {}
}
