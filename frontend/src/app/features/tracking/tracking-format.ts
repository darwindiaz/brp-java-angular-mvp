const grouped = new Intl.NumberFormat('es-CO');

export function displayDecimal(value: string, minimumFractionDigits = 0): string {
  const parts = /^(-?)(\d+)(?:\.(\d+))?$/.exec(value);
  if (!parts) return value;
  const [, sign, integer, fraction = ''] = parts;
  const decimals = fraction.padEnd(minimumFractionDigits, '0');
  return `${sign}${grouped.format(BigInt(integer))}${decimals ? `,${decimals}` : ''}`;
}

export function calendarDate(value: string | null): string {
  if (!value) return '—';
  const [year, month, day] = value.split('-');
  return day ? `${day}/${month}/${year}` : value;
}
