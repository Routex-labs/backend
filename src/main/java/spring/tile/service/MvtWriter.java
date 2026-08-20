package spring.tile.service;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MVT(Mapbox Vector Tile) 바이트를 직접 만든다.
 *
 * <p>라이브러리를 쓰지 않는 이유: JVM용 MVT 인코더는 사실상 {@code com.wdtinc:mapbox-vector-tile}
 * 하나뿐인데 2019년 이후 관리가 없고 JTS를 전이 의존으로 끌고 온다. 필요한 것은 스펙의 일부
 * (Point/Polygon, 문자열·숫자 속성)뿐이라 직접 쓰는 편이 짧고 의존성이 없다.
 *
 * <p>좌표 규약은 스펙 그대로 <b>왼쪽 위 원점, y 아래로 증가</b>다. 파이썬 구현이 내보내는
 * 바이트와 같은 규약인지는 실제로 인코딩해 확인했다(docs/migration/대응표.md).
 *
 * <p>스펙: {@code Tile{layers=3} Layer{name=1,features=2,keys=3,values=4,extent=5,version=15}
 * Feature{id=1,tags=2,type=3,geometry=4}}
 */
final class MvtWriter {

    private MvtWriter() {}

    /** MVT 기본 격자 해상도. 클라이언트가 다른 값을 가정하지 않도록 스펙 기본값을 그대로 쓴다. */
    static final int EXTENT = 4096;

    private static final int GEOM_POINT = 1;
    private static final int GEOM_POLYGON = 3;

    private static final int CMD_MOVE_TO = 1;
    private static final int CMD_LINE_TO = 2;
    private static final int CMD_CLOSE_PATH = 7;

    /** 타일 하나. 레이어 순서는 넘긴 순서를 유지한다 — 클라이언트가 그 순서로 레이어를 꽂는다. */
    record Layer(String name, List<Feature> features) {}

    /**
     * feature 하나.
     *
     * @param polygonRing 폴리곤이면 닫힌 링(첫 점 = 끝 점), 점이면 null
     * @param point 점이면 {lng, lat}, 폴리곤이면 null
     */
    record Feature(double[] point, List<double[]> polygonRing, Map<String, Object> properties) {}

    static byte[] encode(List<Layer> layers, TileBounds bounds) {
        ByteArrayOutputStream tile = new ByteArrayOutputStream();
        for (Layer layer : layers) {
            writeLengthDelimited(tile, 3, encodeLayer(layer, bounds));
        }
        return tile.toByteArray();
    }

