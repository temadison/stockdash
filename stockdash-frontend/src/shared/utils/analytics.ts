export function daysBetween(startIso: string, endIso: string): number {
  const start = new Date(`${startIso}T00:00:00`);
  const end = new Date(`${endIso}T00:00:00`);
  return Math.max(0, Math.round((end.getTime() - start.getTime()) / 86400000));
}

export function computeReturn(startValue: number, endValue: number): number | null {
  if (startValue <= 0) return null;
  return (endValue - startValue) / startValue;
}

export function computeCagr(startValue: number, endValue: number, startIso: string, endIso: string): number | null {
  const elapsedDays = daysBetween(startIso, endIso);
  const years = elapsedDays / 365.2425;
  if (startValue <= 0 || endValue <= 0 || years <= 0) return null;
  return Math.pow(endValue / startValue, 1 / years) - 1;
}
