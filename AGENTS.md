# Repository Architecture & AI Boundaries

This is a hybrid mobile application (iOS/Android) for peer-to-peer Bluetooth chatting. The codebase strictly separates the Native Shell from the Python Brain.

**Do NOT mix these responsibilities.**

## 1. The Python Brain (~20%)

- **Role:** Core logic, cryptography (AES-GCM/ECDH), multi-hop mesh routing, and SQLite database management.
- **Location:** `/python_core/`
- **Constraint:** Pure Python only. Do not import native mobile libraries here. Do not write UI code here.

## 2. The Android Shell (Kotlin)

- **Role:** User Interface (Jetpack Compose), background execution, and OS-level `android.bluetooth` (BLE) management.
- **Bridge:** Uses Chaquopy to pass raw BLE payloads to `/python_core/`.
- **Location:** `/android_app/`

## 3. The iOS Shell (Swift)

- **Role:** User Interface (SwiftUI), background execution, and OS-level `CoreBluetooth` management.
- **Bridge:** Uses PythonKit and pre-compiled Python XCFramework to pass raw BLE payloads to `/python_core/`.
- **Location:** `/ios_app/`

## Communication Flow

1. Native shells handle all Bluetooth scanning, advertising, and MTU chunking.
2. Native shells pass raw byte-strings to the Python Brain.
3. Python Brain decrypts, routes, and updates the local SQLite database, then returns the processed data payload back to the Native shell for UI rendering or re-broadcasting.
