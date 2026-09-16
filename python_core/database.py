import os
import sqlite3

from schema import ALL_TABLES, topological_drop_order

# Global connection object
_connection: sqlite3.Connection | None = None


def get_db_connection() -> sqlite3.Connection:
    """Get or create a database connection with absolute path for Android compatibility."""
    global _connection

    if _connection is None:
        # Construct absolute path using the directory of this file
        base_dir = os.path.abspath(os.path.dirname(__file__))

        DB_FILE_NAME = "database.db"
        db_path = os.path.join(base_dir, DB_FILE_NAME)

        _connection = sqlite3.connect(db_path)

        # Enable foreign key constraints
        _ = _connection.execute("PRAGMA foreign_keys = ON")

    return _connection


def init_database() -> bool:
    """
    Initialize the SQLite connection and build required tables.
    Returns True if successful, False otherwise.
    """
    try:
        connection = get_db_connection()
        cursor = connection.cursor()

        for table in ALL_TABLES:
            _ = cursor.execute(table.create_sql())

        connection.commit()
        return True

    except sqlite3.Error as e:
        print(f"Database initialization error: {e}")
        return False


def trigger_panic_wipe() -> bool:
    """
    Instantly drops all SQLite tables and clears active encryption keys.
    Returns True if successful, False otherwise.
    """
    try:
        connection = get_db_connection()
        cursor = connection.cursor()

        # Drop tables in reverse order of dependencies (messages first due to foreign keys)
        for table in topological_drop_order(ALL_TABLES):
            _ = cursor.execute(f"DROP TABLE IF EXISTS {table.name}")

        connection.commit()
        return True
    except sqlite3.Error as e:
        print(f"Panic wipe error: {e}")
        return False


# Optional: Close connection when needed (for cleanup)
def close_db_connection():
    global _connection

    if _connection is not None:
        _connection.close()
        _connection = None
