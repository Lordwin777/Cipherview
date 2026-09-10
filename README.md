# CipherView

**CipherView** is a high-assurance, privacy-centric Android application designed for secure, ephemeral, and local-first document sharing. It eliminates the need for cloud intermediaries, ensuring that sensitive data remains exclusively between the sender and the recipient.

---

### 👤 Author & Project Credentials
* **Lead Engineer**: **Lordwin Joseph**
* **Enrollment Number**: **92301733065**
* **Official Repository**: [https://github.com/Lordwin777/Cipherview.git](https://github.com/Lordwin777/Cipherview.git)
* **Comprehensive Documentation**: 
  * 📄 **[10-Page Technical Specification (Markdown)](docs/CIPHERVIEW_DOCUMENTATION.md)**
  * 🖨️ **[10-Page Printable Report (Interactive HTML / Save as PDF)](docs/CIPHERVIEW_DOCUMENTATION.html)**

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

## 📸 Physical Device Screenshot Showcase

| 1. Onboarding (Zero-PII) | 2. Local Vault Dashboard | 3. Share & Protect Studio |
| :---: | :---: | :---: |
| <img src="docs/images/01_onboarding.png" width="220"/> | <img src="docs/images/02_vault_dashboard.png" width="220"/> | <img src="docs/images/03_share_encrypt.png" width="220"/> |

| 4. Receive & Unlock Station | 5. Protected Document Viewer | 6. Historical Nickname Audit |
| :---: | :---: | :---: |
| <img src="docs/images/04_receive_station.png" width="220"/> | <img src="docs/images/07_protected_viewer.png" width="220"/> | <img src="docs/images/08_profile_nickname_history.png" width="220"/> |

---

## 🛠 Tech Stack

- **Language**: Kotlin 2.3.20
- **UI Framework**: Jetpack Compose (Material 3) + Navigation3
- **Concurrency**: Kotlin Coroutines & StateFlow
- **Security Primitives**: AES-256-GCM, PBKDF2WithHmacSHA256 (100K Rounds), Android Keystore (StrongBox HSM)
- **Networking**: Zero-Cloud TCP Sockets, Android NSD / mDNS, CameraX 1.4.1 + ZXing Core 3.5.3
- **Architecture**: Clean Architecture / MVVM with Repository Pattern

---

### 👨‍💻 Project Information & Contact
* **Author**: **Lordwin Joseph**
* **Enrollment Number**: **92301733065**
* **GitHub Repository**: [https://github.com/Lordwin777/Cipherview.git](https://github.com/Lordwin777/Cipherview.git)
* *Designed and built with absolute commitment to digital sovereignty and human privacy.*
