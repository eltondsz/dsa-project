import os
import sqlite3
import time
import uuid
from typing import cast

from crypto import KEY_LENGTH, decrypt, derive_shared_secret, encrypt, generate_keypair, get_mesh_network_key
from database import SQLITE3_ROW_TYPE, get_db_connection
from database import init_database as db_init_database
from database import trigger_panic_wipe as db_trigger_panic_wipe
from errors import ErrorCode
from packets import (
    HANDSHAKE_PACKET_FORMAT,
    MESSAGE_PACKET_FORMAT,
    PacketType,
)
from schema import MESSAGES, PEERS, MessageStatus

BASE_DIR = os.path.abspath(os.path.dirname(__file__))
KEYSTORE_PUB_PATH = os.path.join(BASE_DIR, "keystore_pub.bin")
KEYSTORE_PRIV_PATH = os.path.join(BASE_DIR, "keystore_priv.bin")
DEVICE_ID_PATH = os.path.join(BASE_DIR, "device_id.txt")


OLD_DUMMY_DEVICE_ID = "9fbf76d1-18fe-4aac-a84a-a56bf6477a1b"


def get_or_create_keypair() -> tuple[bytes, bytes] | None:
    """
    Loads the local device's keys from disk.
    If they don't exist, generates a new X25519 identity and saves the files.
    Returns:
        tuple[bytes, bytes]: (private_key_bytes, public_key_bytes) on success, None on failure
    """
    try:
        # Check if device_id is legacy dummy; if so, purge stale keys
        if os.path.exists(DEVICE_ID_PATH):
            try:
                with open(DEVICE_ID_PATH, "r") as f_did:
                    if f_did.read().strip() == OLD_DUMMY_DEVICE_ID:
                        if os.path.exists(KEYSTORE_PRIV_PATH):
                            os.remove(KEYSTORE_PRIV_PATH)
                        if os.path.exists(KEYSTORE_PUB_PATH):
                            os.remove(KEYSTORE_PUB_PATH)
            except Exception:
                pass

        if os.path.exists(KEYSTORE_PRIV_PATH) and os.path.exists(KEYSTORE_PUB_PATH):
            with open(KEYSTORE_PRIV_PATH, "rb") as f_priv:
                priv_bytes = f_priv.read()

            with open(KEYSTORE_PUB_PATH, "rb") as f_pub:
                pub_bytes = f_pub.read()

            if len(priv_bytes) == KEY_LENGTH and len(pub_bytes) == KEY_LENGTH:
                return priv_bytes, pub_bytes

        pair = generate_keypair()
        if pair is None:
            return None

        priv_bytes, pub_bytes = pair

        priv_tmp = KEYSTORE_PRIV_PATH + ".tmp"
        pub_tmp = KEYSTORE_PUB_PATH + ".tmp"

        with open(priv_tmp, "wb") as f_priv:
            _ = f_priv.write(priv_bytes)

        with open(pub_tmp, "wb") as f_pub:
            _ = f_pub.write(pub_bytes)

        os.replace(priv_tmp, KEYSTORE_PRIV_PATH)
        os.replace(pub_tmp, KEYSTORE_PUB_PATH)

        return priv_bytes, pub_bytes

    except OSError as e:
        print(f"Failed to load or create device keypair: {e}")
        return None


def get_or_create_device_id() -> str | None:
    """
    Loads this device's persistent UUID from disk.
    If it doesn't exist or is the legacy dummy ID, generates a new one and saves it.
    Returns:
        str: the device's UUID, or None on failure.
    """
    try:
        if os.path.exists(DEVICE_ID_PATH):
            with open(DEVICE_ID_PATH, "r") as f:
                val = f.read().strip()
                if val and val != OLD_DUMMY_DEVICE_ID:
                    return val

        device_id = str(uuid.uuid4())

        tmp_path = DEVICE_ID_PATH + ".tmp"
        with open(tmp_path, "w") as f:
            _ = f.write(device_id)

        os.replace(tmp_path, DEVICE_ID_PATH)

        return device_id

    except OSError as e:
        print(f"Failed to load or create device ID: {e}")
        return None


def get_device_id() -> dict[str, str | int]:
    """Returns this device's persistent UUID, for Kotlin to query/display."""
    device_id = get_or_create_device_id()
    if device_id is None:
        return {"code": ErrorCode.KEYSTORE_ERROR}

    return {"code": ErrorCode.OK, "device_id": device_id}


