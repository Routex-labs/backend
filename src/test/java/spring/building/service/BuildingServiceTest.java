package spring.building.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import spring.building.domain.Building;
import spring.building.domain.Floor;

/**
 * 층 정렬·기본 층 규칙의 검증 기준. 파이썬 building_queries._to_building_summary /
 * _default_floor 와 같은 동작을 해야 한다.
 */
class BuildingServiceTest {

    private static Building buildingWith(List<Floor> floors) {
        return new Building("b", "건물", null, null, null, floors);
    }

    private static Floor floor(String name, int level) {
        return new Floor(name.toLowerCase(), null, name, level);
    }

    @Test
    @DisplayName("층은 엘리베이터 버튼판 순서(위층이 앞)로 정렬된다")
    void sortsFloorsTopFirst() {
        Building building = buildingWith(List.of(floor("B1", -1), floor("2F", 2), floor("1F", 1)));

        List<String> names = BuildingService.sortedFloors(building).stream().map(Floor::getName).toList();

        assertThat(names).containsExactly("2F", "1F", "B1");
    }

    @Test
    @DisplayName("기본 층은 지상 최저층(1F)이다 — 목록 첫 항목(최상층)이 아니다")
    void defaultFloorIsLowestAboveGround() {
        Building building = buildingWith(List.of(floor("B1", -1), floor("2F", 2), floor("1F", 1)));

        assertThat(BuildingService.defaultFloor(BuildingService.sortedFloors(building))).isEqualTo("1F");
    }

    @Test
    @DisplayName("지상층이 없는 건물은 최상층으로 폴백한다")
    void defaultFloorFallsBackToTopWhenAllUnderground() {
        Building building = buildingWith(List.of(floor("B3", -3), floor("B1", -1), floor("B2", -2)));

        assertThat(BuildingService.defaultFloor(BuildingService.sortedFloors(building))).isEqualTo("B1");
    }

    @Test
    @DisplayName("층이 없으면 기본 층은 null이다")
    void defaultFloorIsNullWithoutFloors() {
        Building building = buildingWith(List.of());

        assertThat(BuildingService.defaultFloor(BuildingService.sortedFloors(building))).isNull();
    }
}
