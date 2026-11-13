MediaDown

<p align="center">
**A simple, powerful Android media downloader.**
</p>

<p align="center">
Download videos, photos, and audio from supported social media platforms with a clean, modern Android experience.
</p>

<p align="center">
<img src="https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white" alt="Android"/>
<img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin"/>
<img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white" alt="Jetpack Compose"/>
<img src="https://img.shields.io/badge/Min%20SDK-29-brightgreen" alt="Minimum SDK"/>
<img src="https://img.shields.io/badge/License-See%20LICENSE-lightgrey" alt="License"/>
</p>

📱 About MediaDown

MediaDown is an Android application designed to make downloading online media simple and convenient.

Paste a supported media link, choose between video/photo or MP3 audio, and let MediaDown handle the download.

With a modern dark interface, download progress tracking, Instagram authentication support, and Android Share integration, MediaDown provides a convenient media downloading experience.

✨ Features
🎬 Video Downloads – Download supported online videos.
🖼️ Photo Downloads – Save supported images and photo posts.
🎵 MP3 Downloads – Extract audio and save it in MP3 format.
📲 Multiple Platforms – Supports links from YouTube, Instagram, TikTok, X, Facebook, Reddit, and Pinterest, subject to platform and media compatibility.
🔗 Quick Link Sharing – Receive shared links directly from other Android apps.
⚡ Automatic Downloads – Enable automatic downloading when sharing a link.
📊 Download Progress – Track download progress and processing stages.
❌ Cancel Downloads – Cancel an ongoing download.
📁 Download History – Access previously downloaded files.
🔐 Instagram Login – Optional Instagram authentication for supported restricted content.
🍪 Cookie Import – Import a cookies.txt file when authentication is needed.
🔄 Engine Updates – Automatically update the yt-dlp engine.
🌙 Dark UI – Minimal, modern dark interface powered by Material 3.
📸 Screenshots

<p align="center">
<img src="screenshots/home.png" width="250" alt="MediaDown home screen"/>
<img src="screenshots/download.png" width="250" alt="MediaDown download screen"/>
<img src="screenshots/tools.png" width="250" alt="MediaDown tools screen"/>
</p>

<p align="center">
<img src="screenshots/history.png" width="250" alt="MediaDown download history"/>
<img src="screenshots/audio.png" width="250" alt="MediaDown audio download mode"/>
</p>

Replace these example screenshot files with actual screenshots of your application.
🌐 Supported Platforms

MediaDown recognizes links from the following platforms:

Platform	Link support
YouTube	Yes
Instagram	Yes
TikTok	Yes
X (Twitter)	Yes
Facebook	Yes
Reddit	Yes
Pinterest	Yes

Note: Actual download availability depends on the source platform, media type, authentication requirements, and yt-dlp compatibility.

🚀 Getting Started
Requirements
Android 10 (API 29) or later.
ARM64-compatible Android device.
Internet connection.
Android Studio for development.
Installation
Download the latest APK from the project's Releases page.
Open the downloaded APK on your Android device.
Allow installation if Android requests permission.
Install and launch MediaDown.
Start downloading supported media.
Build from Source

1. Clone the repository

bash
git clone https://github.com/itsprofessorX/MediaDown.git


2. Open the project

Open the cloned folder in Android Studio.

3. Build the application

bash
./gradlew assembleDebug


4. Find the generated APK

text
app/build/outputs/apk/debug/app-debug.apk


On Windows, use:

bash
gradlew.bat assembleDebug

📖 How to Use
Open MediaDown.
Copy a supported media URL.
Paste the link into the input field.
Select your preferred download mode:
Video / Photo
Audio MP3
Tap Download.
Monitor the download progress.
Find your downloaded media in your device's gallery or download history.
Instagram Authentication

Some Instagram content may require authentication.

Open the Tools section.
Select Instagram login.
Complete the login process.
Alternatively, import a compatible cookies.txt file.
Retry the download.

Only use authentication for accounts and content you are authorized to access.

Quick Share Download

MediaDown supports Android's Share functionality.

Open a supported social media application.
Select Share on a media link.
Choose MediaDown.
If automatic downloading is enabled, the download starts automatically.
🛠️ Technology Stack
Technology	Purpose
Kotlin	Main programming language
Jetpack Compose	Modern declarative UI
Material 3	UI components and theming
Android SDK	Android application framework
ViewModel	UI state management
Coroutines	Asynchronous operations
yt-dlp Android	Media extraction and downloading
FFmpeg	Audio and media processing
MediaStore	Saving downloaded media
📂 Project Structure
text
MediaDown/
│
├── app/
│   └── src/
│       └── main/
│           ├── java/
│           │   └── com/professorx/mediadl/
│           │       ├── MainActivity.kt
│           │       ├── DlViewModel.kt
│           │       ├── Instagram.kt
│           │       └── Images.kt
│           │
│           └── AndroidManifest.xml
│
├── .github/
│   └── workflows/
│
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── README.md

⚙️ App Configuration
Configuration	Value
Application ID	com.professorx.mediadl
Minimum SDK	29
Target SDK	34
Compile SDK	34
Version Name	1.2
Version Code	3
Java Version	17
Native ABI	arm64-v8a
🔒 Privacy & Security

MediaDown is designed to process downloads on the user's Android device.

Instagram authentication is optional.
Imported cookies are stored in the application's private files.
Users can remove imported cookies through the Tools section.
Downloaded media is saved using Android MediaStore.
Never share your authentication cookies with anyone.

Please review the application's source code and permissions before using it.

⚠️ Disclaimer

MediaDown is an independent, third-party Android project.

This project is not affiliated with, endorsed by, or sponsored by YouTube, Instagram, TikTok, Facebook, X, Reddit, or Pinterest.
Users are responsible for ensuring that their downloads comply with applicable copyright laws and platform terms of service.
Only download content that you own or have permission to download.
Platform availability and download functionality may change without notice.
🤝 Contributing

Contributions, suggestions, bug reports, and improvements are welcome!

Fork this repository.
Create a new branch:
bash
git checkout -b feature/your-feature

Make your changes.
Commit your changes:
bash
git commit -m "Add your feature"

Push your branch:
bash
git push origin feature/your-feature

Open a Pull Request.
🐛 Issues & Support

Found a bug or have a feature request?

Please open an issue:

Report an Issue

⭐ Support the Project

If you find MediaDown useful:

Give the repository a ⭐ Star.
Share it with other developers.
Report bugs and suggest improvements.
Contribute to the project.
👨‍💻 Author

ProfessorX

GitHub: @itsprofessorX

Project: MediaDown

<p align="center">
Made with ❤️ using Kotlin and Jetpack Compose.
</p>
