# Design System & UI Specifications (Figma)

This document contains the visual design tokens, layout hierarchy, and Jetpack Compose component specifications extracted directly from the official Figma design file:
- **Figma Source:** [DSA UI on Figma](https://www.figma.com/design/eIg32SvBYdRBKwxYUNytcM/DSA-UI?node-id=0-1)
- **App Name:** NetChat (Offline P2P BLE Mesh Messenger)

---

## 1. Color Palette & Design Tokens

```kotlin
// Compose Color Palette Definition
val BackgroundDark = Color(0xFF090D10)     // Main Screen Background
val SurfaceDark = Color(0xFF171D22)        // Cards, Incoming Bubbles, Inputs
val SurfaceNavbar = Color(0xFF0D1216)      // Bottom Navigation Bar
val PrimaryBlue = Color(0xFF1687FF)        // Outgoing Bubbles, CTA Buttons, Highlights
val TextPrimary = Color(0xFFF4F6F8)        // Primary text, headings, incoming message text
val TextSecondary = Color(0xFF9AA4AE)      // Timestamps, subtitles, placeholders, inactive tabs
val TextOnAccent = Color(0xFFDCEEFF)       // Timestamps inside outgoing blue bubbles
val AvatarBg = Color(0xFF3A444D)           // Avatar background for initials
val DividerColor = Color(0xFF26292C)       // Thin divider lines
val TrackColor = Color(0xFF2D353D)         // Progress / Storage meter track background
val ImageContainerBg = Color(0xFF29383E)   // Image preview placeholder container
```

---

## 2. Typography & Component Metrics

- **Typography Family:** `Inter` / System Sans-Serif
  - **Screen Titles:** `27px` - `32px`, SemiBold (`FontWeight.W600`)
  - **Section Headers & User Names:** `15px`, Medium (`FontWeight.W500`)
  - **Body & Chat Text:** `13px` - `14px`, Normal (`FontWeight.W400`)
  - **Timestamps & Badges:** `9px` - `10px`, Regular / SemiBold
- **Corner Radii:**
  - Chat message bubbles: `11.dp` - `15.dp`
  - Input fields & list cards: `12.dp` - `14.dp`
  - Action buttons: `12.dp` - `14.dp`
  - Unread badge pills: `9.dp`
  - Circular buttons & avatars: `50%` / `21.dp` - `50.dp`

---

## 3. Screen Hierarchy & UI Breakdown

### **1. 01 Splash (`01 Splash`)**
- Background: `BackgroundDark` (`#090D10`)
- Title: *"NetChat"* (`32px`, `TextPrimary`)
- Tagline: *"Messages beyond networks."* (`15px`, `TextSecondary`)
- Footer: *"Peer-to-peer. No internet. Just people."* (`14px`, `TextPrimary`)

### **2. 02 Onboarding (`02 Onboarding`)**
- Visual: Central Bluetooth Mesh graphic illustration
- Heading: *"Connect Around You"* (`22px`, SemiBold)
- Subtext: *"Uses Bluetooth to find and connect with people nearby."* (`15px`, `TextSecondary`)
- Action Button: **Next** (Background: `#FFFFFF`, Text: `#101417`, radius `12.dp`)
- Secondary Link: **Skip** (`TextPrimary`)

### **3. 03 Permissions (`03 Permissions`)**
- Visual: Bluetooth icon inside `SurfaceDark` rounded card (`16.dp`)
- Heading: *"Enable Bluetooth"* (`22px`, SemiBold)
- Subtext: *"We need Bluetooth access to find and connect with nearby devices."* (`15px`, `TextSecondary`)
- Action Button: **Allow Bluetooth** (Background: `#FFFFFF`, Text: `#101417`)
- Secondary: **Not now** / **Skip**

### **4. 04 Home Chats (`04 Home Chats`)**
- **Top App Bar:**
  - Title: *"Chats"* (`28px`, SemiBold)
  - Action: `+` button (Create chat / group)
- **Search Bar:**
  - Container: `SurfaceDark` (`radius 13.dp`)
  - Placeholder: *"Search conversations"* (`TextSecondary`) with search icon (`⌕`)
- **Chat Item List:**
  - Avatar: Circular (`AvatarBg`, radius `21.dp`) with initial letter
  - Name: `TextPrimary` (`15px`, Medium)
  - Last Message: `TextSecondary` (`12px`) — supports text, `🎙 Voice message`, `📷 Image`
  - Timestamp: `TextSecondary` (`10px` — e.g. `2m`, `12m`, `54m`, `2h`, `5h`)
  - Unread Badge: `PrimaryBlue` pill (`radius 9.dp`) with white text
  - Separator: Divider line (`DividerColor`)
- **Bottom Navigation Bar (`SurfaceNavbar` `#0D1216`):**
  - Items: **Chats** (Selected), **Nearby**, **Groups**, **Settings**

### **5. 05 Direct Chat (`05 Chat`)**
- **Header:** Back button, Contact Avatar + Initial, Contact Name (*"Alex"*), Connection State (*"Connected"* `TextSecondary`)
- **Timeline:** Date header pill (*"Today"* in `TextSecondary`)
- **Incoming Bubble:**
  - Container: `SurfaceDark` (`radius 11.dp`)
  - Content: `TextPrimary` (`13px`)
  - Timestamp: `TextSecondary` (`9px`)
- **Outgoing Bubble:**
  - Container: `PrimaryBlue` (`radius 11.dp`)
  - Content: `#FFFFFF` / `TextPrimary` (`13px`)
  - Timestamp: `TextOnAccent` (`9px`) + double tick delivery indicator
- **Rich Message Types:**
  - Image message: `SurfaceDark` card with image preview thumbnail (`ImageContainerBg`, radius `10.dp`)
  - Voice message: `SurfaceDark` bar with play button `▶` and duration `0:08`
- **Input Bottom Bar:**
  - Text Field: `SurfaceDark` (`radius 14.dp`) with placeholder *"Type a message..."*
  - Send Button: Circular `PrimaryBlue` button (`radius 22.dp`) with arrow `➤`

### **6. 06 Nearby Devices (`06 Nearby Devices`)**
- **Header:** Title *"Nearby"*, Subheading *"Devices Nearby"*
- **Discovery Radar Animation / Graphic**
- **Peer List Items:**
  - Name: e.g. *"Alex’s iPhone"*, *"Sam’s Android"*, *"Maya’s Phone"*, *"Unknown Device"*
  - Signal / Distance: `PrimaryBlue` indicator icon + estimated distance (`~ 5 m`, `~ 12 m`, `~ 18 m`, `~ 25 m`)

### **7. 08 Create Group (`08 Create Group`)**
- **Header:** Title *"Create Group"*, Action link *"Create"* (`PrimaryBlue`)
- **Group Photo:** Circular camera/avatar placeholder (`SurfaceDark`, radius `50.dp`)
- **Group Name Field:** `SurfaceDark` input (`radius 12.dp`)
- **Section:** *"Add Members (Nearby)"*
- **Peer Selection Checklist:** Peer list with checkable square/circle checkboxes (`PrimaryBlue` filled with `✓`)

### **8. 09 Group Chat (`09 Group Chat`)**
- **Header:** Group name (*"Group: Hike Crew"*), Subtitle (*"4 members"*)
- **Sender Identification:** Mini member avatar pills (`A`, `GR`, `MI`, `S` in `AvatarBg`) beside incoming messages
- **Message List:** Supports text bubbles, audio player waveforms (`▶ ▁▃▅▇▅▃▆▂▅▃ 0:08`), and photo cards

### **9. 10 Settings (`10 Settings`)**
- **Items:**
  - **Bluetooth:** Value *"On"* (`TextSecondary`)
  - **My Profile:** Value *"Name"* (`TextSecondary`)
  - **Discovery:** Value *"Visible to nearby"* (`TextSecondary`)
  - **Notifications**
  - **Storage**
  - **About**
  - **Panic Wipe Trigger Button** (Emergency data wipe)

### **10. Storage & Diagnostics (`XXXXX`)**
- **Header:** Title *"Storage"*
- **Storage Gauge:** *"1.4 GB of 5.0 GB"* (`TrackColor` bar with `PrimaryBlue` progress)
- **Item Breakdown:**
  - Messages (`842 MB`)
  - Images (`392 MB`)
  - Voice messages (`121 MB`)
  - Other (`45 MB`)
- **Action Buttons:** **Clear Cache** (`SurfaceDark`), **Manage Media** (`SurfaceDark`)
- **Footnote:** *"Messages are kept locally on this device."*

### **11. 10 My-Profile (`10 My-profile`)**
- **Profile Header:** Large circular avatar with initial (*"E"*, `AvatarBg`, radius `50.dp`), Name (*"Elton"*), Status (*"Visible to nearby"*)
- **Information Rows (`SurfaceDark`, radius `13.dp`):**
  - Name: *"Elton"*
  - Device: *"This Device / This iPhone"*
  - Discovery: *"Visible"*
  - Bio: *"Share your name with nearby people."*
- **Privacy Note:** *"Your profile is shared only when discovery is enabled."*
- **Primary CTA:** **Edit Profile** button (`PrimaryBlue`)

