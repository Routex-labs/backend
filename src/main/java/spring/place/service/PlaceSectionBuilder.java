package spring.place.service;

import static spring.place.service.OverlayReader.entries;
import static spring.place.service.OverlayReader.map;
import static spring.place.service.OverlayReader.required;
import static spring.place.service.OverlayReader.text;
import static spring.place.service.OverlayReader.textList;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import spring.common.geometry.LocalPoint;
import spring.place.domain.Store;
import spring.place.dto.section.BusinessInfoSection;
import spring.place.dto.section.ContactSection;
import spring.place.dto.section.DemoInfoSection;
import spring.place.dto.section.HeroSection;
import spring.place.dto.section.HoursSection;
import spring.place.dto.section.KeyValueSection;
import spring.place.dto.section.LinksSection;
import spring.place.dto.section.MapSection;
import spring.place.dto.section.MenuSection;
import spring.place.dto.section.NoticeSection;
import spring.place.dto.section.PlaceSection;
import spring.place.dto.section.SummarySection;
import spring.place.dto.section.TagsSection;

/**
 * 오버레이 + 매장으로 상세 섹션 목록을 만든다.
 *
 * <p><b>순서는 서버가 고정한다.</b> 오버레이가 준 순서를 따르면 매장마다 순서가 달라져 사용자가
 * 같은 정보를 같은 자리에서 찾지 못한다. 왜 이 순서인지는 docs/decisions/006-상세-섹션-순서.md.
 *
 * <p>비어 있는 섹션은 아예 만들지 않는다 — "정보 없음 카드"를 만들지 않기 위한 조건이다.
 */
final class PlaceSectionBuilder {

    private PlaceSectionBuilder() {}

    /** 요일 키 7개. 하나라도 없으면 영업시간 섹션 자체를 만들지 않는다. */
    private static final List<String> WEEKDAY_KEYS = List.of("mon", "tue", "wed", "thu", "fri", "sat", "sun");

    /** 매장이 전부 서울에 있어 기본값이 뜻을 갖는다(KST = UTC+9). */
    private static final int DEFAULT_UTC_OFFSET_MINUTES = 540;

    static List<PlaceSection> build(Store store, String kind, Map<String, Object> overlay) {
        // 상세를 열지 않는 대상에는 섹션을 만들지 않는다. 코어만으로 충분하고, 클라이언트도
        // 이 kind에서는 시트를 띄우지 않는다.
        if (PlaceKind.EXCLUDED.equals(kind)) {
            return List.of();
        }

        List<PlaceSection> sections = new ArrayList<>();
        addIfPresent(sections, heroSection(overlay));
        addIfPresent(sections, noticeSection(overlay));
        addIfPresent(sections, hoursSection(overlay));
        addIfPresent(sections, contactSection(overlay));
        addIfPresent(sections, tagsSection(overlay));
        addIfPresent(sections, summarySection(overlay));
        addIfPresent(sections, menuSection(overlay));
        addIfPresent(sections, keyValueSection(overlay));
        addIfPresent(sections, demoInfoSection(overlay));
        addIfPresent(sections, linksSection(overlay));
        addIfPresent(sections, businessInfoSection(overlay));
        addIfPresent(sections, mapSection(store));
        return List.copyOf(sections);
    }

    private static void addIfPresent(List<PlaceSection> sections, PlaceSection section) {
        if (section != null) {
            sections.add(section);
        }
    }

    private static PlaceSection heroSection(Map<String, Object> overlay) {
        List<HeroSection.Item> items = entries(overlay.get("hero")).stream()
                .map(entry -> required(entry, "local_asset"))
                .filter(Objects::nonNull)
                .map(values -> new HeroSection.Item(values.get("local_asset")))
                .toList();
        return items.isEmpty() ? null : new HeroSection(items);
    }

    private static PlaceSection noticeSection(Map<String, Object> overlay) {
        Map<String, Object> notice = map(overlay.get("notice"));
        String content = text(notice, "text");
        return content == null ? null : new NoticeSection(content, text(notice, "until"));
    }

    private static PlaceSection tagsSection(Map<String, Object> overlay) {
        List<String> tags = textList(overlay.get("tags"));
        return tags == null ? null : new TagsSection(tags);
    }

    private static PlaceSection summarySection(Map<String, Object> overlay) {
        String summary = text(overlay, "summary");
        return summary == null ? null : new SummarySection(summary);
    }

    private static PlaceSection menuSection(Map<String, Object> overlay) {
        List<MenuSection.Item> items = new ArrayList<>();
        for (Map<String, Object> entry : entries(overlay.get("menu"))) {
            Map<String, String> core = required(entry, "name", "image_asset");
            if (core == null) {
                continue;
            }
            items.add(new MenuSection.Item(
                    core.get("name"),
                    core.get("image_asset"),
                    text(entry, "group"),
                    text(entry, "category"),
                    text(entry, "name_en"),
                    text(entry, "description"),
                    text(entry, "price"),
                    text(entry, "volume"),
                    text(entry, "calories"),
                    text(entry, "caffeine"),
                    text(entry, "allergens"),
                    textList(entry.get("badges"))));
        }
        return items.isEmpty() ? null : new MenuSection(items);
    }

    private static PlaceSection keyValueSection(Map<String, Object> overlay) {
        List<KeyValueSection.Item> items = entries(overlay.get("keyValue")).stream()
                .map(entry -> required(entry, "label", "value"))
                .filter(Objects::nonNull)
                .map(values -> new KeyValueSection.Item(values.get("label"), values.get("value")))
                .toList();
        return items.isEmpty() ? null : new KeyValueSection(items);
    }

