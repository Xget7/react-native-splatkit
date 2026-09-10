import { forwardRef, useImperativeHandle, useRef } from 'react';
import type { ViewProps } from 'react-native';
import NativeSplatView, {
  Commands,
  type NativeProps,
} from './SplatViewNativeComponent';
import {
  normalizeQuality,
  type QualityPreset,
  type QualitySettings,
} from './quality';
import type { CameraPose } from './SplatViewNativeComponent';

export type {
  SplatSource,
  CameraPose,
  EngineReadyEvent,
  WorldReadyEvent,
  FailureEvent,
  LoadProgressEvent,
  StatsEvent,
} from './SplatViewNativeComponent';
export type { QualityPreset, QualitySettings } from './quality';

export type SplatViewProps = Omit<NativeProps, keyof ViewProps | 'quality'> &
  ViewProps & {
    /**
     * A preset name, or a preset plus overrides:
     * `quality="medium"` or `quality={{ preset: 'medium', renderScale: 0.8 }}`.
     * `high` when omitted. Out of range values are clamped with a warning in
     * development; an unknown preset falls back to `high`.
     */
    quality?: QualityPreset | QualitySettings;
  };

export type SplatViewHandle = {
  /**
   * Continuous walking in meters per second: forward is where the camera looks,
   * right is sideways. Call it every frame from a joystick and call it with
   * zeroes when the finger lifts.
   */
  setWalkVelocity: (forward: number, right: number) => void;
  /**
   * Teleport. Position in meters, yaw and pitch in radians; when walking the
   * camera settles on the floor under the point. The `cameraPose` prop does the
   * same declaratively; use this for a "go here" button.
   */
  setCameraPose: (pose: CameraPose) => void;
  /** A reproducible turn. The frame time distribution lands in logcat under the tag SplatKit. */
  startBenchmark: (seconds?: number) => void;
};

/**
 * A walkable Gaussian splat world.
 *
 * The view has to have a size; a `SurfaceView` with no height renders nothing
 * and reports no error, so give it `flex: 1` or explicit dimensions.
 * Children are not laid out; put a HUD in a sibling view.
 */
const SplatViewComponent = forwardRef<SplatViewHandle, SplatViewProps>(
  ({ quality, ...props }, ref) => {
    const nativeRef = useRef<React.ComponentRef<typeof NativeSplatView>>(null);

    useImperativeHandle(
      ref,
      () => ({
        setWalkVelocity(forward: number, right: number) {
          if (nativeRef.current == null) return;
          Commands.setWalkVelocity(nativeRef.current, forward, right);
        },
        setCameraPose({ x, y, z, yaw = 0, pitch = 0 }: CameraPose) {
          if (nativeRef.current == null) return;
          Commands.setCameraPose(nativeRef.current, x, y, z, yaw, pitch);
        },
        startBenchmark(seconds = 10) {
          if (nativeRef.current == null) return;
          Commands.startBenchmark(nativeRef.current, seconds);
        },
      }),
      []
    );

    // Normalised on every render: it is a handful of comparisons, Fabric diffs
    // the struct by value, and the Android side skips a value it already
    // applied, so a memo here would only add a dependency list to keep in sync.
    return (
      <NativeSplatView
        ref={nativeRef}
        quality={normalizeQuality(quality)}
        {...props}
      />
    );
  }
);

SplatViewComponent.displayName = 'SplatView';

export const SplatView = SplatViewComponent;
