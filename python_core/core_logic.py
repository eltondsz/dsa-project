import os
import sqlite3
import time
import uuid
from typing import cast

from cryptography.exceptions import InvalidTag

from crypto import decrypt, encrypt, generate_key
from database import get_db_connection

# Import our own modules
from database import init_database as db_init_database
from database import trigger_panic_wipe as db_trigger_panic_wipe

# Global variables for encryption key
_encryption_key: bytes | None = None


def _get_or_create_encryption_key() -> bytes:
    """Get or create a persistent encryption key for the application."""
    global _encryption_key
    if _encryption_key is None:
        _encryption_key = generate_key()
    return _encryption_key


def _ensure_peer_exists(
    peer_id: str, display_name: str | None = None, public_key: bytes | None = None
) -> bool:
    """Ensure a peer exists in the peers table. If not, insert it."""
    try:
        conn = get_db_connection()
        cursor = conn.cursor()

        _ = cursor.execute("SELECT peer_id FROM peers WHERE peer_id = ?", (peer_id,))
        existing = cast(tuple[str, ...] | None, cursor.fetchone())

        if not existing:
            display_name = display_name or f"Device_{peer_id[:8]}"
            public_key = public_key or os.urandom(32)
            last_seen = int(time.time())

            _ = cursor.execute(
                """
                INSERT INTO peers (peer_id, display_name, public_key, last_seen)
                VALUES (?, ?, ?, ?)
            """,
                (peer_id, display_name, public_key, last_seen),
            )
            conn.commit()

        return True

    except sqlite3.Error as e:
        print(f"Database error ensuring peer exists: {e}")
        return False


def init_database() -> bool:
    """Initialize the SQLite connection and build required tables."""
    return db_init_database()


def process_incoming_ble(raw_payload: bytes) -> dict[str, str | int]:
    """
    Receives a raw byte-string from Kotlin, decrypts it, updates the local database,
    and returns a dict for the UI to render.
    """
    try:
        if len(raw_payload) < 18:
            return {"error": "Payload too short"}

        hop_count = raw_payload[0]
        packet_type = raw_payload[1]
        message_uuid_bytes = raw_payload[2:18]
        message_uuid = str(uuid.UUID(bytes=message_uuid_bytes))
        ciphertext_payload = raw_payload[18:]

        key = _get_or_create_encryption_key()
        try:
            plaintext_bytes = decrypt(ciphertext_payload, key)
            plaintext = plaintext_bytes.decode("utf-8")
        except (InvalidTag, UnicodeDecodeError) as e:
            return {"error": f"Decryption failed: {e!s}"}

        conn = get_db_connection()
        cursor = conn.cursor()

        sender_id = "unknown_sender"
        recipient_id = "local_device"
        try:
            _ = cursor.execute(
                "SELECT sender_id, recipient_id FROM messages WHERE msg_id = ?",
                (message_uuid,),
            )
            result = cast(tuple[str, str] | None, cursor.fetchone())
            if result:
                sender_id = result[0]
                recipient_id = result[1]
        except sqlite3.Error:
            pass

        _ = _ensure_peer_exists(sender_id, f"Remote Device ({sender_id[:8]})")
        _ = _ensure_peer_exists(recipient_id, f"Local Device ({recipient_id[:8]})")

        encrypted_payload_hex = ciphertext_payload.hex()

        _ = cursor.execute(
            """
            INSERT OR REPLACE INTO messages (msg_id, sender_id, recipient_id, payload, timestamp, status)
            VALUES (?, ?, ?, ?, ?, ?)
        """,
            (
                message_uuid,
                sender_id,
                recipient_id,
                encrypted_payload_hex,
                int(time.time()),
                2,
            ),
        )
        conn.commit()

        return {
            "msg_id": message_uuid,
            "sender_id": sender_id,
            "recipient_id": recipient_id,
            "message": plaintext,
            "timestamp": int(time.time()),
            "status": 2,
            "hop_count": hop_count,
            "packet_type": packet_type,
        }

    except (ValueError, sqlite3.Error) as e:
        return {"error": f"Processing failed: {e!s}"}


def prepare_outgoing_message(recipient_uuid: str, message: str) -> bytes:
    """
    Takes plaintext from the Kotlin UI, encrypts it, attaches the mesh routing headers,
    and returns the raw bytes for Kotlin to broadcast.
    """
    try:
        message_uuid = str(uuid.uuid4())
        message_uuid_bytes = uuid.UUID(message_uuid).bytes

        key = _get_or_create_encryption_key()
        plaintext_bytes = message.encode("utf-8")
        encrypted_payload = encrypt(plaintext_bytes, key)

        hop_count = 1
        packet_type = 0x01

        packet = bytearray()
        packet.append(hop_count)
        packet.append(packet_type)
        packet.extend(message_uuid_bytes)
        packet.extend(encrypted_payload)

        conn = get_db_connection()
        cursor = conn.cursor()

        sender_id = "local_device"

        _ = _ensure_peer_exists(sender_id, f"Local Device ({sender_id[:8]})")
        _ = _ensure_peer_exists(recipient_uuid, f"Remote Device ({recipient_uuid[:8]})")

        encrypted_payload_hex = encrypted_payload.hex()

        _ = cursor.execute(
            """
            INSERT OR REPLACE INTO messages (msg_id, sender_id, recipient_id, payload, timestamp, status)
            VALUES (?, ?, ?, ?, ?, ?)
        """,
            (
                message_uuid,
                sender_id,
                recipient_uuid,
                encrypted_payload_hex,
                int(time.time()),
                0,
            ),
        )
        conn.commit()

        return bytes(packet)

    except (ValueError, UnicodeEncodeError, sqlite3.Error) as e:
        print(f"Error preparing outgoing message: {e}")
        return b""


def trigger_panic_wipe() -> bool:
    """Instantly drops all SQLite tables and clears active encryption keys."""
    global _encryption_key
    result = db_trigger_panic_wipe()
    _encryption_key = None
    return result
