-- 개발·테스트용 최소 시드. 실데이터는 파이썬 백엔드의 시드 스크립트가 만든다.
-- 여기서는 엔드포인트가 살아 있는지 확인할 만큼만 넣는다.
INSERT INTO buildings (id, name, area_m2, perimeter_m, footprint_local_m) VALUES
  ('thehyundai-seoul', '더현대 서울', 21000.0, 620.5,
   '[{"x":0.0,"y":0.0},{"x":100.0,"y":0.0},{"x":100.0,"y":80.0},{"x":0.0,"y":80.0}]')
ON CONFLICT (id) DO NOTHING;

-- 일부러 뒤섞어 넣는다. 응답이 level 내림차순(2F,1F,B1)으로 나오는지 보려면
-- 삽입 순서와 기대 순서가 달라야 한다.
-- 1F만 자기 외곽선을 갖는다. 나머지 층은 건물 대표 외곽으로 폴백하는 경로를 확인하려고
-- 일부러 비워 둔다.
INSERT INTO floors (id, building_id, name, level, map_calibration_version, footprint_local_m) VALUES
  ('ths-1f', 'thehyundai-seoul', '1F',  1, 'v1',
   '[{"x":0.0,"y":0.0},{"x":120.0,"y":0.0},{"x":120.0,"y":90.0},{"x":0.0,"y":90.0}]'),
  ('ths-b1', 'thehyundai-seoul', 'B1', -1, 'unversioned', NULL),
  ('ths-2f', 'thehyundai-seoul', '2F',  2, 'unversioned', NULL)
ON CONFLICT (id) DO NOTHING;

-- 그래프 시드. 실측 앵커(lat/lng)를 3개 채운다 — 3개 미만이면 좌표 변환이 합성
-- 대응점으로 폴백해서, wgs84 응답이 서울시청 근처의 자리끼움 값이 된다.
INSERT INTO nodes (id, floor_id, type, name, x_m, y_m, lat, lng) VALUES
  ('ths-1f:n1', 'ths-1f', 'junction', NULL, 10.0, 10.0, 37.56650, 126.97800),
  ('ths-1f:n2', 'ths-1f', 'elevator', '엘리베이터 A', 50.0, 10.0, 37.56662, 126.97845),
  ('ths-1f:n3', 'ths-1f', 'junction', NULL, 50.0, 40.0, 37.56689, 126.97845),
  ('ths-2f:n4', 'ths-2f', 'junction', NULL, 10.0, 10.0, NULL, NULL),
  ('ths-2f:n5', 'ths-2f', 'elevator', '엘리베이터 A', 50.0, 10.0, NULL, NULL)
ON CONFLICT (id) DO NOTHING;

-- 층 내부 간선은 floor_id를 갖고, 수직 전이 간선은 NULL이다. 전이는 특정 층에 속하지
-- 않으므로 층별 조회에서 빠지고 건물 전체 그래프에서만 합류한다.
INSERT INTO edges (id, floor_id, from_node_id, to_node_id, length_m, cost_m, bidirectional, geometry, transfer_mode) VALUES
  ('ths-1f:e1', 'ths-1f', 'ths-1f:n1', 'ths-1f:n2', 40.0, 40.0, true,  '[{"x":10.0,"y":10.0},{"x":30.0,"y":12.0},{"x":50.0,"y":10.0}]', NULL),
  ('ths-1f:e2', 'ths-1f', 'ths-1f:n2', 'ths-1f:n3', 30.0, 30.0, true,  NULL, NULL),
  ('ths-2f:e3', 'ths-2f', 'ths-2f:n4', 'ths-2f:n5', 40.0, 40.0, true,  NULL, NULL),
  -- 전이 간선은 cost_m이 length_m와 다르다. 실제 이동은 4m지만 라우팅 비용은 튜닝값이다.
  ('ths:t-elev', NULL, 'ths-1f:n2', 'ths-2f:n5', 4.0, 20.0, true, NULL, 'elevator'),
  ('ths:t-esc',  NULL, 'ths-1f:n1', 'ths-2f:n4', 12.0, 15.0, false, NULL, 'escalator')
ON CONFLICT (id) DO NOTHING;

