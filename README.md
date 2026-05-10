# Vajra: Software-Defined Emergency Mesh Network

Vajra is a decentralized, software-defined emergency communication protocol designed for scenarios where cellular networks and Wi-Fi have failed. It transforms smartphones into resilient nodes within a local mesh network using existing Bluetooth Low Energy (BLE) hardware.

## Features

### 📡 Zero-Infrastructure Communication
Broadcasts and relays SOS signals and GPS coordinates without internet or towers using BLE Advertising.

### 🔗 Intelligent Mesh Routing
A custom connectionless protocol with TTL (Time-To-Live) and deduplication logic to prevent network congestion while extending range through "leapfrog" relaying.

### 🗜️ TinyNLP Compression
Maps complex emergency intents (e.g., "Medical: Cardiac Arrest") into compact 1-byte codes, allowing high-context data to fit into ultra-small radio packets.

### 🗺️ Offline Mapping Support
Fully integrated with `osmdroid` for high-detail, pre-loaded mapping. Supports bundled `.sqlite` map archives for total network isolation.

### 🔊 Proximity Precision (1-10m)
Includes an audio "Geiger-counter" style beep that triggers and increases in frequency as responders approach a victim's precise location.

### 🛡️ Always-On Background Monitoring
A persistent foreground service listens for emergency packets even when the phone is locked or the app is closed.

## Technical Focus
1. **Software-Defined Routing:** Logic at the application layer with no specialized hardware requirements.
2. **Traffic Prioritization:** Dynamically shifts radio scanning modes based on the criticality of detected emergency codes.
3. **Low-Power Design:** Optimized for battery survival during extended power outages.

## Setup & Use
1. **Permissions:** Grant Bluetooth and Location permissions on startup.
2. **Enable Bluetooth:** The app will prompt to enable Bluetooth if it's off.
3. **Select Intent:** Choose your emergency type from the dropdown.
4. **Trigger SOS:** Long-press the SOS button to start broadcasting.
5. **Locate Victims:** Tap any intercepted message in the feed to open the offline map and start proximity beeping.

## License
Distributed under the MIT License. See `LICENSE` for more information.
