package spring.building.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import spring.common.geometry.LocalPoint;

/** 건물. 층(Floor)을 1:N으로 거느린다. */
@Entity
@Table(name = "buildings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Building {

    /** 건물 고유 id (예: thehyundai-seoul). 사람이 읽는 slug이며 서버가 만들지 않는다. */
    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    /** 바닥 면적(㎡). 원천 데이터에 없을 수 있어 null 허용. */
    private Double areaM2;

    /** 둘레(m). 원천 데이터에 없을 수 있어 null 허용. */
    // 컬럼명을 적는 이유: Hibernate 네이밍 전략은 **끝에 붙은 대문자 앞에 언더바를 넣지 않는다**.
    // perimeterM은 perimeterm이 되고 footprintLocalM은 footprint_localm이 된다
    // (areaM2는 뒤에 숫자가 있어 area_m2로 정상 변환된다). 실제로 여기서 한 번 깨졌다.
    @Column(name = "perimeter_m")
    private Double perimeterM;

    /**
     * 건물 대표 외곽선. 기준층 것이라 층별 외곽은 층 지도 응답을 써야 한다.
     * 층마다 윤곽이 달라서(지하 주차장이 지상보다 넓다) 이걸 전 층에 돌려쓰면 안 된다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "footprint_local_m", columnDefinition = "jsonb")
    private List<LocalPoint> footprintLocalM;

    @OneToMany(mappedBy = "building")
    private List<Floor> floors = new ArrayList<>();
}
