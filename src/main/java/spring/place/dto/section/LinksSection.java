package spring.place.dto.section;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** 외부 링크. 운영 정보 아래에 둔다 — 고른 뒤에 더 알아보려는 사람이 찾는 자리다. */
public record LinksSection(String type, List<Item> items) implements PlaceSection {

    public LinksSection(List<Item> items) {
        this("links", items);
    }

    /** {@code iconAsset}은 없으면 키 자체를 내보내지 않는다. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Item(String label, String url, String iconAsset) {}
}
