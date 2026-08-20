package spring.place.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 오버레이 JSON(사람이 손으로 쓴 자료)에서 값을 안전하게 꺼내는 헬퍼.
 *
 * <p>모든 접근이 방어적이다 — 검증 스크립트가 시드 단계에서 막지만, 그 검사를 우회한 데이터가
 * 들어와도 API가 500을 내지 않고 그 항목만 조용히 빠지게 하는 두 번째 문이다.
 */
final class OverlayReader {

    private OverlayReader() {}

    /** 공백을 떼고 빈 문자열이면 null. 없는 것과 비어 있는 것을 구분하지 않는다. */
    static String text(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value == null) {
            return null;
        }
        String trimmed = String.valueOf(value).strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object raw) {
        return raw instanceof Map<?, ?> value ? (Map<String, Object>) value : Map.of();
    }

    /** 리스트 안의 dict만 남긴다. 리스트가 아니면 빈 목록. */
    static List<Map<String, Object>> entries(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?>) {
                entries.add(map(item));
            }
        }
        return entries;
    }

    /** 공백 아닌 문자열만 남긴 목록. 결과가 비면 null이라 호출부가 키 자체를 뺄 수 있다. */
    static List<String> textList(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return null;
        }
        List<String> values = list.stream()
                .map(item -> String.valueOf(item).strip())
                .filter(item -> !item.isEmpty())
                .toList();
        return values.isEmpty() ? null : values;
    }

    /**
     * 필수 키를 전부 꺼낸다. 하나라도 비면 null — 호출부는 그 항목을 버린다.
     *
     * @return 키 → 공백 제거된 값. 넘긴 순서를 유지한다.
     */
    static Map<String, String> required(Map<String, Object> entry, String... keys) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : keys) {
            String value = text(entry, key);
            if (value == null) {
                return null;
            }
            values.put(key, value);
        }
        return values;
    }
}
