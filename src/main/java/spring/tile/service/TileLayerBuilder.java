package spring.tile.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import spring.building.domain.Building;
import spring.building.domain.Floor;
import spring.common.geo.GeoTransform;
import spring.common.geo.LabelPoint;
import spring.common.geo.SharedPolygons;
import spring.common.geometry.LatLng;
import spring.common.geometry.LocalPoint;
import spring.place.domain.Poi;
import spring.place.domain.Store;

/**
 * 층 하나를 MVT 레이어(footprint / stores / non_walkable / store_labels / pois)로 만든다.
 *
 * <p><b>레이어 순서가 클라이언트가 꽂는 순서다</b>(뒤에 올수록 위). {@code non_walkable}이
 * {@code stores} 위인 이유: 아래에 두면 지하 주차 구획이 전부 매장이라 기둥 면적의 40%가
 * 주차칸에 가린다(실측).
 *
 * <p>도형이 없는 층에서도 <b>빈 레이어를 반드시 낸다.</b> 층마다 레이어 유무가 갈리면
 * 클라이언트 sourceLayer 배선이 그 층에서만 달라진다.
 *
 * <p>타일과 겹치지 않는 feature는 bbox 교차로만 거른다 — 실내 지도는 feature 수가 적어 정밀
 * 클리핑 없이도 타일이 과도하게 커지지 않는다.
 */
final class TileLayerBuilder {

    private TileLayerBuilder() {}

    static List<MvtWriter.Layer> build(
            Building building,
            Floor floor,
            List<Store> stores,
            List<Poi> pois,
            GeoTransform transform,
            TileBounds bounds,
            Map<String, double[]> labelMemo) {

        List<MvtWriter.Layer> layers = new ArrayList<>();

        // 층 외곽선을 받으면 그것을 그린다. 건물 footprint는 기준층 것이라 지하층 타일에도
        // 1F 윤곽이 찍힌다.
        List<LocalPoint> footprint = floor.getFootprintLocalM() != null && !floor.getFootprintLocalM().isEmpty()
                ? floor.getFootprintLocalM()
                : building.getFootprintLocalM();
        List<double[]> footprintRing = closedRing(footprint, transform);
        if (footprintRing.size() >= 4 && bounds.intersects(bbox(footprintRing))) {
            layers.add(new MvtWriter.Layer(
                    "footprint",
                    List.of(new MvtWriter.Feature(
                            null, footprintRing, properties("kind", "footprint", "building_id", building.getId())))));
        }

        List<MvtWriter.Feature> storeFeatures = new ArrayList<>();
        List<MvtWriter.Feature> labelFeatures = new ArrayList<>();

        // 묶음 매장(다른 매장 이름을 이어 붙인 항목)은 라벨을 달지 않고 칸 배치 그룹에서도 뺀다 —
        // 구성 매장들이 이미 각자 폴리곤·라벨을 갖고 있다.
        Set<String> aggregateIds = SharedPolygons.aggregateStoreIds(stores);
        List<List<Store>> sharedGroups = SharedPolygons.sharedGroups(stores, aggregateIds);
        Map<String, List<LocalPoint>> sharedPolygons = SharedPolygons.split(stores);
        Map<String, double[]> sharedLabels = sharedLabelPoints(sharedGroups, transform);
        // 세 곳 이상 그룹의 구성 매장 — 개별 라벨 대신 묶음 라벨 하나로 접는다.
        Set<String> clusteredIds = new HashSet<>();
        sharedGroups.stream().filter(group -> group.size() >= 3).forEach(group -> group.forEach(store -> clusteredIds.add(store.getId())));

        for (Store store : stores) {
            if (store.getPolygon() == null || store.getPolygon().isEmpty()) {
                continue;
            }
            // 나눠 쓰는 자리는 fill도 자기 칸으로 잘라 내보낸다 — 화면에서 실제로 나뉜 구역으로
            // 보이고 탭 판정이 폴리곤만으로 정확해진다.
            List<LocalPoint> polygonLocal = sharedPolygons.getOrDefault(store.getId(), store.getPolygon());
            List<double[]> ring = closedRing(polygonLocal, transform);
            if (ring.size() < 4 || !bounds.intersects(bbox(ring))) {
                continue;
            }
            storeFeatures.add(new MvtWriter.Feature(null, ring, storeProperties(store, false)));

            if (aggregateIds.contains(store.getId()) || clusteredIds.contains(store.getId())) {
                continue;
            }

            double[] sharedLabel = sharedLabels.get(store.getId());
            double[] labelXy = sharedLabel != null
                    ? sharedLabel
                    : labelMemo.computeIfAbsent(store.getId(), ignored -> LabelPoint.of(openRing(ring)));

            // **라벨 점은 그 점이 들어 있는 타일에만 싣는다.** 폴리곤과 같은 기준으로 실으면
            // 타일 경계에 걸친 매장이 양쪽 타일에 라벨을 하나씩 갖고, 두 타일이 함께 떠 있는
            // 순간 같은 이름이 두 번 찍힌다.
            if (bounds.containsPoint(labelXy[0], labelXy[1])) {
                labelFeatures.add(new MvtWriter.Feature(labelXy, null, storeProperties(store, sharedLabel != null)));
            }
        }

        for (MvtWriter.Feature cluster : clusterLabels(sharedGroups, transform)) {
            if (bounds.containsPoint(cluster.point()[0], cluster.point()[1])) {
                labelFeatures.add(cluster);
            }
        }

        layers.add(new MvtWriter.Layer("stores", storeFeatures));
        layers.add(new MvtWriter.Layer("non_walkable", nonWalkableFeatures(floor, transform, bounds)));
        layers.add(new MvtWriter.Layer("store_labels", labelFeatures));

        List<MvtWriter.Feature> poiFeatures = new ArrayList<>();
        for (Poi poi : pois) {
            LatLng position = transform.apply(poi.getXM(), poi.getYM());
            if (!bounds.containsPoint(position.lng(), position.lat())) {
                continue;
            }
            poiFeatures.add(new MvtWriter.Feature(
                    new double[] {position.lng(), position.lat()},
                    null,
                    properties("id", poi.getId(), "name", poi.getName(), "type", poi.getType())));
        }
        layers.add(new MvtWriter.Layer("pois", poiFeatures));

        return layers;
    }