def get_public_key() -> dict[str, str | int]:
    """Returns this device's Curve25519 public key in hex format."""
    pair = get_or_create_keypair()
    if pair is None:
        return {"code": ErrorCode.KEYSTORE_ERROR}
    _, pub_bytes = pair
    return {"code": ErrorCode.OK, "public_key": pub_bytes.hex()}


def _ensure_peer_exists(peer_id: str, public_key: bytes | None = None) -> bool:
    """Ensure a peer exists in the peers table. If not, insert it."""
    connection = get_db_connection()
    if connection is None:
        return False

    try:
        cursor = connection.cursor()

        _ = cursor.execute(
            f"""SELECT {PEERS.columns.peer_id.name}
            FROM {PEERS.name}
            WHERE {PEERS.columns.peer_id.name} = ?""",
            (peer_id,),
        )
        existing = cast(SQLITE3_ROW_TYPE | None, cursor.fetchone())

        if existing is None:
            public_key = public_key or b""
            last_seen = int(time.time())

            _ = cursor.execute(
                f"""
                INSERT INTO {PEERS.name}
                (
                    {PEERS.columns.peer_id.name},
                    {PEERS.columns.public_key.name},
                    {PEERS.columns.last_seen.name}
                )
                VALUES (?, ?, ?)
                """,
                (peer_id, public_key, last_seen),
            )
            connection.commit()

        return True

    except sqlite3.Error as e:
        print(f"Database error ensuring peer exists: {e}")
        return False


def _get_peer_public_key(peer_id: str) -> bytes | None:
    """Retrieves a peer's public key from the database."""
    connection = get_db_connection()
    if connection is None:
        return None

    try:
        cursor = connection.cursor()

        _ = cursor.execute(
            f"""SELECT {PEERS.columns.public_key.name}
            FROM {PEERS.name}
            WHERE {PEERS.columns.peer_id.name} = ?""",
            (peer_id,),
        )
        row = cast(SQLITE3_ROW_TYPE | None, cursor.fetchone())

        if row is not None:
            public_key = cast(bytes | None, row[PEERS.columns.public_key.name])
            if public_key and len(public_key) == KEY_LENGTH:
                return public_key

        # Fallback 1: check if peer_id matches partial or alias
        _ = cursor.execute(
            f"""SELECT {PEERS.columns.public_key.name}
            FROM {PEERS.name}
            WHERE {PEERS.columns.peer_id.name} LIKE ? OR ? LIKE '%' || {PEERS.columns.peer_id.name} || '%'""",
            (f"%{peer_id}%", peer_id),
        )
        partial_row = cast(SQLITE3_ROW_TYPE | None, cursor.fetchone())
        if partial_row is not None:
            partial_key = cast(bytes | None, partial_row[PEERS.columns.public_key.name])
            if partial_key and len(partial_key) == KEY_LENGTH:
                return partial_key

        # Fallback 2: in 1-to-1 mesh, return the most recently seen other peer
        device_id = get_or_create_device_id()
        _ = cursor.execute(
            f"""SELECT {PEERS.columns.public_key.name}
            FROM {PEERS.name}
            WHERE {PEERS.columns.peer_id.name} != ?
            ORDER BY {PEERS.columns.last_seen.name} DESC
            LIMIT 1""",
            (device_id or "",),
        )
        fallback_row = cast(SQLITE3_ROW_TYPE | None, cursor.fetchone())
        if fallback_row is not None:
            fallback_key = cast(bytes | None, fallback_row[PEERS.columns.public_key.name])
            if fallback_key and len(fallback_key) == KEY_LENGTH:
                return fallback_key

    except sqlite3.Error as e:
        print(f"Database error retrieving peer public key: {e}")

    return None


