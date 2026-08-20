package spring.search.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 질의 ↔ 매장 어휘 매칭 순위.
 *
 * <p>tier 0(정확 이름) · tier 1(카테고리·intent) · tier 2(이름 부분 일치)로 나누고, tier 2 안에서는
 * 이름의 어디에 걸렸는지로 정밀도를 매긴다. 정렬 키는 (tier, 후보 순서, 정밀도, 층 level, id)라
 * 같은 입력에 항상 같은 순서가 나온다.
 */
@Component
@RequiredArgsConstructor
public class QueryRanking {

    private final QuerySynonyms synonyms;
    private final QueryMorph morph;

    /** 질의 꼬리(조사·의문형) — 정규화 때 최대 1개 제거. 긴 것부터 검사한다. */
    private static final List<String> TAILS = List.of("몇 층이야", "몇층이야", "몇 층", "몇층", "어디야", "어디", "위치", "알려줘");

    /** 문장 끝에서 후보로 벗겨 볼 구두점. 내부 구두점("We,pet")은 건드리지 않는다. */
    private static final String SENTENCE_PUNCTUATION = "?!.,，。！？…";

    public static final int MAX_QUERY_LENGTH = 200;

    /**
     * 같은 이름이어도 서로 다른 물리 선택지로 봐야 하는 값.
     *
     * <p>Studio의 Store 레코드에는 원본 POI type(exit 등)이 보존되지 않아, 현재 데모 데이터에서는
     * 이 집합에만 명시한다 — 일반 매장명 중복을 목록으로 바꾸지 않기 위해서다.
     */
    private static final Set<String> MULTI_PHYSICAL_NAMES = Set.of("출구");

    // tier 2 정밀도. 낮을수록 우선. 사용자는 이름 앞에서부터 친다 — "레이어드"는 "카페 레이어드"를
    // 찾는 것이지 다른 이름의 중간 어딘가를 찾는 게 아니다.
    private static final int NAME_PREFIX = 0;
    private static final int WORD_PREFIX = 1;
    private static final int CONTAINS = 2;

    // 띄어쓰기를 양쪽에서 뗀 뒤의 일치. 위 셋보다 **항상 뒤에** 온다 — 원문 띄어쓰기 그대로 걸린
    // 매장이 있으면 그쪽이 먼저여야 하고, 공백을 떼면 후보가 늘기만 하기 때문이다.
    private static final int SPACELESS_EXACT = 3;
    private static final int SPACELESS_PREFIX = 4;
    private static final int SPACELESS_CONTAINS = 5;

    /**
     * tier 2에 들어가려면 질의가 최소 2글자여야 한다.
     *
     * <p>형태소 분해가 오타·조사를 지우면서 질의가 1글자로 축소되는 경우가 있는데("샤낼"→"샤"),
     * 그 한 글자가 무관한 매장 이름 접두와 우연히 맞아 오탐이 난다. tier 0·1은 이 하한의 영향을
     * 받지 않는다 — "송"·"온" 같은 한 글자 매장명은 정확 일치로 계속 잡혀야 한다.
     */
    static final int MIN_NAME_PARTIAL_MATCH_LEN = 2;

    /** 순위가 매겨진 한 줄. {@code candidateOrder}는 구두점 후보 순서(원문이 0). */
    public record Scored(int tier, int candidateOrder, int precision, StoreRow row) {

        /** 최상위 그룹 판정에 쓰는 키 — 세 값이 같으면 같은 그룹이다. */
        List<Integer> group() {
            return List.of(tier, candidateOrder, precision);
        }
    }

    static String norm(String text) {
        return text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
    }

    static boolean isMultiPhysical(StoreRow row) {
        return MULTI_PHYSICAL_NAMES.contains(norm(row.name()));
    }

    boolean isMultiPhysicalQuery(String text) {
        List<String> candidates = queryCandidates(text);
        return !candidates.isEmpty() && MULTI_PHYSICAL_NAMES.contains(candidates.get(candidates.size() - 1));
    }

    private static String stripTail(String text) {
        return TAILS.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .filter(text::endsWith)
                .findFirst()
                .map(tail -> text.substring(0, text.length() - tail.length()).strip())
                .orElse(text);
    }

