# react-native-splatkit

React Native component for real-time Gaussian splatting.
Load an SPZ scene, put a `<SplatView />` in a tree, pick a quality preset, and walk through it from JavaScript.

```sh
npm install react-native-splatkit
```

## What this package is

A binding, and nothing more.
The renderer lives in [SplatKit](https://github.com/Xget7/splatkit-android), an Android engine published to Maven Central, and this package depends on it the way any Android app would:

```gradle
api "io.github.xget7:splatkit-android:0.1.0-alpha04"
```

There is no C++ here, no shaders, no copy of the engine.
A fix to the renderer is a new version of that artifact, not a release of this package, and an Android developer can work on the engine without ever installing Node.

## Use it

```tsx
import { SplatView } from 'react-native-splatkit';

<SplatView
  style={StyleSheet.absoluteFill}
  source={{ uri: 'file:///sdcard/Android/data/com.example.app/files/world.spz' }}
  collider={{ uri: 'file:///sdcard/Android/data/com.example.app/files/collider.glb' }}
  quality="medium"
  onEngineReady={(e) => console.log(e.nativeEvent.gpu)}
  onWorldReady={(e) => console.log(e.nativeEvent.splatCount, 'splats')}
/>;
```

The view has to have a size.
A surface with no height renders nothing and reports no error, so give it `flex: 1` or explicit dimensions.

One finger looks around, two fingers walk, a double tap toggles the gyroscope.
Without a collider the camera flies; with one it walks on the mesh.
World Labs exports both files for every world.

### Sources

The bytes never cross the bridge.
JavaScript hands over a location and the native side reads it on a background thread, because a world is tens to hundreds of megabytes and serialising that would stall the app for as long as it took.

`file://`, `content://`, `asset://` for a file in the app assets, `http://`, `https://`, or an absolute path.
A file on disk goes to the engine as a path and is mapped, not copied through the Java heap; the other schemes are streamed to the app's cache directory once and mapped from there, with `onLoadProgress` along the way.

### Props

| Prop | What it does |
|---|---|
| `source` | The world. SPZ versions 2 to 4; the format is detected from the bytes. |
| `collider` | A GLB mesh. Switches the camera from flying to walking. |
| `quality` | A preset name, `low`, `medium`, `high` (the default) or `ultra`, or a preset plus overrides: `{ preset: 'medium', renderScale: 0.8 }`. The overrides are `renderScale` (0.1 to 2, above 1 supersamples), `shDegree` (0 to 3, rounded, the harmonics degree drawn), `splatBudget` (0 draws all), `cullMarginDegrees` (0 to 90) and `linearBlending`. Out of range values are clamped with a warning in development; an unknown preset falls back to `high`. The reason behind each preset and its frame times are in the [engine's README](https://github.com/Xget7/splatkit-android/blob/main/packages/splatkit-android/README.md). |
| `cameraPose` | `{ x, y, z, yaw?, pitch? }`, meters and radians. Applied when it changes and again when the world and the collider become ready, so it can be set before the world loads. When walking the camera settles on the floor under the point. |
| `motionEnabled` | The gyroscope drives the look direction. |
| `lookSensitivity`, `walkSensitivity` | Gesture tuning. Radians per pixel for one finger looking (default 0.004) and meters per pixel for two finger walking (default 0.01). |
| `statsInterval` | Milliseconds between `onStats`. 0, the default, turns the event off. |

### Events

`onEngineReady` fires once with `{ available, gpu }`.
When `available` is false the device could not start the renderer and the view stays blank; every other call is a no-op.

`onLoadProgress` gives `{ kind, bytes, total }` at most every 100 ms while a source that is not a local file is being copied; `kind` is `world` or `collider` and `total` is -1 when the server did not say.
`onWorldReady` gives `{ splatCount }`, `onWorldFailed` and `onColliderFailed` give `{ message }`, `onColliderReady` takes no payload, and `onStats` gives `{ fps, frameMs, gpuMs, sortMs, splatCount, pose }`, where `pose` is the camera as of the last frame in the shape of `cameraPose`.
Read it to save a viewpoint and hand it back later.

### Imperative

```tsx
import { SplatView, type SplatViewHandle } from 'react-native-splatkit';

const splat = useRef<SplatViewHandle>(null);

splat.current?.setWalkVelocity(forward, right);          // meters per second, for a joystick
splat.current?.setCameraPose({ x: 0, y: 1.5, z: 0 });    // teleport; yaw and pitch optional
splat.current?.startBenchmark(10);                       // a reproducible turn, timings in logcat
```

## Requirements and setup

React Native 0.85 or newer with the New Architecture (the default since 0.76); there is no interop layer support.
Android 10 (API 29) and a Vulkan 1.1 device.

In `android/build.gradle` of the app set the floor the engine needs:

```groovy
ext {
    minSdkVersion = 29
}
```

The library pins 29 itself, so a lower app floor fails at build time with this package's name in the message rather than at runtime.

The engine ships `arm64-v8a` only.
An app that builds every ABI still installs on an x86_64 emulator and then dies on the first frame, so develop on a physical arm64 device, or keep the emulator from installing it at all with `reactNativeArchitectures=arm64-v8a` in `android/gradle.properties`.

### Expo

Works in a development build, not in Expo Go.
Raise the floor with `expo-build-properties`:

```json
["expo-build-properties", { "android": { "minSdkVersion": 29 } }]
```

### Remote sources

A release build refuses plain `http://` from Android 9 on, and the refusal arrives as `onWorldFailed` with "Cleartext HTTP traffic ... not permitted".
Serve worlds over `https://`, or allow cleartext in the host app's manifest or network security config for development.

### Retrying, unloading, caching

React Native resends a prop only when it changes, so after `onWorldFailed` a retry needs a new `uri` (a query string will do) or a new `key` on the view.
The engine has no unload call yet; setting `source` to `undefined` leaves the current world in place.
Sources that are not local files are copied once to the app's cache directory, keyed by URI, and mapped from there; a changed file behind the same URI is not noticed, so change the URI or clear the app cache.
The cache is capped at 512 MB and evicts the least recently used world first, so a handful of worlds stay and a season's worth does not.

### Children

`SplatView` does not lay out React children.
Put a HUD or a joystick in a sibling view, as the example does.

## Performance

Measured on a Xiaomi Mi 9 (Adreno 640), the 500k splat World Labs kitchen with its collider, preset `medium`, release builds, camera at the origin, same session, phone cooled between runs.
The engine's own benchmark reports the numbers (one turn over 10 s); the binding adds nothing to the frame.

| Host | GPU ms p50 | frame ms p50 | fps |
|---|---|---|---|
| Engine dev app | 12.8 | 16.7 | 59.6 |
| This package, example app | 12.8 | 16.7 | 59.6 |

`statsInterval={16}`, one event per frame, measured the same frame time as `0`, so a HUD can run at any rate.
A remote world is streamed to disk and mapped, so loading a 51 MB file (3.6 M splats) kept the Java heap under 8 MB; read into memory it peaked at 58 MB.
Two views on one screen both render; expect the frame rate to split between them.
The engine's numbers per preset and per scene are in [docs/BENCHMARKS.md](https://github.com/Xget7/splatkit-android/blob/main/docs/BENCHMARKS.md).

## iOS

The component, its props and its events exist on iOS and compile, but there is no engine behind them yet.
`onEngineReady` reports `available: false`, which is the same signal an Android device without Vulkan gives, so an app that already handles that case needs no extra branch.

iOS will link a published SplatKit package the same way Android links the AAR.
Copying a renderer into this repository would defeat the point of the split.

## Running the example

The example reads a world off the device rather than committing one:

```sh
adb push kitchen.spz /sdcard/Android/data/splatkit.example/files/world.spz
adb push kitchen.glb /sdcard/Android/data/splatkit.example/files/collider.glb
yarn && yarn example android
```

That is the app's own directory, so it needs no runtime permission.
Reading `/sdcard/Download` instead means asking for `READ_EXTERNAL_STORAGE`, which is the host app's decision to make, not this package's.

Everything from the engine logs under the tag `SplatKit`.
MIUI hides application logs until `adb shell setprop persist.log.tag.SplatKit V`.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the development loop, the checks that run on a pull request and the conventions.

## License

MIT.
