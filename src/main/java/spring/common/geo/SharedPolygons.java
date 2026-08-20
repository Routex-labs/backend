package spring.common.geo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import spring.common.geometry.LocalPoint;
import spring.place.domain.Store;

/**
 * 한 폴리곤을 여러 매장이 나눠 쓰는 자리의 칸 계산.
 *
 * <p>타일과 층 도면 응답이 <b>이 클래스 하나를 함께 쓴다.</b> 두 벌로 두면 바닥 fill과 강조
 * 폴리곤이 서로 다른 자리를 가리킨다 — 원본 저장소에서 실제로 그랬다.
 *
 * <p>설계 근거: docs/decisions/005-공유-폴리곤-분할.md
 */
public final class SharedPolygons {

    private SharedPolygons() {}

    /** 나눠 쓰는 폴리곤에서 매장 하나가 받는 띠. 축에 수직인 두 경계 사이다. */
    record Slab(boolean axisIsX, double low, double high) {}

    /**
     * 매장 id → 자기 칸으로 자른 폴리곤. 나눠 쓰지 않는 매장은 키가 없다.
     *
     * <p><b>{@code stores}는 한 층의 매장 전체여야 한다.</b> 부분 집합을 주면 짝을 못 찾아
     * 아무것도 나누지 않는다.
     */
    public static Map<String, List<LocalPoint>> split(List<Store> stores) {
        List<List<Store>> groups = sharedGroups(stores, aggregateStoreIds(stores));
        Map<String, Store> byId = new HashMap<>();
        groups.forEach(group -> group.forEach(store -> byId.put(store.getId(), store)));

        Map<String, List<LocalPoint>> out = new LinkedHashMap<>();
        slabs(groups).forEach((storeId, slab) -> {
            List<LocalPoint> polygon = byId.get(storeId).getPolygon();
            List<LocalPoint> clipped = clipToSlab(polygon == null ? List.of() : polygon, slab);
            if (!clipped.isEmpty()) {
                out.put(storeId, clipped);
            }
        });
        return out;
    }

    /**
     * 다른 매장 이름 2개 이상을 이어 붙인 "묶음 매장" id. 라벨도 칸 배치도 주지 않는다.
     * (예: "아디다스나이키"처럼 두 브랜드명이 한 이름에 들어 있는 원천 데이터 항목)
     */
    static Set<String> aggregateStoreIds(List<Store> stores) {
        Map<String, String> squashed = new LinkedHashMap<>();
        for (Store store : stores) {
            if (store.getName() != null) {
                squashed.put(store.getId(), store.getName().replaceAll("\s+", ""));
            }
        }
        Set<String> aggregateIds = new HashSet<>();
        for (Store store : stores) {
            String mine = squashed.getOrDefault(store.getId(), "");
            if (mine.length() < 4) {
                continue;
            }
            Set<String> contained = new HashSet<>();
            squashed.forEach((otherId, otherName) -> {
                if (!otherId.equals(store.getId()) && otherName.length() >= 2 && !otherName.equals(mine) && mine.contains(otherName)) {
                    contained.add(otherName);
                }
            });
            if (contained.size() >= 2) {
                aggregateIds.add(store.getId());
            }
        }
        return aggregateIds;
    }

    /** centroid가 같은 매장 묶음. 2곳 이상인 그룹만 돌려준다. */
    static List<List<Store>> sharedGroups(List<Store> stores, Set<String> excludeIds) {
        Map<String, List<Store>> groups = new LinkedHashMap<>();
        for (Store store : stores) {
            if (store.getPolygon() == null || store.getPolygon().isEmpty() || excludeIds.contains(store.getId())) {
                continue;
            }
            // mm 단위 반올림. 원본이 같은 값을 복사해 넣은 경우만 묶이고, 실제로 다른 매장이
            // 우연히 묶일 수 없는 정밀도다.
            String key = round3(store.getCentroidXM()) + "," + round3(store.getCentroidYM());
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(store);
        }
        return groups.values().stream().filter(group -> group.size() >= 2).toList();
    }

