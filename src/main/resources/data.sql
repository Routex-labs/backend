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
