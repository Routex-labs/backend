package spring.place.dto.section;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 공지. {@code until}은 없으면 키 자체를 내보내지 않는다. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NoticeSection(String type, String text, String until) implements PlaceSection {

    public NoticeSection(String text, String until) {
        this("notice", text, until);
    }
}
