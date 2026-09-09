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
 * How much the renderer spends per frame. `preset` picks the engine's
 * `RenderQuality.LOW`, `MEDIUM`, `HIGH` or `ULTRA`; every other field
 * overrides one value of that preset. Omit a field to keep the preset's.
 * The reason behind each preset and its frame times are in the engine's README.
 */
export type QualitySettings = {
  /** `low`, `medium`, `high` (the default) or `ultra`. */
  preset?: string;
  /** Fraction of the surface the splats are drawn at, 0.1 to 2. Above 1 supersamples. */
  renderScale?: CodegenTypes.Double;
  /** Spherical harmonics degree drawn, 0 to 3, capped by what the world carries. Takes effect on the next frame. */
  shDegree?: CodegenTypes.Int32;
  /** Most splats drawn per frame through the level of detail tree; 0 draws them all. Applies to worlds loaded after it is set. */
  splatBudget?: CodegenTypes.Int32;
  /** Angular margin around the view kept drawn so a turn never meets an empty edge. */
  cullMarginDegrees?: CodegenTypes.Double;
  /** Blend in linear light instead of the encoded space the training used. */
  linearBlending?: boolean;
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
  quality?: QualitySettings;
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
  /** Teleport: position in meters, yaw and pitch in radians. */
  setCameraPose: (
    viewRef: React.ElementRef<SplatViewNativeComponentType>,
    x: CodegenTypes.Double,
    y: CodegenTypes.Double,
    z: CodegenTypes.Double,
    yaw: CodegenTypes.Double,
    pitch: CodegenTypes.Double
  ) => void;
  /** A reproducible turn; the frame time distribution lands in logcat under the tag SplatKit. */
  startBenchmark: (
    viewRef: React.ElementRef<SplatViewNativeComponentType>,
    seconds: CodegenTypes.Double
  ) => void;
}

export const Commands: NativeCommands = codegenNativeCommands<NativeCommands>({
  supportedCommands: ['setWalkVelocity', 'setCameraPose', 'startBenchmark'],
});

export default codegenNativeComponent<NativeProps>(
  'SplatView'
) as SplatViewNativeComponentType;
