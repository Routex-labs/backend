package spring.route.domain;

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

/**
 * 길찾기 그래프의 정점. 통로 교차점·매장 입구·수직이동 지점이 여기 해당한다.
 *
 * <p>{@code floor}는 지연로딩이지만 {@code getFloor().getId()}는 프록시의 식별자라
 * 쿼리를 일으키지 않는다. 응답에는 층 id만 나가므로 이 접근으로 충분하다.
 */
@Entity
@Table(
        name = "nodes",
        indexes = {@Index(name = "idx_nodes_floor", columnList = "floor_id"), @Index(name = "idx_nodes_type", columnList = "type")})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Node {

    /** 노드 고유 id. 층 스코프로 접두가 붙는다: {@code {floor_id}:{원본id}} */
    @Id
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "floor_id", nullable = false)
    private Floor floor;

    /** 노드 종류(junction/elevator/escalator 등). 경로 안내 문구와 아이콘의 근거다. */
    @Column(nullable = false)
    private String type;

    private String name;

    // 컬럼명을 적는 이유: Hibernate는 끝에 붙은 대문자 앞에 언더바를 넣지 않는다(xM → xm).
    @Column(name = "x_m", nullable = false)
    private double xM;

    @Column(name = "y_m", nullable = false)
    private double yM;

    /** 실측 위도. 이 값이 채워진 노드들이 좌표 변환의 앵커가 된다. 3개 미만이면 합성 좌표로 폴백한다. */
    private Double lat;

    private Double lng;

    /** 원천 데이터 원좌표(디버그·역추적용). 응답에는 나가지 않는다. */
    private Double sourceX;

    private Double sourceY;
}
