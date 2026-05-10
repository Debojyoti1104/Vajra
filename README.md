# Vajra: Software-Defined Emergency Mesh Network

**Vajra** is a decentralized, software-defined emergency communication protocol designed for scenarios where cellular networks and Wi-Fi have failed. It transforms smartphones into resilient nodes within a local mesh network using existing Bluetooth Low Energy (BLE) hardware.

## 📡 Core Features
* **Zero-Infrastructure Communication:** Broadcasts and relays SOS signals without internet or towers using BLE Advertising.
* **Intelligent Mesh Routing:** Connectionless protocol with TTL and deduplication to prevent network congestion.
* **TinyNLP Compression:** Maps complex emergency intents into compact 1-byte codes for ultra-small radio packets.
* **Offline Mapping:** Fully integrated with `osmdroid` for high-detail, pre-loaded maps that work in total network isolation.
* **Proximity Precision (1-10m):** Audio-visual alerts that increase in frequency as responders approach a victim's precise location.
* **Always-On Monitoring:** A persistent foreground service listens for emergency packets even when the phone is locked.

## 🛠️ Technical Focus
1. **Software-Defined Routing:** Logic at the application layer with no specialized hardware requirements.
2. **Traffic Prioritization:** Dynamically shifts radio modes based on the criticality of detected emergency codes.
3. **Low-Power Design:** Optimized for long-term battery survival during extended power outages.
