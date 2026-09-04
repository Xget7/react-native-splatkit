import { useRef, useState } from 'react';
import {
  PanResponder,
  Platform,
  StyleSheet,
  Text,
  View,
  type GestureResponderEvent,
  type PanResponderGestureState,
} from 'react-native';
import { SplatView, type SplatViewHandle } from 'react-native-splatkit';

/**
 * Worlds are far too big to commit, so the example reads them off the device.
 *
 *   adb push kitchen.spz /sdcard/Android/data/splatkit.example/files/world.spz
 *   adb push kitchen.glb /sdcard/Android/data/splatkit.example/files/collider.glb
 *
 * This is the app's own directory, readable with no runtime permission.
 * /sdcard/Download needs READ_EXTERNAL_STORAGE from API 29 on, which an example
 * should not have to ask for just to show a renderer.
 *
 * Without the collider the camera flies instead of walking.
 */
const FILES = 'file:///sdcard/Android/data/splatkit.example/files';
const WORLD = { uri: `${FILES}/world.spz` };
const COLLIDER = { uri: `${FILES}/collider.glb` };

const JOYSTICK_RADIUS = 60;
const WALK_SPEED = 1.6;

type Stats = {
  fps: number;
  frameMs: number;
  gpuMs: number;
  splatCount: number;
};

export default function App() {
  const splat = useRef<SplatViewHandle>(null);
  const [engine, setEngine] = useState('starting the engine');
  const [status, setStatus] = useState('waiting for a world');
  const [stats, setStats] = useState<Stats | null>(null);
  const [knob, setKnob] = useState({ x: 0, y: 0 });

  const walk = (gesture: PanResponderGestureState) => {
    const clamp = (v: number) =>
      Math.max(-JOYSTICK_RADIUS, Math.min(JOYSTICK_RADIUS, v));
    const x = clamp(gesture.dx);
    const y = clamp(gesture.dy);
    setKnob({ x, y });
    splat.current?.setWalkVelocity(
      (-y / JOYSTICK_RADIUS) * WALK_SPEED,
      (x / JOYSTICK_RADIUS) * WALK_SPEED
    );
  };

  const stop = () => {
    setKnob({ x: 0, y: 0 });
    splat.current?.setWalkVelocity(0, 0);
  };

  const joystick = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onPanResponderMove: (_e: GestureResponderEvent, g) => walk(g),
      onPanResponderRelease: stop,
      onPanResponderTerminate: stop,
    })
  ).current;

  return (
    <View style={styles.root}>
      <SplatView
        ref={splat}
        style={StyleSheet.absoluteFill}
        source={WORLD}
        collider={COLLIDER}
        renderScale={0.7}
        motionEnabled={false}
        statsInterval={500}
        onEngineReady={(e) =>
          setEngine(
            e.nativeEvent.available
              ? e.nativeEvent.gpu
              : `no renderer on this device${Platform.OS === 'ios' ? ' (iOS engine is not built yet)' : ''}`
          )
        }
        onWorldReady={(e) =>
          setStatus(`${e.nativeEvent.splatCount.toLocaleString()} splats`)
        }
        onWorldFailed={(e) =>
          setStatus(`world failed: ${e.nativeEvent.message}`)
        }
        onColliderReady={() => setStatus((s) => `${s}, walking`)}
        onColliderFailed={(e) =>
          setStatus((s) => `${s}, flying (${e.nativeEvent.message})`)
        }
        onStats={(e) => setStats(e.nativeEvent)}
      />

      <View style={styles.hud} pointerEvents="none">
        <Text style={styles.line}>{engine}</Text>
        <Text style={styles.line}>{status}</Text>
        {stats != null && (
          <Text style={styles.line}>
            {stats.fps.toFixed(0)} fps · {stats.frameMs.toFixed(1)} ms frame ·{' '}
            {stats.gpuMs.toFixed(1)} ms gpu
          </Text>
        )}
      </View>

      <View style={styles.joystick} {...joystick.panHandlers}>
        <View
          style={[
            styles.knob,
            { transform: [{ translateX: knob.x }, { translateY: knob.y }] },
          ]}
        />
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1, backgroundColor: '#000' },
  hud: {
    position: 'absolute',
    top: 48,
    left: 16,
    right: 16,
    gap: 2,
  },
  line: {
    color: '#fff',
    fontSize: 12,
    fontVariant: ['tabular-nums'],
    textShadowColor: '#000',
    textShadowRadius: 3,
  },
  joystick: {
    position: 'absolute',
    left: 32,
    bottom: 48,
    width: JOYSTICK_RADIUS * 2,
    height: JOYSTICK_RADIUS * 2,
    borderRadius: JOYSTICK_RADIUS,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.35)',
    alignItems: 'center',
    justifyContent: 'center',
  },
  knob: {
    width: 44,
    height: 44,
    borderRadius: 22,
    backgroundColor: 'rgba(255,255,255,0.55)',
  },
});
