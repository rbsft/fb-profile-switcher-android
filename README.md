# Facebook Profile Switcher.


**This app works only on a rooted Android device!**

It allows creating multiple Facebook profiles and switching between these profiles. New profile creation works by copying the entire app directory (for Facebook that's /data/data/com.facebook.katana) to separate place on device. Profile switch just swaps the current and the 'backup' directories.

You give a name to each profile for easier identification. 

This way you can create and maintain multiple separate accounts on your device. While apps like Facebook typically allow you to store several accounts, and switch between them, there are limitations when it comes to number of profiles, and also switching profiles is visible to the platform which can block all these accounts. With this app, switching profiles is not visible to the platform, and there's no limit on number of accounts - apart from storage capacity limitation. This means you can have tens or hundreds of separate, unrelated accounts on a single device.

Because the app manipulates other apps data, root permission is required on the device. Additionally - if using Magisk (highly recommended!) - please ensure that "Mount Namespace Mode" is set to Global.

The app currently works for Facebook, but can be easily extended to any other app (Instagram, Twitter, Tik Tok, etc.), if needed.