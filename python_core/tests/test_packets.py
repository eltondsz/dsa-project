import os
import uuid

from packets import (
    HANDSHAKE_PACKET_FORMAT,
    MESSAGE_PACKET_FORMAT,
    PacketType,
)


def test_packet_matches():
    """Test that the matches() method correctly identifies packet types."""
    # Construct raw bytes with hop_count at index 0 and packet_type at index 1
    handshake_raw = b"\x01" + bytes([PacketType.HANDSHAKE]) + b"\x00" * 48
    message_raw = b"\x01" + bytes([PacketType.MESSAGE]) + b"\x00" * 32
    invalid_raw = b"\x01\x99" + b"\x00" * 10
    too_short_raw = b"\x01"

    assert HANDSHAKE_PACKET_FORMAT.matches(handshake_raw) is True
    assert HANDSHAKE_PACKET_FORMAT.matches(message_raw) is False
    assert MESSAGE_PACKET_FORMAT.matches(message_raw) is True

    assert HANDSHAKE_PACKET_FORMAT.matches(invalid_raw) is False
    assert HANDSHAKE_PACKET_FORMAT.matches(too_short_raw) is False


def test_handshake_pack_and_unpack():
    """Test full cycle of packing and unpacking a Handshake packet."""
    hop_count = b"\x01"
    sender_uuid = uuid.uuid4().bytes
    public_key = os.urandom(32)

    packet = HANDSHAKE_PACKET_FORMAT.pack(
        hop_count=hop_count,
        sender_uuid=sender_uuid,
        public_key=public_key,
    )
    assert packet is not None
    assert len(packet) == HANDSHAKE_PACKET_FORMAT.min_length

    unpacked = HANDSHAKE_PACKET_FORMAT.unpack(packet)
    assert unpacked is not None

    # Verify the unpacked fields match the original inputs
    assert unpacked["hop_count"] == hop_count
    assert unpacked["packet_type"] == bytes([PacketType.HANDSHAKE])
    assert unpacked["sender_uuid"] == sender_uuid
    assert unpacked["public_key"] == public_key


def test_message_pack_and_unpack_variable_length():
    """Test full cycle of packing/unpacking a Message packet with variable ciphertext."""
    hop_count = b"\x02"
    msg_uuid = uuid.uuid4().bytes
    sender_uuid = uuid.uuid4().bytes

    # Variable length ciphertext (e.g., 12-byte nonce + 50 bytes encrypted data)
    ciphertext = os.urandom(62)

    packet = MESSAGE_PACKET_FORMAT.pack(
        hop_count=hop_count,
        message_uuid=msg_uuid,
        sender_uuid=sender_uuid,
        ciphertext=ciphertext,
    )
    assert packet is not None
    assert len(packet) == MESSAGE_PACKET_FORMAT.min_length + len(ciphertext)

    unpacked = MESSAGE_PACKET_FORMAT.unpack(packet)
    assert unpacked is not None

    assert unpacked["message_uuid"] == msg_uuid
    assert unpacked["sender_uuid"] == sender_uuid
    assert unpacked["ciphertext"] == ciphertext


def test_pack_invalid_length_and_missing_fields():
    """Test that pack() safely fails when given invalid field lengths or missing keys."""
    # Missing public_key
    missing_field_packet = HANDSHAKE_PACKET_FORMAT.pack(
        hop_count=b"\x01",
        sender_uuid=uuid.uuid4().bytes,
    )
    assert missing_field_packet is None

    # Invalid public_key length (31 bytes instead of 32)
    invalid_length_packet = HANDSHAKE_PACKET_FORMAT.pack(
        hop_count=b"\x01",
        sender_uuid=uuid.uuid4().bytes,
        public_key=os.urandom(31),
    )
    assert invalid_length_packet is None


def test_unpack_too_short():
    """Test that unpack() catches payloads that are smaller than the minimum format length."""
    # Construct a packet that matches the type but is truncated
    truncated_handshake = b"\x01" + bytes([PacketType.HANDSHAKE]) + b"\x00" * 10

    # Should return None instead of throwing an index out-of-bounds error
    result = HANDSHAKE_PACKET_FORMAT.unpack(truncated_handshake)
    assert result is None


if __name__ == "__main__":
    test_packet_matches()
    test_handshake_pack_and_unpack()
    test_message_pack_and_unpack_variable_length()
    test_pack_invalid_length_and_missing_fields()
    test_unpack_too_short()
    print("All packet tests passed!")
