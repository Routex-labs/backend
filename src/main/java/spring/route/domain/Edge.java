package spring.route.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import spring.building.domain.Floor;
import spring.common.geometry.LocalPoint;

/** 두 노드를 잇는 간선. */
@Entity
@Table(
        name = "edges",
        indexes = {
            @Index(name = "idx_edges_floor", columnList = "floor_id"),
            @Index(name = "idx_edges_from", columnList = "from_node_id"),
            @Index(name = "idx_edges_to", columnList = "to_node_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Edge {

    @Id
    private String id;

    /**
     * 층 내부 간선은 해당 층 id를 가진다. 층을 잇는 수직 전이 간선은 특정 층에 속하지 않아
     * <b>null</b>이다 — 그래서 층별 조회에서 자동으로 빠지고, 건물 전체 그래프에서만 합류한다.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id")
    private Floor floor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_node_id", nullable = false)
    private Node fromNode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_node_id", nullable = false)
    private Node toNode;

    /** 실제 이동 거리(미터). 사용자에게 보여 주는 거리·진행률의 근거다. */
    @Column(name = "length_m", nullable = false)
    private double lengthM;

    /**
     * 라우팅 비용(미터 단위 가중치) — 다익스트라가 최소화하는 값.
     *
     * <p>층 내부 간선은 {@code lengthM}과 같고 수직 전이 간선만 다르다. 둘을 한 컬럼으로
     * 겸하면 에스컬레이터 20m 같은 가중치가 사용자에게 보이는 총 거리에 그대로 더해진다.
     */
    @Column(name = "cost_m", nullable = false)
    private double costM;

    @Column(nullable = false)
    private boolean bidirectional;

    /** 간선을 그릴 꺾은선(local_m). 직선이면 비어 있다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<LocalPoint> geometry;

    /** 층 내부 간선은 null, 수직 전이 간선은 elevator/escalator. */
    private String transferMode;
}
