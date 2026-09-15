import sqlite3
from pathlib import Path

from database import close_db_connection, init_database, trigger_panic_wipe


def get_db_path() -> Path:
    """Get the database path as used in database.py."""
    return Path(__file__).resolve().parent.parent / "database.db"


def test_init_database_creates_tables():
    """Test that init_database creates the peers and messages tables."""
    # Clean up any existing database and connection
    db_path = get_db_path()
    close_db_connection()
    if db_path.exists():
        db_path.unlink()

    # Initialize the database
    result = init_database()
    assert result is True, "init_database should return True on success"
    assert db_path.exists(), "Database file should be created"

    # Check that tables exist
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()

    # Check peers table
    _ = cursor.execute("""
        SELECT name FROM sqlite_master
        WHERE type='table' AND name='peers'
    """)
    assert cursor.fetchone() is not None, "peers table should exist"

    # Check messages table
    _ = cursor.execute("""
        SELECT name FROM sqlite_master
        WHERE type='table' AND name='messages'
    """)
    assert cursor.fetchone() is not None, "messages table should exist"

    conn.close()


def test_trigger_panic_wipe_drops_tables():
    """Test that trigger_panic_wipe drops the tables."""
    # Clean up any existing database and connection
    db_path = get_db_path()
    close_db_connection()
    if db_path.exists():
        db_path.unlink()

    # Initialize the database first
    result = init_database()
    assert result is True, "Failed to initialize database for test setup"

    # Trigger panic wipe
    result = trigger_panic_wipe()
    assert result is True, "trigger_panic_wipe should return True on success"

    # Check that tables are dropped
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()

    # Check peers table is dropped
    _ = cursor.execute("""
        SELECT name FROM sqlite_master
        WHERE type='table' AND name='peers'
    """)
    assert cursor.fetchone() is None, "peers table should be dropped"

    # Check messages table is dropped
    _ = cursor.execute("""
        SELECT name FROM sqlite_master
        WHERE type='table' AND name='messages'
    """)
    assert cursor.fetchone() is None, "messages table should be dropped"

    conn.close()

    # Clean up
    close_db_connection()
    if db_path.exists():
        db_path.unlink()
