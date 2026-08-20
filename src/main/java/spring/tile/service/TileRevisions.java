package spring.tile.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 타일 입력이 바뀌었는지 값 하나로 판정하는 리비전.
 *
 * <p>타일 캐시의 무효화 키이자 {@code /buildings/{id}} 응답의 {@code tile_revision}이다.
 * 클라이언트가 타일 URL에 {@code ?v=}로 붙이면 서버가 immutable을 줄 수 있다.
 *
 * <p><b>왜 그래프 리비전을 쓰지 않나:</b> 타일이 그리는 것은 매장·POI·외곽선·못 걷는 면(+좌표
 * 변환)인데, 그래프 리비전은 노드·간선만 해싱하므로 "타일에는 보이지만 그래프에는 없는" 변경
 * (매장 이름·폴리곤 편집)을 잡지 못한다.
 *
 * <p><b>왜 DB 파일 세대를 쓰지 않나:</b> 파이썬은 SQLite 파일의 (경로, mtime)을 썼다.
 * PostgreSQL에는 대응하는 것이 없어, 타일 입력 컬럼을 그대로 해싱한다 — mtime보다 정확하고
 * 건물 단위로 갈린다. 대신 매번 계산하면 비싸므로 짧게 캐시한다(재시드는 개발 시점 사건이라
 * 몇 초의 지연은 무해하다).
 */
@Service
@RequiredArgsConstructor
public class TileRevisions {

    /**
     * 타일이 실제로 그리는 컬럼만 모아 해싱한다. 여기 없는 컬럼이 바뀌어도 타일은 그대로이므로
     * 리비전이 유지되어야 한다 — 안 그러면 무관한 편집마다 클라이언트 캐시가 통째로 깨진다.
     */
    private static final String REVISION_SQL =
            """
            select md5(coalesce(string_agg(payload, '|' order by payload), 'empty')) from (
              select s.id || ':' || s.name || ':' || coalesce(s.category, '') || ':'
                     || coalesce(s.subcategory, '') || ':' || s.centroid_x_m || ':' || s.centroid_y_m
                     || ':' || coalesce(s.polygon::text, '') as payload
                from stores s join floors f on s.floor_id = f.id where f.building_id = ?
              union all
              select p.id || ':' || coalesce(p.name, '') || ':' || p.type || ':' || p.x_m || ':' || p.y_m
                from pois p join floors f on p.floor_id = f.id where f.building_id = ?
              union all
              select f.id || ':' || f.name || ':' || coalesce(f.footprint_local_m::text, '')
                     || ':' || coalesce(f.non_walkable_polygons_local_m::text, '')
                from floors f where f.building_id = ?
              union all
              select n.id || ':' || coalesce(n.lat::text, '') || ':' || coalesce(n.lng::text, '')
                     || ':' || n.x_m || ':' || n.y_m
                from nodes n join floors nf on n.floor_id = nf.id where nf.building_id = ?
              union all
              select b.id || ':' || coalesce(b.footprint_local_m::text, '')
                from buildings b where b.id = ?
            ) inputs
            """;

    private final JdbcTemplate jdbcTemplate;

    private final LoadingCache<String, String> cache =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(5)).build(this::compute);

    /** 건물이 없어도 값을 준다("empty"의 해시) — 존재 여부는 호출부가 이미 확인했다. */
    public String forBuilding(String buildingId) {
        return cache.get(buildingId);
    }

    private String compute(String buildingId) {
        return jdbcTemplate.queryForObject(
                REVISION_SQL, String.class, buildingId, buildingId, buildingId, buildingId, buildingId);
    }
}
