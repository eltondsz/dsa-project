const fs = require("fs");
const path = require("path");

const outDir = __dirname;

function esc(value) {
  return String(value)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

function svg(width, height, title, body) {
  return `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}" role="img" aria-labelledby="title desc">
  <title id="title">${esc(title)}</title>
  <desc id="desc">Generated from PRD.md, ARCHITECTURE.md, CONTRACTS.md, and current source files.</desc>
  <defs>
    <marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse">
      <path d="M 0 0 L 10 5 L 0 10 z" fill="#334155"/>
    </marker>
    <marker id="openArrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="8" markerHeight="8" orient="auto-start-reverse">
      <path d="M 1 1 L 9 5 L 1 9" fill="none" stroke="#334155" stroke-width="1.8"/>
    </marker>
  </defs>
  <rect width="100%" height="100%" fill="#ffffff"/>
  <text x="32" y="42" font-family="Segoe UI, Arial, sans-serif" font-size="24" font-weight="700" fill="#0f172a">${esc(title)}</text>
  ${body}
</svg>
`;
}

function text(x, y, value, size = 14, weight = 400, fill = "#0f172a", anchor = "start") {
  return `<text x="${x}" y="${y}" font-family="Segoe UI, Arial, sans-serif" font-size="${size}" font-weight="${weight}" fill="${fill}" text-anchor="${anchor}">${esc(value)}</text>`;
}

function lines(x, y, values, size = 13, fill = "#334155") {
  return values.map((value, i) => text(x, y + i * (size + 6), value, size, 400, fill)).join("\n");
}

function box(x, y, w, h, title, items = [], fill = "#ffffff", stroke = "#64748b") {
  const headH = 34;
  return `
  <rect x="${x}" y="${y}" width="${w}" height="${h}" rx="4" fill="${fill}" stroke="${stroke}" stroke-width="1.5"/>
  <rect x="${x}" y="${y}" width="${w}" height="${headH}" rx="4" fill="#f1f5f9" stroke="${stroke}" stroke-width="1.5"/>
  <path d="M ${x} ${y + headH} H ${x + w}" stroke="${stroke}" stroke-width="1.5"/>
  ${text(x + w / 2, y + 23, title, 14, 700, "#0f172a", "middle")}
  ${lines(x + 14, y + 58, items)}
  `;
}

function classBox(x, y, w, title, attrs, methods) {
  const h = 44 + attrs.length * 20 + methods.length * 20 + 30;
  const attrY = y + 52;
  const methodY = attrY + attrs.length * 20 + 12;
  return `
  <rect x="${x}" y="${y}" width="${w}" height="${h}" fill="#ffffff" stroke="#334155" stroke-width="1.5"/>
  <path d="M ${x} ${y + 34} H ${x + w}" stroke="#475569" stroke-width="1.2"/>
  <path d="M ${x} ${methodY - 18} H ${x + w}" stroke="#475569" stroke-width="1.2"/>
  ${text(x + w / 2, y + 23, title, 14, 700, "#0f172a", "middle")}
  ${lines(x + 12, attrY, attrs, 12, "#334155")}
  ${lines(x + 12, methodY, methods, 12, "#334155")}
  `;
}

function arrow(x1, y1, x2, y2, label = "", dashed = false) {
  const dash = dashed ? ` stroke-dasharray="6 5"` : "";
  const lx = (x1 + x2) / 2;
  const ly = (y1 + y2) / 2 - 8;
  return `
  <line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="#334155" stroke-width="1.6" marker-end="url(#arrow)"${dash}/>
  ${label ? text(lx, ly, label, 12, 600, "#475569", "middle") : ""}
  `;
}

function pathArrow(points, label = "", dashed = false) {
  const dash = dashed ? ` stroke-dasharray="6 5"` : "";
  const d = points.map((p, i) => `${i ? "L" : "M"} ${p[0]} ${p[1]}`).join(" ");
  const mid = points[Math.floor(points.length / 2)];
  return `
  <path d="${d}" fill="none" stroke="#334155" stroke-width="1.6" marker-end="url(#arrow)"${dash}/>
  ${label ? text(mid[0], mid[1] - 8, label, 12, 600, "#475569", "middle") : ""}
  `;
}

function componentBox(x, y, w, h, title, items = []) {
  return `
  <rect x="${x}" y="${y}" width="${w}" height="${h}" fill="#ffffff" stroke="#334155" stroke-width="1.5"/>
  <rect x="${x + w - 34}" y="${y + 16}" width="22" height="14" fill="#ffffff" stroke="#334155" stroke-width="1.3"/>
  <rect x="${x + w - 34}" y="${y + 36}" width="22" height="14" fill="#ffffff" stroke="#334155" stroke-width="1.3"/>
  ${text(x + 18, y + 28, `<<component>>`, 12, 400, "#475569")}
  ${text(x + w / 2, y + 50, title, 15, 700, "#0f172a", "middle")}
  ${lines(x + 18, y + 82, items, 13, "#334155")}
  `;
}

function activity(x, y, w, h, title, items = []) {
  return `
  <rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${h / 2}" fill="#ffffff" stroke="#334155" stroke-width="1.5"/>
  ${text(x + w / 2, y + 28, title, 14, 700, "#0f172a", "middle")}
  ${items.length ? lines(x + 22, y + 54, items, 12, "#334155") : ""}
  `;
}

function actor(x, y, label) {
  return `
  <circle cx="${x}" cy="${y}" r="14" fill="none" stroke="#0f172a" stroke-width="1.5"/>
  <line x1="${x}" y1="${y + 14}" x2="${x}" y2="${y + 58}" stroke="#0f172a" stroke-width="1.5"/>
  <line x1="${x - 28}" y1="${y + 30}" x2="${x + 28}" y2="${y + 30}" stroke="#0f172a" stroke-width="1.5"/>
  <line x1="${x}" y1="${y + 58}" x2="${x - 24}" y2="${y + 92}" stroke="#0f172a" stroke-width="1.5"/>
  <line x1="${x}" y1="${y + 58}" x2="${x + 24}" y2="${y + 92}" stroke="#0f172a" stroke-width="1.5"/>
  ${text(x, y + 118, label, 13, 700, "#0f172a", "middle")}
  `;
}

function ellipse(x, y, w, h, label) {
  return `
  <ellipse cx="${x}" cy="${y}" rx="${w / 2}" ry="${h / 2}" fill="#ffffff" stroke="#64748b" stroke-width="1.4"/>
  ${text(x, y + 5, label, 13, 600, "#0f172a", "middle")}
  `;
}

function relation(x1, y1, x2, y2) {
  return `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="#64748b" stroke-width="1.3"/>`;
}

function lifeline(x, label) {
  return `
  <rect x="${x - 72}" y="78" width="144" height="44" fill="#ffffff" stroke="#334155" stroke-width="1.4"/>
  ${text(x, 105, label, 13, 700, "#0f172a", "middle")}
  <line x1="${x}" y1="122" x2="${x}" y2="650" stroke="#94a3b8" stroke-width="1.2" stroke-dasharray="6 5"/>
  `;
}

function activation(x, y, h) {
  return `<rect x="${x - 5}" y="${y}" width="10" height="${h}" fill="#ffffff" stroke="#334155" stroke-width="1.2"/>`;
}

function msg(x1, y, x2, label, dashed = false) {
  const dash = dashed ? ` stroke-dasharray="6 5"` : "";
  return `
  <line x1="${x1}" y1="${y}" x2="${x2}" y2="${y}" stroke="#334155" stroke-width="1.5" marker-end="url(#arrow)"${dash}/>
  ${text((x1 + x2) / 2, y - 9, label, 12, 600, "#334155", "middle")}
  `;
}

function file(name, content) {
  fs.writeFileSync(path.join(outDir, name), content, "utf8");
}

file("class_diagram.svg", svg(1220, 900, "Class Diagram - Offline BLE Mesh Chat", `
  ${classBox(40, 90, 250, "MainActivity", ["+ onCreate(savedInstanceState)"], ["+ setContent(ChatApp)"])}
  ${classBox(40, 330, 250, "Compose UI", ["+ ChatApp()", "+ PermissionGate()", "+ requiredBlePermissions()"], ["+ request BLE permissions", "+ render ready state"])}
  ${classBox(330, 90, 270, "ChaquopyBridge", ["+ Kotlin callAttr()", "+ raw BLE byte arrays"], ["+ init_database()", "+ process_incoming_ble()", "+ prepare_outgoing_message()", "+ trigger_panic_wipe()"])}
  ${classBox(640, 90, 270, "core_logic.py", ["- _encryption_key: bytes?", "+ BLE header: hop/type/msg_id"], ["+ init_database()", "+ process_incoming_ble(raw)", "+ prepare_outgoing_message(to,msg)", "+ trigger_panic_wipe()"])}
  ${classBox(950, 90, 230, "schema.py", ["+ Column", "+ ForeignKey", "+ Table", "+ PEERS", "+ MESSAGES"], ["+ Table.create_sql()", "+ topological_drop_order()"])}
  ${classBox(640, 360, 270, "crypto.py", ["+ NONCE_NBYTES = 12", "+ AES-GCM key"], ["+ generate_key()", "+ encrypt(plaintext,key)", "+ decrypt(data,key)"])}
  ${classBox(950, 360, 230, "database.py", ["- _connection", "+ database.db"], ["+ get_db_connection()", "+ init_database()", "+ trigger_panic_wipe()", "+ close_db_connection()"])}
  ${classBox(330, 610, 270, "Peer", ["+ peer_id: TEXT", "+ display_name: TEXT", "+ public_key: BLOB", "+ last_seen: INTEGER"], [])}
  ${classBox(640, 610, 270, "Message", ["+ msg_id: TEXT", "+ sender_id: TEXT", "+ recipient_id: TEXT", "+ payload: TEXT", "+ timestamp: INTEGER", "+ status: INTEGER"], [])}
  ${arrow(290, 170, 330, 170, "calls")}
  ${arrow(600, 170, 640, 170, "bridges")}
  ${arrow(910, 170, 950, 170, "uses")}
  ${arrow(775, 320, 775, 360, "encrypts")}
  ${arrow(910, 430, 950, 430, "stores")}
  ${pathArrow([[1065, 360], [1065, 305], [1065, 305], [1065, 274]], "schemas")}
  ${arrow(165, 248, 165, 330, "uses")}
  ${pathArrow([[1065, 548], [1065, 690], [910, 690]], "persists")}
  ${relation(600, 690, 640, 690)}
  ${relation(600, 730, 640, 730)}
  ${text(620, 680, "sender", 12, 600, "#475569", "middle")}
  ${text(620, 750, "recipient", 12, 600, "#475569", "middle")}
  ${text(42, 868, "Note: Kotlin BLE scan/advertise services and MVVM ViewModels are required by the docs but not yet implemented in source.", 13, 600, "#475569")}
`));

file("component_diagram.svg", svg(1180, 720, "UML Component Diagram - Hybrid Architecture", `
  ${componentBox(60, 110, 280, 190, "Android Shell", ["Jetpack Compose UI", "Permission handling", "BLE lifecycle owner", "MTU chunking"])}
  ${componentBox(450, 110, 280, 190, "Chaquopy Bridge", ["Loads Python module", "Kotlin .callAttr()", "Passes bytes/dicts"])}
  ${componentBox(840, 110, 280, 190, "Python Brain", ["Core entry points", "Mesh route decisions", "Store-and-forward"])}
  ${componentBox(60, 410, 280, 130, "Android BLE APIs", ["android.bluetooth.le", "Scan / advertise"])}
  ${componentBox(450, 410, 280, 130, "Nearby Devices", ["Direct recipient", "Relay peer", "Group participant"])}
  ${componentBox(820, 410, 150, 130, "Crypto", ["AES-GCM", "Noise target"])}
  ${componentBox(990, 410, 150, 130, "SQLite", ["peers", "messages"])}
  ${arrow(340, 205, 450, 205, "raw bytes")}
  ${arrow(730, 205, 840, 205, "callAttr")}
  ${arrow(980, 300, 895, 410, "uses")}
  ${arrow(990, 300, 1065, 410, "persists")}
  ${arrow(200, 300, 200, 410, "controls")}
  ${arrow(340, 475, 450, 475, "BLE packets")}
  ${pathArrow([[840, 235], [760, 235], [760, 475], [730, 475]], "encrypted payloads", true)}
  ${text(60, 665, "Boundary rule: Android owns UI/BLE hardware; Python owns cryptography, routing, and SQLite.", 13, 600, "#475569")}
`));

file("sequence_message_flow.svg", svg(1200, 720, "UML Sequence Diagram - Send, Relay, Receive", `
  ${lifeline(120, "User")}
  ${lifeline(300, "Compose UI")}
  ${lifeline(480, "Android BLE")}
  ${lifeline(660, "Chaquopy")}
  ${lifeline(840, "Python Brain")}
  ${lifeline(1020, "Remote Peer")}
  ${activation(300, 155, 455)}
  ${activation(660, 205, 355)}
  ${activation(840, 255, 315)}
  ${activation(480, 355, 115)}
  ${activation(1020, 410, 60)}
  ${msg(120, 160, 300, "compose message")}
  ${msg(300, 210, 660, "prepare_outgoing_message(to, text)")}
  ${msg(660, 260, 840, "encrypt + attach 18-byte header")}
  ${msg(840, 310, 660, "return raw bytes", true)}
  ${msg(660, 360, 480, "encrypted BLE payload", true)}
  ${msg(480, 410, 1020, "advertise / relay up to 7 hops")}
  ${msg(1020, 460, 660, "incoming raw_payload")}
  ${msg(660, 510, 840, "process_incoming_ble(raw)")}
  ${msg(840, 560, 300, "JSON: msg_id, sender, message, status", true)}
  ${msg(300, 610, 120, "render delivered/read status", true)}
`));

file("activity_panic_wipe.svg", svg(920, 680, "UML Activity Diagram - Panic Wipe", `
  <circle cx="460" cy="95" r="14" fill="#0f172a"/>
  ${activity(320, 140, 280, 70, "User triggers panic mode", ["Triple tap logo or settings button"])}
  ${activity(320, 245, 280, 70, "Kotlin calls bridge", ["trigger_panic_wipe()"])}
  ${activity(320, 350, 280, 70, "Python database wipe", ["Drop messages then peers"])}
  ${activity(320, 455, 280, 70, "Clear encryption keys", ["Set active key to null"])}
  ${activity(320, 560, 280, 58, "Return result", ["UI confirms local data removed"])}
  <circle cx="460" cy="642" r="16" fill="none" stroke="#0f172a" stroke-width="2"/>
  <circle cx="460" cy="642" r="9" fill="#0f172a"/>
  ${arrow(460, 109, 460, 140)}
  ${arrow(460, 210, 460, 245)}
  ${arrow(460, 315, 460, 350)}
  ${arrow(460, 420, 460, 455)}
  ${arrow(460, 525, 460, 560)}
  ${arrow(460, 618, 460, 625)}
`));

file("use_case_diagram.svg", svg(1220, 820, "Use Case Diagram - Offline Mesh Messenger", `
  ${actor(90, 130, "Local User")}
  ${actor(90, 525, "Group Admin")}
  ${actor(1110, 210, "Nearby Peer")}
  ${actor(1110, 525, "Android OS")}
  <rect x="230" y="85" width="760" height="650" rx="12" fill="#ffffff" stroke="#94a3b8" stroke-width="1.5"/>
  ${text(610, 118, "Offline BLE Mesh Chat App", 16, 700, "#0f172a", "middle")}
  ${ellipse(420, 180, 210, 54, "Set nickname/avatar")}
  ${ellipse(420, 270, 210, 54, "Grant BLE permissions")}
  ${ellipse(420, 360, 210, 54, "Send direct message")}
  ${ellipse(420, 450, 210, 54, "Send group message")}
  ${ellipse(420, 540, 210, 54, "Set expiry timer")}
  ${ellipse(420, 630, 210, 54, "Trigger panic wipe")}
  ${ellipse(770, 180, 240, 54, "Discover nearby peers")}
  ${ellipse(770, 270, 240, 54, "Encrypt/decrypt message")}
  ${ellipse(770, 360, 240, 54, "Relay multi-hop packet")}
  ${ellipse(770, 450, 240, 54, "Store-and-forward cache")}
  ${ellipse(770, 540, 240, 54, "View mesh diagnostics")}
  ${ellipse(770, 630, 240, 54, "Manage group basics")}
  ${relation(118, 222, 315, 180)}
  ${relation(118, 222, 315, 270)}
  ${relation(118, 222, 315, 360)}
  ${relation(118, 222, 315, 450)}
  ${relation(118, 222, 315, 540)}
  ${relation(118, 222, 315, 630)}
  ${relation(118, 555, 315, 450)}
  ${relation(118, 555, 650, 630)}
  ${relation(1082, 302, 890, 180)}
  ${relation(1082, 302, 890, 360)}
  ${relation(1082, 302, 890, 450)}
  ${relation(1082, 555, 890, 270)}
  ${relation(1082, 555, 890, 180)}
`));

file("user_roles.svg", svg(1020, 720, "User Roles", `
  ${box(60, 110, 260, 470, "End User", ["Creates local identity", "Grants Bluetooth permissions", "Sends and receives DMs", "Joins group chats", "Sets message expiry", "Uses panic wipe"], "#ffffff")}
  ${box(380, 110, 260, 470, "Group Admin", ["Creates or names group", "Sets group display photo", "Manages basic admin controls", "Participates as normal user", "Keeps group metadata local"], "#ffffff")}
  ${box(700, 110, 260, 470, "Relay Peer", ["Discovers nearby devices", "Forwards encrypted packets", "Caches undelivered payloads", "Cannot read message contents", "Contributes hop path health"], "#ffffff")}
  ${arrow(320, 345, 380, 345, "can become")}
  ${arrow(640, 345, 700, 345, "also acts as")}
  ${text(60, 645, "All app users may act as relay peers because mesh forwarding is part of the offline-first design.", 13, 600, "#475569")}
`));

file("stakeholders.svg", svg(1120, 760, "Stakeholder Diagram", `
  ${box(430, 300, 260, 120, "Offline BLE Mesh Chat", ["Encrypted messenger", "No internet/cellular dependency", "Android + Python hybrid"], "#ffffff", "#475569")}
  ${box(60, 90, 260, 130, "Primary Users", ["People needing offline messaging", "Group chat participants", "Users in low-connectivity areas"], "#ffffff")}
  ${box(430, 90, 260, 130, "Developers", ["Android UI/BLE implementation", "Python crypto/routing/database", "Testing and packaging"], "#ffffff")}
  ${box(800, 90, 260, 130, "Project Evaluators", ["College/project reviewers", "Architecture readers", "Security/privacy reviewers"], "#ffffff")}
  ${box(60, 530, 260, 130, "Android Platform", ["Runtime permissions", "BLE hardware limits", "Background execution rules"], "#ffffff")}
  ${box(430, 530, 260, 130, "Nearby Community", ["Relay devices", "Message recipients", "Group members"], "#ffffff")}
  ${box(800, 530, 260, 130, "Maintenance Owners", ["Dependency updates", "Schema changes", "Security fixes"], "#ffffff")}
  ${arrow(320, 155, 430, 330, "needs")}
  ${arrow(560, 220, 560, 300, "builds")}
  ${arrow(800, 155, 690, 330, "assesses")}
  ${arrow(320, 595, 430, 390, "constrains")}
  ${arrow(560, 530, 560, 420, "uses")}
  ${arrow(800, 595, 690, 390, "sustains")}
`));

console.log("Generated SVG diagrams in " + outDir);
