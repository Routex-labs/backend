package spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 생성된 컬럼 이름이 파이썬 백엔드의 스키마와 같은지 지킨다.
 *
 * <p><b>왜 이 테스트가 있나:</b> Hibernate의 네이밍 전략은 <b>끝에 붙은 대문자 앞에 언더바를
 * 넣지 않는다.</b> {@code perimeterM}은 {@code perimeterm}, {@code sourceX}는 {@code sourcex}가
 * 된다({@code areaM2}는 뒤에 숫자가 있어 정상 변환된다). 이 함정에 세 번 걸렸고, 그중 둘은
 * 응답에 안 나가는 컬럼이라 다른 테스트로는 잡히지 않았다 — 실데이터를 넣을 때야 드러났다.
 *
 * <p>기대값은 원본 저장소의 ORM 모델(`app/models/*.py`)이 단일 출처다.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SchemaColumnNamesTest {

    private static final Map<String, List<String>> EXPECTED_COLUMNS = Map.of(
            "buildings", List.of("area_m2", "footprint_local_m", "id", "name", "perimeter_m"),
            "floors",
                    List.of(
                            "building_id",
                            "footprint_local_m",
                            "id",
                            "level",
                            "map_calibration_version",
                            "name",
                            "non_walkable_polygons_local_m"),
            "nodes", List.of("floor_id", "id", "lat", "lng", "name", "source_x", "source_y", "type", "x_m", "y_m"),
            "edges",
                    List.of(
                            "bidirectional",
                            "cost_m",
                            "floor_id",
                            "from_node_id",
                            "geometry",
                            "id",
                            "length_m",
                            "to_node_id",
                            "transfer_mode"),
            "stores",
                    List.of(
                            "category",
                            "centroid_x_m",
                            "centroid_y_m",
                            "entrance_node_id",
                            "entrance_x_m",
                            "entrance_y_m",
                            "floor_id",
                            "id",
                            "name",
                            "polygon",
                            "search_facets",
                            "subcategory"),
            "pois", List.of("floor_id", "id", "linked_node_id", "name", "type", "x_m", "y_m"));

    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("생성된 컬럼 이름이 파이썬 스키마와 정확히 같다")
    void columnNamesMatchPythonSchema() {
        EXPECTED_COLUMNS.forEach((table, expected) -> {
            List<String> actual = jdbcTemplate.queryForList(
                    "select column_name from information_schema.columns "
                            + "where table_schema = 'public' and table_name = ? order by column_name",
                    String.class,
                    table);

            assertThat(actual)
                    .as("%s 컬럼 — 이름이 어긋나면 실데이터 적재가 깨진다", table)
                    .containsExactlyElementsOf(expected);
        });
    }
}
