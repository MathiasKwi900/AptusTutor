# AptusTutor: On-Device AI for the Underserved Educator 🧑‍🏫

## Leveraging Gemma 3n's Architectural Efficiency for Private, Offline Classroom Analytics on Low-Cost Hardware

AptusTutor is an Android application developed for the Google Gemma 3n Impact Challenge. It leverages the power of Google’s Gemma 3n model to deliver private, offline, AI-powered student assessment and classroom analytics directly on low-cost Android devices.

The project’s vision is to address a critical gap in global education: the limited availability of data-driven teaching tools in low-connectivity, resource-constrained environments. By proving its utility on representative low-end hardware, AptusTutor establishes a viable blueprint for serving millions of educators who have access to smartphones but lack reliable internet or high-end devices.


## 🔗 Links & Demo

| Link Type                   | URL                                                         |
| --------------------------- | ----------------------------------------------------------- |
| 🎬 Video Demo               | [Paste your video demo link here]()                        |
| 📦 Live Demo (APK Download) | <https://github.com/MathiasKwi900/AptusTutor/releases/tag/v1.0.0>                   |
| 📄 Full Technical Write-up  | <https://www.kaggle.com/competitions/google-gemma-3n-hackathon/writeups/unlocking-offline-ai-power-with-aptustutor>     |


## ✨ Key Features

| Feature                          | Description                                                                                     |
| -------------------------------- | ----------------------------------------------------------------------------------------------- |
| 📶 Offline Peer-to-Peer Classroom | Teachers can create a local digital classroom using the Google Nearby Connections API, allowing students to connect and submit work without any internet or Wi-Fi infrastructure. |
| 🤖 AI-Powered Grading             | Grade student submissions (text and handwritten images) with a single tap using the on-device Gemma 3n model. |
| 🔒 100% Private and On-Device      | All data, including student submissions and AI analysis, remains on the user’s device. No data is sent to the cloud, ensuring absolute privacy. |
| 💪 Optimized for Low-End Hardware | Deliberately designed and tested to run effectively on resource-constrained devices (e.g., MediaTek Helio G81, 4 GB RAM). |
| 🖼️ Multimodal Input               | Capable of processing both text and image-based student answers.                                 |
| 🛡️ Resilient by Design           | Features proactive device health monitoring (RAM, thermal), robust background model downloading, and resilient AI output parsing. |


## 📸 Screenshots & Demo

![App Main Flow GIF](path/to/your-demo.gif)

| AI Sandbox           | Grading Screen                                 | AI Model Management                                  |
| -------------------- | ---------------------------------------------- | ---------------------------------------------------- |
| `![Sandbox](path/to/sandbox.png)` | `![Grading](path/to/grading.png)`       | `![Model Mgmt](path/to/model-management.png)`        |


## 🛠️ Architecture & Tech Stack

AptusTutor is a modern Android application built with a focus on scalability, maintainability, and a clean separation of concerns.

| Component               | Technology / Library                                    | Rationale for Selection                                                                 |
| ----------------------- | ------------------------------------------------------- | --------------------------------------------------------------------------------------- |
| Core Logic              | Kotlin                                                  | Selected for its conciseness, null safety, and official support for modern Android development. |
| User Interface          | Jetpack Compose & MVVM                                  | Implements a modern, declarative UI with a clear separation between the UI layer and business logic. |
| Core Intelligence       | Google Gemma 3n (E2B-it-int4) via Google AI Edge SDK (0.10.25) | The central technology enabling private, offline, multimodal analysis on resource-constrained hardware. |
| Offline Networking      | Google Nearby Connections API                          | Enables robust, offline, peer-to-peer communication without external infrastructure.     |
| Dependency Injection    | Hilt                                                    | Standardizes dependency injection, reducing boilerplate and improving code modularity.   |
| Asynchronicity          | Kotlin Coroutines & Flow                                | Manages long-running tasks like AI inference and database I/O off the main thread.       |
| Data Persistence        | Room Database                                           | Provides a robust, type-safe abstraction over a local SQLite database.                   |
| User Preferences        | Jetpack Datastore                                       | A modern, coroutine-based solution for storing simple key-value data.                    |
| Background Tasks        | WorkManager                                             | Guarantees the execution of deferrable, long-running tasks like the AI model download.  |


