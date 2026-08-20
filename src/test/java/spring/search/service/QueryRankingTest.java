package spring.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import spring.search.service.QueryRanking.Scored;
import tools.jackson.databind.json.JsonMapper;

/**
 * 어휘 매칭 순위 — 이식에서 가장 어긋나기 쉬운 부분이라 DB 없이 직접 고정한다.
 *
 * <p>기대값의 출처는 배포된 파이썬 백엔드다. {@code tools/diff_contract.py}가 같은 질의를 두 서버에
 * 보내 응답 전체를 대조하지만, 그건 서버 둘이 떠 있어야 돌아간다. 여기서는 판정 규칙만 떼어 잡는다.
 */
class QueryRankingTest {

    private static QueryRanking ranking;

    @BeforeAll
    static void setUp() {
        QuerySynonyms synonyms = new QuerySynonyms(JsonMapper.builder().build());
        synonyms.load();
        QueryMorph morph = new QueryMorph();
        morph.registerWords(List.of("노스페이스", "이솝", "리모와"));
        ranking = new QueryRanking(synonyms, morph);
    }

    private static StoreRow store(String id, String name, String category, String subcategory, Map<String, List<String>> facets) {
        return new StoreRow(id, name, category, subcategory, 0, 0, "ND-1", facets, "FL-1", "1F", 1);
    }

    @Test
    @DisplayName("정확한 이름이 부분 일치보다 앞선다")
    void exactNameBeatsPartial() {
        List<StoreRow> rows = List.of(store("s1", "이솝 화장품", null, null, null), store("s2", "이솝", null, null, null));

        List<Scored> scored = ranking.rank(rows, "이솝");

        assertThat(scored.get(0).row().id()).isEqualTo("s2");
        assertThat(scored.get(0).tier()).isZero();
    }

    @Test
    @DisplayName("이름 접두 일치가 중간 포함보다 앞선다")
    void namePrefixBeatsContains() {
        List<StoreRow> rows = List.of(store("s1", "카페 레이어드", null, null, null), store("s2", "레이어드 서울", null, null, null));

        List<Scored> scored = ranking.rank(rows, "레이어드");

        assertThat(scored.get(0).row().id()).as("이름이 질의로 시작하는 쪽이 먼저다").isEqualTo("s2");
    }

    @Test
    @DisplayName("띄어 친 브랜드명이 공백 없는 이름과 매칭된다")
    void spacelessMatch() {
        List<StoreRow> rows = List.of(store("s1", "노스페이스", null, null, null));

        assertThat(ranking.rank(rows, "노스 페이스")).hasSize(1);
    }

    @Test
    @DisplayName("한 글자로 줄어든 질의는 부분 일치를 만들지 않는다")
    void singleCharDoesNotPartialMatch() {
        List<StoreRow> rows = List.of(store("s1", "샤넬 뷰티", null, null, null));

        assertThat(ranking.rank(rows, "샤")).as("한 글자 접두가 무관한 매장을 끌어오면 오탐이다").isEmpty();
    }

    @Test
    @DisplayName("intent 태그가 카테고리와 같은 tier로 걸린다")
    void intentMatchesAsCategory() {
        List<StoreRow> rows = List.of(store("s1", "MLB", "패션", "슈즈", Map.of("intents", List.of("신발"))));

        List<Scored> scored = ranking.rank(rows, "신발");

        assertThat(scored).hasSize(1);
        assertThat(scored.get(0).tier()).isEqualTo(1);
    }

    @Test
    @DisplayName("최상위 그룹에 서로 다른 이름이 여럿이면 확정하지 않는다")
    void ambiguousWhenTopGroupHasManyNames() {
        List<StoreRow> rows = List.of(
                store("s1", "몽클레르", "패션", "명품", null), store("s2", "데이릿", "패션", "명품", null));

        List<Scored> scored = ranking.rank(rows, "명품");

        assertThat(scored).hasSize(2);
        assertThat(ranking.isConfident(scored)).as("목록을 보여줄 질의를 한 곳으로 고정하면 안 된다").isFalse();
    }

    @Test
    @DisplayName("같은 이름이 여러 층에 있으면 한 대상으로 보고 확정한다")
    void confidentWhenSameNameOnManyFloors() {
        List<StoreRow> rows = List.of(
                store("s1", "화장실", null, null, null),
                new StoreRow("s2", "화장실", null, null, 0, 0, "ND-2", null, "FL-2", "2F", 2));

        assertThat(ranking.isConfident(ranking.rank(rows, "화장실"))).isTrue();
    }

    @Test
    @DisplayName("조사와 의문형 꼬리를 떼고 매칭한다")
    void stripsParticlesAndTails() {
        List<StoreRow> rows = List.of(store("s1", "화장실", null, null, null));

        assertThat(ranking.rank(rows, "화장실이 어디야")).as("조사 '이'와 꼬리 '어디야'가 떨어져야 한다").hasSize(1);
        assertThat(ranking.rank(rows, "화장실 몇 층이야")).hasSize(1);
    }

    @Test
    @DisplayName("등록된 매장명은 조사로 오해돼 잘리지 않는다")
    void registeredNameSurvivesAnalysis() {
        List<StoreRow> rows = List.of(store("s1", "리모와", null, null, null));

        assertThat(ranking.rank(rows, "리모와")).as("'와'를 접속조사로 보면 '리모'로 잘린다").hasSize(1);
    }
}
