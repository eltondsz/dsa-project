import os

from cryptography.hazmat.primitives.ciphers.aead import AESGCM


def generate_key() -> bytes:
    """
    Generate a random 256-bit key for AES-GCM.
    Returns:
        bytes: The generated key (32 bytes).
    """
    return AESGCM.generate_key(bit_length=256)


def encrypt(plaintext: bytes, key: bytes) -> bytes:
    """
    Encrypt plaintext using AES-GCM with a randomly generated nonce.
    Args:
        plaintext: The message to encrypt as bytes.
        key: The 256-bit key to use for encryption.
    Returns:
        bytes: The nonce (12 bytes) concatenated with the ciphertext.
    """
    aesgcm = AESGCM(key)
    nonce = os.urandom(12)  # 96-bit nonce for AES-GCM
    ciphertext = aesgcm.encrypt(nonce, plaintext, None)
    return nonce + ciphertext


def decrypt(encrypted_data: bytes, key: bytes) -> bytes:
    """
    Decrypt data encrypted with encrypt().
    Args:
        encrypted_data: The nonce (12 bytes) concatenated with the ciphertext.
        key: The 256-bit key to use for decryption.
    Returns:
        bytes: The decrypted plaintext.
    Raises:
        InvalidTag: If the ciphertext does not authenticate with the key.
    """
    if len(encrypted_data) < 12:
        raise ValueError("Encrypted data too short to contain a nonce.")
    nonce = encrypted_data[:12]
    ciphertext = encrypted_data[12:]
    aesgcm = AESGCM(key)
    return aesgcm.decrypt(nonce, ciphertext, None)