    /**
     * 못 걷는 면. 좌표 경로는 매장 폴리곤과 완전히 같다 — 다른 경로를 타면 같은 층에서 도형만
     * 몇 m 밀린다.
     *
     * <p>properties는 kind만 싣는다. 매장 id·name·category를 실으면 카테고리 강조 필터나 탭
     * 판정에 새어 들어간다.
     */
    private static List<MvtWriter.Feature> nonWalkableFeatures(Floor floor, GeoTransform transform, TileBounds bounds) {
        List<MvtWriter.Feature> features = new ArrayList<>();
        List<Floor.NonWalkablePolygon> shapes = floor.getNonWalkablePolygonsLocalM();
        if (shapes == null) {
            return features;
        }
        for (Floor.NonWalkablePolygon shape : shapes) {
            List<double[]> ring = closedRing(shape.polygonLocalM(), transform);
            if (ring.size() < 4 || !bounds.intersects(bbox(ring))) {
                continue;
            }
            features.add(new MvtWriter.Feature(
                    null, ring, properties("kind", shape.kind() == null ? "non_walkable" : shape.kind())));
        }
        return features;
    }

    /**
     * 세 곳 이상이 한 폴리곤을 나눠 쓰는 자리의 <b>묶음 라벨 하나</b>.
     *
     * <p>칸으로 나누면 도면이 줄무늬 표처럼 보인다(실기기 확인). 대신 "첫 매장 외 N" 라벨 하나를
     * centroid에 놓고, 클라이언트가 누르면 매장 목록 시트를 띄운다 — {@code cluster}가 그 신호이고
     * {@code id}는 대표 매장이라 같은 centroid의 매장들을 되찾는 열쇠가 된다.
     */
    private static List<MvtWriter.Feature> clusterLabels(List<List<Store>> groups, GeoTransform transform) {
        List<MvtWriter.Feature> features = new ArrayList<>();
        for (List<Store> group : groups) {
            if (group.size() < 3) {
                continue;
            }
            Store first = SharedPolygons.orderAlongAxis(group).get(0);
            LatLng position = transform.apply(first.getCentroidXM(), first.getCentroidYM());
            Map<String, Object> properties = storeProperties(first, true);
            properties.put("name", "%s 외 %d".formatted(first.getName(), group.size() - 1));
            properties.put("cluster", group.size());
            features.add(new MvtWriter.Feature(new double[] {position.lng(), position.lat()}, null, properties));
        }
        return features;
    }

