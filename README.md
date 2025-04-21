# SmartLoginConditions 🚀

An Android app that turns your login screen into a **smart security challenge!**  
To log in, users must satisfy **seven clever environmental conditions** — blending fun, sensors, and security.

---

## 🔐 Can you meet all 7 conditions?

To unlock the app, all of the following must be true:

1. ✅ Battery percentage matches the user's input  
2. 🧭 Phone is facing East  
3. 🔊 Environment is noisy enough (via microphone)  
4. 🔌 Device is charging  
5. 📱 Device has been shaken several times  
6. 🎵 Music is playing on the device (Spotify, YouTube, etc.)  
7. 😄 User is smiling at the front camera (Google ML Kit)

---

## 📸 Smile Detection

Smile detection is done in real-time using the front camera and **Google ML Kit's Face Detection API**.  
Only users who smile confidently are allowed in! 😉

> 📷 *Camera permission is required for this feature*

---

## 🚀 Technologies Used

- Java (Android)
- SensorManager
- AudioManager
- MediaRecorder
- Google ML Kit (Face Detection)
- Activity Result APIs (modern permissions handling)
- Material Design Components

---

## 🛠️ How to Run

1. Clone the repository  
2. Open in Android Studio  
3. Run on a real Android device (sensors required)  
4. Grant microphone and camera permissions  
5. Try to unlock by meeting all 7 conditions!

---

## 🙋‍♂️ Author

Created by [Gal Angel](https://www.linkedin.com/in/galangel)  
Final project for Mobile Security course 📱🔒
