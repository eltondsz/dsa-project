import os
import sys
# Add the parent directory of this file (which is python_core) to the path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

import sqlite3
from database import init_database, trigger_panic_wipe, close_db_connection

def get_db_path():
    """Get the database path as used in database.py."""
    # The database.py file is in python_core, which is now in the path
    base_dir = os.path.abspath(os.path.dirname(__file__))
    base_dir = os.path.join(base_dir, '..')
    return os.path.join(base_dir, 'database.db')

def test_init_database_creates_tables():
    """Test that init_database creates the peers and messages tables."""
    # Clean up any existing database and connection
    db_path = get_db_path()
    if os.path.exists(db_path):
        os.remove(db_path)
    close_db_connection()

    # Initialize the database
    result = init_database()
    assert result is True, "init_database should return True on success"
    assert os.path.exists(db_path), "Database file should be created"

    # Check that tables exist
    conn = sqlite3.connect(db_path)
    cursor = conn.cursor()

    # Check peers table
    cursor.execute("""
        SELECT name FROM sqlite_master
        WHERE type='table' AND name='peers'
    """)
    assert cursor.fetchone() is not None, "peers table should exist"

    # Check messages table
    cursor.execute("""
        SELECT name FROM sqlite_master
        WHERE type='table' AND name='messages'
    """)
    assert cursor.fetchone() is not None, "messages table should exist"

    conn.close()

def test_trigger_panic_wipe_drops_tables():
    """Test that trigger_panic_wipe drops the tables."""
    # Clean up any existing database and connection
    db_path = get_db_path()
    if os.path.exists(db_path):
        os.remove(db_path)
    close_db_connection()

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
    cursor.execute("""
        SELECT name FROM sqlite_master
        WHERE type='table' AND name='peers'
    """)
    assert cursor.fetchone() is None, "peers table should be dropped"

    # Check messages table is dropped
    cursor.execute("""
        SELECT name FROM sqlite_master
        WHERE type='table' AND name='messages'
    """)
    assert cursor.fetchone() is None, "messages table should be dropped"

    conn.close()

    # Clean up
    close_db_connection()
    if os.path.exists(db_path):
        os.remove(db_path)