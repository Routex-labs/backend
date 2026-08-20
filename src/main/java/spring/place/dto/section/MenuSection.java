package spring.place.dto.section;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** 메뉴판. */
public record MenuSection(String type, List<Item> items) implements PlaceSection {

    public MenuSection(List<Item> items) {
        this("menu", items);
    }

    /**
     * {@code name}과 {@code imageAsset}만 필수다. 나머지는 값이 있을 때만 실린다.
     *
     * <p>선택 키를 빈 문자열로 채워 내보내지 않는 이유: 빈 값이 실리면 클라이언트가 "있는데
     * 비었다"와 "없다"를 구분하는 분기를 갖게 되고, 그 분기가 카드마다 다른 높이로 새어 나온다.
     * {@code badges}는 빈 배열도 싣지 않는다 — 응답에 {@code "badges": []}가 316줄 붙으면
     * 그만큼이 그냥 전송 낭비다.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Item(
            String name,
            String imageAsset,
            String group,
            String category,
            String nameEn,
            String description,
            String price,
            String volume,
            String calories,
            String caffeine,
            String allergens,
            List<String> badges) {}
}
