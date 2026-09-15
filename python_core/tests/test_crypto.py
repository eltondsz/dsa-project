import os
import sys

# Add the parent directory of this file (which is python_core) to the path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from cryptography.exceptions import InvalidTag

from crypto import decrypt, encrypt, generate_key


def test_encrypt_decrypt_roundtrip():
    """Test that encrypting and then decrypting returns the original plaintext."""
    key = generate_key()
    plaintext = b"Hello, World! This is a test message."
    encrypted = encrypt(plaintext, key)
    decrypted = decrypt(encrypted, key)
    assert decrypted == plaintext, "Decrypted text should match original plaintext"


def test_tampered_ciphertext_raises_exception():
    """Test that tampering with the ciphertext causes decryption to fail."""
    key = generate_key()
    plaintext = b"Secret message"
    encrypted = encrypt(plaintext, key)
    # Tamper with the ciphertext (flip a bit in the ciphertext part)
    # Ensure we have at least one byte of ciphertext
    assert len(encrypted) > 12, "Encrypted data should be longer than just the nonce"
    # Convert to bytearray to modify, then back to bytes
    tampered = bytearray(encrypted)
    # Flip a bit in the first byte of the ciphertext (offset 12)
    tampered[12] ^= 0x01
    tampered_bytes = bytes(tampered)
    # Attempting to decrypt should raise InvalidTag
    try:
        _ = decrypt(tampered_bytes, key)
        assert False, "Expected InvalidTag exception was not raised"
    except InvalidTag:
        # Expected exception
        pass


def test_nonce_is_unique():
    """Test that two encryptions of the same plaintext produce different nonces (and thus different output)."""
    key = generate_key()
    plaintext = b"Same plaintext"
    encrypted1 = encrypt(plaintext, key)
    encrypted2 = encrypt(plaintext, key)
    # The nonces (first 12 bytes) should be different
    assert encrypted1[:12] != encrypted2[:12], (
        "Nonces should be different for two encryptions"
    )
    # The ciphertexts will also be different due to different nonces


if __name__ == "__main__":
    # Allow running the test file directly for debugging
    test_encrypt_decrypt_roundtrip()
    test_tampered_ciphertext_raises_exception()
    test_nonce_is_unique()
    print("All tests passed.")