def init_database() -> dict[str, str | int]:
    """
    Initialize the SQLite connection, build required tables, and bootstrap
    this device's identity (UUID + keypair), registering itself as a peer.
    """
    if not db_init_database():
        return {"code": ErrorCode.DATABASE_ERROR}

    conn = get_db_connection()
    if conn:
        try:
            cursor = conn.cursor()
            cursor.execute("DELETE FROM peers WHERE peer_id IN ('test-recipient', 'peer1', '9fbf76d1-18fe-4aac-a84a-a56bf6477a1b')")
            cursor.execute("DELETE FROM messages WHERE recipient_id IN ('test-recipient', 'peer1', '9fbf76d1-18fe-4aac-a84a-a56bf6477a1b') OR sender_id IN ('test-recipient', 'peer1', '9fbf76d1-18fe-4aac-a84a-a56bf6477a1b')")
            conn.commit()
        except Exception as e:
            print(f"Error purging legacy dummy records: {e}")

    device_id = get_or_create_device_id()
    if device_id is None:
        return {"code": ErrorCode.KEYSTORE_ERROR}

    pair = get_or_create_keypair()
    if pair is None:
        return {"code": ErrorCode.KEYSTORE_ERROR}

    _, my_pub_bytes = pair

    if not _ensure_peer_exists(device_id, my_pub_bytes):
        return {"code": ErrorCode.DATABASE_ERROR}

    return {"code": ErrorCode.OK}


def prepare_handshake_packet() -> dict[str, str | int]:
    """Creates a handshake packet containing this device's UUID and public key."""
    try:
        device_id = get_or_create_device_id()
        if device_id is None:
            return {"code": ErrorCode.KEYSTORE_ERROR}

        pair = get_or_create_keypair()
        if pair is None:
            return {"code": ErrorCode.KEYSTORE_ERROR}

        _, pub_bytes = pair

        packet = HANDSHAKE_PACKET_FORMAT.pack(
            hop_count=bytes([1]),
            packet_type=bytes([PacketType.HANDSHAKE]),
            sender_uuid=uuid.UUID(device_id).bytes,
            public_key=pub_bytes,
        )
        if packet is None:
            return {"code": ErrorCode.INVALID_PACKET}

        return {"code": ErrorCode.OK, "packet": packet.hex()}

    except ValueError as e:
        print(f"Error preparing handshake packet: {e}")
        return {"code": ErrorCode.INVALID_UUID}


