# Korean Flashcards — Clean Rebuild

এই ফোল্ডারে **কোনো binary ফাইল নেই** (gradlew wrapper jar, PNG icon ইত্যাদি বাদ
দেওয়া হয়েছে) — তাই phone দিয়ে zip download → extract → git push করলে কোনো
ফাইল ভাঙার/হারানোর ভয় নেই। সব plain text।

## কেন gradlew নেই?
GitHub Actions workflow (`.github/workflows/build-apk.yml`) নিজেই সঠিক Gradle
version install করে নেয় (`gradle/actions/setup-gradle`), তাই `gradlew` script
বা তার সাথের `gradle-wrapper.jar` লাগে না। ভবিষ্যতে PC-তে Android Studio দিয়ে
এই project খুললে, Android Studio নিজে থেকেই wrapper regenerate করে দেবে।

## GitHub-এ আপলোড করার ধাপ (Termux দিয়ে, আগেরবার যেভাবে করেছিলে)

**পুরনো repo content সম্পূর্ণ মুছে নতুন করে বসাতে হবে**, কারণ আগের repo-তে
ভাঙা ফাইল আছে। সবচেয়ে সহজ উপায় — repo টা মুছে আবার নতুন করে বানানো:

1. GitHub-এ গিয়ে পুরনো repo-র **Settings → Danger Zone → Delete this repository**
   (অথবা নতুন নামে একটা repo বানাও, যেমন `korean-flashcards-android-v2`)
2. নতুন খালি repo বানাও (README/gitignore কিছু যোগ না করে)
3. Termux-এ:
   ```
   cd ~/storage/downloads
   unzip korean-flashcards-clean.zip
   cd korean-flashcards-clean
   git init
   git remote add origin https://github.com/jubaerDev/তোমার-নতুন-repo-নাম.git
   git add .
   git commit -m "Clean rebuild - no binary files"
   git branch -M main
   git push -u origin main
   ```
   (Username/Password চাইলে GitHub username আর **নতুন** Personal Access Token
   ব্যবহার করবে — আগেরটা delete করে দিয়েছিলে মনে আছে তো)

4. Push শেষ হলে GitHub repo-র **Actions** ট্যাবে গিয়ে build শুরু হচ্ছে কিনা দেখো

## যদি এবারও কোনো ফাইল সমস্যা করে
সব ফাইলই এখন ছোট plain-text (.kt, .xml, .kts, .properties) — কোনোটাই কয়েক KB
এর বেশি না, তাই transfer এ ভাঙার সম্ভাবনা প্রায় নেই। তারপরও কোনো build error
এলে, সেই error message দিলে ঠিক করে দেব।
