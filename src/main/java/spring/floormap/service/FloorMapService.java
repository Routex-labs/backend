package spring.floormap.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import spring.building.domain.Floor;
import spring.building.repository.FloorRepository;
import spring.common.geo.BuildingGeoTransforms;
import spring.common.geo.GeoTransform;
import spring.common.geo.SharedPolygons;
import spring.common.geometry.LocalPoint;
import spring.floormap.dto.FloorMapResponse;
import spring.floormap.dto.FloorResponse;
import spring.place.domain.Store;
import spring.place.repository.PoiRepository;
import spring.place.repository.StoreRepository;
import spring.route.service.GraphService;

/** 층 하나를 그리는 데 필요한 것을 모아 준다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FloorMapService {

    private final FloorRepository floorRepository;
    private final StoreRepository storeRepository;
    private final PoiRepository poiRepository;
    private final BuildingGeoTransforms geoTransforms;
    private final GraphService graphService;

    /** 없는 건물·층이면 빈 Optional. */
    public Optional<FloorMapResponse> floorMap(String buildingId, String floorName) {
        return floorRepository.findByBuildingIdAndName(buildingId, floorName).map(floor -> toResponse(buildingId, floor));
    }

    private FloorMapResponse toResponse(String buildingId, Floor floor) {
        List<Store> stores = storeRepository.findByFloorId(floor.getId());
        GeoTransform transform = geoTransforms.forBuilding(buildingId);

        // 나눠 쓰는 폴리곤은 타일과 **같은 코드**로 자른다. 강조 오버레이가 이 값을 쓰는데
        // 타일만 자르면 바닥 fill 위에 통짜 사각형이 얹힌다.
        Map<String, List<LocalPoint>> sharedPolygons = SharedPolygons.split(stores);
        List<LocalPoint> footprint = footprintOf(floor);

        return new FloorMapResponse(
                new FloorResponse(floor.getId(), floor.getName(), floor.getLevel()),
                "local_m",
                floor.getMapCalibrationVersion(),
                footprint,
                footprint.isEmpty() ? null : BuildingGeoTransforms.toLatLng(footprint, transform),
                graphService.toFloorGraph(floor),
                stores.stream()
                        .map(store -> PlaceMapper.toStore(store, transform, sharedPolygons))
                        .toList(),
                poiRepository.findByFloorId(floor.getId()).stream()
                        .map(poi -> PlaceMapper.toPoi(poi, transform))
                        .toList());
    }

    /**
     * 층 자체 외곽선이 있으면 그것을 쓰고, 없으면 건물 대표 외곽으로 폴백한다. 건물 footprint는
     * 기준층(1F) 것이라 전 층에 돌려쓰면 지하 주차장에도 1F 윤곽이 그려진다.
     */
    private static List<LocalPoint> footprintOf(Floor floor) {
        if (floor.getFootprintLocalM() != null && !floor.getFootprintLocalM().isEmpty()) {
            return floor.getFootprintLocalM();
        }
        List<LocalPoint> buildingFootprint = floor.getBuilding().getFootprintLocalM();
        return buildingFootprint == null ? List.of() : buildingFootprint;
    }
}
