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

/**
 * The fully populated quality struct the wrapper builds with `normalizeQuality`.
 * Every field is present; -1 means "keep the preset's value". See `quality.ts`.
 */
type NativeQualityStruct = {
  preset: string;
  renderScale: CodegenTypes.Double;
  shDegree: CodegenTypes.Int32;
  splatBudget: CodegenTypes.Int32;
  cullMarginDegrees: CodegenTypes.Double;
  /** -1 unset, 0 false, 1 true; codegen has no optional boolean with a sentinel. */
  linearBlending: CodegenTypes.Int32;
};

/** Where the camera stands, in meters, and where it looks, in radians. */
export type CameraPose = {
  x: CodegenTypes.Double;
  y: CodegenTypes.Double;
  z: CodegenTypes.Double;
  yaw?: CodegenTypes.Double;
  pitch?: CodegenTypes.Double;
};

type WorldReadyEvent = {
  splatCount: CodegenTypes.Int32;
};

type FailureEvent = {
  message: string;
};

type LoadProgressEvent = {
  /** `world` or `collider`. */
  kind: string;
  bytes: CodegenTypes.Double;
  /** -1 when the source does not say how big it is. */
  total: CodegenTypes.Double;
};

type StatsEvent = {
  fps: CodegenTypes.Double;
  frameMs: CodegenTypes.Double;
  gpuMs: CodegenTypes.Double;
  sortMs: CodegenTypes.Double;
  splatCount: CodegenTypes.Int32;
  /** The camera as of the last frame. */
  pose: {
    x: CodegenTypes.Double;
    y: CodegenTypes.Double;
    z: CodegenTypes.Double;
    yaw: CodegenTypes.Double;
    pitch: CodegenTypes.Double;
  };
};

export interface NativeProps extends ViewProps {
  source?: SplatSource;
  collider?: SplatSource;

  /** Preset plus overrides; `high` with no overrides when omitted. */
  quality?: NativeQualityStruct;
  /**
   * Where the camera starts. Applied when it changes and again when the world
   * and the collider become ready, so it can be set before the world loads.
   * When walking, the camera settles on the floor under the point.
   */
  cameraPose?: CameraPose;

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
  /** Bytes copied so far for a source that is not a local file; at most every 100 ms. */
  onLoadProgress?: CodegenTypes.DirectEventHandler<LoadProgressEvent>;
  onStats?: CodegenTypes.DirectEventHandler<StatsEvent>;
}

export type SplatViewNativeComponentType = HostComponent<NativeProps>;

interface NativeCommands {
  /** Continuous walking in meters per second, for an on screen joystick. */
  setWalkVelocity: (
    viewRef: React.ComponentRef<SplatViewNativeComponentType>,
    forward: CodegenTypes.Double,
    right: CodegenTypes.Double
  ) => void;
  /** Teleport: position in meters, yaw and pitch in radians. */
  setCameraPose: (
    viewRef: React.ComponentRef<SplatViewNativeComponentType>,
    x: CodegenTypes.Double,
    y: CodegenTypes.Double,
    z: CodegenTypes.Double,
    yaw: CodegenTypes.Double,
    pitch: CodegenTypes.Double
  ) => void;
  /** A reproducible turn; the frame time distribution lands in logcat under the tag SplatKit. */
  startBenchmark: (
    viewRef: React.ComponentRef<SplatViewNativeComponentType>,
    seconds: CodegenTypes.Double
  ) => void;
}

export const Commands: NativeCommands = codegenNativeCommands<NativeCommands>({
  supportedCommands: ['setWalkVelocity', 'setCameraPose', 'startBenchmark'],
});

export default codegenNativeComponent<NativeProps>(
  'SplatView'
) as SplatViewNativeComponentType;