## 🔬 The Engineering Journey: A Summary

As a solo developer, the path to a stable on-device AI application involved significant engineering challenges. An initial high-performance caching architecture (“One Engine Per Question”) was designed to minimize latency. However, rigorous debugging revealed this approach was unviable due to state management bugs within the experimental `tasks-genai:0.10.25` library (validated against public GitHub issues #5999 and #6014).

A deliberate engineering decision was made to revert to a more stable, albeit slower, “Ephemeral Engine” architecture. This approach, while incurring a full prefill cost per task, guarantees correctness and context isolation. To make this intensive process robust on low-end hardware, five additional layers of stabilization were implemented, including proactive device health monitoring, resource serialization with a `Mutex`, and resilient output parsing.


## 🚀 Getting Started
### **1. App Installation**

You have two main ways to try AptusTutor: a quick direct installation or building the project from the source code.

**Option 1: Direct Install (Recommended for Most Users)**

This is the fastest way to get the app running on your device.

**Download the APK:** Grab the latest release from the Live Demo (APK Download) link at the top of this README.

**Install the App:** Open the downloaded .apk file on your Android device to install it. You may need to enable "Install from unknown sources" in your device's security settings.


**Option 2: Clone the Repository**  
   ```bash
   git clone https://github.com/MathiasKwi900/AptusTutor.git
   ```

Open the cloned project directory in the latest stable version of **Android Studio** and allow **Gradle** to sync.

Note: For security reasons, the `google-services.json` file required to build the project is not included in this repository. This file contains API keys linked to a specific Firebase project.

To build and run the app, you will need to generate your own `google-services.json` file by following these steps:

1.  Go to the [Firebase Console](https://console.firebase.google.com/) and create a new free project.
2.  In your new project, click on **Add app** and select the **Android** platform.
3.  Follow the on-screen instructions to register the app.
4.  Download the generated `google-services.json` file.
5.  Place this file in the `app/` directory of this project.
6.  You should now be able to build and run the project successfully in Android Studio.

### **2. Setting up the AI Model (CRITICAL STEP)**

Whether you installed the app via option 1 or 2, the AI features **will not work** without the **Gemma 3n** model file. You have two options:

### ✅ Option A: In-App Download (Recommended for most users)

1. Install or build and run the app on your Android device.
2. Select a user role (**Tutor** or **Student**) and navigate to the main dashboard.
3. Tap the **three-dot menu** in the top-right corner and select **'AI Model Settings'**.
4. On the **'AI Model Management'** screen, tap the **"Download Model"** button.
5. The app will use **WorkManager** to download the `gemma-3n-E2B-it-int4.task` model from **Firebase Storage** (~3.14 GB) and place it in the correct directory.



### ⚙️ Option B: Manual Setup

If you already have the `gemma-3n-E2B-it-int4.task` file, you can load it directly.

1. Install or build and run the app.
2. Navigate to the **'AI Model Settings'** screen as described above.
3. Tap the **"Load from Device Storage"** button.
4. Use the **file picker** to select your local `.task` model file.

> **Note:** The app is specifically architected for the `gemma-3n-E2B-it-int4.task` model.  
> Using other models may result in unexpected behavior or crashes.


## 📂 Project Structure — `com.nexttechtitan.aptustutor`

### 🧠 Root

| File | Description |
|------|-------------|
| `AptusTutorApplication.kt` | Main Application class, Hilt & WorkManager init |
| `MainActivity.kt`          | Single Activity hosting all Compose UI |



### 🤖 `ai/` — AI-related logic

| File | Description |
|------|-------------|
| `GemmaAiService.kt`       | Core service for Gemma 3n inference and prompt engineering |
| `ModelDownloadWorker.kt`  | WorkManager for background model download |



### 💾 `data/` — Data layer: Repository, DAOs, Models

| File | Description |
|------|-------------|
| `AptusTutorDatabase.kt`        | Room database definition and schema |
| `AptusTutorRepository.kt`      | Single source of truth for all app data (DB and Network) |
| `Daos.kt`                      | Interfaces defining all Room database queries |
| `AptusTutorEntities.kt`                | `@Entity` classes defining all database tables |
| `Payloads.kt`                  | Data classes for Nearby Connections network payloads |
| `UiState.kt`             | Data classes for UI state (e.g., `TutorDashboardUiState`) |
| `UserDataStore.kt` | Manages user settings with Jetpack DataStore |



### 🧩 `di/` — Hilt dependency injection modules

| File | Description |
|------|-------------|
| `HiltModules.kt` | Provides singleton instances of services, DB, etc. |



### 🎨 `ui/` — Jetpack Compose UI screens and ViewModels

#### `AptusTutorApp.kt`  
NavHost and navigation graph setup for the entire app

#### `main/`

| File | Description |
|------|-------------|
| `MainViewModel.kt`      | Determines the app's initial start screen |
| `AptusHubViewModel.kt`  | ViewModel for the AI Sandbox and Analytics vision |
| `AptusHubScreen.kt`     | UI for the Aptus Hub |

#### `roleselection/`

| File | Description |
|------|-------------|
| `RoleSelectionViewModel.kt` | Logic for saving user's role |
| `RoleSelectionScreen.kt`    | UI for the initial Tutor/Student role choice |

#### `settings/`

| File | Description |
|------|-------------|
| `AiSettingsViewModel.kt` | Logic for managing the AI model file |
| `AiSettingsScreen.kt`    | UI for downloading, loading, and deleting the AI model |

#### `student/`

| File | Description |
|------|-------------|
| `StudentDashboardViewModel.kt` | Logic for student dashboard (discovering/joining sessions) |
| `StudentDashboardScreen.kt`    | UI for the student's main dashboard |
| `AssessmentScreen.kt`          | UI for taking a timed assessment |
| `SubmissionResultViewModel.kt` | Logic for loading a graded submission |
| `SubmissionResultScreen.kt`    | UI for viewing final score and feedback |

#### `tutor/`

| File | Description |
|------|-------------|
| `TutorDashboardViewModel.kt`    | Logic for tutor dashboard (managing sessions/roster) |
| `TutorDashboardScreen.kt`       | UI for the tutor's main dashboard |
| `SubmissionDetailsViewModel.kt` | Logic for grading a single submission |
| `SubmissionDetailsScreen.kt`    | UI for the detailed grading screen |
| `TutorHistoryViewModel.kt`      | Logic for loading past sessions |
| `TutorHistoryScreen.kt`         | UI for the tutor's session history |
| `SubmissionsListViewModel.kt`   | Logic for loading all submissions for an assessment |
| `SubmissionsListScreen.kt`      | UI for listing submissions from a historic assessment |



### 🛠️ `utils/` — Reusable, self-contained helper classes

| File | Description |
|------|-------------|
| `DeviceHealthManager.kt` | RAM and thermal status checks for AI safety |
| `FileUtils.kt`           | Helpers for saving files to internal storage |
| `ImageUtils.kt`          | High-performance image compression utility |
| `JsonExtractionUtils.kt` | Safely parses LLM JSON output |
| `NetworkUtils.kt`        | Checks for Wi-Fi connectivity |
| `NotificationHelper.kt`  | Manages Android notifications for downloads |
| `ThermalManager.kt`      | Wraps PowerManager for thermal status checks |

    
## 📄 License

This project is licensed under the **Apache License, Version 2.0**.  
See the [`LICENSE`](LICENSE) file for details.
