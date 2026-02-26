export const money = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD'
});

export function todayIso(): string {
  const date = new Date();
  return date.toISOString().slice(0, 10);
}
