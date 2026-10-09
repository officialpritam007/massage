<div align="center">

<img src="https://via.placeholder.com/150/000000/FFFFFF?text=LC+Logo" width="120" style="border-radius: 20%; box-shadow: 0 8px 24px rgba(0,0,0,0.2); margin-bottom: 20px;" />

# 💧 Liquid Chat

**A native Android real-time messaging masterpiece inspired by the iOS 26 Liquid Glass design language.**

[![Build Status](https://github.com/PritamPal07/Liquid-Chat/actions/workflows/build-apk.yml/badge.svg)](https://github.com/PritamPal07/Liquid-Chat/actions/workflows/build-apk.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-7F52FF.svg?logo=kotlin&logoColor=white)](http://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/Compose-Material_3-4CAF50.svg?logo=android&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Platform](https://img.shields.io/badge/Platform-Android_8.0+-3DDC84?logo=android&logoColor=white)](#)
[![Architecture](https://img.shields.io/badge/Architecture-MVVM_%2B_Clean-blue)](#)
[![License](https://img.shields.io/badge/License-MIT-gray.svg)](#)

<br>

<img src="https://via.placeholder.com/250x500/121212/FFFFFF?text=Conversation" width="220" style="border-radius: 16px; margin: 0 10px; box-shadow: 0 8px 24px rgba(0,0,0,0.15);" />
<img src="https://via.placeholder.com/250x500/121212/FFFFFF?text=Settings" width="220" style="border-radius: 16px; margin: 0 10px; box-shadow: 0 8px 24px rgba(0,0,0,0.15);" />
<img src="https://via.placeholder.com/250x500/121212/FFFFFF?text=Chats+List" width="220" style="border-radius: 16px; margin: 0 10px; box-shadow: 0 8px 24px rgba(0,0,0,0.15);" />

<br><br>

*Built by **Pritam Pal** using Kotlin, Jetpack Compose, Material 3, Clean Architecture, and a Zero-Cost Serverless Backend.*

</div>

---

## 🌟 Identity & Highlights

| | |
|---|---|
| 📱 **Platform** | Native Android (API 26+) |
| 🛠 **Language** | Kotlin 2.0 |
| 🎨 **UI Framework** | Jetpack Compose + Material 3 |
| 📐 **Architecture** | Clean Architecture + MVVM + Repository Pattern |
| 💎 **Design Language**| iOS 26-inspired Liquid Glass (Frosted Translucency, Specular Highlights) |
| ☁️ **Backend** | Firebase (Auth, Firestore) + Appwrite Cloud (Edge Functions & S3 Gateway) |

---

## 💎 Liquid Glass Design System

The application features a custom, high-performance **Liquid Glass design system** engineered natively for Android:

- 🪟 **Translucent Frosted Surfaces:** Dynamic multi-layered alpha overlays with gradient refraction borders.
- 🌫️ **Backdrop Blur & Fallbacks:** Hardware-accelerated `RenderEffect` on Android 12+ (API 31+) with graceful tint and specular fallbacks on older devices.
- 🔲 **Squircle Geometry:** Smooth rounded squircle corners (`LiquidTheme.shapes`) across cards, bubbles, and floating navigation bars.
- 🌀 **Fluid Spring Animations:** Physics-driven spring transitions (`LiquidMotion`) for navigation, button press deformations, and real-time message arrivals.
- 🌗 **Dynamic Themes:** Full native support for both Light and Dark Liquid themes (`LightLiquidColors`, `DarkLiquidColors`).

### 🧩 Reusable Glass Components
> *A fully customized, production-ready design library replacing standard Material components.*

*   `LiquidGlassSurface` — Foundational frosted-glass surface with specular edge border and shadow.
*   `LiquidGlassCard` — Interactive glass container with elevation and ripple.
*   `LiquidGlassButton` — Primary filled and secondary glass button with spring-press deformation.
*   `LiquidGlassTextField` — Translucent glass input field with integrated validation.
*   `LiquidGlassNavigationBar` — Floating squircle bottom navigation bar with animated tab selection.
*   `LiquidGlassSearchBar` — Translucent search bar with instant query clearing.
*   `LiquidGlassMessageBubble` — Adaptive squircle bubble with outgoing/incoming glass refraction.
*   `LiquidGlassFloatingButton` — Circular glass floating action button.
*   `LiquidGlassDialog` & `LiquidGlassBottomSheet` — Liquid glass dialogs/sheets with blur scrim overlay and gesture dismiss.

---

## 🏛 Cutting-Edge Tech Stack

Liquid Chat achieves a highly secure, scalable, and **zero-cost operation** by leveraging a hybrid cloud architecture designed to run safely within free-tier limits:

*   **Frontend Core:** Kotlin, Jetpack Compose, Material 3, Hilt (Dependency Injection), Coroutines & StateFlow.
*   **Local Persistence (Offline-First):** Room (account-isolated local database for unbreakable offline reliability) and Coil (advanced image caching).
*   **Identity & Authentication:** Firebase Authentication (Secure Email/Password & Google Sign-In integrations).
*   **Real-Time Database:** Cloud Firestore. Strictly enforced with bulletproof, participant-only security rules.
*   **Storage Gateway:** Appwrite Cloud (Free Tier) Serverless Edge Functions dynamically generating AWS S3-compatible pre-signed URLs to strictly protect private attachments.
*   **Smart Push Notifications:** Appwrite Serverless Function acting as a secure HTTP v1 FCM dispatcher, resolving tokens securely on the server.
*   **App Integrity:** Firebase App Check with Play Integrity (`APP_CHECK_PROVIDER=auto`) prevents unauthorized API abuse.

---

## ✨ Core Messaging Features

- ⚡ **Fluid Real-Time Engine:** Instant messaging with hyper-accurate typing indicators and per-conversation presence.
- 🚥 **Micro-Interaction States:** Full cycle visibility (Sending → Sent → Delivered → Read) with beautiful transitions.
- 💬 **Advanced Message Handling:** Seamlessly reply, edit, pin, star, and react to messages on the fly.
- 🛡️ **Granular Privacy Controls:** Sender-only "delete for everyone", local "delete for me", and customizable disappearing message timers.
- 📎 **Rich Media Support:** Share high-resolution images, HD videos, voice notes, and documents instantly.
- 🎨 **Immersive Customization:** Chat-specific wallpapers, favorites, archive, and granular mute capabilities tailored to your workflow.

---

## ⚙️ Quick Setup & Deployment

### 1. Firebase Provisioning
1. Create a project in the [Firebase Console](https://console.firebase.google.com).
2. Enable **Authentication** (Email/Password & Google) and **Firestore**.
3. Download the `google-services.json` file and place it exactly in the `app/` directory.

### 2. Appwrite Backend Configuration
Liquid Chat utilizes two serverless functions deployed to an Appwrite Cloud Free project to securely dispatch notifications and handle S3-compatible media uploads.

👉 **Follow the comprehensive [Appwrite Setup Guide](APPWRITE_SETUP.md) for deployment instructions.**

### 3. Environment Injection
Copy the `.env.example` to a new `.env` file in the project root and provide your edge endpoints:
```env
APPWRITE_FUNCTION_FCM_ENDPOINT=https://<your-fcm-domain>.appwrite.global/
APPWRITE_FUNCTION_STORAGE_ENDPOINT=https://<your-storage-domain>.appwrite.global/
```
*(⚠️ **Security Notice**: Never place Firebase Service Account keys or Appwrite API keys in this file).*

---

## 🔐 Security, Privacy & CI/CD

- **Ironclad Data Isolation:** The local Room database completely clears and isolates data per authenticated user (`uid`), preventing data leakage.
- **Zero Secrets in APK:** Administrative SDK keys remain securely locked in the Appwrite backend functions. 
- **Irreversible Deletion:** "Delete Account" initiates a comprehensive workflow that completely erases all personal data, conversation trees, and media assets.
- **Automated CI/CD:** GitHub Actions workflow (`build-apk.yml`) handles Android Lint, Firestore Emulator testing, and automated Debug APK generation on every commit.

---

## 👨‍💻 About the Developer

<div align="center">
  
**Pritam Pal**  
*Senior Android & Backend Engineer*

Passionate about crafting fluid, modern, and privacy-centric mobile applications using the latest Android technologies and scalable cloud architectures.

<br>

[![Email](https://img.shields.io/badge/Email-officialpritam07%40gmail.com-D14836?style=for-the-badge&logo=gmail&logoColor=white)](mailto:officialpritam07@gmail.com)
[![GitHub](https://img.shields.io/badge/GitHub-PritamPal07-181717?style=for-the-badge&logo=github&logoColor=white)](https://github.com/PritamPal07)
[![LinkedIn](https://img.shields.io/badge/LinkedIn-Pritam_Pal-0A66C2?style=for-the-badge&logo=linkedin&logoColor=white)](https://www.linkedin.com/in/pritam-pal-240a6b252/)
[![X](https://img.shields.io/badge/X-PritamPal__07-000000?style=for-the-badge&logo=x&logoColor=white)](https://x.com/PritamPal_07)
[![Instagram](https://img.shields.io/badge/Instagram-officialpritam.07-E4405F?style=for-the-badge&logo=instagram&logoColor=white)](https://instagram.com/officialpritam.07)
[![Facebook](https://img.shields.io/badge/Facebook-officialpritam.007-1877F2?style=for-the-badge&logo=facebook&logoColor=white)](https://facebook.com/officialpritam.007)

<br>

*© 2026 Pritam Pal. All Rights Reserved.*

</div>

---

## 📚 Additional Documentation

Deep dive into the internals of Liquid Chat:
- [Appwrite Setup & Configuration](APPWRITE_SETUP.md)
- [Architecture Invariants](ARCHITECTURE_INVARIANTS.md)
- [Permanent Deletion Workflow](PERMANENT_DELETION_SETUP.md)
- [Trusted Push Notifications](TRUSTED_PUSH_SETUP.md)
- [App Check Verification](APP_CHECK_SETUP.md)