package spring.tile.service;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import spring.building.domain.Floor;
import spring.building.repository.FloorRepository;
import spring.common.geo.BuildingGeoTransforms;
import spring.common.geo.GeoTransform;
import spring.place.domain.Poi;
import spring.place.domain.Store;
import spring.place.repository.PoiRepository;
import spring.place.repository.StoreRepository;

/**
 * 층 지도를 MVT 바이트로 렌더링한다.
 *
 * <p><b>왜 캐시가 필요한가:</b> MVT 인코딩은 CPU 바운드다(층 하나 9개 타일에 1초 이상). 층을
 * 전환할 때 MapLibre가 격자 여러 개를 병렬 요청하면 뒤쪽 타일이 3~4초 이상 걸리고, 그 사이
 * keep-alive 소켓 경쟁으로 실패한 타일을 MapLibre가 한동안 재요청하지 않아 층 오버레이가 빈 채로
 * 남는 증상이 있었다. 타일 바이트는 (건물 데이터, 층, z, x, y)에 대해 결정적이라 프로세스
 * 메모리에 캐싱해도 안전하다.
 *
 * <p>상한을 두는 이유: 예전 구현은 무제한 전역 맵이었고, 오래 사는 배포 프로세스에서 조합이
 * 쌓이며 메모리가 상한 없이 커졌다.
 */
@Service
@RequiredArgsConstructor
public class TileService {

    /** 타일 캐시 상한(바이트). 넘으면 LRU로 축출한다. */
    private static final long MAX_CACHE_BYTES = 64L * 1024 * 1024;

    private final FloorRepository floorRepository;
    private final StoreRepository storeRepository;
    private final PoiRepository poiRepository;
    private final BuildingGeoTransforms geoTransforms;
    private final TileRevisions tileRevisions;

    private final LoadingCache<TileKey, byte[]> tileCache = Caffeine.newBuilder()
            .maximumWeight(MAX_CACHE_BYTES)
            .weigher((TileKey key, byte[] bytes) -> bytes.length)
            .build(this::render);

    /**
     * 매장 라벨 좌표 memo — 리비전 하나에 대한 {매장 id: (lng, lat)}.
     *
     * <p>라벨 점 격자 탐색이 타일 생성 비용의 대부분인데, 결과는 매장 폴리곤과 건물 단위 변환에만
     * 의존해 z/x/y와 무관하다. 같은 리비전에서는 어느 타일에서 계산하든 같은 좌표가 나온다.
     * 리비전이 바뀌면 키가 통째로 달라져 낡은 좌표가 재사용되지 않는다.
     */
    private final LoadingCache<String, Map<String, double[]>> labelMemos = Caffeine.newBuilder()
            .maximumSize(8)
            .build(revision -> new ConcurrentHashMap<>());

    private record TileKey(String buildingId, String floorName, int z, int x, int y, String revision) {}

    /**
     * 없는 건물·층이면 빈 Optional.
     *
     * @throws IllegalArgumentException z/x/y가 타일 격자를 벗어나면
     */
    @Transactional(readOnly = true)
    public Optional<byte[]> renderFloorTile(String buildingId, String floorName, int z, int x, int y) {
        // 좌표 검증을 캐시 조회보다 먼저 한다 — 잘못된 좌표로 캐시 항목을 만들지 않는다.
        TileBounds.of(z, x, y);

        if (floorRepository.findByBuildingIdAndName(buildingId, floorName).isEmpty()) {
            return Optional.empty();
        }
        String revision = tileRevisions.forBuilding(buildingId);
        return Optional.of(tileCache.get(new TileKey(buildingId, floorName, z, x, y, revision)));
    }

    private byte[] render(TileKey key) {
        Floor floor = floorRepository
                .findByBuildingIdAndName(key.buildingId(), key.floorName())
                .orElseThrow();
        List<Store> stores = storeRepository.findByFloorId(floor.getId());
        List<Poi> pois = poiRepository.findByFloorId(floor.getId());
        GeoTransform transform = geoTransforms.forBuilding(key.buildingId());
        TileBounds bounds = TileBounds.of(key.z(), key.x(), key.y());

        return MvtWriter.encode(
                TileLayerBuilder.build(
                        floor.getBuilding(),
                        floor,
                        stores,
                        pois,
                        transform,
                        bounds,
                        labelMemos.get(key.revision())),
                bounds);
    }
}