    private static PlaceSection demoInfoSection(Map<String, Object> overlay) {
        List<DemoInfoSection.Item> items = entries(overlay.get("demoInfo")).stream()
                .map(entry -> required(entry, "label", "value", "source", "confirmed_at"))
                .filter(Objects::nonNull)
                .map(values -> new DemoInfoSection.Item(
                        values.get("label"), values.get("value"), values.get("source"), values.get("confirmed_at")))
                .toList();
        return items.isEmpty() ? null : new DemoInfoSection(items);
    }

    private static PlaceSection linksSection(Map<String, Object> overlay) {
        List<LinksSection.Item> items = new ArrayList<>();
        for (Map<String, Object> entry : entries(overlay.get("links"))) {
            Map<String, String> core = required(entry, "label", "url");
            if (core != null) {
                items.add(new LinksSection.Item(core.get("label"), core.get("url"), text(entry, "icon_asset")));
            }
        }
        return items.isEmpty() ? null : new LinksSection(items);
    }

    private static PlaceSection businessInfoSection(Map<String, Object> overlay) {
        List<BusinessInfoSection.Item> items = entries(overlay.get("businessInfo")).stream()
                .map(entry -> required(entry, "label", "value"))
                .filter(Objects::nonNull)
                .map(values -> new BusinessInfoSection.Item(values.get("label"), values.get("value")))
                .toList();
        return items.isEmpty() ? null : new BusinessInfoSection(items);
    }

    /** 폴리곤이 없는 매장이 14건 있다. 그 경우 지도 섹션 자체를 만들지 않는다. */
    private static PlaceSection mapSection(Store store) {
        List<LocalPoint> polygon = store.getPolygon();
        return polygon == null || polygon.isEmpty() ? null : new MapSection(polygon);
    }

    /**
     * 영업시간 섹션. 요일 7개가 다 있고, 구간이 하나라도 있고, 출처와 확인일이 있어야 만든다.
     *
     * <p>요일 키가 하나라도 없으면 섹션을 만들지 않는 이유: 화면에 "모르는 요일 = 휴무"가 뜨지
     * 않게 하기 위해서다. 7일이 전부 비면 그건 영업시간이 아니라 폐점이다.
     */
    private static PlaceSection hoursSection(Map<String, Object> overlay) {
        Map<String, Object> raw = map(overlay.get("hours"));
        Map<String, Object> weeklyRaw = map(raw.get("weekly"));
        if (!weeklyRaw.keySet().containsAll(WEEKDAY_KEYS)) {
            return null;
        }

        Map<String, List<HoursSection.Interval>> weekly = new LinkedHashMap<>();
        boolean anyInterval = false;
        for (String day : WEEKDAY_KEYS) {
            List<HoursSection.Interval> intervals = intervals(weeklyRaw.get(day));
            weekly.put(day, intervals);
            anyInterval |= !intervals.isEmpty();
        }
        if (!anyInterval) {
            return null;
        }

        String source = text(raw, "source");
        String confirmedAt = text(raw, "confirmed_at");
        if (source == null || confirmedAt == null) {
            return null;
        }

        Object offset = raw.get("utc_offset_minutes");
        int utcOffsetMinutes =
                offset instanceof Number number && !(offset instanceof Boolean) ? number.intValue() : DEFAULT_UTC_OFFSET_MINUTES;

        return new HoursSection(weekly, exceptions(raw.get("exceptions")), utcOffsetMinutes, confirmedAt, source);
    }

    /**
     * 연락처 섹션. 번호·출처·확인일 셋이 다 있어야 만든다.
     *
     * <p>셋 중 하나라도 없으면 섹션을 만들지 않는 이유는 영업시간과 같다 — 출처 없는 번호는
     * 확인할 방법이 없고, 확인할 수 없는 값을 실어 보내지 않겠다는 것이 이 필드를 자유 문자열
     * 대신 구조체로 만든 이유 그 자체다.
     */
    private static PlaceSection contactSection(Map<String, Object> overlay) {
        Map<String, Object> raw = map(overlay.get("contact"));
        Map<String, String> values = required(raw, "tel", "confirmed_at", "source");
        if (values == null) {
            return null;
        }
        return new ContactSection(values.get("tel"), values.get("confirmed_at"), values.get("source"));
    }

    private static List<HoursSection.Interval> intervals(Object raw) {
        List<HoursSection.Interval> intervals = new ArrayList<>();
        for (Map<String, Object> entry : entries(raw)) {
            Map<String, String> values = required(entry, "open", "close");
            if (values != null) {
                intervals.add(new HoursSection.Interval(values.get("open"), values.get("close")));
            }
        }
        return intervals;
    }

    private static List<HoursSection.Exception> exceptions(Object raw) {
        List<HoursSection.Exception> exceptions = new ArrayList<>();
        for (Map<String, Object> entry : entries(raw)) {
            String day = text(entry, "date");
            if (day == null) {
                continue;
            }
            boolean closed = Boolean.TRUE.equals(entry.get("closed"));
            List<HoursSection.Interval> intervals = intervals(entry.get("intervals"));
            // 휴무도 아니고 구간도 없는 예외는 그 날짜에 아무 말도 하지 않는다.
            if (!closed && intervals.isEmpty()) {
                continue;
            }
            exceptions.add(new HoursSection.Exception(day, closed, intervals, text(entry, "note")));
        }
        return exceptions;
    }
}
