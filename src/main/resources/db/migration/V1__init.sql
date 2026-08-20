-- 기준선. 지금까지 `ddl-auto: update`가 만들어 온 스키마를 그대로 옮겼다.
--
-- 원본은 운영 DB에서 뜬 `pg_dump --schema-only`다. 손으로 적은 것이 아니라 실제로
-- 돌고 있는 모양이라, 이 파일로 만든 새 DB는 기존 DB와 같은 스키마가 된다.
--
-- 외래키 이름만 바꿨다. Hibernate가 붙인 `fk4bh9yh92glu96hunm6w31yt7b` 같은 난수
-- 이름은 이 파일이 사람이 읽는 단일 출처가 된 이상 의미가 없다. 이미 만들어진 DB의
-- 제약 이름은 그대로 남지만 `ddl-auto: validate`는 제약 이름을 보지 않는다.
--
-- 컬럼 이름의 단일 출처는 파이썬 백엔드의 ORM 모델이다. Hibernate 네이밍 전략이
-- 끝에 붙은 대문자 앞에 언더바를 넣지 않아 `sourceX`가 `sourcex`가 되는 함정이 있어,
-- SchemaColumnNamesTest가 생성 결과를 직접 검사한다.

create table buildings (
    id                 varchar(255) not null,
    name               varchar(255) not null,
    area_m2            double precision,
    perimeter_m        double precision,
    footprint_local_m  jsonb,
    constraint buildings_pkey primary key (id)
);

create table floors (
    id                            varchar(255) not null,
    building_id                   varchar(255) not null,
    name                          varchar(255) not null,
    level                         integer      not null,
    map_calibration_version       varchar(255) not null,
    footprint_local_m             jsonb,
    non_walkable_polygons_local_m jsonb,
    constraint floors_pkey primary key (id),
    -- 건물 안에서 층 라벨은 유일하다. 질의가 층 라벨("B2")을 내부 id 대신 받을 수 있는
    -- 근거가 이 제약이다.
    constraint uq_floors_building_name unique (building_id, name),
    constraint fk_floors_building foreign key (building_id) references buildings (id)
);

create table nodes (
    id       varchar(255) not null,
    floor_id varchar(255) not null,
    type     varchar(255) not null,
    name     varchar(255),
    x_m      double precision not null,
    y_m      double precision not null,
    lat      double precision,
    lng      double precision,
    source_x double precision,
    source_y double precision,
    constraint nodes_pkey primary key (id),
    constraint fk_nodes_floor foreign key (floor_id) references floors (id)
);

create index idx_nodes_floor on nodes (floor_id);
create index idx_nodes_type on nodes (type);

create table edges (
    id            varchar(255) not null,
    floor_id      varchar(255),
    from_node_id  varchar(255) not null,
    to_node_id    varchar(255) not null,
    length_m      double precision not null,
    cost_m        double precision not null,
    bidirectional boolean not null,
    geometry      jsonb,
    transfer_mode varchar(255),
    constraint edges_pkey primary key (id),
    constraint fk_edges_floor foreign key (floor_id) references floors (id),
    constraint fk_edges_from_node foreign key (from_node_id) references nodes (id),
    constraint fk_edges_to_node foreign key (to_node_id) references nodes (id)
);

create index idx_edges_floor on edges (floor_id);
create index idx_edges_from on edges (from_node_id);
create index idx_edges_to on edges (to_node_id);

create table stores (
    id               varchar(255) not null,
    floor_id         varchar(255) not null,
    name             varchar(255) not null,
    category         varchar(255),
    subcategory      varchar(255),
    centroid_x_m     double precision not null,
    centroid_y_m     double precision not null,
    entrance_x_m     double precision,
    entrance_y_m     double precision,
    -- 입구와 이어진 그래프 노드. 엔티티가 연관이 아니라 문자열로 들고 있어 외래키가
    -- 없다(파이썬 스키마에는 있다). 적재 순서는 tools/load_real_data.py가 지킨다.
    entrance_node_id varchar(255),
    polygon          jsonb,
    search_facets    jsonb,
    constraint stores_pkey primary key (id),
    constraint fk_stores_floor foreign key (floor_id) references floors (id)
);

create index idx_stores_floor on stores (floor_id);
create index idx_stores_name on stores (name);

create table pois (
    id             varchar(255) not null,
    floor_id       varchar(255) not null,
    type           varchar(255) not null,
    name           varchar(255),
    x_m            double precision not null,
    y_m            double precision not null,
    -- stores.entrance_node_id와 같은 이유로 외래키가 없다.
    linked_node_id varchar(255),
    constraint pois_pkey primary key (id),
    constraint fk_pois_floor foreign key (floor_id) references floors (id)
);

create index idx_pois_floor on pois (floor_id);
create index idx_pois_type on pois (type);
