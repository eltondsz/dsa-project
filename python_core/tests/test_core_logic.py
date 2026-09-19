from crypto import decrypt, derive_shared_secret, encrypt, generate_keypair


def test_asymmetric_keypair_generation():
    """Test X25519 keypair generation produces valid 32-byte keys."""
    keypair = generate_keypair()
    assert keypair is not None, "Keypair generation should not fail"

    priv, pub = keypair
    assert len(priv) == 32, "Private key should be exactly 32 bytes"
    assert len(pub) == 32, "Public key should be exactly 32 bytes"


def test_ecdh_shared_secret_derivation():
    """Test that two parties generate the exact same shared secret."""
    alice_pair = generate_keypair()
    bob_pair = generate_keypair()

    assert alice_pair is not None and bob_pair is not None
    alice_priv, alice_pub = alice_pair
    bob_priv, bob_pub = bob_pair

    # Alice derives secret using Bob's public key
    alice_secret = derive_shared_secret(alice_priv, bob_pub)
    # Bob derives secret using Alice's public key
    bob_secret = derive_shared_secret(bob_priv, alice_pub)

    assert alice_secret is not None
    assert bob_secret is not None
    assert len(alice_secret) == 32, "AES shared secret should be 32 bytes"
    assert alice_secret == bob_secret, "Both parties must derive identical secrets"


def test_encrypt_decrypt_roundtrip():
    """Test full E2EE encryption and decryption cycle using derived secret."""
    alice_pair = generate_keypair()
    bob_pair = generate_keypair()
    assert alice_pair is not None and bob_pair is not None

    shared_secret = derive_shared_secret(alice_pair[0], bob_pair[1])
    assert shared_secret is not None

    plaintext = b"Offline mesh network testing payload."
    encrypted = encrypt(plaintext, shared_secret)
    assert encrypted is not None

    decrypted = decrypt(encrypted, shared_secret)
    assert decrypted == plaintext, "Decrypted text should match original plaintext"


def test_tampered_ciphertext_returns_none():
    """Test that modifying the GCM ciphertext safely returns None."""
    alice_pair = generate_keypair()
    bob_pair = generate_keypair()
    assert alice_pair is not None and bob_pair is not None

    shared_secret = derive_shared_secret(alice_pair[0], bob_pair[1])
    assert shared_secret is not None

    plaintext = b"Secret message"
    encrypted = encrypt(plaintext, shared_secret)
    assert encrypted is not None

    assert len(encrypted) > 12, (
        "Encrypted data should include 12-byte nonce and auth tag"
    )

    # Flip a bit in the ciphertext to simulate tampering or corruption
    tampered = bytearray(encrypted)
    tampered[15] ^= 0x01
    tampered_bytes = bytes(tampered)

    # The decrypt function catches InvalidTag and returns None
    result = decrypt(tampered_bytes, shared_secret)
    assert result is None, "Tampered ciphertext should return None, not raise exception"


if __name__ == "__main__":
    test_asymmetric_keypair_generation()
    print("test_asymmetric_keypair_generation passed")
    test_ecdh_shared_secret_derivation()
    print("test_ecdh_shared_secret_derivation passed")
    test_encrypt_decrypt_roundtrip()
    print("test_encrypt_decrypt_roundtrip passed")
    test_tampered_ciphertext_returns_none()
    print("test_tampered_ciphertext_returns_none passed")
    print("All crypto tests passed!")
