import os
import uuid
from typing import cast

import core_logic
import crypto
from database import get_db_connection
from errors import ErrorCode
from packets import HANDSHAKE_PACKET_FORMAT, MESSAGE_PACKET_FORMAT
from schema import MessageStatus


def test_init_database():
    """Test that the database initializes correctly."""
    _ = core_logic.trigger_panic_wipe()
    result = core_logic.init_database()
    assert result.get("code") == ErrorCode.OK, (
        "Database initialization should return OK"
    )


def test_handshake_and_messaging_flow():
    """Test a full interaction: Handshake (0x03) followed by bidirectional Messaging (0x01)."""
    _ = core_logic.trigger_panic_wipe()
    assert core_logic.init_database().get("code") == ErrorCode.OK

    # 1. Setup simulated remote peer
    dummy_keypair = crypto.generate_keypair()
    assert dummy_keypair is not None
    dummy_priv, dummy_pub = dummy_keypair
    dummy_uuid = str(uuid.uuid4())

    # 2. Simulate Incoming Handshake (Type 0x03)
    hs_packet = HANDSHAKE_PACKET_FORMAT.pack(
        hop_count=bytes([1]),
        sender_uuid=uuid.UUID(dummy_uuid).bytes,
        public_key=dummy_pub,
    )
    assert hs_packet is not None

    hs_result = core_logic.process_incoming_ble(hs_packet)
    assert hs_result.get("code") == ErrorCode.OK, (
        "Handshake should process successfully"
    )
    assert hs_result.get("sender_id") == dummy_uuid, (
        "Handshake should identify correct sender"
    )

    # 3. Test Outgoing Message (Type 0x01)
    test_message = "Hello, Mesh Network!"
    out_result = core_logic.prepare_outgoing_message(dummy_uuid, test_message)
    assert out_result.get("code") == ErrorCode.OK, (
        "Outgoing message should prepare successfully"
    )

    out_packet_hex = str(out_result["packet"])
    out_packet = bytes.fromhex(out_packet_hex)
    assert MESSAGE_PACKET_FORMAT.matches(out_packet), (
        "Packet should match MESSAGE format"
    )

    # 4. Simulate Incoming E2EE Message (Type 0x01)
    local_keypair = core_logic.get_or_create_keypair()
    assert local_keypair is not None
    _, local_pub = local_keypair

    # Derive secret using dummy's private key and local device's public key
    shared_secret = crypto.derive_shared_secret(dummy_priv, local_pub)
    assert shared_secret is not None

    incoming_text = "Message received loud and clear!"
    encrypted_payload = crypto.encrypt(incoming_text.encode("utf-8"), shared_secret)
    assert encrypted_payload is not None

    incoming_msg_uuid = uuid.uuid4()
    in_packet = MESSAGE_PACKET_FORMAT.pack(
        hop_count=bytes([1]),
        message_uuid=incoming_msg_uuid.bytes,
        sender_uuid=uuid.UUID(dummy_uuid).bytes,
        ciphertext=encrypted_payload,
    )
    assert in_packet is not None

    in_result = core_logic.process_incoming_ble(in_packet)
    assert in_result.get("code") == ErrorCode.OK, (
        "Incoming message should process successfully"
    )
    assert in_result.get("message") == incoming_text, (
        "Decrypted message should match original"
    )
    assert in_result.get("sender_id") == dummy_uuid
    assert in_result.get("status") == MessageStatus.RECEIVED


def test_process_incoming_ble_invalid():
    """Test processing a payload that is structurally invalid."""
    _ = core_logic.trigger_panic_wipe()
    assert core_logic.init_database().get("code") == ErrorCode.OK

    # Simulate a corrupted / truncated packet
    short_payload = b"\x01\x01" + b"\x00" * 5
    result = core_logic.process_incoming_ble(short_payload)

    # Check if it was rejected due to being unknown or invalid
    assert result.get("code") in (
        ErrorCode.INVALID_PACKET,
        ErrorCode.UNKNOWN_PACKET_TYPE,
    )


def test_trigger_panic_wipe():
    """Test that trigger_panic_wipe clears the database and deletes keys."""
    _ = core_logic.trigger_panic_wipe()
    assert core_logic.init_database().get("code") == ErrorCode.OK

    # Verify keys exist
    assert os.path.exists(core_logic.KEYSTORE_PRIV_PATH)
    assert os.path.exists(core_logic.KEYSTORE_PUB_PATH)

    wipe_result = core_logic.trigger_panic_wipe()
    assert wipe_result.get("code") == ErrorCode.OK

    # Verify files are deleted
    assert not os.path.exists(core_logic.KEYSTORE_PRIV_PATH), (
        "Private key should be deleted"
    )
    assert not os.path.exists(core_logic.KEYSTORE_PUB_PATH), (
        "Public key should be deleted"
    )

    # Verify DB is emptied
    assert core_logic.init_database().get("code") == ErrorCode.OK
    conn = get_db_connection()
    assert conn is not None
    cursor = conn.cursor()
    _ = cursor.execute("SELECT COUNT(*) FROM messages")

    count_row = cast(tuple[int] | None, cursor.fetchone())
    assert count_row is not None
    assert count_row[0] == 0, "Database should be empty after panic wipe"


if __name__ == "__main__":
    test_init_database()
    print("test_init_database passed")
    test_handshake_and_messaging_flow()
    print("test_handshake_and_messaging_flow passed")
    test_process_incoming_ble_invalid()
    print("test_process_incoming_ble_invalid passed")
    test_trigger_panic_wipe()
    print("test_trigger_panic_wipe passed")
    print("All core logic tests passed!")
