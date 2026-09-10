/**
 * `quality` as the app writes it, and as the native side reads it.
 *
 * The native struct is always fully populated: an override the app did not set
 * is sent as `UNSET` (-1). Codegen on some platforms zero-fills struct fields
 * that are absent, and 0 is a valid render scale and a valid budget, so
 * absence cannot mean "keep the preset's". A negative number can.
 */

export type QualityPreset = 'low' | 'medium' | 'high' | 'ultra';

export type QualitySettings = {
  /** `low`, `medium`, `high` (the default) or `ultra`. */
  preset?: QualityPreset;
  /** Fraction of the surface the splats are drawn at, 0.1 to 2. Above 1 supersamples. */
  renderScale?: number;
  /** Spherical harmonics degree drawn, 0 to 3, capped by what the world carries. */
  shDegree?: number;
  /** Most splats drawn per frame through the level of detail tree; 0 draws them all. Applies to worlds loaded after it is set. */
  splatBudget?: number;
  /** Angular margin around the view kept drawn so a turn never meets an empty edge, 0 to 90. */
  cullMarginDegrees?: number;
  /** Blend in linear light instead of the encoded space the training used. */
  linearBlending?: boolean;
};

/** What crosses to native. Every field present; `UNSET` keeps the preset's value. */
export type NativeQuality = {
  preset: string;
  renderScale: number;
  shDegree: number;
  splatBudget: number;
  cullMarginDegrees: number;
  /** -1 unset, 0 false, 1 true. */
  linearBlending: number;
};

export const UNSET = -1;

const PRESETS: readonly QualityPreset[] = ['low', 'medium', 'high', 'ultra'];
/** The native field is 32 bit; anything larger would saturate silently on the way over. */
const INT32_MAX = 2147483647;

type Warn = (message: string) => void;

// One line per distinct problem for the life of the app, in development and
// in release alike: a clamped value changes what is drawn, and a store build
// with no record of it is the kind of bug nobody can attribute later.
const warned = new Set<string>();
const defaultWarn: Warn = (message) => {
  if (warned.has(message)) return;
  warned.add(message);
  console.warn(`[react-native-splatkit] ${message}`);
};

function clamped(
  name: string,
  value: number | undefined,
  min: number,
  max: number,
  integer: boolean,
  warn: Warn
): number {
  if (value === undefined) return UNSET;
  if (!Number.isFinite(value)) {
    warn(`quality.${name} is ${value}; ignoring it`);
    return UNSET;
  }
  let v = integer ? Math.round(value) : value;
  if (v < min || v > max) {
    warn(`quality.${name} ${value} is outside ${min} to ${max}; clamping`);
    v = Math.min(max, Math.max(min, v));
  }
  return v;
}

export function normalizeQuality(
  quality: QualityPreset | QualitySettings | undefined,
  warn: Warn = defaultWarn
): NativeQuality {
  let settings: QualitySettings;
  if (quality == null) {
    settings = {};
  } else if (typeof quality === 'string') {
    settings = { preset: quality };
  } else if (typeof quality === 'object') {
    settings = quality;
  } else {
    warn(`quality must be a preset name or an object, got ${typeof quality}`);
    settings = {};
  }

  let preset: QualityPreset = 'high';
  if (settings.preset !== undefined) {
    if (PRESETS.includes(settings.preset)) {
      preset = settings.preset;
    } else {
      warn(`unknown quality preset '${String(settings.preset)}'; using 'high'`);
    }
  }

  return {
    preset,
    renderScale: clamped(
      'renderScale',
      settings.renderScale,
      0.1,
      2,
      false,
      warn
    ),
    shDegree: clamped('shDegree', settings.shDegree, 0, 3, true, warn),
    splatBudget: clamped(
      'splatBudget',
      settings.splatBudget,
      0,
      INT32_MAX,
      true,
      warn
    ),
    cullMarginDegrees: clamped(
      'cullMarginDegrees',
      settings.cullMarginDegrees,
      0,
      90,
      false,
      warn
    ),
    linearBlending:
      settings.linearBlending === undefined
        ? UNSET
        : settings.linearBlending
          ? 1
          : 0,
  };
}
