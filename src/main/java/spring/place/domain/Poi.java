package spring.place.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import spring.building.domain.Floor;

/** 지도 위 마커. 그래프 노드에서 승격된 시설점(엘리베이터·에스컬레이터)이다. */
@Entity
@Table(
        name = "pois",
        indexes = {@Index(name = "idx_pois_floor", columnList = "floor_id"), @Index(name = "idx_pois_type", columnList = "type")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Poi {

    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id", nullable = false)
    private Floor floor;

    /** 종류(elevator/escalator 등). 클라이언트가 아이콘을 고르는 키다. */
    @Column(nullable = false)
    private String type;

    private String name;

    @Column(name = "x_m", nullable = false)
    private double xM;

    @Column(name = "y_m", nullable = false)
    private double yM;

    /** 이 마커가 승격된 원본 그래프 노드 id. */
    private String linkedNodeId;
}
