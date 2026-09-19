import os
import sqlite3
import time
import uuid
from typing import cast

from crypto import KEY_LENGTH, decrypt, derive_shared_secret, encrypt, generate_keypair
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


def get_or_create_keypair() -> tuple[bytes, bytes] | None:
    """
    Loads the local device's keys from disk.
    If they don't exist, generates a new X25519 identity and saves the files.
    Returns:
        tuple[bytes, bytes]: (private_key_bytes, public_key_bytes) on success, None on failure
    """
    try:
        if os.path.exists(KEYSTORE_PRIV_PATH) and os.path.exists(KEYSTORE_PUB_PATH):
            with open(KEYSTORE_PRIV_PATH, "rb") as f_priv:
                priv_bytes = f_priv.read()

            with open(KEYSTORE_PUB_PATH, "rb") as f_pub:
                pub_bytes = f_pub.read()

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
    If it doesn't exist, generates a new one and saves it.
    Returns:
        str: the device's UUID, or None on failure.
    """
    try:
        if os.path.exists(DEVICE_ID_PATH):
            with open(DEVICE_ID_PATH, "r") as f:
                return f.read().strip()

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

        if row is None:
            return None

        public_key = cast(bytes | None, row[PEERS.columns.public_key.name])

        if public_key and len(public_key) == KEY_LENGTH:
            return public_key

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
        if not recipient_pub_bytes:
            print(
                """Cannot send message: Recipient's public key is missing.
                Handshake required."""
            )
            return {"code": ErrorCode.MISSING_PUBLIC_KEY}

        pair = get_or_create_keypair()
        if pair is None:
            return {"code": ErrorCode.KEYSTORE_ERROR}

        my_priv_bytes, _ = pair

        aes_key = derive_shared_secret(my_priv_bytes, recipient_pub_bytes)
        if aes_key is None:
            return {"code": ErrorCode.KEYSTORE_ERROR}

        message_uuid = str(uuid.uuid4())
        message_uuid_bytes = uuid.UUID(message_uuid).bytes
        sender_uuid_bytes = uuid.UUID(device_id).bytes

        plaintext_bytes = message.encode("utf-8")
        encrypted_payload = encrypt(plaintext_bytes, aes_key)

        if encrypted_payload is None:
            return {"code": ErrorCode.ENCRYPTION_FAILED}

        packet = MESSAGE_PACKET_FORMAT.pack(
            hop_count=bytes([1]),
            message_uuid=message_uuid_bytes,
            sender_uuid=sender_uuid_bytes,
            ciphertext=encrypted_payload,
        )
        if packet is None:
            return {"code": ErrorCode.INVALID_PACKET}

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

        if cursor.fetchone():
            return {"code": ErrorCode.DUPLICATE_MESSAGE}

        sender_pub_bytes = _get_peer_public_key(sender_uuid)

        if not sender_pub_bytes:
            return {"code": ErrorCode.MISSING_PUBLIC_KEY}

        pair = get_or_create_keypair()
        if pair is None:
            return {"code": ErrorCode.KEYSTORE_ERROR}

        my_priv_bytes, _ = pair

        aes_key = derive_shared_secret(my_priv_bytes, sender_pub_bytes)
        if aes_key is None:
            return {"code": ErrorCode.KEYSTORE_ERROR}

        plaintext_bytes = decrypt(ciphertext_payload, aes_key)
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


def process_incoming_ble(raw_payload: bytes) -> dict[str, str | int]:
    """
    Routes incoming packets based on their packet format.
    """
    if HANDSHAKE_PACKET_FORMAT.matches(raw_payload):
        return _process_handshake_packet(raw_payload)

    if MESSAGE_PACKET_FORMAT.matches(raw_payload):
        return _process_message_packet(raw_payload)

    return {"code": ErrorCode.UNKNOWN_PACKET_TYPE}


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