    private static byte[] encodeLayer(Layer layer, TileBounds bounds) {
        // 키·값은 레이어 단위 사전이고 feature의 tags는 그 사전의 인덱스 쌍이다.
        Map<String, Integer> keyIndex = new LinkedHashMap<>();
        Map<Object, Integer> valueIndex = new LinkedHashMap<>();
        List<byte[]> encodedFeatures = new ArrayList<>();

        for (Feature feature : layer.features()) {
            encodedFeatures.add(encodeFeature(feature, bounds, keyIndex, valueIndex));
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeLengthDelimited(out, 1, layer.name().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        for (byte[] feature : encodedFeatures) {
            writeLengthDelimited(out, 2, feature);
        }
        for (String key : keyIndex.keySet()) {
            writeLengthDelimited(out, 3, key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        for (Object value : valueIndex.keySet()) {
            writeLengthDelimited(out, 4, encodeValue(value));
        }
        writeVarintField(out, 5, EXTENT);
        writeVarintField(out, 15, 2);
        return out.toByteArray();
    }

    private static byte[] encodeFeature(
            Feature feature, TileBounds bounds, Map<String, Integer> keyIndex, Map<Object, Integer> valueIndex) {
        ByteArrayOutputStream tags = new ByteArrayOutputStream();
        feature.properties().forEach((key, value) -> {
            if (value == null) {
                // null은 키 자체를 싣지 않는다. MapLibre 필터에서 ['has', k]로 값 유무를 판정할 수
                // 있어야 하는데, null이 실리면 has가 참이 되면서 비교는 어긋난다.
                return;
            }
            writeVarint(tags, keyIndex.computeIfAbsent(key, ignored -> keyIndex.size()));
            writeVarint(tags, valueIndex.computeIfAbsent(value, ignored -> valueIndex.size()));
        });

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeLengthDelimited(out, 2, tags.toByteArray());
        writeVarintField(out, 3, feature.point() != null ? GEOM_POINT : GEOM_POLYGON);
        writeLengthDelimited(out, 4, encodeGeometry(feature, bounds));
        return out.toByteArray();
    }

    private static byte[] encodeGeometry(Feature feature, TileBounds bounds) {
        ByteArrayOutputStream geometry = new ByteArrayOutputStream();
        if (feature.point() != null) {
            int[] xy = toTile(feature.point()[0], feature.point()[1], bounds);
            writeVarint(geometry, command(CMD_MOVE_TO, 1));
            writeVarint(geometry, zigzag(xy[0]));
            writeVarint(geometry, zigzag(xy[1]));
            return geometry.toByteArray();
        }

        List<double[]> ring = feature.polygonRing();
        // 닫힘 중복점은 ClosePath가 대신한다 — 그대로 두면 길이 0짜리 변이 하나 생긴다.
        int count = ring.size();
        if (count > 1 && ring.get(0)[0] == ring.get(count - 1)[0] && ring.get(0)[1] == ring.get(count - 1)[1]) {
            count--;
        }
        if (count < 3) {
            return geometry.toByteArray();
        }

        int previousX = 0;
        int previousY = 0;
        int[] first = toTile(ring.get(0)[0], ring.get(0)[1], bounds);
        writeVarint(geometry, command(CMD_MOVE_TO, 1));
        writeVarint(geometry, zigzag(first[0] - previousX));
        writeVarint(geometry, zigzag(first[1] - previousY));
        previousX = first[0];
        previousY = first[1];

        writeVarint(geometry, command(CMD_LINE_TO, count - 1));
        for (int index = 1; index < count; index++) {
            int[] xy = toTile(ring.get(index)[0], ring.get(index)[1], bounds);
            writeVarint(geometry, zigzag(xy[0] - previousX));
            writeVarint(geometry, zigzag(xy[1] - previousY));
            previousX = xy[0];
            previousY = xy[1];
        }
        writeVarint(geometry, command(CMD_CLOSE_PATH, 1));
        return geometry.toByteArray();
    }

    /** 경위도를 타일 격자 좌표로. 왼쪽 위가 원점이라 y는 북쪽에서 잰다. */
    private static int[] toTile(double lng, double lat, TileBounds bounds) {
        double x = (lng - bounds.west()) / (bounds.east() - bounds.west()) * EXTENT;
        double y = (bounds.north() - lat) / (bounds.north() - bounds.south()) * EXTENT;
        return new int[] {(int) Math.round(x), (int) Math.round(y)};
    }

    private static byte[] encodeValue(Object value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (value instanceof String text) {
            writeLengthDelimited(out, 1, text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } else if (value instanceof Boolean flag) {
            writeVarintField(out, 7, flag ? 1 : 0);
        } else if (value instanceof Integer || value instanceof Long) {
            writeVarintField(out, 4, ((Number) value).longValue());
        } else if (value instanceof Number number) {
            // double_value(3)는 고정 8바이트다.
            out.write(fieldHeader(3, 1));
            long bits = Double.doubleToLongBits(number.doubleValue());
            for (int index = 0; index < 8; index++) {
                out.write((int) ((bits >> (8 * index)) & 0xFF));
            }
        } else {
            writeLengthDelimited(out, 1, String.valueOf(value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    private static int command(int id, int count) {
        return (id & 0x7) | (count << 3);
    }

    private static long zigzag(int value) {
        return ((long) value << 1) ^ (value >> 31);
    }

    private static int fieldHeader(int fieldNumber, int wireType) {
        return (fieldNumber << 3) | wireType;
    }

    private static void writeLengthDelimited(ByteArrayOutputStream out, int fieldNumber, byte[] payload) {
        writeVarint(out, fieldHeader(fieldNumber, 2));
        writeVarint(out, payload.length);
        out.write(payload, 0, payload.length);
    }

    private static void writeVarintField(ByteArrayOutputStream out, int fieldNumber, long value) {
        writeVarint(out, fieldHeader(fieldNumber, 0));
        writeVarint(out, value);
    }

    private static void writeVarint(ByteArrayOutputStream out, long value) {
        long remaining = value;
        while ((remaining & ~0x7FL) != 0) {
            out.write((int) ((remaining & 0x7F) | 0x80));
            remaining >>>= 7;
        }
        out.write((int) remaining);
    }
}
