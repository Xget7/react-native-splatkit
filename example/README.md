# react-native-splatkit example

Walks a World Labs world with an on screen joystick, switches presets live and shows the engine's stats.

Push a world and its collider into the app's own directory, then run it on a physical arm64 device:

```sh
adb push kitchen.spz /sdcard/Android/data/splatkit.example/files/world.spz
adb push kitchen.glb /sdcard/Android/data/splatkit.example/files/collider.glb
yarn && yarn example android
```

The example resolves the library from the workspace source through the `react-native-splatkit-source` condition in `metro.config.js`, so edits to `src/` show up on reload.
Release builds run with R8 on so the example proves the published engine survives minification.
