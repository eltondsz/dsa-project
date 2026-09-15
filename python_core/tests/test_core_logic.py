import sys
import uuid
from typing import cast

# Add the parent directory to the path so we can import core_logic
sys.path.insert(0, "/Users/rampathak/Documents/dsa-project/python_core")

import core_logic
from crypto import decrypt
from database import get_db_connection


def test_init_database():
    """Test that the database initializes correctly."""
    _ = core_logic.trigger_panic_wipe()
    result = core_logic.init_database()
    assert result is True, "Database initialization should return True"


def test_prepare_and_process_outgoing_message():
    """Test preparing an outgoing message and processing it as incoming."""
    _ = core_logic.trigger_panic_wipe()
    assert core_logic.init_database() is True

    recipient_uuid = str(uuid.uuid4())
    test_message = "Hello, Mesh Network!"

    ble_packet = core_logic.prepare_outgoing_message(recipient_uuid, test_message)
    assert isinstance(ble_packet, bytes), "BLE packet should be bytes"
    assert len(ble_packet) >= 18, "BLE packet should be at least 18 bytes (header)"

    hop_count = ble_packet[0]
    packet_type = ble_packet[1]
    message_uuid_bytes = ble_packet[2:18]
    ciphertext_payload = ble_packet[18:]

    assert hop_count == 1, "Hop count should be 1 for direct message"
    assert packet_type == 0x01, "Packet type should be 0x01 for Direct Chat"

    try:
        original_msg_id = str(uuid.UUID(bytes=message_uuid_bytes))
    except ValueError:
        assert False, "Invalid UUID in message header"

    result_dict = core_logic.process_incoming_ble(ble_packet)

    assert "error" not in result_dict, (
        f"Processing should not return an error: {result_dict.get('error')}"
    )
    assert result_dict["message"] == test_message, (
        "Decrypted message should match original"
    )
    assert result_dict["msg_id"] == original_msg_id, "Message ID should match"
    assert result_dict["sender_id"] == "local_device", (
        "Sender ID should be local_device"
    )
    assert result_dict["recipient_id"] == recipient_uuid, "Recipient ID should match"
    assert result_dict["status"] == 2, (
        "Status should be 2 (Delivered) for incoming message"
    )
    assert result_dict["hop_count"] == hop_count, "Hop count should match"
    assert result_dict["packet_type"] == packet_type, "Packet type should match"

    conn = get_db_connection()
    cursor = conn.cursor()
    _ = cursor.execute(
        "SELECT msg_id, sender_id, recipient_id, payload, timestamp, status FROM messages WHERE msg_id = ?",
        (result_dict["msg_id"],),
    )

    # Cast the row to silence Pyright 'Any' warnings
    row = cast(tuple[str, str, str, str, int, int] | None, cursor.fetchone())
    assert row is not None, "Message should be stored in the database"

    db_msg_id, db_sender_id, db_recipient_id, db_payload, _, db_status = row

    assert db_msg_id == result_dict["msg_id"]
    assert (
        db_sender_id == result_dict["sender_id"]
        and db_recipient_id == result_dict["recipient_id"]
    ) or (
        db_sender_id == result_dict["recipient_id"]
        and db_recipient_id == result_dict["sender_id"]
    ), "Sender/recipient IDs should match expected values"
    assert db_payload == ciphertext_payload.hex(), (
        "Payload should store encrypted bytes as hex string"
    )
    assert db_status in [0, 2], (
        f"Status should be 0 (Draft) or 2 (Delivered), got {db_status}"
    )

    # Ignore specifically the private usage warning from Pyright for tests
    key = core_logic._get_or_create_encryption_key()  # pyright: ignore[reportPrivateUsage]
    encrypted_bytes = bytes.fromhex(str(db_payload))
    decrypted_bytes = decrypt(encrypted_bytes, key)
    decrypted_message = decrypted_bytes.decode("utf-8")
    assert decrypted_message == test_message, (
        "Stored encrypted payload should decrypt to original message"
    )

    _ = core_logic.trigger_panic_wipe()


def test_process_incoming_ble_too_short():
    """Test processing a payload that is too short."""
    _ = core_logic.trigger_panic_wipe()
    assert core_logic.init_database() is True

    short_payload = b"\x01\x01" + b"\x00" * 10
    result = core_logic.process_incoming_ble(short_payload)
    assert "error" in result

    # Cast dictionary value to string to resolve the Pyright 'in' operator type warning
    assert "Payload too short" in str(result["error"])

    _ = core_logic.trigger_panic_wipe()


def test_trigger_panic_wipe():
    """Test that trigger_panic_wipe clears the database."""
    _ = core_logic.trigger_panic_wipe()
    assert core_logic.init_database() is True

    recipient_uuid = str(uuid.uuid4())
    test_message = "Test message"
    ble_packet = core_logic.prepare_outgoing_message(recipient_uuid, test_message)
    _ = core_logic.process_incoming_ble(ble_packet)

    conn = get_db_connection()
    cursor = conn.cursor()
    _ = cursor.execute("SELECT COUNT(*) FROM messages")

    # Cast to silence Pyright
    count_row = cast(tuple[int] | None, cursor.fetchone())
    assert count_row is not None
    count_before = count_row[0]
    assert count_before > 0, "There should be at least one message in the database"

    wipe_result = core_logic.trigger_panic_wipe()
    assert wipe_result is True, "Panic wipe should return True"

    assert core_logic.init_database() is True

    conn = get_db_connection()
    cursor = conn.cursor()
    _ = cursor.execute("SELECT COUNT(*) FROM messages")

    count_after_row = cast(tuple[int] | None, cursor.fetchone())
    assert count_after_row is not None
    count_after = count_after_row[0]
    assert count_after == 0, "Database should be empty after panic wipe"

    _ = cursor.execute("SELECT COUNT(*) FROM peers")
    peer_count_after_row = cast(tuple[int] | None, cursor.fetchone())
    assert peer_count_after_row is not None
    peer_count_after = peer_count_after_row[0]
    assert peer_count_after == 0, "Peers table should be empty after panic wipe"


if __name__ == "__main__":
    test_init_database()
    print("test_init_database passed")
    test_prepare_and_process_outgoing_message()
    print("test_prepare_and_process_outgoing_message passed")
    test_process_incoming_ble_too_short()
    print("test_process_incoming_ble_too_short passed")
    test_trigger_panic_wipe()
    print("test_trigger_panic_wipe passed")
    print("All tests passed!")
