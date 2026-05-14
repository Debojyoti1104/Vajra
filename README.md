# Vajra: Decentralized Bluetooth Mesh Emergency SOS Network

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Kotlin](https://img.shields.io/badge/kotlin-2.2.10-blue.svg)](https://kotlinlang.org/)
[![Android](https://img.shields.io/badge/platform-Android-green.svg)](https://developer.android.com/)

**Vajra** is a high-performance, software-defined emergency communication protocol designed for complete network isolation. In disaster scenarios where cellular towers and Wi-Fi infrastructure fail, Vajra transforms standard smartphones into resilient, self-healing nodes of a decentralized mesh network.

Using only existing device radios (BLE and GPS), Vajra enables survivors and rescue teams to coordinate, share coordinates, and signal life-critical intents with zero infrastructure.

---

## 🚀 Atomic Feature Breakdown

### 1. Connectionless Bluetooth Low Energy (BLE) Mesh
The backbone of Vajra is a connectionless propagation protocol.
*   **Advertising-Based Routing:** Unlike standard Bluetooth, Vajra uses BLE "Advertising" packets (31 bytes). It requires no pairing, no handshakes, and zero connection latency.
*   **Leapfrog Propagation Algorithm:** Every device that intercepts an SOS signal automatically acts as a signal booster (relay). This extends the 50m hardware limit to a software-defined mesh of 500m+.
*   **Intelligent Deduplication:** Uses a 32-bit unique MessageID system. If a node detects a packet it has already processed, it silently discards it to prevent "Broadcast Storms" and conserve battery.
*   **Time-To-Live (TTL) Control:** Each packet carries a 1-byte TTL. With every "hop" between phones, the TTL decrements, ensuring the network doesn't become congested with stale data.

### 2. TinyNLP Intent Compression
Vajra solves the 31-byte radio packet constraint using a localized software-defined dictionary.
*   **1-Byte Encoding:** Instead of sending raw text, the app maps 50+ complex emergency scenarios into a single byte (`0x02` = Building Collapse, `0x07` = Severe Bleeding).
*   **Localized Dictionary (`intents.json`):** The compression and decompression happen entirely on-device. No cloud API is required for "translation."
*   **Payload Optimization:** By using only 1 byte for the "intent," Vajra reserves maximum space for high-precision GPS coordinates and security signatures.

### 3. Integrated Live Hybrid Mapping
A robust GIS (Geographic Information System) that functions in total network isolation.
*   **Zero-Redirect In-App Maps:** Powered by Google Maps SDK, the tracking interface is fully embedded. Rescuers never have to leave the app or lose tactical focus.
*   **Satellite/Hybrid Visualization:** Uses high-detail satellite imagery to provide terrain context (rubble, water levels, road blocks) which is critical during disasters.
*   **Offline Data Continuity:** Supports bundled `.sqlite` map archives and aggressive local caching, ensuring the map remains visible even without a data connection.

### 4. High-Precision Tactical Navigation
*   **Dynamic Visual Path:** A real-time Cyan Polyline connects the responder's "Blue Dot" to the victim's marker, updating dynamically as either party moves.
*   **Auto-Zoom Intelligence:** The map automatically calculates a `LatLngBounds` to ensure both the responder and victim are visible on screen at the same time.
*   **Fused Location Tracking:** Integrates Google's `FusedLocationProviderClient` for the highest available accuracy, combining GPS, Accelerometer, and Magnetometer data.

### 5. "Geiger-Counter" Proximity Alert
When GPS signals are degraded by rubble or smoke, Vajra switches to audio-based location.
*   **10m Precision Trigger:** Using the `Location.distanceBetween` API, the app calculates the distance to sub-meter accuracy.
*   **Variable Frequency Beeping:**
    *   **< 100m:** Slow "Warning" pulse.
    *   **< 10m:** Rapid-fire "Found" beep (0.2s intervals).
*   **Tactical Silence Toggle:** A dedicated UI button allows rescuers to toggle the beep for stealth or noise management during sensitive operations.

### 6. Always-On Background Guardian
*   **Foreground Sticky Service:** Implements a `MeshBackgroundService` that prevents the Android system from putting the mesh engine to sleep.
*   **Deep-Link System Notifications:** When a nearby SOS is intercepted, the phone triggers a High-Priority alert. Tapping the notification automatically deep-links to the exact location on the map.

---

## 🛠️ Technical Stack & Architecture
*   **Language:** Kotlin 2.2.10 (Optimized for performance and memory safety).
*   **UI Framework:** Jetpack Compose (Declarative, reactive UI architecture).
*   **Communication:** BLE Advertising (Android Bluetooth LE API).
*   **Location:** Google Play Services Maps & Location (API 21.3.0+).
*   **Concurrency:** Kotlin Coroutines & Flows (Asynchronous, non-blocking mesh processing).
*   **Compatibility:** Android 8.0 (API 26) through Android 15 (API 36).

---

## 📖 Setup & Development
1.  **Clone the Repository:** `git clone https://github.com/Debojyoti1104/Vajra.git`
2.  **Add Secrets:** Create a `secrets.properties` file in the root directory and add `MAPS_API_KEY=YOUR_KEY`.
3.  **Build:** Open in Android Studio and run the `:app:assembleDebug` task.

---

## 🔍 Search Engine Keywords
Bluetooth Mesh Network, Decentralized SOS, Offline Emergency Communication, Software-Defined Mesh, Android Mesh Network Kotlin, BLE Advertising Mesh, Disaster Management App, Disaster Recovery Software, Zero-Infrastructure Messaging, Vajra Protocol.

## ⚖️ License
Vajra is distributed under the **MIT License**. See `LICENSE` for more information.
