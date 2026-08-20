package spring.search.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import spring.building.repository.BuildingRepository;
import spring.common.geo.BuildingGeoTransforms;
import spring.common.geo.GeoTransform;
import spring.common.geometry.LatLng;
import spring.common.geometry.LocalPoint;
import spring.search.dto.DestinationResponse;
import spring.search.dto.DiscoveryMatchResponse;
import spring.search.dto.DiscoveryOption;
import spring.search.dto.DiscoveryResponse;
import spring.search.dto.InfoResponse;
import spring.search.dto.QueryMatchResponse;
import spring.search.repository.QuerySearchRepository;
import spring.search.service.QueryRanking.Scored;

/**
 * 자연어 질의 매칭(경량 — 임베딩 없음).
 *
 * <p>매장 이름·카테고리·동의어·intent를 어휘로 매칭한다. 파이썬은 여기서 놓친 열린 질의를 2차
 * 임베딩 검색으로 넘기지만, 이 이식은 <b>1차 경량 경로만</b> 담는다. 실측상 "스타벅스"·"신발"·
 * "밥집" 같은 실사용 질의는 전부 이 경로가 답하고(source=light), 임베딩이 실제로 필요한 것은
 * "생일선물 살만한 곳"처럼 이름·분류로 이어지지 않는 열린 질의뿐이다.
 *
 * <p>건물이 없으면 빈 Optional(→ 컨트롤러가 404). 매칭 0건은 status="no_match"로 정상 응답이다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuerySearchService {

    /** 후보를 무엇으로 잡았는지. 임베딩을 이식하기 전까지 이 서버의 응답은 항상 light다. */
    private static final String SOURCE_LIGHT = "light";

    /** 되물을 수 없는 상태의 추천 상한, 그리고 "후보가 넓다"의 기준. */
    private static final int MAX_DISCOVERY_MATCHES = 5;

    /** 질문과 함께 보여줄 초기 후보 수. */
    private static final int CLARIFY_PREVIEW_MATCHES = 3;

    /**
     * 목록 상한.
     *
     * <p>추천 5건은 <b>질문이 아직 서 있는 화면</b>의 규칙이다. 되물을 축이 없는 질의("커피" —
     * 후보 53건이 전부 카페라 나눌 축이 없다)는 그 5건이 곧 최종 답이 되고 화면에 "전체 보기"도
     * 없어서, 53곳 중 5곳만 보여주고 나머지로 갈 길이 사라진다. chip에 적힌 숫자만큼은 도달할 수
     * 있어야 한다.
     */
    private static final int MAX_RESULT_MATCHES = 100;

    /** 되물을 축의 우선순위. 한 번에 한 축만 묻는다. */
    private static final List<String> QUESTION_AXIS_ORDER =
            List.of("intents", "cuisines", "styles", "menus", "occasions", "audiences");

    /** 질문 템플릿. 문장을 생성하지 않고 축별로 고정한다. */
    private static final Map<String, String> QUESTION_TEMPLATES = Map.of(
            "intents", "무엇을 찾으세요?",
            "cuisines", "어떤 종류가 좋으세요?",
            "styles", "어떤 스타일을 찾으세요?",
            "menus", "무엇을 드시고 싶으세요?",
            "occasions", "어떤 상황에 맞는 곳을 찾으세요?",
            "audiences", "누구를 위한 곳인가요?");

    /** 추천 이유 템플릿. 검증된 태그에서만 조립한다 — 이름·카테고리로 취급 품목을 추측하지 않는다. */
    private static final Map<String, String> REASON_TEMPLATES = Map.of(
            "intents", "%s 관련 매장이에요.",
            "cuisines", "%s 음식점이에요.",
            "styles", "%s 스타일 매장이에요.",
            "menus", "%s 메뉴가 있어요.",
            "occasions", "%s에 어울려요.",
            "audiences", "%s를 위한 곳이에요.");

    private final QuerySearchRepository queryRepository;
    private final BuildingRepository buildingRepository;
    private final BuildingGeoTransforms geoTransforms;
    private final QueryRanking ranking;
    private final QueryMorph morph;
    private final QuerySynonyms synonyms;

    // --- 목적지 -----------------------------------------------------------

    @Transactional(readOnly = true)
    public Optional<DestinationResponse> matchDestination(String buildingId, String text, String currentFloorId) {
        if (!buildingRepository.existsById(buildingId)) {
            return Optional.empty();
        }
        List<Scored> scored = ranking.rank(load(buildingId, currentFloorId), text);
        if (scored.isEmpty()) {
            return Optional.of(new DestinationResponse("no_match", text, null));
        }
        // 확정할 수 없는 매칭은 1건으로 좁히지 않는다. "명품"(서로 다른 이름 43건)을 첫 매장으로
        // 고정하면 사용자가 목록을 볼 기회 자체가 없다 — 클라이언트는 destination이 성공하면
        // /query/ai를 부르지 않는다. match=null로 돌려주면 빈 결과로 파싱해 목록 계약으로 이어진다.
        if (!ranking.isConfident(scored)) {
            return Optional.of(new DestinationResponse("ambiguous", text, null));
        }
        StoreRow row = scored.get(0).row();
        return Optional.of(new DestinationResponse(status(row), text, toMatch(row, transform(buildingId))));
    }

    // --- 정보 -------------------------------------------------------------

    @Transactional(readOnly = true)
    public Optional<InfoResponse> matchInfo(String buildingId, String text, String currentFloorId) {
        if (!buildingRepository.existsById(buildingId)) {
            return Optional.empty();
        }
        List<Scored> scored = ranking.rank(load(buildingId, currentFloorId), text);
        if (scored.isEmpty()) {
            return Optional.of(new InfoResponse("no_match", text, null, List.of()));
        }
        StoreRow row = scored.get(0).row();
        return Optional.of(new InfoResponse("ok", text, toMatch(row, transform(buildingId)), floorNamesFor(scored, row.name())));
    }

    /**
     * 대표 매장과 <b>이름이 같은</b> 후보가 존재하는 층만 level 순으로.
     *
     * <p>낮은 tier의 다른 부분 일치까지 섞으면 "A.P.C." 응답에 "A.P.C 골프"의 층이 붙는다.
     */
    private List<String> floorNamesFor(List<Scored> scored, String selectedName) {
        String target = QueryRanking.norm(selectedName);
        Map<String, Integer> byLevel = new LinkedHashMap<>();
        for (Scored s : scored) {
            if (QueryRanking.norm(s.row().name()).equals(target)) {
                byLevel.putIfAbsent(s.row().floorName(), s.row().floorLevel());
            }
        }
        return byLevel.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .toList();
    }

    // --- 탐색 -------------------------------------------------------------

    /**
     * 탐색 질의. 명확하면 바로 안내하고, 넓으면 되묻거나 여러 후보를 추천한다.
     *
     * <p>판정 순서:
     *
     * <ol>
     *   <li>경량 1차가 단일 대상으로 확정되면 direct. 선택(facet)이 이미 있으면 건너뛴다
     *   <li>확정 실패면 경량 후보 집합을 그대로 쓴다
     *   <li>selectedFacets로 좁힌다. 0건이면 선택을 해제하고 전체 후보로 되돌아간다 — 결과가 없는데
     *       계속 세분화 질문을 하는 막다른 흐름을 만들지 않기 위해서다
     *   <li>이름 중복 제거 → 구분력 있는 축이 있으면 clarify, 아니면 results
     * </ol>
     *
     * <p>{@code currentFloorId}는 1차 경량에서만 층 스코프로 쓰고("화장실"을 현재 층에서 확정),
     * 탐색 후보 집합은 건물 전체를 보되 같은 이름이 현재 층에도 있으면 그 층을 대표로 고르는
     * 정렬 보조로만 쓴다.
     */
    @Transactional(readOnly = true)
    public Optional<DiscoveryResponse> discover(
            String buildingId, String text, String currentFloorId, Map<String, List<String>> selectedFacets, boolean showAll) {
        if (!buildingRepository.existsById(buildingId)) {
            return Optional.empty();
        }

        Map<String, List<String>> selection = new LinkedHashMap<>();
        if (selectedFacets != null) {
            selectedFacets.forEach((axis, values) -> {
                if (values != null && !values.isEmpty()) {
                    selection.put(axis, List.copyOf(values));
                }
            });
        }

        GeoTransform transform = transform(buildingId);
        List<StoreRow> buildingRows = load(buildingId, null);
        List<Scored> buildingScored = ranking.rank(buildingRows, text);
        List<Scored> floorScoped =
                currentFloorId == null ? buildingScored : ranking.rank(load(buildingId, currentFloorId), text);

        if (selection.isEmpty()) {
            // 층 스코프 1차가 우선 — 현재 층에 있는 시설을 그 층에서 확정한다.
            for (List<Scored> scored : List.of(floorScoped, buildingScored)) {
                if (ranking.isConfident(scored)) {
                    StoreRow row = scored.get(0).row();
                    // 한 건으로 확정돼도 intent로 걸린 것이면 그 근거를 남긴다 — 확정 여부와
                    // "왜 이게 답인지"는 별개다.
                    Map<String, List<String>> basis = intentBasis(queryMatchedIntents(List.of(row), text));
                    return Optional.of(discovery(
                            text, "direct", null, List.of(), List.of(toDiscoveryMatch(row, transform, basis))));
                }
            }
        }

        List<StoreRow> pool;
        // 현재 층에서 여러 출구가 맞으면 다른 층의 동명 출구를 섞지 않는다.
        if (currentFloorId != null && ranking.isMultiPhysicalQuery(text)) {
            List<StoreRow> currentFloorPhysical = floorScoped.stream()
                    .map(Scored::row)
                    .filter(QueryRanking::isMultiPhysical)
                    .toList();
            pool = currentFloorPhysical.isEmpty() ? rows(buildingScored) : currentFloorPhysical;
        } else {
            pool = rows(buildingScored);
        }

        if (!selection.isEmpty()) {
            List<StoreRow> narrowed = pool.stream().filter(row -> matchesSelection(row, selection)).toList();
            if (!narrowed.isEmpty()) {
                pool = narrowed;
            } else {
                selection.clear(); // 선택을 해제하고 전체 후보를 보여준다(막다른 흐름 방지)
            }
        }

        List<StoreRow> candidates = dedupeByName(pool, currentFloorId);
        if (candidates.isEmpty()) {
            // 파이썬이라면 여기서 2차 임베딩 검색으로 넘어간다. 그 이식이 값을 하는지는
            // "이 자리에 실제로 얼마나 오는가"에 달렸는데 지금은 그 숫자가 없다. 질의를
            // 남겨 한 주치를 세면 추측이 아니라 비율로 결정할 수 있다.
            //
            // 로그 하나로 끝내는 이유: 카운터·지표 파이프라인을 새로 놓을 만큼 오래 볼
            // 값이 아니다. 결정이 나면 이 줄은 지운다.
            log.info("경량 미스(임베딩이 답했을 질의): {}", text);
            return Optional.of(discovery(text, "no_match", null, List.of(), List.of()));
        }

        // 후보 집합이 왜 모였는지의 근거. 질문 축과 별개로 모든 mode에 실린다.
        Map<String, List<String>> intentBasis = intentBasis(queryMatchedIntents(candidates, text));

        if (!selection.isEmpty() || showAll) {
            // 선택으로 좁힌 결과도 목록 상한을 쓴다. chip에는 후보 수가 적혀 있으므로
            // 61이라고 적힌 것을 눌렀는데 5건만 오면 나머지 56건으로 갈 길이 없다.
            Map<String, List<String>> basis = new LinkedHashMap<>(intentBasis);
            basis.putAll(selection);
            return Optional.of(discovery(text, "results", null, List.of(), matches(candidates, MAX_RESULT_MATCHES, basis, transform, null)));
        }

        if (candidates.size() > MAX_DISCOVERY_MATCHES) {
            Question question = pickQuestion(candidates, intentBasis.get("intents"));
            if (question != null) {
                // 질문 축이 intents여도 **질의가 가리킨 intent를 덮지 않는다.** 덮으면 사용자가 친
                // 말이 reason에서 사라진다 — "신발" 질의에 "의류 관련 매장이에요"만 남는 식이다.
                List<String> merged = new ArrayList<>(intentBasis.getOrDefault(question.axis(), List.of()));
                question.options().forEach(option -> merged.add(option.value()));
                Map<String, List<String>> basis = new LinkedHashMap<>(intentBasis);
                basis.put(question.axis(), merged);

                // 초기 후보는 그 축의 태그가 있는 매장에서 고른다 — 미태깅 매장이 섞이면 질문의
                // 근거(reason)가 비어 보인다. 태그된 후보가 없으면 전체에서 고른다.
                List<StoreRow> preview = candidates.stream()
                        .filter(row -> !row.facet(question.axis()).isEmpty())
                        .toList();
                return Optional.of(discovery(
                        text,
                        "clarify",
                        QUESTION_TEMPLATES.get(question.axis()),
                        question.options(),
                        matches(
                                preview.isEmpty() ? candidates : preview,
                                CLARIFY_PREVIEW_MATCHES,
                                basis,
                                transform,
                                question.axis())));
            }
        }

        // 구분력 있는 축이 없으면 억지로 되묻지 않는다 — 다양성 보정된 목록을 그대로 준다.
        return Optional.of(discovery(text, "results", null, List.of(), matches(candidates, MAX_RESULT_MATCHES, intentBasis, transform, null)));
    }

    private record Question(String axis, List<DiscoveryOption> options) {}

    /**
     * 현재 후보를 실제로 둘 이상으로 나누는 축을 고른다. 없으면 null.
     *
     * <p>세 가지를 모두 만족해야 질문이 된다.
     *
     * <ol>
     *   <li>서로 다른 값이 둘 이상 — "명품" 후보 43건은 전부 styles=["명품"]이라 다시 물어도 그대로다
     *   <li><b>그 값들이 서로 다른 후보를 가리킨다</b> — "화장품"과 "향수"는 둘 다 소분류
     *       "화장품·향수"에서 나와 어느 쪽을 눌러도 같은 10건이었다. 고르는 행동이 아무것도 바꾸지
     *       못하면 질문이 아니다
     *   <li>그 축을 가진 후보가 절반 이상 — 상위 10건 중 2건만 태그가 있어도 "값이 2개"는 통과하고,
     *       그 질문에 답하면 태그 없는 8건이 통째로 사라진다
     * </ol>
     *
     * <p>질의가 이미 가리킨 intent 값은 선택지에서 뺀다. 방금 친 말을 되돌려주는 건 질문이 아니라
     * 메아리다 — "신발"에 대고 "신발/의류 중 무엇을 찾으세요?"를 묻게 된다.
     */
    private Question pickQuestion(List<StoreRow> candidates, List<String> askedIntents) {
        Set<String> excluded = askedIntents == null ? Set.of() : Set.copyOf(askedIntents);
        for (String axis : QUESTION_AXIS_ORDER) {
            List<DiscoveryOption> options = facetOptions(candidates, axis);
            if (axis.equals("intents") && !excluded.isEmpty()) {
                options = options.stream().filter(option -> !excluded.contains(option.value())).toList();
            }
            options = dropIndistinguishable(candidates, axis, options);
            if (options.size() < 2) {
                continue;
            }
            long covered = candidates.stream().filter(row -> !row.facet(axis).isEmpty()).count();
            if (covered * 2 >= candidates.size()) {
                return new Question(axis, options);
            }
        }
        return null;
    }

    /** 축의 값별 후보 수. 실제 후보가 있는 값만 만든다. */
    private List<DiscoveryOption> facetOptions(List<StoreRow> candidates, String axis) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (StoreRow row : candidates) {
            for (String value : row.facet(axis)) {
                counts.merge(value, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(e -> -e.getValue()).thenComparing(Map.Entry::getKey))
                .map(e -> new DiscoveryOption(axis, e.getKey(), e.getKey(), e.getValue()))
                .toList();
    }

    /**
     * 가리키는 후보가 똑같은 선택지를 하나로 접는다.
     *
     * <p>같은 매장 집합을 가리키는 값이 둘이면 chip은 둘 뜨지만 어느 쪽을 눌러도 결과가 같다.
     * 남길 값은 {@link #facetOptions} 순서(후보 수 내림차순 → 값 오름차순)의 첫 번째다.
     */
    private List<DiscoveryOption> dropIndistinguishable(List<StoreRow> candidates, String axis, List<DiscoveryOption> options) {
        Map<Set<String>, DiscoveryOption> kept = new LinkedHashMap<>();
        for (DiscoveryOption option : options) {
            Set<String> members = candidates.stream()
                    .filter(row -> row.facet(axis).contains(option.value()))
                    .map(StoreRow::id)
                    .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
            if (members.isEmpty()) {
                continue;
            }
            kept.putIfAbsent(members, option);
        }
        return List.copyOf(kept.values());
    }

    /** 축 사이는 AND, 축 안의 값들은 OR. 태그가 없는 매장은 선택된 축에서 탈락한다. */
    private boolean matchesSelection(StoreRow row, Map<String, List<String>> selection) {
        for (Map.Entry<String, List<String>> entry : selection.entrySet()) {
            if (row.facet(entry.getKey()).stream().noneMatch(entry.getValue()::contains)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 질의어 자체가 가리킨 intent 값들. 후보에 실제로 있는 값만 남긴다.
     *
     * <p>후보 집합이 왜 모였는지는 매장이 아니라 <b>질의</b>가 알고 있다. 이게 없으면 "신발" 질의의
     * 추천 이유가 "명품 스타일 매장이에요"로만 나온다 — 신발을 물었는데 신발 이야기가 없는 문장이다.
     */
    private List<String> queryMatchedIntents(List<StoreRow> candidates, String text) {
        Set<String> wanted = new LinkedHashSet<>();
        for (String candidate : ranking.queryCandidates(text)) {
            wanted.add(candidate);
            wanted.add(synonyms.canonical(candidate));
        }
        List<String> matched = new ArrayList<>();
        for (StoreRow row : candidates) {
            for (String value : row.facet("intents")) {
                if (wanted.contains(QueryRanking.norm(value)) && !matched.contains(value)) {
                    matched.add(value);
                }
            }
        }
        return matched;
    }

    private Map<String, List<String>> intentBasis(List<String> intents) {
        // 빈 축을 만들지 않는다.
        return intents.isEmpty() ? Map.of() : Map.of("intents", intents);
    }

    /**
     * 같은 이름은 한 건만 남긴다 — 다양성 보정의 1순위.
     *
     * <p>코퍼스의 67%가 편의시설이고 엘리베이터·에스컬레이터는 12개 층에 같은 이름으로 있다. 이름
     * 축을 빼면 상위 N이 같은 시설의 층 목록으로 채워진다. 대표로 남길 층은 관련도 1순위를 쓰되,
     * 같은 이름이 현재 층에도 있으면 그쪽을 고른다.
     */
    private List<StoreRow> dedupeByName(List<StoreRow> rows, String currentFloorId) {
        Map<String, StoreRow> picked = new LinkedHashMap<>();
        for (StoreRow row : rows) {
            // 일반 매장·전 층 공용 시설은 이름으로 묶되, 출구는 같은 층에서도 서로 다른 물리
            // 선택지라 id를 유지한다.
            String key = QueryRanking.isMultiPhysical(row)
                    ? row.id()
                    : Optional.of(QueryRanking.norm(row.name())).filter(n -> !n.isEmpty()).orElse(row.id());
            StoreRow existing = picked.get(key);
            if (existing == null) {
                picked.put(key, row);
            } else if (isCurrentFloor(row, currentFloorId) && !isCurrentFloor(existing, currentFloorId)) {
                picked.put(key, row);
            }
        }
        return List.copyOf(picked.values());
    }

    private static boolean isCurrentFloor(StoreRow row, String currentFloorId) {
        return currentFloorId != null && (currentFloorId.equals(row.floorName()) || currentFloorId.equals(row.floorId()));
    }

    /**
     * 같은 소분류·같은 층 쏠림을 라운드로빈으로 완화한다.
     *
     * <p>버킷 순서는 관련도 순서(각 버킷의 첫 등장 순)를 그대로 따르므로 1순위 후보는 항상 1순위로
     * 남는다. {@code axis}를 주면 소분류 대신 그 축의 값으로 버킷을 나눈다 — 스타일을 되묻는
     * 화면의 미리보기가 전부 같은 스타일이면 질문의 근거가 보이지 않는다.
     */
    private List<StoreRow> diversify(List<StoreRow> rows, int limit, String axis) {
        Map<List<String>, List<StoreRow>> buckets = new LinkedHashMap<>();
        for (StoreRow row : rows) {
            String group;
            if (axis == null) {
                group = QueryRanking.norm(row.subcategory());
            } else {
                List<String> values = row.facet(axis);
                group = values.isEmpty() ? "" : values.get(0);
            }
            buckets.computeIfAbsent(List.of(group, row.floorName()), key -> new ArrayList<>()).add(row);
        }

        List<StoreRow> picked = new ArrayList<>();
        while (picked.size() < limit) {
            boolean progressed = false;
            for (List<StoreRow> bucket : buckets.values()) {
                if (bucket.isEmpty()) {
                    continue;
                }
                picked.add(bucket.remove(0));
                progressed = true;
                if (picked.size() >= limit) {
                    break;
                }
            }
            if (!progressed) {
                break;
            }
        }
        return picked;
    }

    private List<DiscoveryMatchResponse> matches(
            List<StoreRow> candidates, int limit, Map<String, List<String>> basis, GeoTransform transform, String axis) {
        return diversify(candidates, limit, axis).stream()
                .map(row -> toDiscoveryMatch(row, transform, basis))
                .toList();
    }

    /** 이번 질문·선택(basis)과 실제로 겹친 태그만 남긴다. 원본 facet 전체를 내보내지 않는다. */
    private Map<String, List<String>> matchedFacets(StoreRow row, Map<String, List<String>> basis) {
        Map<String, List<String>> matched = new LinkedHashMap<>();
        basis.forEach((axis, wanted) -> {
            List<String> hit = row.facet(axis).stream().filter(wanted::contains).toList();
            if (!hit.isEmpty()) {
                matched.put(axis, hit);
            }
        });
        return matched;
    }

    /** 검증된 태그에서만 문장을 조립한다. 태그가 없으면 이유를 생략한다. */
    private String reason(Map<String, List<String>> matched) {
        List<String> parts = QUESTION_AXIS_ORDER.stream()
                .filter(axis -> matched.containsKey(axis) && REASON_TEMPLATES.containsKey(axis))
                .map(axis -> REASON_TEMPLATES.get(axis).formatted(String.join("·", matched.get(axis))))
                .toList();
        return parts.isEmpty() ? null : String.join(" ", parts);
    }

    private DiscoveryMatchResponse toDiscoveryMatch(StoreRow row, GeoTransform transform, Map<String, List<String>> basis) {
        Map<String, List<String>> matched = basis.isEmpty() ? Map.of() : matchedFacets(row, basis);
        QueryMatchResponse match = toMatch(row, transform);
        return new DiscoveryMatchResponse(
                match.storeId(),
                match.name(),
                match.category(),
                match.subcategory(),
                match.floorId(),
                match.floorName(),
                match.entranceNodeId(),
                match.centroidLocalM(),
                match.centroidWgs84(),
                matched,
                reason(matched));
    }

    private DiscoveryResponse discovery(
            String text, String mode, String question, List<DiscoveryOption> options, List<DiscoveryMatchResponse> matches) {
        return new DiscoveryResponse(mode, text, SOURCE_LIGHT, question, options, matches);
    }

    // --- 공통 -------------------------------------------------------------

    private List<StoreRow> load(String buildingId, String currentFloorId) {
        // 형태소 사전은 프로세스 전역이라 첫 질의에서 전 매장명으로 한 번만 굽는다.
        morph.registerWords(queryRepository.findAllNames());
        return currentFloorId == null
                ? queryRepository.findAllByBuilding(buildingId)
                : queryRepository.findByBuildingAndFloor(buildingId, currentFloorId);
    }

    private static List<StoreRow> rows(List<Scored> scored) {
        return scored.stream().map(Scored::row).toList();
    }

    /** 입구 노드가 없으면 클라이언트가 경로를 못 만든다 — ok와 구분해 알린다. */
    private static String status(StoreRow row) {
        return row.entranceNodeId() != null ? "ok" : "ok_no_route";
    }

    private GeoTransform transform(String buildingId) {
        return geoTransforms.forBuilding(buildingId);
    }

    private QueryMatchResponse toMatch(StoreRow row, GeoTransform transform) {
        // wgs84는 지도 표시용. 건물에 실좌표 앵커가 없으면 transform이 없어 null이 된다.
        LatLng wgs84 = transform == null ? null : transform.apply(row.centroidXM(), row.centroidYM());
        return new QueryMatchResponse(
                row.id(),
                row.name(),
                row.category(),
                row.subcategory(),
                row.floorId(),
                row.floorName(),
                row.entranceNodeId(),
                new LocalPoint(row.centroidXM(), row.centroidYM()),
                wgs84);
    }
}
