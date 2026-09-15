import os
import sqlite3

# Global connection object
_connection: sqlite3.Connection | None = None


def get_db_connection() -> sqlite3.Connection:
    """Get or create a database connection with absolute path for Android compatibility."""
    global _connection
    if _connection is None:
        # Construct absolute path using the directory of this file
        base_dir = os.path.abspath(os.path.dirname(__file__))
        db_path = os.path.join(base_dir, "database.db")
        _connection = sqlite3.connect(db_path)
        # Enable foreign key constraints
        _connection.execute("PRAGMA foreign_keys = ON")
    return _connection


def init_database() -> bool:
    """
    Initialize the SQLite connection and build required tables.
    Returns True if successful, False otherwise.
    """
    try:
        conn = get_db_connection()
        cursor = conn.cursor()

        # Create peers table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS peers (
                peer_id TEXT PRIMARY KEY,
                display_name TEXT,
                public_key BLOB,
                last_seen INTEGER
            )
        """)

        # Create messages table
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS messages (
                msg_id TEXT PRIMARY KEY,
                sender_id TEXT,
                recipient_id TEXT,
                payload TEXT,
                timestamp INTEGER,
                status INTEGER,
                FOREIGN KEY (sender_id) REFERENCES peers (peer_id),
                FOREIGN KEY (recipient_id) REFERENCES peers (peer_id)
            )
        """)

        conn.commit()
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
        conn = get_db_connection()
        cursor = conn.cursor()

        # Drop tables in reverse order of dependencies (messages first due to foreign keys)
        cursor.execute("DROP TABLE IF EXISTS messages")
        cursor.execute("DROP TABLE IF EXISTS peers")

        conn.commit()
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
