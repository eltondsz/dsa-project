# Product Requirements Document (PRD) — Bridgify

## 1. Overview

**Bridgify** is an offline-first messenger that utilizes a peer-to-peer Bluetooth Low Energy (BLE) mesh network. It delivers encrypted text, voice notes, and lightweight media without relying on internet or cellular networks, offering users a familiar, modern chat experience.

## 2. MVP Scope (v1.0)

### 2.1 Core Identity & Onboarding

- **Zero-Account Setup:** Users do not need to register with a phone number, email, or password.
- **Custom Display Names:** Users set a local nickname and avatar, which are saved purely on the device.
- **Offline-First Interface:** The app launches directly into the chat interface, requesting only the necessary OS permissions for Bluetooth scanning and advertising.

### 2.2 Messaging & Media

- **Direct & Group Chats:**
    - Secure 1-to-1 direct messaging (DMs).
    - Standard group chats featuring group names, display photos, and basic admin controls.
- **Modern UI (WhatsApp-Style):**
    - Inline replies.
    - Message reactions (emojis).
    - Delivery status indicators (Sent, Delivered, Read).
- **Lightweight Media Transfer:** Support for small, compressed photos, audio/voice notes, and small document files.

### 2.3 Mesh Networking & Architecture (Python Engine)

- **Multi-Hop Routing:** Background packet relaying that utilizes intermediate devices to extend range (supporting up to 7 hops).
- **Store-and-Forward Caching:** Encrypted messages for out-of-range users are cached locally in a SQLite database and automatically delivered when the recipient reconnects to the mesh.
- **Nearby Peer Discovery:** Continuous background scanning to detect active Bridgify users in physical proximity.

### 2.4 Privacy, Security & Diagnostics

- **Mandatory End-to-End Encryption (E2EE):** All direct messages are fully encrypted (AES-GCM or Noise Protocol Framework). Intermediary relay devices cannot read the payloads.
- **Ephemeral Messaging:** Users can set message expiration timers (e.g., 1 hour, 24 hours, Never) to auto-delete local chat history.
- **Emergency Wipe (Panic Mode):** A fast-action trigger (e.g., triple-tapping the app logo or a settings button) that instantly drops all SQLite tables and erases local encryption keys.
- **Mesh Diagnostic Dashboard:** A dedicated UI screen showing network health, including hop counts, active relay paths, and direct connection signal strengths.

---

## 3. Explicitly Excluded from MVP (v1.0)

To ensure technical feasibility and reliable Bluetooth performance, the following features are strictly excluded from the initial release:

- **Large Media & Video Transfers:** Excluded due to BLE bandwidth limits and the complexity of chunking/resuming large files.
- **IRC-Style Terminal Commands:** Excluded to maintain a mainstream, user-friendly UI.
- **Geo-Drop & Location-Locked Channels:** Excluded to avoid GPS dependencies and complex global routing in an offline-first app.
- **Advanced Chat Management:** Features like pinned chats, archived threads, and global chat search are deferred to future versions.
