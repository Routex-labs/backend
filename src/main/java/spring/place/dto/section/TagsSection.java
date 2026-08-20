package spring.place.dto.section;

import java.util.List;

/** 짧은 키워드 묶음. */
public record TagsSection(String type, List<String> tags) implements PlaceSection {

    public TagsSection(List<String> tags) {
        this("tags", tags);
    }
}
