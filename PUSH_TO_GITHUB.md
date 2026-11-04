# Push this Android-ready source to your empty GitHub repository (Windows)

1. Extract this ZIP so that `Cargo.toml`, `android/`, `apps/` and `.github/` are directly inside one folder. Do not push the outer ZIP folder as a nested directory.
2. Install Git for Windows, open Command Prompt in that extracted folder, then run these commands one at a time:

```bat
git init
git branch -M main
git remote add origin https://github.com/Prathvirajkharvi/effectcraft-for-android.git
git add .
git commit -m "Add Android-optimized EffectCraft"
git push -u origin main
```

3. When Git asks you to sign in, complete the browser sign-in / Git Credential Manager flow. Do not put a personal access token in the remote URL or in source files.
4. The push to `main` starts **Actions → Build Android APK**. When it completes, open the workflow run and download the `effectcraft-android-apk` artifact.

The pasted personal access token from the chat should be revoked in GitHub settings; do not reuse it.