    /**
     * 원문과 문장 끝 구두점을 한 글자씩 벗긴 정규화 후보들.
     *
     * <p>원문 후보를 먼저 둬서 "A.P.C." 같은 실제 상호를 보존하고, 뒤 후보로 "화장실이 어디야?"
     * 같은 문장부호 입력을 받는다. 빈 후보는 category가 null인 임의 매장과 일치할 수 있어 뺀다.
     */
    List<String> queryCandidates(String text) {
        String current = norm(text);
        if (current.length() > MAX_QUERY_LENGTH) {
            return List.of();
        }

        List<String> variants = new ArrayList<>();
        variants.add(current);
        while (!current.isEmpty() && SENTENCE_PUNCTUATION.indexOf(current.charAt(current.length() - 1)) >= 0) {
            current = current.substring(0, current.length() - 1).stripTrailing();
            variants.add(current);
        }

        List<String> candidates = new ArrayList<>();
        for (String variant : variants) {
            String normalized = normalizeVariant(variant);
            if (normalized != null && !normalized.isEmpty() && !candidates.contains(normalized)) {
                candidates.add(normalized);
            }
        }
        return candidates;
    }

    /**
     * 꼬리 제거 → 형태소 정규화.
     *
     * <p>꼬리 제거를 먼저 하는 이유: "몇 층이야"의 "층"은 분석기가 일반명사로 보기 때문에 형태소만
     * 돌리면 "화장실 몇 층이야" → "화장실 층"이 되어 이름 일치가 깨진다. 분석기가 없으면 꼬리
     * 제거 결과만 쓴다.
     */
    private String normalizeVariant(String text) {
        String stripped = stripTail(text);
        String normalized = morph.normalize(stripped);
        return normalized == null ? stripped : normalized;
    }

    private static String squashSpaces(String text) {
        return text.replaceAll("\\s+", "");
    }

    /** 공백을 뗀 뒤의 부분 일치 정밀도. 뗄 공백이 아예 없으면 검사하지 않는다. */
    private static Integer spacelessMatchRank(String name, String query) {
        String squashedName = squashSpaces(name);
        String squashedQuery = squashSpaces(query);
        // 양쪽 다 공백이 없으면 바로 위 검사와 완전히 같은 비교다 — 두 번 하지 않는다.
        if (squashedName.equals(name) && squashedQuery.equals(query)) {
            return null;
        }
        if (squashedQuery.length() < MIN_NAME_PARTIAL_MATCH_LEN || !squashedName.contains(squashedQuery)) {
            return null;
        }
        if (squashedName.equals(squashedQuery)) {
            return SPACELESS_EXACT;
        }
        return squashedName.startsWith(squashedQuery) ? SPACELESS_PREFIX : SPACELESS_CONTAINS;
    }

    private static Integer nameMatchRank(String name, String query) {
        if (query == null || query.isEmpty()) {
            return null;
        }
        if (query.length() >= MIN_NAME_PARTIAL_MATCH_LEN && name.contains(query)) {
            if (name.startsWith(query)) {
                return NAME_PREFIX;
            }
            for (String token : name.split("\\s+")) {
                if (token.startsWith(query)) {
                    return WORD_PREFIX;
                }
            }
            return CONTAINS;
        }
        return spacelessMatchRank(name, query);
    }

    /** 매칭 우선순위 (tier, 정밀도). 안 걸리면 null. */
    private static int[] tier(StoreRow row, String query, String canonical, List<String> expansions) {
        String name = norm(row.name());
        String category = norm(row.category());
        String subcategory = norm(row.subcategory());

        if (name.equals(query) || name.equals(canonical)) {
            return new int[] {0, 0};
        }
        if (query.equals(category) || query.equals(subcategory) || canonical.equals(category) || canonical.equals(subcategory)) {
            return new int[] {1, 0};
        }
        // intent 일치도 카테고리와 같은 tier 1이다. intent는 "사용자가 치는 말"이고(신발·밥집)
        // 분류 라벨은 운영자가 쓰는 말이라(슈즈·레스토랑) 둘이 어긋나는 게 정상이다. 어떤 매장이
        // 신발을 파는지는 사람이 검수한 태그로 search_facets에 이미 구워져 있다.
        for (String intent : row.facet("intents")) {
            String normalized = norm(intent);
            if (normalized.equals(query) || normalized.equals(canonical)) {
                return new int[] {1, 0};
            }
        }

        List<Integer> ranks = new ArrayList<>();
        for (String probe : List.of(query, canonical)) {
            Integer rank = nameMatchRank(name, probe);
            if (rank != null) {
                ranks.add(rank);
            }
        }
        for (String expanded : expansions) {
            Integer rank = nameMatchRank(name, expanded);
            if (rank != null) {
                ranks.add(rank);
            }
        }
        if (ranks.isEmpty()) {
            return null;
        }
        return new int[] {2, ranks.stream().mapToInt(Integer::intValue).min().orElseThrow()};
    }

