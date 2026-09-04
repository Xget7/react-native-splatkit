import {
  codegenNativeComponent,
  codegenNativeCommands,
  type CodegenTypes,
  type ViewProps,
  type HostComponent,
} from 'react-native';

/**
 * Where a world or a collider comes from.
 *
 * The bytes never cross the bridge. JavaScript hands over a location and the
 * native side reads it on a background thread, because a world is tens or
 * hundreds of megabytes and serialising that through the bridge would stall
 * the app for as long as it took.
 *
 * Accepted: `file://`, `content://`, `asset://` for a file in the app assets,
 * `http://` and `https://`, or an absolute path.
 */
export type SplatSource = {
  uri: string;
};

type EngineReadyEvent = {
  /** False when the device could not start the renderer; the view stays blank. */
  available: boolean;
  /** GPU name and graphics API version as the driver reports them, empty when unavailable. */
  gpu: string;
};

type WorldReadyEvent = {
  splatCount: CodegenTypes.Int32;
};

type FailureEvent = {
  message: string;
};

type StatsEvent = {
  fps: CodegenTypes.Double;
  frameMs: CodegenTypes.Double;
  gpuMs: CodegenTypes.Double;
  sortMs: CodegenTypes.Double;
  splatCount: CodegenTypes.Int32;
};

export interface NativeProps extends ViewProps {
  source?: SplatSource;
  collider?: SplatSource;

  /** Fraction of the surface the splats are drawn at before upscaling. 0.7 is hard to tell from 1.0 and much cheaper. */
  renderScale?: CodegenTypes.WithDefault<CodegenTypes.Double, 1.0>;
  /** Most splats drawn per frame through the level of detail tree. 0 draws them all. */
  splatBudget?: CodegenTypes.WithDefault<CodegenTypes.Int32, 0>;
  /** Highest spherical harmonics degree kept from the file, 0 to 3. */
  maxShDegree?: CodegenTypes.WithDefault<CodegenTypes.Int32, 3>;
  linearBlending?: CodegenTypes.WithDefault<boolean, false>;

  /** The gyroscope drives the look direction. */
  motionEnabled?: CodegenTypes.WithDefault<boolean, false>;
  lookSensitivity?: CodegenTypes.WithDefault<CodegenTypes.Double, 0.004>;
  walkSensitivity?: CodegenTypes.WithDefault<CodegenTypes.Double, 0.01>;

  /** Milliseconds between `onStats`. 0 turns the event off. */
  statsInterval?: CodegenTypes.WithDefault<CodegenTypes.Int32, 0>;

  onEngineReady?: CodegenTypes.DirectEventHandler<EngineReadyEvent>;
  onWorldReady?: CodegenTypes.DirectEventHandler<WorldReadyEvent>;
  onWorldFailed?: CodegenTypes.DirectEventHandler<FailureEvent>;
  onColliderReady?: CodegenTypes.DirectEventHandler<null>;
  onColliderFailed?: CodegenTypes.DirectEventHandler<FailureEvent>;
  onStats?: CodegenTypes.DirectEventHandler<StatsEvent>;
}

export type SplatViewNativeComponentType = HostComponent<NativeProps>;

interface NativeCommands {
  /** Continuous walking in meters per second, for an on screen joystick. */
  setWalkVelocity: (
    viewRef: React.ElementRef<SplatViewNativeComponentType>,
    forward: CodegenTypes.Double,
    right: CodegenTypes.Double
  ) => void;
  /** A reproducible turn; the frame time distribution lands in logcat under the tag SplatKit. */
  startBenchmark: (
    viewRef: React.ElementRef<SplatViewNativeComponentType>,
    seconds: CodegenTypes.Double
  ) => void;
}

export const Commands: NativeCommands = codegenNativeCommands<NativeCommands>({
  supportedCommands: ['setWalkVelocity', 'startBenchmark'],
});

export default codegenNativeComponent<NativeProps>(
  'SplatView'
) as SplatViewNativeComponentType;
