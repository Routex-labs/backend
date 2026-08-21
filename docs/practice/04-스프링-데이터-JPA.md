# 04. 스프링 데이터 JPA

**대응 강의** — 실전! 스프링 데이터 JPA `3. 공통 인터페이스 기능`, `4. 쿼리 메소드 기능`,
`5. 확장 기능`, `7. 나머지 기능들`

리포지토리는 8개다.

| 인터페이스 | 상속 | 메서드 수 |
|---|---|---|
| `BuildingRepository` | `JpaRepository<Building, String>` | 2 (둘 다 오버라이드) |
| `FloorRepository` | `JpaRepository<Floor, String>` | 2 |
| `StoreRepository` | `JpaRepository<Store, String>` | 3 |
| `PoiRepository` | `JpaRepository<Poi, String>` | 1 |
| `NodeRepository` | `JpaRepository<Node, String>` | 2 |
| `EdgeRepository` | `JpaRepository<Edge, String>` | 3 |
| `CategoryQueryRepository` | **`Repository<Store, String>`** | 2 |
| `QuerySearchRepository` | **`Repository<Store, String>`** | 3 |

마지막 두 개가 `JpaRepository`가 아닌 것이 이 저장소에서 제일 의식적으로 고른 지점이다.

---

## 1. 쿼리 메소드 — 이름으로 만든 것

```java
List<Store> findByFloorId(String floorId);                              // StoreRepository
List<Poi>   findByFloorId(String floorId);                              // PoiRepository
List<Node>  findByFloorId(String floorId);                              // NodeRepository
List<Edge>  findByFloorId(String floorId);                              // EdgeRepository
Optional<Floor> findByBuildingIdAndName(String buildingId, String name); // FloorRepository
List<Floor>     findByBuildingId(String buildingId);                     // FloorRepository
```

여섯 개가 전부다. 눈여겨볼 것:

**`findByFloorId`는 조인을 만들지 않는다.** `Store.floor`는 `@ManyToOne`이고 FK
`floor_id`가 `stores` 자기 행에 있으므로, `floor.id` 경로 탐색은 **자기 컬럼 비교**로
끝난다. `floors` 테이블을 건드리지 않는다. p6spy 로그로 확인할 수 있다.

**`findByBuildingIdAndName`은 조인을 만든다.** `Floor.building.id`도 마찬가지로
`floors.building_id` 컬럼이라 사실 조인이 필요 없고, Hibernate도 그렇게 최적화한다.

**메서드 이름이 길어지기 전에 멈췄다.** 강의가 경고하는
`findByBuildingIdAndCategoryAndSubcategoryOrderBy...` 같은 것이 하나도 없다. 조건이 둘을
넘어가는 순간 전부 `@Query`로 갔다.

### 반환 타입 규약

- 단건이 없을 수 있으면 **`Optional`** — `findByBuildingIdAndName`, `findById`.
- 목록은 **`List`**, 없으면 빈 리스트(null이 아니다).
- 서비스가 그 `Optional`을 그대로 위로 올리고, **404 변환은 컨트롤러가 한다.** 서비스
  주석마다 "없는 건물·층이면 빈 Optional. HTTP 상태 변환은 컨트롤러가 한다"가 적혀 있다.

### `existsById` — 엔티티를 안 읽고 존재만 본다

```java
// QuerySearchService
if (!buildingRepository.existsById(buildingId)) {
    return Optional.empty();
}
```

`findById`로 읽고 버리면 `@EntityGraph` 때문에 층까지 딸려 온다. `existsById`는
`select count(*)` 한 방이다.

---

## 2. `@Query` — JPQL을 직접 적은 것

여섯 자리다. 전부 **조건이 연관을 두 단계 이상 타거나**, **투영이 필요하거나**,
**메서드 이름으로 표현이 안 되는** 경우다.

