<div align="center">
  <img src="https://avatars.githubusercontent.com/u/78095377?s=200&v=4" width="100" height="100" alt="Logo" />
  <h1>LeviLauncher (Unlocked Edition)</h1>
  <p><strong>A high-performance, modular, and lightweight Android launcher for Minecraft: Bedrock Edition (MCBE).</strong></p>
  <a href="https://github.com/khoadangkim2014-arch/PocketCosmosLevi-Unlocked">
    <img src="https://capsule-render.vercel.app/api?type=waving&color=gradient&customColorList=12,24,30&height=200&section=header&text=PocketCosmos%20Levi&fontSize=60&animation=fadeIn&fontColor=ffffff" width="100%" />
  </a>
  <br /><br />
  <a href="https://github.com/khoadangkim2014-arch/PocketCosmosLevi-Unlocked/releases">
    <img src="https://img.shields.io/github/v/tag/khoadangkim2014-arch/PocketCosmosLevi-Unlocked?style=for-the-badge&color=7289da&logo=github&label=RELEASE" alt="Release" />
  </a>
  <a href="https://github.com/khoadangkim2014-arch/PocketCosmosLevi-Unlocked/blob/main/LICENSE">
    <img src="https://img.shields.io/github/license/khoadangkim2014-arch/PocketCosmosLevi-Unlocked?style=for-the-badge&color=43b581" alt="License" />
  </a>
  <a href="https://github.com/khoadangkim2014-arch/PocketCosmosLevi-Unlocked/stargazers">
    <img src="https://img.shields.io/github/stars/khoadangkim2014-arch/PocketCosmosLevi-Unlocked?style=for-the-badge&color=faa61a&logo=github" alt="Stars" />
  </a>
  <br /><br />
  <img src="https://img.shields.io/github/repo-size/khoadangkim2014-arch/PocketCosmosLevi-Unlocked?style=flat-square&logo=github&color=blue" />
  <img src="https://img.shields.io/github/last-commit/khoadangkim2014-arch/PocketCosmosLevi-Unlocked?style=flat-square&logo=git&color=orange" />
  <img src="https://img.shields.io/badge/Android-9.0%2B-green?style=flat-square&logo=android" />
</div>
---
## ⚡ Overview
**LeviLauncher** is an open-source, custom-tailored Android launcher engineered for **Minecraft: Bedrock Edition (MCBE)**. It breaks free from standard Google Play restrictions, allowing you to run, isolate, patch, and manage multiple Minecraft versions side-by-side with external module support.
> ⚠️ **Disclaimer:** LeviLauncher is designed strictly for **legitimate owners** of Minecraft: Bedrock Edition. You must own an official copy purchased from Google Play to use this software.
---
## ✨ Key Features

| Feature | Description |
| :--- | :--- |
| 📦 **Installation-Free Launch** | Import official Minecraft APKs directly and run them in an isolated environment without system-wide APK installation. |
| 🧩 **Native `.so` Module Injection** | Load external native C/C++ libraries (`.so`) to extend game mechanics, improve engine performance, or inject custom mods. |
| 🔄 **Multi-Version Isolation** | Keep multiple game versions completely separated—isolated configs, worlds, cache, and resource packs. |
| 👤 **Multi-Xbox Account Switcher** | Manage and switch between multiple Xbox Live profiles with seamless single-click authorization. |
| 🗺️ **World & Resource Management** | Built-in manager to easily backup, export, or import `.mcworld`, `.mcpack`, and `.mcaddon` files. |
| ⚡ **Lightweight & Fast** | Minimal resource footprint with zero bloat, maximizing RAM and CPU headroom for maximum FPS. |

---
## 🛠️ Tech Stack
- **Primary Languages:** Java, Kotlin, C++ (Android NDK)
- **Target OS:** Android 9.0+ (API 28+)
- **Architecture:** ARM64 (`arm64-v8a`)
- **Build System:** Gradle with GitHub Actions CI/CD Integration
---
## 📋 System Requirements

| Specification | Minimum Requirement | Recommended |
| :--- | :--- | :--- |
| **OS Version** | Android 9.0 (API level 28) | Android 12.0+ (API level 31+) |
| **Architecture** | ARM64 (`arm64-v8a`) | ARM64 (`arm64-v8a`) |
| **RAM** | 2 GB Available | 4 GB+ Free RAM |
| **Storage** | 2 GB Free Space | 5 GB+ High-Speed Storage |
| **Game License** | Valid MCBE Google Play License | Valid MCBE Google Play License |

---
## 🚀 Quick Start Guide
1. Head over to the [Releases Page](https://github.com/khoadangkim2014-arch/PocketCosmosLevi-Unlocked/releases).
2. Download the latest `LeviLauncher-release.apk`.
3. Install the APK on your Android device (*Allow installation from unknown sources if prompted*).
4. Launch **LeviLauncher**, grant the necessary storage permissions, and import your official Minecraft APK.
---
## 💻 Building From Source
Follow these steps to build the APK locally on your machine:
```bash
# 1. Clone the repository
git clone [https://github.com/khoadangkim2014-arch/PocketCosmosLevi-Unlocked.git](https://github.com/khoadangkim2014-arch/PocketCosmosLevi-Unlocked.git)
# 2. Navigate into the project folder
cd PocketCosmosLevi-Unlocked
# 3. Build the release APK via Gradle Wrapper
./gradlew assembleRelease