    /**
     * 순위표. 정렬 키는 (tier, 후보 순서, 정밀도, 층 level, 매장 id).
     *
     * <p>정밀도를 후보 순서 <b>뒤에</b> 두는 이유: 후보 순서는 "원문에 가까운 정규화"를 뜻하고
     * 정밀도는 "그 후보가 이름 어디에 걸렸는지"를 뜻한다. 원문 우선을 먼저 지켜야 "A.P.C."가
     * "A.P.C 골프"의 부분 일치보다 앞선다.
     */
    public List<Scored> rank(List<StoreRow> rows, String text) {
        List<String> candidates = queryCandidates(text);
        if (candidates.isEmpty()) {
            return List.of();
        }

        // 후보마다 표준형과 접두 확장을 **매장 루프 밖에서** 한 번만 만든다. 사전이 321건이라
        // 매장마다 다시 돌면 1,640 × 321이 된다.
        List<String> canonicals = new ArrayList<>();
        List<List<String>> expansions = new ArrayList<>();
        for (String candidate : candidates) {
            canonicals.add(synonyms.canonical(candidate));
            expansions.add(synonyms.prefixExpansions(candidate));
        }

        List<Scored> scored = new ArrayList<>();
        for (StoreRow row : rows) {
            // 걸리는 후보마다 (tier, 후보 순서, 정밀도)를 만들어 사전순으로 가장 작은 것을 고른다.
            // tier가 같으면 원문에 가까운 후보가 먼저라 "A.P.C."가 "A.P.C 골프"의 부분 일치보다 앞선다.
            int[] best = null;
            for (int order = 0; order < candidates.size(); order++) {
                int[] matched = tier(row, candidates.get(order), canonicals.get(order), expansions.get(order));
                if (matched == null) {
                    continue;
                }
                int[] key = {matched[0], order, matched[1]};
                if (best == null || Arrays.compare(key, best) < 0) {
                    best = key;
                }
            }
            if (best != null) {
                scored.add(new Scored(best[0], best[1], best[2], row));
            }
        }

        scored.sort(Comparator.comparingInt(Scored::tier)
                .thenComparingInt(Scored::candidateOrder)
                .thenComparingInt(Scored::precision)
                .thenComparingInt(s -> s.row().floorLevel())
                .thenComparing(s -> s.row().id()));
        return scored;
    }

    /**
     * 경량 결과를 바로 한 건으로 확정해도 되는지.
     *
     * <p><b>목적지 경로와 탐색 경로가 이 판정 하나를 공유한다.</b> 같은 질의에 다른 결론을 내면 어느
     * 경로로 들어왔는지에 따라 사용자가 보는 결과가 달라진다 — 파이썬에서 실제로 그랬고, 그래서
     * "명품"(서로 다른 이름 43건)이 첫 매장 한 곳으로 고정됐다.
     *
     * <p>기준은 하나다: 최상위 (tier, 후보 순서, 정밀도) 그룹 안에 <b>서로 다른 매장명이 하나</b>일
     * 때만 확정한다. 같은 시설이 여러 층에 있는 경우는 이름이 같으므로 한 대상으로 본다.
     */
    public boolean isConfident(List<Scored> scored) {
        if (scored.isEmpty()) {
            return false;
        }
        List<Integer> bestGroup = scored.get(0).group();
        List<StoreRow> bestRows = scored.stream()
                .filter(s -> s.group().equals(bestGroup))
                .map(Scored::row)
                .toList();

        Set<String> names = new LinkedHashSet<>();
        bestRows.forEach(row -> names.add(norm(row.name())));
        if (names.size() != 1) {
            return false;
        }
        // 출구처럼 이름은 같아도 좌표가 다른 POI들은 사용자가 고를 목록이어야 한다.
        return !(bestRows.size() > 1 && bestRows.stream().allMatch(QueryRanking::isMultiPhysical));
    }
}
