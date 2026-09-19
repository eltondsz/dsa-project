import os
import sqlite3

from schema import ALL_TABLES, topological_drop_order

SQLITE3_ROW_TYPE = sqlite3.Row

# Global connection object
_connection: sqlite3.Connection | None = None


def get_db_connection() -> sqlite3.Connection | None:
    """Get or create a database connection with absolute path for Android compatibility."""
    global _connection

    if _connection is None:
        try:
            base_dir = os.path.abspath(os.path.dirname(__file__))
            db_path = os.path.join(base_dir, "database.db")
        except OSError as e:
            print(f"Error getting db connection: {e}")
            return None

        try:
            connection = sqlite3.connect(db_path)

            # Enabling this stops entering an entry with a foreign key into a table
            # without it actually being in the table the foreign key is the primary key of
            _ = connection.execute("PRAGMA foreign_keys = ON")
        except sqlite3.Error as e:
            print(f"Error getting db connection: {e}")
            return None

        # Returns rows in a dictionary format, so columns are accessible by names: row[column_name]
        connection.row_factory = SQLITE3_ROW_TYPE
        _connection = connection

    return _connection


def init_database() -> bool:
    """
    Initialize the SQLite connection and build required tables.
    Returns True if successful, False otherwise.
    """
    connection = get_db_connection()

    if connection is None:
        return False

    try:
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
    connection = get_db_connection()

    if connection is None:
        return False

    try:
        cursor = connection.cursor()

        # Drop tables in reverse order of dependencies
        for table in topological_drop_order(ALL_TABLES):
            _ = cursor.execute(f"DROP TABLE IF EXISTS {table.name}")

        connection.commit()
        return True
    except sqlite3.Error as e:
        print(f"Panic wipe error: {e}")
        return False


def close_db_connection() -> bool:
    """
    Close the database connection if one is open.
    Returns True if successful (or if there was nothing to close), False otherwise.
    """
    global _connection

    if _connection is not None:
        try:
            _connection.close()
        except sqlite3.Error as e:
            print(f"Error closing db connection: {e}")
            return False
        finally:
            _connection = None

    return True
