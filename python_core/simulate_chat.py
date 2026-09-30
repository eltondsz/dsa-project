#!/usr/bin/env python3
"""
NetChat Two-Node Chat Simulation
=================================
Runs Alice and Bob as isolated Python processes, each with a private
temp directory (separate DB, keypairs, device ID). Packets are passed
between them as hex strings over subprocess stdin/stdout.

Test covers both directions:
  Alice --> Bob: "Hey Bob! This is Alice. Can you hear me over the mesh? [satellite]"
  Bob --> Alice: "Alice, loud and clear! Bob here. Mesh routing confirmed [check]"
"""
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).parent

SEP   = "-" * 58
RESET = "\033[0m"
BOLD  = "\033[1m"
CYAN  = "\033[96m"
GREEN = "\033[92m"
YELLOW= "\033[93m"
MAG   = "\033[95m"
RED   = "\033[91m"
BLUE  = "\033[94m"


# ---------------------------------------------------------------------------
# Mini-script template executed inside each node process
# ---------------------------------------------------------------------------

NODE_SCRIPT = '''
import sys, os
sys.path.insert(0, {src_dir!r})

# Redirect core_logic / database BASE_DIR to isolated temp dir
import database
import core_logic
database.BASE_DIR = {tmp!r}
database._connection = None
core_logic.BASE_DIR = {tmp!r}
core_logic.DB_PATH           = os.path.join({tmp!r}, "database.db")
core_logic.KEYSTORE_PRIV_PATH = os.path.join({tmp!r}, "key.priv")
core_logic.KEYSTORE_PUB_PATH  = os.path.join({tmp!r}, "key.pub")
core_logic.DEVICE_ID_PATH     = os.path.join({tmp!r}, "device_id.txt")

action = {action!r}
args   = {args!r}

if action == "init":
    r = core_logic.init_database()
    assert r["code"] == 0, f"DB init failed: {{r}}"
    kp = core_logic.get_or_create_keypair()
    assert kp, "keypair failed"
    uuid_r = core_logic.get_device_id()
    hs_r   = core_logic.prepare_handshake_packet()
    assert hs_r["code"] == 0
    print(f"UUID={{uuid_r['device_id']}}")
    print(f"HS_HEX={{hs_r['packet']}}")

elif action == "recv_handshake":
    r = core_logic.process_incoming_ble(bytes.fromhex(args[0]))
    assert r["code"] == 0, f"Handshake processing failed: {{r}}"
    print(f"SENDER={{r.get('sender_id', 'ok')}}")

elif action == "send_msg":
    recipient_uuid, message = args[0], args[1]
    r = core_logic.prepare_outgoing_message(recipient_uuid, message)
    assert r["code"] == 0, f"prepare_outgoing_message failed: {{r}}"
    print(f"MSG_HEX={{r['packet']}}")

elif action == "recv_msg":
    r = core_logic.process_incoming_ble(bytes.fromhex(args[0]))
    assert r["code"] == 0, f"process_incoming_ble failed: {{r}}"
    print(f"DECRYPTED={{r.get('message', r.get('msg','?'))}}")
    print(f"SENDER_ID={{r.get('sender_id', '?')}}")
'''


def run_node(tmp: str, action: str, args: list[str]) -> dict[str, str]:
    """Execute a node action in an isolated subprocess, return parsed key=val dict."""
    script = NODE_SCRIPT.format(
        src_dir=str(HERE),
        tmp=tmp,
        action=action,
        args=args,
    )
    result = subprocess.run(
        [sys.executable, "-c", script],
        capture_output=True, text=True
    )
    if result.returncode != 0:
        print(f"{RED}Node process error:\n{result.stderr}{RESET}")
        sys.exit(1)
    output = {}
    for line in result.stdout.strip().splitlines():
        if "=" in line:
            k, _, v = line.partition("=")
            output[k.strip()] = v.strip()
    return output


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def banner(text: str, color: str = BOLD) -> None:
    print(f"\n{color}{SEP}")
    print(f"  {text}")
    print(f"{SEP}{RESET}")


def step(n: int, desc: str) -> None:
    print(f"\n{YELLOW}>> Step {n}: {desc}{RESET}")


