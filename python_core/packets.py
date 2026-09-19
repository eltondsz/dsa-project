from dataclasses import dataclass
from enum import IntEnum


class PacketType(IntEnum):
    MESSAGE = 0x01
    HANDSHAKE = 0x03


@dataclass(frozen=True)
class Field:
    name: str
    length: int | None = None  # None means variable length, has to be last


@dataclass(frozen=True)
class PacketFormat:
    packet_type: PacketType
    fields: tuple[Field, ...]

    @property
    def min_length(self) -> int:
        return sum(f.length for f in self.fields if f.length is not None)

    def matches(self, raw: bytes) -> bool:
        """Return whether raw is a packet of this format."""
        packet_type_offset = 1

        if len(raw) <= packet_type_offset:
            return False

        return raw[packet_type_offset] == self.packet_type

    def unpack(self, raw: bytes) -> dict[str, bytes] | None:
        """Unpack a packet after verifying that it matches this format."""
        if not self.matches(raw):
            print(f"packet does not match format: expected type {self.packet_type}")
            return None

        if len(raw) < self.min_length:
            print(f"payload too short: need {self.min_length} bytes, got {len(raw)}")
            return None

        result: dict[str, bytes] = {}
        offset = 0

        for field in self.fields:
            if field.length is None:
                result[field.name] = raw[offset:]
            else:
                result[field.name] = raw[offset : offset + field.length]
                offset += field.length

        return result

    def pack(self, **values: bytes) -> bytes | None:
        """Pack fields into a packet, inserting this format's packet type."""
        packet = bytearray()

        try:
            for field in self.fields:
                if field.name == "packet_type":
                    chunk = bytes([self.packet_type])
                else:
                    chunk = values[field.name]

                if field.length is not None and len(chunk) != field.length:
                    raise ValueError(
                        f"{field.name} expected {field.length} bytes, got {len(chunk)}"
                    )

                packet.extend(chunk)

            return bytes(packet)

        except KeyError as e:
            print(f"Failed to pack packet: missing field {e}")
            return None

        except ValueError as e:
            print(f"Failed to pack packet: {e}")
            return None

    def __getattr__(self, name: str) -> Field:
        for f in self.fields:
            if f.name == name:
                return f

        raise AttributeError(f'Invalid access, field "{name}" doesn\'t exist')


HANDSHAKE_PACKET_FORMAT = PacketFormat(
    packet_type=PacketType.HANDSHAKE,
    fields=(
        Field("hop_count", 1),
        Field("packet_type", 1),
        Field("sender_uuid", 16),
        Field("public_key", 32),
    ),
)

MESSAGE_PACKET_FORMAT = PacketFormat(
    packet_type=PacketType.MESSAGE,
    fields=(
        Field("hop_count", 1),
        Field("packet_type", 1),
        Field("message_uuid", 16),
        Field("sender_uuid", 16),
        Field("ciphertext", None),
    ),
)