```java
// StoreRepository — 매장 → 층 → 건물, 두 단계
@Query("select s from Store s where s.floor.building.id = :buildingId and s.name like %:query%")

// NodeRepository / EdgeRepository — 같은 이유
@Query("select n from Node n where n.floor.building.id = :buildingId")
@Query("select e from Edge e where e.floor.building.id = :buildingId")

// EdgeRepository — null 조건 + IN 절 두 개
@Query("select e from Edge e where e.floor is null and e.fromNode.id in :nodeIds and e.toNode.id in :nodeIds")

// QuerySearchRepository — 건물 안 전 매장명
@Query("select s.name from Store s")
```

### `@Param`을 항상 적는다

```java
List<Node> findByBuildingId(@Param("buildingId") String buildingId);
```

`-parameters` 컴파일 옵션이 있으면 생략해도 되지만, 빌드 설정 하나에 런타임 동작이
걸리는 걸 피하려고 전부 명시했다. `build.gradle`의 `compilerArgs`에는 `-Xlint:deprecation`
만 있고 `-parameters`가 없다 — Spring Boot 그래들 플러그인이 자동으로 넣어 주긴 하지만,
그 사실을 알아야만 동작하는 코드는 두지 않는 편이 낫다.

### 텍스트 블록으로 적은 JPQL

```java
@Query("""
        select f.name, s.category, s.subcategory, count(s.id)
          from Store s join s.floor f
         where f.building.id = :buildingId and s.category is not null
         group by f.name, s.category, s.subcategory
         order by f.name, s.category, s.subcategory
        """)
```

자바 15의 텍스트 블록이다. 강의 시점(자바 8~11)에는 `"select ..." + " from ..."` 문자열
연결이었다. **줄바꿈 앞뒤 공백이 그대로 들어간다는 점만 주의**하면 SQL처럼 정렬해 읽을 수
있다.

### `IN` 절에 컬렉션을 넘길 때

```java
List<Edge> findTransferEdges(@Param("nodeIds") Collection<String> nodeIds);
```

`GraphService`가 **빈 컬렉션을 넘기지 않도록 먼저 막는다.**

```java
if (!nodeIds.isEmpty()) {
    edgeRepository.findTransferEdges(nodeIds)...
}
```

`in ()`은 DB에 따라 문법 오류이거나 항상 거짓이라, 방어해 두는 편이 예측 가능하다.

또 이 쿼리는 **양 끝 노드를 모두 검사**한다. `fromNode`만 보면 `toNode`가 다른 건물인
간선이 딸려 와, 그래프에 없는 노드를 가리키는 dangling 간선이 응답에 실린다.

---

## 3. 조회 전용 리포지토리 — `Repository` 마커 인터페이스

```java
public interface CategoryQueryRepository extends Repository<Store, String> { ... }
public interface QuerySearchRepository   extends Repository<Store, String> { ... }
```

`JpaRepository` 대신 **`org.springframework.data.repository.Repository`** (마커 인터페이스)를
상속했다. 결과:

- `save`, `delete`, `deleteAll`, `flush` 같은 **쓰기 메서드가 아예 존재하지 않는다.**
- `findAll()` 같이 이 화면이 쓰지 않는 메서드도 노출되지 않는다.
- **여기 적힌 두세 개가 이 인터페이스의 전부**라, 무엇을 위한 리포지토리인지 파일 하나로
  드러난다.

강의 5장의 "사용자 정의 리포지토리"(`XxxRepositoryCustom` + `XxxRepositoryImpl`)와 목적이
겹치지만 훨씬 단순하다. 그쪽은 **Querydsl처럼 구현이 필요한 경우**의 답이고, JPQL만으로
되는 조회 전용 인터페이스는 이렇게 마커만 상속하면 끝난다.

**같은 엔티티(`Store`)에 리포지토리가 셋인 것도 의도적이다.**

