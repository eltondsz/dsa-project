# Repository Architecture & AI Boundaries

This is a hybrid Android application for peer-to-peer Bluetooth chatting. The codebase strictly separates the native Android UI and hardware layers from the Python core logic.

**Do NOT mix these responsibilities.**

## 1. Tech Stack

- **Android Shell:** Kotlin, Jetpack Compose, `android.bluetooth`
- **Python Brain:** Python 3, SQLite3, `cryptography` (Noise Protocol / AES-GCM)
- **Bridge / Integration:** Chaquopy

### Version Matrix

To ensure strict cross-compatibility between the Android build system and the Python runtime, the AI agent must utilize the following versions:

- **Android SDK:** Min SDK 24, Target SDK 34
- **Android Gradle Plugin (AGP):** 8.2.+
- **Kotlin Compiler:** 1.9.+
- **Jetpack Compose:** BOM 2024.02.00+
- **Chaquopy Plugin:** 15.0 (Requires Python 3.11)
- **Python (Local & Core):** 3.11 (Managed via Poetry)

## 2. The Python Brain (~20%)

- **Role:** Core logic, cryptography, multi-hop mesh routing, and SQLite database management.
- **Location:** `/python_core/`
- **Constraint:** Pure Python only. Do not import native mobile libraries here. Do not write UI code here.

## 3. The Android Shell (Kotlin)

- **Role:** User Interface (Jetpack Compose), background execution, and OS-level Bluetooth Low Energy (BLE) management.
- **Bridge:** Uses Chaquopy to pass raw BLE payloads to `/python_core/`.
- **Location:** `/android_app/`

## 4. Communication Flow

1. The Android shell handles all Bluetooth scanning, advertising, and MTU chunking.
2. The Android shell passes raw byte-strings to the Python Brain via Chaquopy.
3. The Python Brain decrypts, routes, and updates the local SQLite database, then returns the processed data payload back to the Android shell for UI rendering or re-broadcasting.

## 5. Product Context & Documentation

- **Features & Scope:** Refer to [PRD.md](PRD.md) for the full list of MVP requirements, UI expectations, and project scope. Always verify feature specifications against `PRD.md` before generating new code or components.
- **System Design:** Refer to [ARCHITECTURE.md](ARCHITECTURE.md) for database schemas, BLE packet structures, and cryptography data flows.
- **API & Data Contracts:** Refer to [CONTRACTS.md](CONTRACTS.md) for Chaquopy function signatures, database schemas, and byte-level payload structures.
- **UI Design System:** Refer to [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md) for exact Figma color tokens, typography scales, layout metrics, and Jetpack Compose component specs.
