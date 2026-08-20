"""파이썬 백엔드의 SQLite 실데이터를 PostgreSQL로 옮긴다.

스키마는 만들지 않는다. 스프링을 한 번 띄워 Hibernate가 테이블을 만든 뒤에 돌린다
(`ddl-auto: update`). 이 스크립트는 데이터만 채운다.

    # 1. 원본을 새로 시드한다(Navigation/backend에서)
    python -m scripts.seed.reset_and_seed

    # 2. SQL로 뽑는다
    python tools/load_real_data.py ../Navigation/backend/data/navigation.db seed_real.sql

    # 3. 밀어 넣는다(psql이 없으면 도커로)
    docker run --rm -i postgres:18 psql "<접속 URL>" -v ON_ERROR_STOP=1 -f - < seed_real.sql

INSERT가 아니라 COPY로 뽑는 이유: 값 인용이 백슬래시 4종만 다루면 끝나서 한글·따옴표·
JSON이 섞여도 깨질 구석이 없고, 6천 행이 한 문장으로 들어간다.

출력 파일을 직접 열고 인코딩을 UTF-8로 못박는다. `> seed_real.sql` 리다이렉트에 맡기면
Windows에서 CP949로 써져 "더현대 서울"이 mojibake로 적재된다(실제로 한 번 그랬다).
"""

from __future__ import annotations

import sqlite3
import sys

# 외래키 순서. nodes가 stores·pois·edges보다 먼저다.
TABLES = ("buildings", "floors", "nodes", "edges", "stores", "pois")


# COPY 텍스트 형식의 한 필드. 이스케이프가 필요한 문자는 이 넷뿐이다(NULL은 \N).
def field(value: object) -> str:
    if value is None:
        return r"\N"
    if isinstance(value, bytes):
        raise TypeError("BLOB 컬럼은 이 경로로 옮기지 않는다")
    text = str(value)
    return (
        text.replace("\\", "\\\\")
        .replace("\n", r"\n")
        .replace("\r", r"\r")
        .replace("\t", r"\t")
    )


def dump(db_path: str, out) -> None:
    conn = sqlite3.connect(db_path)
    conn.text_factory = str

    out.write("BEGIN;\n")
    out.write(f"TRUNCATE {', '.join(reversed(TABLES))} CASCADE;\n\n")

    for table in TABLES:
        columns = [row[1] for row in conn.execute(f"PRAGMA table_info({table})")]
        if not columns:
            raise SystemExit(f"{table} 테이블이 원본에 없다 — 시드를 먼저 돌려라")

        out.write(f"COPY {table} ({', '.join(columns)}) FROM stdin;\n")
        count = 0
        for row in conn.execute(f"SELECT {', '.join(columns)} FROM {table}"):
            out.write("\t".join(field(v) for v in row) + "\n")
            count += 1
        out.write("\\.\n\n")
        print(f"{table}: {count}행 ({len(columns)}컬럼)", file=sys.stderr)

    out.write("COMMIT;\n")

    # 원본 SQLite가 낡으면 이 컬럼이 없다. 없어도 적재는 되지만 MVT의 non_walkable
    # 레이어가 통째로 빈다 — 도면 위 못 걷는 면이 안 그려진다.
    floor_columns = {row[1] for row in conn.execute("PRAGMA table_info(floors)")}
    if "non_walkable_polygons_local_m" not in floor_columns:
        print(
            "경고: floors에 non_walkable_polygons_local_m이 없다. 원본이 낡았다 — "
            "Navigation/backend에서 `python -m scripts.seed.reset_and_seed`를 먼저 돌려라.",
            file=sys.stderr,
        )
    conn.close()


def selftest() -> None:
    assert field(None) == r"\N"
    assert field("더현대 서울") == "더현대 서울"
    assert field('{"x": 1.5}') == '{"x": 1.5}'
    assert field("a\tb\nc\\d") == r"a\tb\nc\\d"
    assert field(1) == "1"
    assert field(0.0) == "0.0"
    print("ok", file=sys.stderr)


if __name__ == "__main__":
    # 콘솔 기본 코드페이지(CP949)로 찍으면 진행 메시지가 깨진다.
    sys.stderr.reconfigure(encoding="utf-8")

    if len(sys.argv) == 2 and sys.argv[1] == "--selftest":
        selftest()
    elif len(sys.argv) == 3:
        with open(sys.argv[2], "w", encoding="utf-8", newline="\n") as out:
            dump(sys.argv[1], out)
    else:
        raise SystemExit(__doc__)
