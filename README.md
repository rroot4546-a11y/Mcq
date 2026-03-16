# MCQ Quiz App 🩺📱

AI-Powered MCQ Quiz Android App for Medical Board Exam Preparation.

## Features
- 📄 **PDF Import:** Automatically extracts MCQs from PDF files
- 🧠 **AI Explanations:** Get detailed explanations via Google Gemini API
- 📖 **Study Mode:** See explanations immediately after answering
- 📝 **Exam Mode:** Explanations shown only after completing the quiz
- 🔄 **Review Mode:** Practice only incorrectly answered questions
- 📊 **Statistics:** Track your progress and accuracy
- 📶 **Offline Support:** Quizzes work without internet (AI features need connection)

## How to Build

### Option 1: GitHub Actions (Recommended)
1. Push this code to a GitHub repository
2. Go to Actions tab → Build APK → Run workflow
3. Download the APK from Artifacts

### Option 2: Android Studio
1. Open this project in Android Studio
2. Click Build → Build Bundle(s) / APK(s) → Build APK(s)

### Option 3: Command Line
```bash
./gradlew assembleDebug
```

## Setup AI Explanations
1. Get a free API key from [Google AI Studio](https://aistudio.google.com/)
2. Enter the key in the app Settings

## Tech Stack
- **Language:** Kotlin
- **Database:** Room (SQLite)
- **PDF Parsing:** PDFBox Android
- **AI Integration:** Google Gemini API
- **Architecture:** MVVM

## For Dr. Roox 🇮🇶
Built specifically for Iraqi Board of Internal Medicine preparation.
