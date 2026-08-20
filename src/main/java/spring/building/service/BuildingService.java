package spring.building.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import spring.building.domain.Building;
import spring.building.domain.Floor;
import spring.building.dto.BuildingDetailResponse;
import spring.building.dto.BuildingSummaryResponse;
import spring.building.repository.BuildingRepository;

/**
 * 건물 목록·상세 조회.
 *
 * <p>층 정렬과 기본 층 선택 규칙이 이 클래스의 전부다. 검증 기준은
 * {@code BuildingServiceTest}가 단일 출처다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BuildingService {

    private final BuildingRepository buildingRepository;

    public List<BuildingSummaryResponse> list() {
        return buildingRepository.findAll().stream().map(BuildingService::toSummary).toList();
    }

    /** 없는 건물이면 빈 Optional. HTTP 상태 변환은 컨트롤러가 한다. */
    public Optional<BuildingDetailResponse> detail(String buildingId) {
        return buildingRepository.findById(buildingId).map(BuildingService::toDetail);
    }

    private static BuildingSummaryResponse toSummary(Building building) {
        List<Floor> floors = sortedFloors(building);
        return new BuildingSummaryResponse(
                building.getId(), building.getName(), floorNames(floors), defaultFloor(floors));
    }

    private static BuildingDetailResponse toDetail(Building building) {
        List<Floor> floors = sortedFloors(building);
        return new BuildingDetailResponse(
                building.getId(),
                building.getName(),
                floorNames(floors),
                defaultFloor(floors),
                building.getAreaM2(),
                building.getPerimeterM(),
                building.getFootprintLocalM() == null ? List.of() : building.getFootprintLocalM(),
                // ponytail: wgs84 외곽선은 실측 앵커로 아핀을 피팅해야 나온다(파이썬 geo_transform).
                // 그 이식이 tile 패키지와 묶여 있어 여기서는 null로 둔다 — 계약이 null을 허용한다.
                null,
                // ponytail: 타일 버전 토큰. tile 패키지 이식 때 채운다. 없으면 캐시 수명만 짧아진다.
                null);
    }

    /**
     * 엘리베이터 버튼판과 같은 순서(위층이 앞). level은 실제 층 높이라 6F=6 … 1F=1 … B6=-6이므로
     * 내림차순이 곧 6F→B6이 된다.
     */
    static List<Floor> sortedFloors(Building building) {
        return building.getFloors().stream()
                .sorted(Comparator.comparingInt(Floor::getLevel).reversed())
                .toList();
    }

    /**
     * 앱이 처음 열 층. 출입구가 있는 지상 1층이 기준이고, 지상층이 없으면 최상층으로 폴백한다
     * (지하 전용 건물도 빈 값 없이 열리도록). 층이 하나도 없으면 null.
     */
    static String defaultFloor(List<Floor> floors) {
        return floors.stream()
                .filter(floor -> floor.getLevel() >= 1)
                .min(Comparator.comparingInt(Floor::getLevel))
                .or(() -> floors.stream().max(Comparator.comparingInt(Floor::getLevel)))
                .map(Floor::getName)
                .orElse(null);
    }

    private static List<String> floorNames(List<Floor> floors) {
        return floors.stream().map(Floor::getName).toList();
    }
}
