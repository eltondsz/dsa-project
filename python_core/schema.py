from dataclasses import dataclass
from textwrap import indent


@dataclass(frozen=True)
class Column:
    name: str
    sql_type: str
    primary_key: bool = False


@dataclass(frozen=True)
class ForeignKey:
    column: str
    ref_table: str
    ref_column: str


@dataclass(frozen=True)
class Table:
    name: str
    columns: tuple[Column, ...]
    foreign_keys: tuple[ForeignKey, ...] = ()

    def create_sql(self) -> str:
        col_lines = [
            f"{c.name} {c.sql_type}" + (" PRIMARY KEY" if c.primary_key else "")
            for c in self.columns
        ]
        fk_lines = [
            f"FOREIGN KEY ({fk.column}) REFERENCES {fk.ref_table} ({fk.ref_column})"
            for fk in self.foreign_keys
        ]
        body = indent(",\n".join(col_lines + fk_lines), "    ")
        return f"CREATE TABLE IF NOT EXISTS {self.name} (\n{body}\n)"


PEERS = Table(
    name="peers",
    columns=(
        Column("peer_id", "TEXT", primary_key=True),
        Column("display_name", "TEXT"),
        Column("public_key", "BLOB"),
        Column("last_seen", "INTEGER"),
    ),
)

MESSAGES = Table(
    name="messages",
    columns=(
        Column("msg_id", "TEXT", primary_key=True),
        Column("sender_id", "TEXT"),
        Column("recipient_id", "TEXT"),
        Column("payload", "TEXT"),
        Column("timestamp", "INTEGER"),
        Column("status", "INTEGER"),
    ),
    foreign_keys=(
        ForeignKey("sender_id", PEERS.name, "peer_id"),
        ForeignKey("recipient_id", PEERS.name, "peer_id"),
    ),
)

ALL_TABLES = [PEERS, MESSAGES]


def topological_drop_order(tables: list[Table]) -> list[Table]:
    """Order tables so a table is dropped before anything it's referenced by."""
    visited: set[str] = set()
    order: list[Table] = []

    def visit(table: Table) -> None:
        if table.name in visited:
            return

        visited.add(table.name)

        for other in tables:
            if any(fk.ref_table == table.name for fk in other.foreign_keys):
                visit(other)

        order.append(table)

    for t in tables:
        visit(t)

    return order