def prepare_outgoing_message(recipient_uuid: str, message: str) -> dict[str, str | int]:
    """
    Derives the shared AES secret, encrypts the message, and attaches headers.
    """
    try:
        device_id = get_or_create_device_id()
        if device_id is None:
            return {"code": ErrorCode.KEYSTORE_ERROR}

        recipient_pub_bytes = _get_peer_public_key(recipient_uuid)

        pair = get_or_create_keypair()
        if pair is None:
            return {"code": ErrorCode.KEYSTORE_ERROR}

        my_priv_bytes, _ = pair

        aes_key = derive_shared_secret(my_priv_bytes, recipient_pub_bytes)
        if aes_key is None:
            aes_key = get_mesh_network_key()

        message_uuid = str(uuid.uuid4())
        message_uuid_bytes = uuid.UUID(message_uuid).bytes
        sender_uuid_bytes = uuid.UUID(device_id).bytes

        plaintext_bytes = message.encode("utf-8")
        encrypted_payload = encrypt(plaintext_bytes, aes_key)

        if encrypted_payload is None:
            return {"code": ErrorCode.ENCRYPTION_FAILED}

        packet = MESSAGE_PACKET_FORMAT.pack(
            hop_count=bytes([1]),
            packet_type=bytes([PacketType.MESSAGE]),
            message_uuid=message_uuid_bytes,
            sender_uuid=sender_uuid_bytes,
            ciphertext=encrypted_payload,
        )
        if packet is None:
            return {"code": ErrorCode.INVALID_PACKET}

        # Ensure recipient peer exists in PEERS table before message insert
        _ensure_peer_exists(recipient_uuid, recipient_pub_bytes)

        connection = get_db_connection()
        if connection is None:
            return {"code": ErrorCode.DATABASE_ERROR}

        cursor = connection.cursor()

        encrypted_payload_hex = encrypted_payload.hex()

        _ = cursor.execute(
            f"""
            INSERT OR IGNORE INTO {MESSAGES.name}
            (
                {MESSAGES.columns.msg_id.name},
                {MESSAGES.columns.sender_id.name},
                {MESSAGES.columns.recipient_id.name},
                {MESSAGES.columns.payload.name},
                {MESSAGES.columns.timestamp.name},
                {MESSAGES.columns.status.name}
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            (
                message_uuid,
                device_id,
                recipient_uuid,
                encrypted_payload_hex,
                int(time.time()),
                MessageStatus.SENT,
            ),
        )
        connection.commit()

        return {"code": ErrorCode.OK, "packet": packet.hex()}

    except (ValueError, UnicodeEncodeError) as e:
        print(f"Error preparing outgoing message (invalid data): {e}")
        return {"code": ErrorCode.INVALID_UUID}

    except sqlite3.Error as e:
        print(f"Error preparing outgoing message (database): {e}")
        return {"code": ErrorCode.DATABASE_ERROR}


def _process_handshake_packet(raw_payload: bytes) -> dict[str, str | int]:
    """Handle a handshake packet: store the sender's public key."""
    try:
        fields = HANDSHAKE_PACKET_FORMAT.unpack(raw_payload)
        if fields is None:
            return {"code": ErrorCode.INVALID_PACKET}

        sender_uuid = str(
            uuid.UUID(bytes=fields[HANDSHAKE_PACKET_FORMAT.sender_uuid.name])
        )
        public_key_bytes = fields[HANDSHAKE_PACKET_FORMAT.public_key.name]

        connection = get_db_connection()
        if connection is None:
            return {"code": ErrorCode.DATABASE_ERROR}

        cursor = connection.cursor()

        # Save or update the peer with their new public key
        _ = cursor.execute(
            f"""
            INSERT INTO {PEERS.name}
            (
                {PEERS.columns.peer_id.name},
                {PEERS.columns.public_key.name},
                {PEERS.columns.last_seen.name}
            )
            VALUES (?, ?, ?)
            ON CONFLICT({PEERS.columns.peer_id.name}) DO UPDATE SET
                {PEERS.columns.public_key.name}=excluded.{PEERS.columns.public_key.name},
                {PEERS.columns.last_seen.name}=excluded.{PEERS.columns.last_seen.name}
            """,
            (
                sender_uuid,
                public_key_bytes,
                int(time.time()),
            ),
        )
        connection.commit()

        return {
            "code": ErrorCode.OK,
            "sender_id": sender_uuid,
            "packet_type": PacketType.HANDSHAKE,
        }

    except ValueError as e:
        print(f"Error processing handshake packet (invalid data): {e}")
        return {"code": ErrorCode.INVALID_PACKET}

    except sqlite3.Error as e:
        print(f"Error processing handshake packet (database): {e}")
        return {"code": ErrorCode.DATABASE_ERROR}


def _process_message_packet(raw_payload: bytes) -> dict[str, str | int]:
    """Handle a message packet: decrypt, dedupe, and store it."""
    try:
        fields = MESSAGE_PACKET_FORMAT.unpack(raw_payload)
        if fields is None:
            return {"code": ErrorCode.INVALID_PACKET}

        message_uuid = str(
            uuid.UUID(bytes=fields[MESSAGE_PACKET_FORMAT.message_uuid.name])
        )
        sender_uuid = str(
            uuid.UUID(bytes=fields[MESSAGE_PACKET_FORMAT.sender_uuid.name])
        )
        ciphertext_payload = fields[MESSAGE_PACKET_FORMAT.ciphertext.name]
        hop_count = fields[MESSAGE_PACKET_FORMAT.hop_count.name][0]

        connection = get_db_connection()
        if connection is None:
            return {"code": ErrorCode.DATABASE_ERROR}

        cursor = connection.cursor()

        _ = cursor.execute(
            f"SELECT 1 FROM {MESSAGES.name} WHERE {MESSAGES.columns.msg_id.name} = ?",
            (message_uuid,),
        )

        sender_pub_bytes = _get_peer_public_key(sender_uuid)

        pair = get_or_create_keypair()
        my_priv_bytes = pair[0] if pair else None

        aes_key = derive_shared_secret(my_priv_bytes, sender_pub_bytes) if my_priv_bytes else get_mesh_network_key()

        plaintext_bytes = decrypt(ciphertext_payload, aes_key)
        if plaintext_bytes is None:
            mesh_key = get_mesh_network_key()
            if aes_key != mesh_key:
                plaintext_bytes = decrypt(ciphertext_payload, mesh_key)

        if plaintext_bytes is None:
            return {"code": ErrorCode.DECRYPTION_FAILED}

        try:
            plaintext = plaintext_bytes.decode("utf-8")
        except UnicodeDecodeError as e:
            print(f"Decryption failed for message {message_uuid}: {e}")
            return {"code": ErrorCode.DECRYPTION_FAILED}

        recipient_id = get_or_create_device_id()
        if recipient_id is None:
            return {"code": ErrorCode.KEYSTORE_ERROR}

        encrypted_payload_hex = ciphertext_payload.hex()

        _ = cursor.execute(
            f"""
            INSERT OR IGNORE INTO {MESSAGES.name}
            (
                {MESSAGES.columns.msg_id.name},
                {MESSAGES.columns.sender_id.name},
                {MESSAGES.columns.recipient_id.name},
                {MESSAGES.columns.payload.name},
                {MESSAGES.columns.timestamp.name},
                {MESSAGES.columns.status.name}
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            (
                message_uuid,
                sender_uuid,
                recipient_id,
                encrypted_payload_hex,
                int(time.time()),
                MessageStatus.RECEIVED,
            ),
        )
        connection.commit()

        return {
            "code": ErrorCode.OK,
            MESSAGES.columns.msg_id.name: message_uuid,
            MESSAGES.columns.sender_id.name: sender_uuid,
            MESSAGES.columns.recipient_id.name: recipient_id,
            "message": plaintext,
            MESSAGES.columns.timestamp.name: int(time.time()),
            MESSAGES.columns.status.name: MessageStatus.RECEIVED,
            MESSAGE_PACKET_FORMAT.hop_count.name: hop_count,
            "packet_type": PacketType.MESSAGE,
        }

    except ValueError as e:
        print(f"Error processing message packet (invalid data): {e}")
        return {"code": ErrorCode.INVALID_PACKET}

    except sqlite3.Error as e:
        print(f"Error processing message packet (database): {e}")
        return {"code": ErrorCode.DATABASE_ERROR}


def process_incoming_ble(raw_payload: bytes | str) -> dict[str, str | int]:
    """
    Routes incoming packets based on their packet format.
    """
    if isinstance(raw_payload, str):
        try:
            raw_payload = bytes.fromhex(raw_payload)
        except ValueError:
            return {"code": ErrorCode.INVALID_PACKET}
    elif not isinstance(raw_payload, bytes):
        try:
            raw_payload = bytes(raw_payload)
        except Exception:
            return {"code": ErrorCode.INVALID_PACKET}

    if HANDSHAKE_PACKET_FORMAT.matches(raw_payload):
        return _process_handshake_packet(raw_payload)

    if MESSAGE_PACKET_FORMAT.matches(raw_payload):
        return _process_message_packet(raw_payload)

    return {"code": ErrorCode.UNKNOWN_PACKET_TYPE}


def get_peers() -> list[dict[str, str | int]]:
    """Returns a list of known peers with their metadata."""
    connection = get_db_connection()
    if connection is None:
        return []

    try:
        cursor = connection.cursor()
        device_id = get_or_create_device_id()
        _ = cursor.execute(
            f"""
            SELECT
                {PEERS.columns.peer_id.name},
                {PEERS.columns.public_key.name},
                {PEERS.columns.last_seen.name}
            FROM {PEERS.name}
            WHERE {PEERS.columns.peer_id.name} NOT IN ('test-recipient', 'peer1', '9fbf76d1-18fe-4aac-a84a-a56bf6477a1b', ?)
            ORDER BY {PEERS.columns.last_seen.name} DESC
            """,
            (device_id or "",),
        )
        rows = cursor.fetchall()

        peers = []
        for row in rows:
            peer_id = row[PEERS.columns.peer_id.name]
            public_key = row[PEERS.columns.public_key.name]
            last_seen = row[PEERS.columns.last_seen.name]

            # Calculate distance based on time since last seen (simplified)
            time_since_seen = int(time.time()) - last_seen if last_seen > 0 else 9999
            # Convert to rough distance estimate (this is a placeholder - real implementation would use RSSI)
            distance_meters = min(float(time_since_seen), 100.0)  # Cap at 100m for demo

            # Format distance text
            if distance_meters < 1:
                distance_text = "< 1 m"
            else:
                distance_text = f"~ {int(distance_meters)} m"

            # Determine if online (seen within last 60 seconds)
            is_online = time_since_seen < 60

            peers.append({
                "id": peer_id,
                "name": f"Peer-{peer_id[-4:]}" if len(peer_id) >= 4 else f"Peer-{peer_id}",
                "distanceText": distance_text,
                "distanceMeters": distance_meters,
                "rssi": max(-100, min(-20, int(-60 - (distance_meters / 2)))),  # Rough RSSI estimate
                "isOnline": is_online,
                "publicKey": public_key.hex() if public_key else ""
            })

        return peers

    except sqlite3.Error as e:
        print(f"Database error getting peers: {e}")
        return []


def get_storage_breakdown() -> dict[str, int]:
    """Returns storage usage breakdown by message type."""
    connection = get_db_connection()
    if connection is None:
        return {
            "messagesBytes": 0,
            "imagesBytes": 0,
            "voiceBytes": 0,
            "otherBytes": 0
        }

    try:
        cursor = connection.cursor()

        # Get total messages size
        _ = cursor.execute(f"SELECT SUM(LENGTH({MESSAGES.columns.payload.name})) FROM {MESSAGES.name}")
        total_payload_size = cursor.fetchone()[0] or 0

        # For now, we'll categorize all as messages since we don't have media type in payload
        # In a real implementation, we might check the decrypted content or have metadata
        messages_bytes = total_payload_size * 2  # Approximate size including overhead

        # Placeholder values for other categories (would be enhanced with media type detection)
        images_bytes = 0
        voice_bytes = 0
        other_bytes = max(0, messages_bytes // 10)  # Some overhead

        return {
            "messagesBytes": messages_bytes,
            "imagesBytes": images_bytes,
            "voiceBytes": voice_bytes,
            "otherBytes": other_bytes
        }

    except sqlite3.Error as e:
        print(f"Database error getting storage breakdown: {e}")
        return {
            "messagesBytes": 0,
            "imagesBytes": 0,
            "voiceBytes": 0,
            "otherBytes": 0
        }


def trigger_panic_wipe() -> dict[str, str | int]:
    """Instantly drops all SQLite tables and deletes the physical keystore files."""
    db_success = db_trigger_panic_wipe()

    for path in [KEYSTORE_PRIV_PATH, KEYSTORE_PUB_PATH, DEVICE_ID_PATH]:
        if os.path.exists(path):
            try:
                os.remove(path)
            except OSError as e:
                print(f"Failed to delete keystore file {path}: {e}")
                return {"code": ErrorCode.KEYSTORE_ERROR}

    if not db_success:
        return {"code": ErrorCode.DATABASE_ERROR}

    return {"code": ErrorCode.OK}


def get_peers() -> list[dict[str, str | int]]:
    """
    Returns a list of all known peers from the database (excluding self).
    Used by PythonCoreBridge.getPeers() on Android to populate the nearby list.
    """
    device_id = get_or_create_device_id()
    connection = get_db_connection()
    if connection is None:
        return []

    try:
        cursor = connection.cursor()
        _ = cursor.execute(
            f"""SELECT
                {PEERS.columns.peer_id.name},
                {PEERS.columns.last_seen.name}
            FROM {PEERS.name}
            WHERE {PEERS.columns.peer_id.name} != ?
            ORDER BY {PEERS.columns.last_seen.name} DESC""",
            (device_id or "",),
        )
        rows = cursor.fetchall()
        result = []
        for row in rows:
            peer_id = row[PEERS.columns.peer_id.name]
            last_seen = row[PEERS.columns.last_seen.name]
            # Estimate age since last seen
            age_sec = int(time.time()) - (last_seen or 0)
            if age_sec < 60:
                dist = "~ 5 m"
            elif age_sec < 300:
                dist = "~ 12 m"
            else:
                dist = "~ 25 m"
            result.append({
                "id": peer_id,
                "name": f"Mesh Node {peer_id[:8]}",
                "distance": dist,
                "rssi": -60,
                "last_seen": last_seen or 0,
            })
        return result
    except sqlite3.Error as e:
        print(f"Database error in get_peers: {e}")
        return []


def get_storage_breakdown() -> dict[str, int]:
    """
    Returns approximate storage usage in MB broken down by category.
    Used by PythonCoreBridge.getStorageBreakdown() on Android.
    """
    connection = get_db_connection()
    db_path = os.path.join(BASE_DIR, "database.db")
    total_db_bytes = 0
    if os.path.exists(db_path):
        try:
            total_db_bytes = os.path.getsize(db_path)
        except OSError:
            total_db_bytes = 0

    msg_count = 0
    if connection is not None:
        try:
            cursor = connection.cursor()
            _ = cursor.execute(f"SELECT COUNT(*) FROM {MESSAGES.name}")
            row = cursor.fetchone()
            if row:
                msg_count = row[0]
        except sqlite3.Error:
            msg_count = 0

    # Estimate breakdown: each message ~500 bytes payload on average
    messages_bytes_mb = max(1, (msg_count * 500) // 1_000_000 + 1)
    # DB overhead and routing cache
    other_bytes_mb = max(1, total_db_bytes // 1_000_000)

    return {
        "messages": messages_bytes_mb,
        "images": 0,
        "voice": 0,
        "other": max(1, other_bytes_mb),
    }


def get_messages_for_peer(peer_id: str) -> list[dict[str, str | int]]:
    """
    Returns all decrypted messages exchanged with a specific peer.
    Used for store-and-forward retrieval when a peer reconnects.
    """
    device_id = get_or_create_device_id()
    connection = get_db_connection()
    if connection is None or device_id is None:
        return []

    try:
        cursor = connection.cursor()
        _ = cursor.execute(
            f"""SELECT
                {MESSAGES.columns.msg_id.name},
                {MESSAGES.columns.sender_id.name},
                {MESSAGES.columns.recipient_id.name},
                {MESSAGES.columns.payload.name},
                {MESSAGES.columns.timestamp.name},
                {MESSAGES.columns.status.name}
            FROM {MESSAGES.name}
            WHERE ({MESSAGES.columns.sender_id.name} = ? AND {MESSAGES.columns.recipient_id.name} = ?)
               OR ({MESSAGES.columns.sender_id.name} = ? AND {MESSAGES.columns.recipient_id.name} = ?)
            ORDER BY {MESSAGES.columns.timestamp.name} ASC""",
            (device_id, peer_id, peer_id, device_id),
        )
        rows = cursor.fetchall()
        result = []

        pair = get_or_create_keypair()
        peer_pub = _get_peer_public_key(peer_id)

        for row in rows:
            payload_hex = row[MESSAGES.columns.payload.name]
            plaintext = ""
            if payload_hex and pair and peer_pub:
                try:
                    ciphertext = bytes.fromhex(payload_hex)
                    my_priv, _ = pair
                    aes_key = derive_shared_secret(my_priv, peer_pub)
                    if aes_key:
                        pt = decrypt(ciphertext, aes_key)
                        if pt:
                            plaintext = pt.decode("utf-8", errors="replace")
                except Exception as e:
                    print(f"Error decrypting message: {e}")

            if not plaintext and payload_hex:
                plaintext = str(payload_hex)

            result.append({
                MESSAGES.columns.msg_id.name: row[MESSAGES.columns.msg_id.name],
                MESSAGES.columns.sender_id.name: row[MESSAGES.columns.sender_id.name],
                MESSAGES.columns.recipient_id.name: row[MESSAGES.columns.recipient_id.name],
                "message": plaintext,
                MESSAGES.columns.timestamp.name: row[MESSAGES.columns.timestamp.name],
                MESSAGES.columns.status.name: row[MESSAGES.columns.status.name],
            })
        return result
    except sqlite3.Error as e:
        print(f"Database error in get_messages_for_peer: {e}")
        return []


def record_raw_chat_message(
    sender_id: str,
    recipient_id: str | None,
    text: str,
    is_incoming: bool = True
) -> bool:
    """Records a plaintext or decoded chat message directly into SQLite."""
    conn = get_db_connection()
    if conn is None:
        return False
    try:
        _ensure_peer_exists(sender_id)
        if recipient_id and recipient_id != "public":
            _ensure_peer_exists(recipient_id)
        cursor = conn.cursor()
        msg_id = str(uuid.uuid4())
        status = MessageStatus.RECEIVED if is_incoming else MessageStatus.SENT
        cursor.execute(
            f"""
            INSERT OR IGNORE INTO {MESSAGES.name}
            (
                {MESSAGES.columns.msg_id.name},
                {MESSAGES.columns.sender_id.name},
                {MESSAGES.columns.recipient_id.name},
                {MESSAGES.columns.payload.name},
                {MESSAGES.columns.timestamp.name},
                {MESSAGES.columns.status.name}
            )
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            (msg_id, sender_id, recipient_id or "public", text, int(time.time()), status),
        )
        conn.commit()
        return True
    except Exception as e:
        print(f"Error in record_raw_chat_message: {e}")
        return False

