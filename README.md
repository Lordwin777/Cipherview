# CipherView

**CipherView** is a high-assurance, privacy-centric Android application designed for secure, ephemeral, and local-first document sharing. It eliminates the need for cloud intermediaries, ensuring that sensitive data remains exclusively between the sender and the recipient.

---

## 🛡️ The Ideology: "Privacy Through Independence"

In an era of ubiquitous cloud storage and permanent digital footprints, **CipherView** operates on the principle of **Controlled Ephemerality**. We believe that sharing a sensitive document shouldn't mean losing control over its lifecycle.

### The Core Pillars:
1.  **Zero-Cloud Dependency**: No servers, no logs, and no internet required. Data moves directly between devices over local mesh networks.
2.  **Cryptographic Sovereignty**: Users own their keys. All encryption is derived from user-generated access codes, ensuring that even if a file is intercepted, it remains a mathematical "black box."
3.  **Consumption Control**: Protecting a document isn't just about encryption; it's about controlling how it's viewed. CipherView enforces restrictions on *when* and *how many times* a document can be opened.
4.  **Capture Resistance**: Using hardware-level protections and digital watermarking to deter data leakage even during active viewing.

---

## ✨ Key Features

-   **P2P Local Transfer**: Share files via high-speed local Wi-Fi TCP sockets or Bluetooth RFCOMM.
-   **Authenticated Encryption**: Uses AES-256-GCM to ensure both confidentiality and integrity of shared documents.
-   **Self-Contained Packages**: Documents are bundled into proprietary `.cview` packages containing encrypted data and tamper-proof metadata.
-   **Smart Restrictions**:
    *   **Expiration**: Set a "self-destruct" time for documents.
    *   **View Limits**: Limit the number of times a recipient can open a file.
-   **Secure Viewer**:
    *   **Screen Capture Protection**: Prevents screenshots and screen recording via Android `FLAG_SECURE`.
    *   **Dynamic Watermarking**: Overlays viewer-specific metadata to discourage taking photos of the screen.
-   **QR-Based Access**: Share access codes via secure QR codes for seamless "scan-and-decrypt" workflows.

---

## ⚙️ Technical Architecture

### 1. Security Model (`CryptoEngine`)
The security backbone of CipherView relies on modern cryptographic primitives:
-   **Key Derivation**: `PBKDF2WithHmacSHA256` with 100,000 iterations to derive 256-bit keys from human-readable access codes.
-   **Authenticated Encryption**: `AES-256-GCM` provides encryption with built-in integrity checking.
-   **AAD (Associated Authenticated Data)**: Metadata (filename, expiry, etc.) is bound to the ciphertext, preventing tampering.

### 2. Transfer Engine (`LocalTransferEngine`)
Designed for offline environments (planes, secure facilities, or remote areas):
-   **NSD (Network Service Discovery)**: Automatically finds peers on the local Wi-Fi network.
-   **Multi-Transport**: Dynamically switches between Wi-Fi sockets for speed and Bluetooth for maximum compatibility.

### 3. Protected Viewer (`ProtectedViewerScreen`)
A specialized sandbox for document consumption:
-   **Ephemeral Rendering**: Documents are decrypted into a private cache and wiped immediately after the viewer is closed.
-   **Capture Resistance**: Blocks system-level screen capture and task-switcher previews.

---

## 🚀 How it Works

1.  **Select & Protect**: Choose a PDF and set your security policy (Access Code, Expiration, View Limit).
2.  **Broadcast**: Start the transfer server. Your peer will see you on the local network.
3.  **Transfer**: The `.cview` package is streamed directly to the recipient's "Vault".
4.  **Decrypt & View**: The recipient scans your QR code or enters the manual Access Code to derive the key and view the document within the protected sandbox.

---

## 🛠 Tech Stack

-   **Language**: Kotlin
-   **UI**: Jetpack Compose (Material 3)
-   **Concurrency**: Kotlin Coroutines & Flow
-   **Security**: Java Cryptography Architecture (JCA), Android Keystore
-   **Networking**: TCP Sockets, Bluetooth RFCOMM, NSD
-   **Architecture**: MVVM with Repository Pattern

---

*Note: CipherView is designed for local, high-security environments and does not provide cloud backup by design.*
