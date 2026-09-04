import { forwardRef, useImperativeHandle, useRef } from 'react';
import type { ViewProps } from 'react-native';
import NativeSplatView, {
  Commands,
  type NativeProps,
} from './SplatViewNativeComponent';

export type { SplatSource } from './SplatViewNativeComponent';

export type SplatViewProps = Omit<NativeProps, keyof ViewProps> & ViewProps;

export type SplatViewHandle = {
  /**
   * Continuous walking in meters per second: forward is where the camera looks,
   * right is sideways. Call it every frame from a joystick and call it with
   * zeroes when the finger lifts.
   */
  setWalkVelocity: (forward: number, right: number) => void;
  /** A reproducible turn. The frame time distribution lands in logcat under the tag SplatKit. */
  startBenchmark: (seconds?: number) => void;
};

/**
 * A walkable Gaussian splat world.
 *
 * The view has to have a size; a `SurfaceView` with no height renders nothing
 * and reports no error, so give it `flex: 1` or explicit dimensions.
 */
const SplatViewComponent = forwardRef<SplatViewHandle, SplatViewProps>(
  (props, ref) => {
    const nativeRef = useRef<React.ElementRef<typeof NativeSplatView>>(null);

    useImperativeHandle(ref, () => ({
      setWalkVelocity(forward: number, right: number) {
        if (nativeRef.current == null) return;
        Commands.setWalkVelocity(nativeRef.current, forward, right);
      },
      startBenchmark(seconds = 10) {
        if (nativeRef.current == null) return;
        Commands.startBenchmark(nativeRef.current, seconds);
      },
    }));

    return <NativeSplatView ref={nativeRef} {...props} />;
  }
);

SplatViewComponent.displayName = 'SplatView';

export const SplatView = SplatViewComponent;
