package spring.search.service;

// Boot 4는 Jackson 3을 쓴다 - 패키지가 com.fasterxml이 아니라 tools.jackson이다.
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 별칭 → 표준어 사전.
 *
 * <p>파일이 없어도 빈 사전으로 동작한다 — 장애 없이 매칭만 약해진다. 기동 시 한 번만 읽는다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QuerySynonyms {

    private static final String PATH = "query_synonyms.json";

    private final ObjectMapper objectMapper;

    /** 순서를 유지한다 — 접두 확장이 사전 순회 순서를 그대로 결과 순서로 쓴다. */
    private Map<String, String> synonyms = Map.of();

    @PostConstruct
    void load() {
        ClassPathResource resource = new ClassPathResource(PATH);
        if (!resource.exists()) {
            log.warn("{}이 없다 — 동의어 없이 매칭한다", PATH);
            return;
        }
        try (InputStream input = resource.getInputStream()) {
            Map<String, String> raw = objectMapper.readValue(input, new TypeReference<LinkedHashMap<String, String>>() {});
            Map<String, String> normalized = new LinkedHashMap<>();
            raw.forEach((alias, canon) -> normalized.put(norm(alias), norm(canon)));
            synonyms = normalized;
            log.info("동의어 사전 {}건", synonyms.size());
        } catch (Exception error) {
            log.warn("{} 읽기 실패(동의어 없이 매칭한다): {}", PATH, error.toString());
        }
    }

    static String norm(String text) {
        return text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
    }

    /** 별칭의 표준형. 없으면 입력 그대로. */
    public String canonical(String query) {
        return synonyms.getOrDefault(query, query);
    }

    /**
     * 질의를 <b>접두로 갖는</b> 별칭들의 표준형.
     *
     * <p>사전 조회가 완전 일치라 {@code starbucks}를 끝까지 쳐야 걸렸다. 한글은 매장명 자체가
     * "스타벅스 리저브"라 "스타"만 쳐도 부분 일치로 잡히는데, 영어로 치는 사용자만 전부 입력해야
     * 했다. 반대 방향(표준형이 질의의 접두)은 넣지 않는다 — "스"가 별칭 전부를 끌어온다.
     */
    public List<String> prefixExpansions(String query) {
        if (query.length() < QueryRanking.MIN_NAME_PARTIAL_MATCH_LEN) {
            return List.of();
        }
        List<String> found = new ArrayList<>();
        synonyms.forEach((alias, canon) -> {
            // 완전 일치는 호출부가 이미 표준형으로 처리한다.
            if (!alias.equals(query) && alias.startsWith(query) && !found.contains(canon)) {
                found.add(canon);
            }
        });
        return found;
    }
}