-- 매장 시드. 세 번째~다섯 번째가 "한 폴리곤을 나눠 쓰는" 자리다.
--   s3 아디다스 · s4 나이키 — centroid와 폴리곤이 같다(원본이 값을 복사해 넣은 자리)
--   s5 아디다스나이키       — 다른 매장 이름 2개를 이어 붙인 "묶음 매장". 그룹에서 빠져야
--                             s3·s4가 둘이 되어 칸이 나뉜다. 안 빠지면 셋이라 분할이 없다.
INSERT INTO stores (id, floor_id, name, category, subcategory, centroid_x_m, centroid_y_m,
                    entrance_x_m, entrance_y_m, entrance_node_id, polygon, search_facets) VALUES
  ('s1', 'ths-1f', '스타벅스', '카페', '카페·베이커리', 20.0, 20.0, 18.0, 20.0, 'ths-1f:n1',
   '[{"x":10.0,"y":10.0},{"x":30.0,"y":10.0},{"x":30.0,"y":30.0},{"x":10.0,"y":30.0}]', NULL),
  ('s2', 'ths-1f', '올리브영', '뷰티', NULL, 60.0, 20.0, NULL, NULL, NULL,
   '[{"x":50.0,"y":10.0},{"x":70.0,"y":10.0},{"x":70.0,"y":30.0},{"x":50.0,"y":30.0}]', NULL),
  ('s3', 'ths-1f', '아디다스', '패션', '스포츠', 80.0, 30.0, 95.0, 30.0, NULL,
   '[{"x":60.0,"y":20.0},{"x":100.0,"y":20.0},{"x":100.0,"y":40.0},{"x":60.0,"y":40.0}]', NULL),
  ('s4', 'ths-1f', '나이키', '패션', '스포츠', 80.0, 30.0, 65.0, 30.0, NULL,
   '[{"x":60.0,"y":20.0},{"x":100.0,"y":20.0},{"x":100.0,"y":40.0},{"x":60.0,"y":40.0}]', NULL),
  ('s5', 'ths-1f', '아디다스나이키', '패션', '스포츠', 80.0, 30.0, NULL, NULL, NULL,
   '[{"x":60.0,"y":20.0},{"x":100.0,"y":20.0},{"x":100.0,"y":40.0},{"x":60.0,"y":40.0}]', NULL),
  -- 카테고리가 없는 매장. 카테고리 집계에서 빠져야 한다(pill을 만들 수 없다).
  ('s6', 'ths-1f', '주차장 A', NULL, '주차', 5.0, 80.0, NULL, NULL, NULL, NULL, NULL),
  ('s7', 'ths-2f', '무인양품', '리빙', NULL, 30.0, 30.0, 28.0, 30.0, 'ths-2f:n4', NULL, NULL)
ON CONFLICT (id) DO NOTHING;

INSERT INTO pois (id, floor_id, type, name, x_m, y_m, linked_node_id) VALUES
  ('poi_ths-1f:n2', 'ths-1f', 'elevator', '엘리베이터 A', 50.0, 10.0, 'ths-1f:n2'),
  ('poi_ths-2f:n5', 'ths-2f', 'elevator', '엘리베이터 A', 50.0, 10.0, 'ths-2f:n5')
ON CONFLICT (id) DO NOTHING;

-- 오버레이가 붙는 매장. id는 실제 데이터의 것을 그대로 쓴다 — 오버레이 JSON의 조인 키가
-- 매장 id이고, 이름은 유일 키가 아니라 병합에 쓸 수 없다(동명 매장이 수백 건이다).
-- PO-HU40njvml1512는 섹션이 가장 많이 붙은 항목이라 상세 조립 경로 전체를 지난다.
INSERT INTO stores (id, floor_id, name, category, subcategory, centroid_x_m, centroid_y_m,
                    entrance_x_m, entrance_y_m, entrance_node_id, polygon, search_facets) VALUES
  ('PO-HU40njvml1512', 'ths-2f', '스타벅스 더현대서울점', '카페', '카페·베이커리', 25.0, 25.0,
   24.0, 25.0, 'ths-2f:n4',
   '[{"x":20.0,"y":20.0},{"x":30.0,"y":20.0},{"x":30.0,"y":30.0},{"x":20.0,"y":30.0}]', NULL),
  -- 사람이 설명을 쓰지 않고 위치 안내만 파생하는 시설. kind=facility로 내려가야 한다.
  ('f1', 'ths-2f', '화장실', '편의시설', '화장실', 70.0, 70.0, NULL, NULL, NULL, NULL, NULL)
ON CONFLICT (id) DO NOTHING;

-- 못 걷는 면(기둥). 타일의 non_walkable 레이어가 그린다 - 층 지도 JSON에는 싣지 않는다.
UPDATE floors SET non_walkable_polygons_local_m =
  '[{"id":"pillar-1","kind":"pillar","polygon_local_m":[{"x":40.0,"y":40.0},{"x":42.0,"y":40.0},{"x":42.0,"y":42.0},{"x":40.0,"y":42.0}]}]'
 WHERE id = 'ths-1f' AND non_walkable_polygons_local_m IS NULL;
