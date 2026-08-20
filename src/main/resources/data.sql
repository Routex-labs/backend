-- 개발·테스트용 최소 시드. 실데이터는 파이썬 백엔드의 시드 스크립트가 만든다.
-- 여기서는 엔드포인트가 살아 있는지 확인할 만큼만 넣는다.
INSERT INTO buildings (id, name, area_m2, perimeter_m, footprint_local_m) VALUES
  ('thehyundai-seoul', '더현대 서울', 21000.0, 620.5,
   '[{"x":0.0,"y":0.0},{"x":100.0,"y":0.0},{"x":100.0,"y":80.0},{"x":0.0,"y":80.0}]')
ON CONFLICT (id) DO NOTHING;

-- 일부러 뒤섞어 넣는다. 응답이 level 내림차순(2F,1F,B1)으로 나오는지 보려면
-- 삽입 순서와 기대 순서가 달라야 한다.
INSERT INTO floors (id, building_id, name, level) VALUES
  ('ths-1f', 'thehyundai-seoul', '1F',  1),
  ('ths-b1', 'thehyundai-seoul', 'B1', -1),
  ('ths-2f', 'thehyundai-seoul', '2F',  2)
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