    /**
     * <b>두 곳이 나눠 쓰는 자리만 나눈다.</b> 세 곳 이상은 칸이 줄무늬처럼 갈라져 도면이
     * 표처럼 보였다(실기기 확인) — 그 자리는 폴리곤도 원본 그대로 둔다.
     */
    static Map<String, Slab> slabs(List<List<Store>> groups) {
        Map<String, Slab> out = new LinkedHashMap<>();
        for (List<Store> group : groups) {
            if (group.size() != 2) {
                continue;
            }
            List<LocalPoint> polygon = group.get(0).getPolygon();
            double minX = polygon.stream().mapToDouble(LocalPoint::x).min().orElseThrow();
            double maxX = polygon.stream().mapToDouble(LocalPoint::x).max().orElseThrow();
            double minY = polygon.stream().mapToDouble(LocalPoint::y).min().orElseThrow();
            double maxY = polygon.stream().mapToDouble(LocalPoint::y).max().orElseThrow();

            boolean axisIsX = (maxX - minX) >= (maxY - minY);
            double low = axisIsX ? minX : minY;
            double span = axisIsX ? maxX - minX : maxY - minY;

            // 순서는 다비오 입구 핀을 축에 투영해 따르고, 핀이 없으면 id로 고정한다.
            List<Store> ordered = group.stream()
                    .sorted(Comparator.comparingDouble((Store store) -> {
                                Double coordinate = axisIsX ? store.getEntranceXM() : store.getEntranceYM();
                                return coordinate == null ? 0.0 : coordinate;
                            })
                            .thenComparing(Store::getId))
                    .toList();

            double width = span / ordered.size();
            for (int index = 0; index < ordered.size(); index++) {
                double slabLow = low + index * width;
                out.put(ordered.get(index).getId(), new Slab(axisIsX, slabLow, slabLow + width));
            }
        }
        return out;
    }

    /**
     * 폴리곤을 축에 수직인 두 경계 사이의 띠로 자른다(Sutherland–Hodgman). 띠가 볼록이라
     * 오목한 폴리곤도 안전하다. 결과가 도형이 못 되면 빈 목록이고 호출부가 원본을 쓴다.
     */
    static List<LocalPoint> clipToSlab(List<LocalPoint> polygon, Slab slab) {
        List<LocalPoint> points = clip(polygon, slab.axisIsX(), slab.low(), true);
        if (points.size() < 3) {
            return List.of();
        }
        points = clip(points, slab.axisIsX(), slab.high(), false);
        return points.size() >= 3 ? points : List.of();
    }

    private static List<LocalPoint> clip(List<LocalPoint> points, boolean axisIsX, double bound, boolean keepGreaterOrEqual) {
        List<LocalPoint> result = new ArrayList<>();
        for (int index = 0; index < points.size(); index++) {
            LocalPoint current = points.get(index);
            LocalPoint previous = points.get((index - 1 + points.size()) % points.size());
            boolean currentIn = isInside(current, axisIsX, bound, keepGreaterOrEqual);
            boolean previousIn = isInside(previous, axisIsX, bound, keepGreaterOrEqual);

            if (currentIn != previousIn) {
                double span = axisValue(current, axisIsX) - axisValue(previous, axisIsX);
                double t = span == 0 ? 0.0 : (bound - axisValue(previous, axisIsX)) / span;
                result.add(new LocalPoint(
                        previous.x() + (current.x() - previous.x()) * t, previous.y() + (current.y() - previous.y()) * t));
            }
            if (currentIn) {
                result.add(current);
            }
        }
        return result;
    }

    private static boolean isInside(LocalPoint point, boolean axisIsX, double bound, boolean keepGreaterOrEqual) {
        double value = axisValue(point, axisIsX);
        return (value >= bound) == keepGreaterOrEqual || value == bound;
    }

    private static double axisValue(LocalPoint point, boolean axisIsX) {
        return axisIsX ? point.x() : point.y();
    }

    private static long round3(double value) {
        return Math.round(value * 1000.0);
    }
}
