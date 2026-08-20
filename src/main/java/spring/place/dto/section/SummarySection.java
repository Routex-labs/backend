package spring.place.dto.section;


/** 매장 소개 문단. */
public record SummarySection(String type, String text) implements PlaceSection {

    public SummarySection(String text) {
        this("summary", text);
    }
}
