# Architecture Overview

## System Overview

The application operates on a hybrid architecture strictly separating the native Android hardware layer from the Python-based routing and cryptography engine. This design isolates complex networking logic from the UI to maintain an efficient, offline-first user experience.

## Component Breakdown

- **Android Shell:** Manages the offline-first Jetpack Compose UI, background execution, and OS-level `android.bluetooth` hardware interactions.
- **Python Brain:** Executes core logic, multi-hop mesh routing capable of supporting up to 7 hops, and SQLite3 store-and-forward caching.
- **Cryptography Engine:** Embedded within Python, it enforces mandatory End-to-End Encryption (E2EE) using the Noise Protocol Framework or AES-GCM.
- **Chaquopy Bridge:** Facilitates direct data transfer, passing raw BLE byte-strings from Kotlin down to Python for decryption and routing.

## Data Communication Flow

1. **Transmission:** The user initiates a direct or group message via the native UI.
2. **Processing:** Chaquopy passes the payload to Python, which encrypts the data and calculates the mesh path.
3. **Broadcast:** Python returns the encrypted byte-string to Kotlin for MTU chunking and `android.bluetooth` advertising.
4. **Relay:** Intermediary devices receive packets, passing them to their local Python Brains to cache in SQLite3 if the recipient is out of range.
5. **Emergency Wipe:** Triggering Panic Mode immediately commands Python to drop all SQLite tables and erase encryption keys.

## Data Schema & Security

- **Ephemeral Storage:** SQLite3 securely stores custom display names, avatars, and messages until user-configured expiration timers trigger auto-deletion.
- **Packet Handling:** To respect BLE bandwidth limitations, large video media is excluded, and small payloads (like text and voice notes) are efficiently chunked at the hardware layer.
- **Network Proximity:** The system relies on continuous background scanning to facilitate peer network discovery and active proximity alerts.
-

## Development Constraints

- **OS-Agnostic Environment:** The local codebase must remain portable and fully compatible across Windows, macOS, and Linux.
- **Pathing:** All file operations (especially SQLite database initialization via Chaquopy) must use standard, OS-safe Python pathing (e.g., `os.path.join`, `os.path.abspath`) rather than hardcoded OS-specific slashes.
- **Dependency Management:** Python dependencies are managed strictly via Poetry to ensure reproducible virtual environments across different operating systems.
