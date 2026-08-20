package spring.building.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 층. 지금은 건물 응답이 쓰는 컬럼(name·level)만 매핑한다.
 * 도면 외곽선·비보행 폴리곤은 floormap 패키지를 이식할 때 여기에 더한다.
 */
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
}
