# react-native-splatkit

Walkable Gaussian splat worlds in React Native.
Load a World Labs Marble `.spz`, put a `<SplatView />` in a tree, and walk through the scene.

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
  source={{ uri: 'file:///sdcard/Download/world.spz' }}
  collider={{ uri: 'file:///sdcard/Download/collider.glb' }}
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
A file on disk goes to the engine as a path and is mapped, not copied through the Java heap; the other schemes are read to bytes first.

### Props

| Prop | What it does |
|---|---|
| `source` | The world. SPZ versions 2 to 4; the format is detected from the bytes. |
| `collider` | A GLB mesh. Switches the camera from flying to walking. |
| `quality` | A preset name, `low`, `medium`, `high` (the default) or `ultra`, or a preset plus overrides: `{ preset: 'medium', renderScale: 0.8 }`. The overrides are `renderScale` (0.1 to 2, above 1 supersamples), `shDegree` (0 to 3, the harmonics degree drawn), `splatBudget` (0 draws all), `cullMarginDegrees` and `linearBlending`. The reason behind each preset and its frame times are in the [engine's README](https://github.com/Xget7/splatkit-android/blob/main/packages/splatkit-android/README.md). |
| `cameraPose` | `{ x, y, z, yaw?, pitch? }`, meters and radians. Applied when it changes and again when the world and the collider become ready, so it can be set before the world loads. When walking the camera settles on the floor under the point. |
| `motionEnabled` | The gyroscope drives the look direction. |
| `lookSensitivity`, `walkSensitivity` | Gesture tuning. |
| `statsInterval` | Milliseconds between `onStats`. 0, the default, turns the event off. |

### Events

`onEngineReady` fires once with `{ available, gpu }`.
When `available` is false the device could not start the renderer and the view stays blank; every other call is a no-op.

`onWorldReady` gives `{ splatCount }`, `onWorldFailed` and `onColliderFailed` give `{ message }`, `onColliderReady` takes no payload, and `onStats` gives `{ fps, frameMs, gpuMs, sortMs, splatCount, pose }`, where `pose` is the camera as of the last frame in the shape of `cameraPose`.
Read it to save a viewpoint and hand it back later.

### Imperative

```tsx
const splat = useRef<SplatViewHandle>(null);

splat.current?.setWalkVelocity(forward, right);          // meters per second, for a joystick
splat.current?.setCameraPose({ x: 0, y: 1.5, z: 0 });    // teleport; yaw and pitch optional
splat.current?.startBenchmark(10);                       // a reproducible turn, timings in logcat
```

## Requirements

New architecture only.
Android 10 (API 29) and a Vulkan 1.1 device, `arm64-v8a` only: the engine ships that ABI alone, so an x86_64 emulator installs and then dies on the first frame.
Develop on a physical arm64 device.

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

## License

MIT.
