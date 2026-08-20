package spring.building.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import spring.common.geometry.LocalPoint;

/** 층. */
@Entity
@Table(
        name = "floors",
        uniqueConstraints =
                @UniqueConstraint(name = "uq_floors_building_name", columnNames = {"building_id", "name"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Floor {

    /** 층 고유 id. 원천 데이터의 내부 식별자이며 불투명하다 — 사람에게 보여주지 않는다. */
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "building_id", nullable = false)
    private Building building;

    /** 사람이 보는 층 라벨(예: B2, 1F). 사이니지 표기이며 "지하 2층"이 아니다. */
    @Column(nullable = false)
    private String name;

    /** 정렬용 정수 (지하 음수 B2=-2, 지상 양수 1F=1). 문자열 name은 정렬할 수 없어 따로 둔다. */
    @Column(nullable = false)
    private int level;

    /** 지도 좌표 보정 버전. 미보정이면 "unversioned". */
    @Column(nullable = false)
    private String mapCalibrationVersion;

    /**
     * 층 외곽선. 층마다 윤곽이 다르므로(지하 주차장이 지상보다 넓다) 건물 하나의 footprint를
     * 전 층에 돌려쓰면 어느 층이든 1F 모양이 그려진다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "footprint_local_m", columnDefinition = "jsonb")
    private List<LocalPoint> footprintLocalM;

    /**
     * 걸어다닐 수 없는 면들(아트리움 구멍·기둥·조경·에스컬레이터 도형). 이름이 없어 매장이 될
     * 수 없는 도형이다.
     *
     * <p>길찾기와는 무관하다 — 경로는 그래프로만 계산한다. 순수 표시용이라 비어 있어도 안내는
     * 정상이다. 항목 모양은 {@code {id, kind, polygon_local_m}}이고 kind는
     * void|pillar|feature|escalator|stairs다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "non_walkable_polygons_local_m", columnDefinition = "jsonb")
    private List<NonWalkablePolygon> nonWalkablePolygonsLocalM;

    /**
     * 못 걷는 면 하나. MVT 타일의 non_walkable 레이어가 그린다.
     *
     * <p>{@code @JsonProperty}를 붙이는 이유: 이 JSON을 읽는 것은 Hibernate의 매퍼이지 Spring이
     * 설정한 매퍼가 아니라서, application.yml의 SNAKE_CASE 전략이 적용되지 않는다. DB에 든 키는
     * {@code polygon_local_m}이므로 여기서 직접 맞춘다.
     */
    public record NonWalkablePolygon(String id, String kind, @JsonProperty("polygon_local_m") List<LocalPoint> polygonLocalM) {}
}