| | 쓰는 곳 | 성격 |
|---|---|---|
| `StoreRepository` | `place`, `floormap`, `category` | 엔티티 조회 |
| `CategoryQueryRepository` | `category` | pill·색인 전용 투영 |
| `QuerySearchRepository` | `search` | 질의 매칭 전용 투영 |

"엔티티당 리포지토리 하나"가 아니라 **화면이 필요로 하는 조회 모양당 하나**다.
[001 결정문서](../decisions/001-화면단위-패키지.md)의 패키지 규칙과 같은 기준이다.

---

## 4. `@EntityGraph`로 기본 메서드 덮어쓰기

```java
@Override
@EntityGraph(attributePaths = "floors")
List<Building> findAll();
```

강의 5장에 나오는 기법이다. `@Override`가 붙어 있어 **시그니처를 틀리면 컴파일이
실패한다** — 이름을 살짝 다르게 적어 새 메서드가 만들어지는(그리고 기본 `findAll`은
그대로 N+1인) 사고를 막아 준다.

자세한 내용은 [03](03-조회-최적화와-N+1.md).

---

## 5. 안 쓴 기능과 그 이유

강의에서 배웠지만 이 저장소에 안 들어온 것들이다. **"아직 필요 없다"와 "이 프로젝트에는
영영 안 맞는다"를 갈라 적는다.**

### 아직 필요 없는 것

| 기능 | 언제 필요해지나 |
|---|---|
| `Pageable` / `Page` / `Slice` | 목록이 페이징될 규모가 아니다. 매장 1,640건은 클라이언트가 색인을 통째로 캐시하는 게 더 낫다. 건물이 여러 개가 되면 `findAll()`부터 본다 |
| `@Modifying @Query` | 쓰기가 생기면 |
| Auditing (`@CreatedDate`, `@LastModifiedDate`, `@EnableJpaAuditing`) | 적재 시각은 지금 파이썬 도구가 관리한다. 서버가 쓰기를 시작하면 필요해진다 |
| `@Lock`, `@Version` | 동시 수정이 없다 |
| 사용자 정의 리포지토리(`...Impl`) | Querydsl이나 `EntityManager` 직접 조작이 필요해지면. 지금은 마커 인터페이스로 충분하다 |

### 이 프로젝트에는 안 맞는 것

| 기능 | 이유 |
|---|---|
| Querydsl | **동적 조건이 없다.** 질의 파라미터가 전부 필수이거나 고정이라 문자열 JPQL로 끝난다. 빌드에 애노테이션 프로세서와 Q타입 생성을 추가할 값이 없다 |
| `Specification` (JPA Criteria) | 위와 같고, 읽기도 훨씬 나쁘다 |
| 인터페이스 기반 Projection (`interface StoreView { String getName(); }`) | record + 생성자 표현식(`select new ...`)을 쓴다. record가 응답 DTO와 같은 문법이라 코드에 타입 종류가 하나 줄어든다 |
| `@DynamicInsert` / `@DynamicUpdate` | 쓰기가 없다 |
| 네이티브 쿼리 | 아직 JPQL로 다 된다. jsonb 연산자가 필요해지는 순간 내려간다 |

### 새로 배운 함정: `Repository`와 `JpaRepository`를 섞으면 트랜잭션이 다르다

`JpaRepository`의 기본 구현(`SimpleJpaRepository`)은 클래스에 `@Transactional(readOnly = true)`
가 붙어 있어서 서비스가 트랜잭션을 안 열어도 조회가 된다. 마커 `Repository`를 상속한
`@Query` 메서드도 스프링 데이터가 같은 프록시를 만들어 주므로 동작은 같다.

**그래도 서비스에 `@Transactional(readOnly = true)`를 붙이는 이유**는 리포지토리를 여러 번
부를 때 **한 트랜잭션 = 한 영속성 컨텍스트**로 묶기 위해서다. 이게 1차 캐시가 N+1을
막아 주는 전제다 → [02](02-영속성-컨텍스트와-지연로딩.md).
