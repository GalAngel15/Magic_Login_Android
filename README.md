# SmartLoginConditions 🚀

An Android app with a fun and secure twist: the login screen only unlocks when **seven smart environmental conditions** are met!

## 🔐 Conditions for successful login:

1. ✅ Battery percentage matches the user's input  
2. 🧭 Phone is facing East  
3. 🔊 Environment is noisy enough (detected via microphone)  
4. 🔌 Device is charging  
5. 📱 Device has been shaken a few times  
6. 🎵 Music is playing on the phone (Spotify, YouTube, etc.)  
7. 😄 The user is smiling at the camera (detected using Google ML Kit Face Detection)

---

## 📸 Smile Detection

Smile detection is done in real-time using the front camera and Google ML Kit's face detection API.  
Only users who smile confidently are allowed in! 😉

> *(Note: Camera permission is required for this feature)*

---

## 📱 Demo

🎥 A short video will be available here soon to demonstrate the login process under different conditions.

---

## 🚀 Technologies Used

- Java (Android)
- SensorManager
- AudioManager
- MediaRecorder
- AudioRecord
- Google ML Kit (Face Detection)
- FusedLocationProviderClient (optional)
- Activity Result APIs (modern permissions handling)
- Material Design components

---

## 🛠️ How to run

1. Clone the repository  
2. Open in Android Studio  
3. Run on a real Android device (not emulator – sensors required)  
4. Grant microphone and camera permissions  
5. Try to unlock by meeting all 7 conditions!

---

## 🙋‍♂️ Author

Built by [Gal Angel](https://www.linkedin.com/in/galangel)  
Final project for mobile security course 📱🔒  