def log(node: str, msg: str, color: str = RESET) -> None:
    tag = f"{CYAN}[Alice]{RESET}" if node == "Alice" else f"{GREEN}[Bob  ]{RESET}"
    print(f"  {tag} {color}{msg}{RESET}")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main() -> None:
    banner("NetChat  |  Two-Node Bidirectional Chat Simulation", BOLD + BLUE)

    alice_dir = tempfile.mkdtemp(prefix="netchat_alice_")
    bob_dir   = tempfile.mkdtemp(prefix="netchat_bob_")

    try:
        # ── Step 1: Init both nodes ─────────────────────────────────────────
        step(1, "Initialising nodes, generating keypairs & handshake packets")

        alice_info = run_node(alice_dir, "init", [])
        log("Alice", f"UUID: {alice_info['UUID']}")
        log("Alice", f"Handshake: {alice_info['HS_HEX'][:40]}... ({len(alice_info['HS_HEX'])//2} bytes)")

        bob_info = run_node(bob_dir, "init", [])
        log("Bob",   f"UUID: {bob_info['UUID']}")
        log("Bob",   f"Handshake: {bob_info['HS_HEX'][:40]}... ({len(bob_info['HS_HEX'])//2} bytes)")

        alice_uuid = alice_info["UUID"]
        bob_uuid   = bob_info["UUID"]
        alice_hs   = alice_info["HS_HEX"]
        bob_hs     = bob_info["HS_HEX"]

        # ── Step 2: Exchange handshakes ─────────────────────────────────────
        step(2, "Exchanging HANDSHAKE packets (BLE Advertisement simulation)")

        banner("Alice -> Bob : HANDSHAKE (Type 0x03)", MAG)
        res = run_node(bob_dir, "recv_handshake", [alice_hs])
        log("Bob", f"Processed Alice handshake | sender_id = {res.get('SENDER', '?')} [OK]", GREEN)

        banner("Bob -> Alice : HANDSHAKE (Type 0x03)", MAG)
        res = run_node(alice_dir, "recv_handshake", [bob_hs])
        log("Alice", f"Processed Bob handshake | sender_id = {res.get('SENDER', '?')} [OK]", CYAN)

        # ── Step 3: Alice sends to Bob ──────────────────────────────────────
        msg_a2b = "Hey Bob! This is Alice. Can you hear me over the mesh?"
        step(3, f"Alice --> Bob : Encrypting & sending message")
        banner(f'Alice says: "{msg_a2b}"', CYAN)

        res = run_node(alice_dir, "send_msg", [bob_uuid, msg_a2b])
        pkt_a2b = res["MSG_HEX"]
        log("Alice", f"AES-GCM encrypted packet: {pkt_a2b[:40]}... ({len(pkt_a2b)//2} bytes)", CYAN)

        res = run_node(bob_dir, "recv_msg", [pkt_a2b])
        decrypted_at_bob = res.get("DECRYPTED", "")
        log("Bob", f'Decrypted message: "{decrypted_at_bob}"', GREEN + BOLD)

        if decrypted_at_bob == msg_a2b:
            log("Bob", "[PASS]  Message matches original exactly!", GREEN + BOLD)
        else:
            log("Bob", f"[FAIL]  Mismatch! Got: {decrypted_at_bob!r}", RED + BOLD)
            sys.exit(1)

        # ── Step 4: Bob replies to Alice ────────────────────────────────────
        msg_b2a = "Alice, loud and clear! Bob here. Mesh routing confirmed."
        step(4, f"Bob --> Alice : Encrypting & sending reply")
        banner(f'Bob replies: "{msg_b2a}"', GREEN)

        res = run_node(bob_dir, "send_msg", [alice_uuid, msg_b2a])
        pkt_b2a = res["MSG_HEX"]
        log("Bob", f"AES-GCM encrypted packet: {pkt_b2a[:40]}... ({len(pkt_b2a)//2} bytes)", GREEN)

        res = run_node(alice_dir, "recv_msg", [pkt_b2a])
        decrypted_at_alice = res.get("DECRYPTED", "")
        log("Alice", f'Decrypted reply: "{decrypted_at_alice}"', CYAN + BOLD)

        if decrypted_at_alice == msg_b2a:
            log("Alice", "[PASS]  Reply matches original exactly!", CYAN + BOLD)
        else:
            log("Alice", f"[FAIL]  Mismatch! Got: {decrypted_at_alice!r}", RED + BOLD)
            sys.exit(1)

        # ── Done ────────────────────────────────────────────────────────────
        banner("SIMULATION COMPLETE  |  Both directions PASSED!", BOLD + GREEN)
        print(f"\n  {GREEN}Alice --> Bob{RESET}  : \"{msg_a2b}\"")
        print(f"  {GREEN}Bob --> Alice{RESET}  : \"{msg_b2a}\"")
        print(f"\n  {BOLD}Encryption: X25519 ECDH key exchange + AES-256-GCM{RESET}")
        print(f"  {BOLD}Transport : Simulated BLE packet bytes (Type 0x01 MESSAGE){RESET}")
        print()

    finally:
        shutil.rmtree(alice_dir, ignore_errors=True)
        shutil.rmtree(bob_dir,   ignore_errors=True)


if __name__ == "__main__":
    main()
