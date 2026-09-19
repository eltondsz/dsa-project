import os

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import x25519
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF

_NONCE_NBYTES = 12
KEY_LENGTH = 32


def generate_keypair() -> tuple[bytes, bytes] | None:
    """
    Generate an X25519 keypair for Elliptic Curve Diffie-Hellman (ECDH).
    Returns:
        tuple[bytes, bytes]: (private_key_bytes, public_key_bytes), or None on failure.
    """
    try:
        private_key = x25519.X25519PrivateKey.generate()
        public_key = private_key.public_key()

        priv_bytes = private_key.private_bytes(
            encoding=serialization.Encoding.Raw,
            format=serialization.PrivateFormat.Raw,
            encryption_algorithm=serialization.NoEncryption(),
        )

        pub_bytes = public_key.public_bytes(
            encoding=serialization.Encoding.Raw,
            format=serialization.PublicFormat.Raw,
        )
        return priv_bytes, pub_bytes
    except ValueError as e:
        print(f"Failed to generate keypair: {e}")
        return None


def derive_shared_secret(
    private_key_bytes: bytes, peer_public_key_bytes: bytes
) -> bytes | None:
    """
    Derive a secure 32-byte AES key using ECDH and HKDF.
    Args:
        private_key_bytes: The local device's 32-byte private key.
        peer_public_key_bytes: The remote contact's 32-byte public key.
    Returns:
        bytes: A secure 32-byte key for AES-GCM encryption, or None on failure.
    """
    try:
        private_key = x25519.X25519PrivateKey.from_private_bytes(private_key_bytes)
        peer_public_key = x25519.X25519PublicKey.from_public_bytes(
            peer_public_key_bytes
        )

        shared_key = private_key.exchange(peer_public_key)

        derived_key = HKDF(
            algorithm=hashes.SHA256(),
            length=KEY_LENGTH,
            salt=None,
            info=b"mesh_chat_e2ee_v1",
        ).derive(shared_key)

        return derived_key
    except ValueError as e:
        print(f"Failed to derive shared secret: {e}")
        return None


def encrypt(plaintext: bytes, key: bytes) -> bytes | None:
    """
    Encrypt plaintext using AES-GCM with a randomly generated nonce.
    Returns:
        bytes: nonce + ciphertext, or None on failure.
    """
    try:
        aesgcm = AESGCM(key)
        nonce = os.urandom(_NONCE_NBYTES)
        ciphertext = aesgcm.encrypt(nonce, plaintext, None)

        return nonce + ciphertext
    except (OSError, ValueError) as e:
        print(f"Failed to encrypt: {e}")
        return None


def decrypt(encrypted_data: bytes, key: bytes) -> bytes | None:
    """
    Decrypt data encrypted with encrypt().
    Returns:
        bytes: the decrypted plaintext, or None on failure (including a
        failed authentication check).
    """
    if len(encrypted_data) < _NONCE_NBYTES:
        print("Encrypted data too short to contain a nonce")
        return None

    nonce = encrypted_data[:_NONCE_NBYTES]
    ciphertext = encrypted_data[_NONCE_NBYTES:]

    try:
        aesgcm = AESGCM(key)
        return aesgcm.decrypt(nonce, ciphertext, None)
    except (ValueError, InvalidTag) as e:
        print(f"Unable to decrypt payload: {e}")
        return None
