import { describe, expect, it, jest } from '@jest/globals';
import { normalizeQuality, UNSET } from '../quality';

const noWarn = () => {};

describe('normalizeQuality', () => {
  it('turns a preset name into a struct with every override unset', () => {
    expect(normalizeQuality('medium', noWarn)).toEqual({
      preset: 'medium',
      renderScale: UNSET,
      shDegree: UNSET,
      splatBudget: UNSET,
      cullMarginDegrees: UNSET,
      linearBlending: UNSET,
    });
  });

  it('defaults to high when nothing is given', () => {
    expect(normalizeQuality(undefined, noWarn).preset).toBe('high');
  });

  it('keeps overrides and marks the rest unset', () => {
    const q = normalizeQuality(
      { preset: 'low', renderScale: 0.8, linearBlending: true },
      noWarn
    );
    expect(q).toEqual({
      preset: 'low',
      renderScale: 0.8,
      shDegree: UNSET,
      splatBudget: UNSET,
      cullMarginDegrees: UNSET,
      linearBlending: 1,
    });
  });

  it('falls back to high and warns on an unknown preset', () => {
    const warn = jest.fn();
    // A typo has to type check to reach here, so cast.
    const q = normalizeQuality({ preset: 'ulta' as 'ultra' }, warn);
    expect(q.preset).toBe('high');
    expect(warn).toHaveBeenCalledWith(expect.stringContaining('ulta'));
  });

  it('clamps out of range values and warns', () => {
    const warn = jest.fn();
    const q = normalizeQuality(
      {
        preset: 'high',
        renderScale: 5,
        shDegree: 7,
        splatBudget: -3,
        cullMarginDegrees: 200,
      },
      warn
    );
    expect(q.renderScale).toBe(2);
    expect(q.shDegree).toBe(3);
    expect(q.splatBudget).toBe(0);
    expect(q.cullMarginDegrees).toBe(90);
    expect(warn).toHaveBeenCalledTimes(4);
  });

  it('rounds a fractional shDegree and splatBudget', () => {
    const q = normalizeQuality(
      { preset: 'high', shDegree: 1.6, splatBudget: 1000.4 },
      noWarn
    );
    expect(q.shDegree).toBe(2);
    expect(q.splatBudget).toBe(1000);
  });

  it('treats NaN and Infinity as unset and warns', () => {
    const warn = jest.fn();
    const q = normalizeQuality(
      { preset: 'high', renderScale: NaN, cullMarginDegrees: Infinity },
      warn
    );
    expect(q.renderScale).toBe(UNSET);
    expect(q.cullMarginDegrees).toBe(UNSET);
    expect(warn).toHaveBeenCalledTimes(2);
  });

  it('pins the unset sentinel to -1, which QualityMapper.kt hardcodes', () => {
    expect(UNSET).toBe(-1);
  });

  it('sends an explicit false as 0, not unset', () => {
    const q = normalizeQuality(
      { preset: 'ultra', linearBlending: false },
      noWarn
    );
    expect(q.linearBlending).toBe(0);
  });

  it('never emits a user value as the unset sentinel', () => {
    const q = normalizeQuality(
      {
        preset: 'high',
        renderScale: -1,
        shDegree: -1,
        splatBudget: -1,
        cullMarginDegrees: -1,
      },
      noWarn
    );
    expect(q.renderScale).toBeGreaterThanOrEqual(0);
    expect(q.shDegree).toBeGreaterThanOrEqual(0);
    expect(q.splatBudget).toBeGreaterThanOrEqual(0);
    expect(q.cullMarginDegrees).toBeGreaterThanOrEqual(0);
  });

  it('treats null like undefined and warns on a number', () => {
    const warn = jest.fn();
    expect(normalizeQuality(null as unknown as undefined, warn).preset).toBe(
      'high'
    );
    expect(warn).not.toHaveBeenCalled();
    expect(normalizeQuality(5 as unknown as 'high', warn).preset).toBe('high');
    expect(warn).toHaveBeenCalledTimes(1);
  });

  it('caps splatBudget at the 32 bit maximum', () => {
    const q = normalizeQuality({ preset: 'high', splatBudget: 1e12 }, noWarn);
    expect(q.splatBudget).toBe(2147483647);
  });
});
