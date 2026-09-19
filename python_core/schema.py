from dataclasses import dataclass
from enum import IntEnum
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


class ColumnContainer:
    """Allows attribute-style access to columns (e.g., container.peer_id)
    while preserving iteration for SQL generation."""

    def __init__(self, columns: tuple[Column, ...]):
        self._columns: tuple[Column, ...] = columns

    def __getattr__(self, name: str) -> Column:
        for c in self._columns:
            if c.name == name:
                return c

        raise AttributeError(f"Column '{name}' not found")

    def __iter__(self):
        return iter(self._columns)

    def __len__(self) -> int:
        return len(self._columns)

    def __getitem__(self, index: int | slice) -> Column | tuple[Column, ...]:
        return self._columns[index]


class ForeignKeyContainer:
    """Allows attribute-style access to foreign keys (e.g., container.sender_id)
    while preserving iteration for SQL generation."""

    def __init__(self, foreign_keys: tuple[ForeignKey, ...]):
        self._foreign_keys: tuple[ForeignKey, ...] = foreign_keys

    def __getattr__(self, name: str) -> ForeignKey:
        for fk in self._foreign_keys:
            if fk.column == name:
                return fk

        raise AttributeError(f"ForeignKey '{name}' not found")

    def __iter__(self):
        return iter(self._foreign_keys)

    def __len__(self) -> int:
        return len(self._foreign_keys)

    def __getitem__(self, index: int | slice) -> ForeignKey | tuple[ForeignKey, ...]:
        return self._foreign_keys[index]


@dataclass(frozen=True)
class Table:
    name: str
    columns: ColumnContainer
    foreign_keys: ForeignKeyContainer

    def __init__(
        self,
        name: str,
        columns: tuple[Column, ...],
        foreign_keys: tuple[ForeignKey, ...] = (),
    ):
        object.__setattr__(self, "name", name)
        object.__setattr__(self, "columns", ColumnContainer(columns))
        object.__setattr__(self, "foreign_keys", ForeignKeyContainer(foreign_keys))

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
        Column("public_key", "BLOB"),
        Column("last_seen", "INTEGER"),
    ),
)


class MessageStatus(IntEnum):
    SENT = 0
    RECEIVED = 1


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
