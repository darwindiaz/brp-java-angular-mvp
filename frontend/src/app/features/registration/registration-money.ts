const SHARE_TOTAL = 1_000_000n;
const MAX_CENTS = 10n ** 19n - 1n;

function scaled(value: string, places: number, integerDigits: number): bigint | null {
  const match = new RegExp(`^(\\d{1,${integerDigits}})(?:[.,](\\d{1,${places}}))?$`).exec(value.trim());
  if (!match) return null;
  return BigInt(match[1] + (match[2] ?? '').padEnd(places, '0'));
}

export function money(value: string) { return scaled(value, 2, 17); }
export function share(value: string) { return scaled(value, 4, 3); }

export function decimal(value: bigint, places = 2): string {
  const digits = value.toString().padStart(places + 1, '0');
  return `${digits.slice(0, -places)}.${digits.slice(-places)}`;
}

export function reconcile(bidders: readonly { percentage: string; payments: readonly { amount: string }[] }[]) {
  const shares = bidders.map(bidder => share(bidder.percentage));
  const amounts = bidders.map(bidder => bidder.payments.map(payment => money(payment.amount)));
  const payments = amounts.map(items => items.reduce<bigint>((sum, item) => sum + (item ?? 0n), 0n));
  const total = payments.reduce((sum, amount) => sum + amount, 0n);
  const issues: string[] = [];
  const validShares = shares.every(value => value !== null && value > 0n && value <= SHARE_TOTAL);
  const validMoney = amounts.every(items => items.every(item => item !== null && item > 0n));
  if (!validShares || shares.reduce<bigint>((sum, value) => sum + (value ?? 0n), 0n) !== SHARE_TOTAL) {
    issues.push('Las participaciones deben sumar exactamente 100 %. Usa hasta cuatro decimales.');
  }
  if (!validMoney || total <= 0n || total > MAX_CENTS) {
    issues.push('Usa importes positivos de hasta 17 enteros y dos decimales. El total debe respetar el mismo límite.');
  }
  if (validShares && validMoney && total > 0n) {
    shares.forEach((value, index) => {
      if (payments[index] * SHARE_TOTAL !== total * (value ?? 0n)) {
        issues.push(`Los pagos del oferente ${index + 1} deben igualar su participación exacta del total.`);
      }
    });
  }
  return { issues, total };
}
