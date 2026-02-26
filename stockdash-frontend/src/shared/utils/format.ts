export const money = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD'
});

export const percent = new Intl.NumberFormat('en-US', {
  style: 'percent',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2
});

export function todayIso(): string {
  const date = new Date();
  return date.toISOString().slice(0, 10);
}