    /** 나눠 쓰는 자리의 라벨 점 — 칸의 가운데다. 산술 계산뿐이라 memo를 거치지 않는다. */
    private static Map<String, double[]> sharedLabelPoints(List<List<Store>> groups, GeoTransform transform) {
        Map<String, double[]> out = new LinkedHashMap<>();
        Map<String, Store> byId = new LinkedHashMap<>();
        groups.forEach(group -> group.forEach(store -> byId.put(store.getId(), store)));

        SharedPolygons.slabs(groups).forEach((storeId, slab) -> {
            Store store = byId.get(storeId);
            double along = (slab.low() + slab.high()) / 2;
            double xM = slab.axisIsX() ? along : store.getCentroidXM();
            double yM = slab.axisIsX() ? store.getCentroidYM() : along;
            LatLng position = transform.apply(xM, yM);
            out.put(storeId, new double[] {position.lng(), position.lat()});
        });
        return out;
    }

    /**
     * 매장 feature의 properties. category/subcategory는 클라이언트가 MapLibre setFilter로 카테고리
     * 필터를 걸 때 쓴다. 두 컬럼 모두 nullable이라 <b>없으면 키 자체를 넣지 않는다</b> — null이
     * 실리면 {@code ['has', 'category']}가 참이 되면서 비교는 어긋나, "값이 없다"를 표현할 방법이
     * 사라진다.
     *
     * @param shared 충돌 판정을 끈 전용 레이어로 그리라는 표시. 칸으로 나눠도 라벨 간격이 글자
     *     폭보다 좁을 수 있어, 일반 레이어에 두면 충돌 처리가 결국 하나를 지운다.
     */
    private static Map<String, Object> storeProperties(Store store, boolean shared) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("id", store.getId());
        properties.put("name", store.getName());
        properties.put("kind", "store");
        if (store.getCategory() != null) {
            properties.put("category", store.getCategory());
        }
        if (store.getSubcategory() != null) {
            properties.put("subcategory", store.getSubcategory());
        }
        if (shared) {
            properties.put("shared", true);
        }
        return properties;
    }

    private static Map<String, Object> properties(Object... keyValues) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (int index = 0; index < keyValues.length; index += 2) {
            properties.put((String) keyValues[index], keyValues[index + 1]);
        }
        return properties;
    }

    /** local_m 폴리곤을 wgs84 (lng, lat) 닫힌 링으로. 비면 빈 목록. */
    private static List<double[]> closedRing(List<LocalPoint> points, GeoTransform transform) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        List<double[]> ring = new ArrayList<>(points.size() + 1);
        for (LocalPoint point : points) {
            LatLng converted = transform.apply(point.x(), point.y());
            ring.add(new double[] {converted.lng(), converted.lat()});
        }
        double[] first = ring.get(0);
        double[] last = ring.get(ring.size() - 1);
        if (first[0] != last[0] || first[1] != last[1]) {
            ring.add(new double[] {first[0], first[1]});
        }
        return ring;
    }

    /**
     * 닫힘 중복점을 뗀 링. {@link LabelPoint}는 링이 닫혀 있지 않다고 보고 마지막-첫 변을 스스로
     * 잇기 때문에, 닫힌 링을 그대로 넘기면 길이 0짜리 변이 하나 끼어든다.
     */
    private static List<double[]> openRing(List<double[]> ring) {
        int count = ring.size();
        if (count > 1 && ring.get(0)[0] == ring.get(count - 1)[0] && ring.get(0)[1] == ring.get(count - 1)[1]) {
            return ring.subList(0, count - 1);
        }
        return ring;
    }

    private static double[] bbox(List<double[]> ring) {
        double minX = Double.MAX_VALUE;
        double minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxY = -Double.MAX_VALUE;
        for (double[] point : ring) {
            minX = Math.min(minX, point[0]);
            minY = Math.min(minY, point[1]);
            maxX = Math.max(maxX, point[0]);
            maxY = Math.max(maxY, point[1]);
        }
        return new double[] {minX, minY, maxX, maxY};
    }
}
