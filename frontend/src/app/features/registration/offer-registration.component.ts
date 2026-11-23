import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import {
  CatalogItem, DiscountPreview, IdentificationType, PaymentMethod, RegisteredOffer,
  RegistrationApi, registrationError,
} from './registration-api.service';
import { decimal, money, reconcile, share } from './registration-money';

@Component({
  selector: 'app-offer-registration',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './offer-registration.component.html',
  styleUrl: './offer-registration.component.scss',
})
export class OfferRegistrationComponent implements OnInit {
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly api = inject(RegistrationApi);
  protected readonly form = this.fb.group({
    brpCode: ['', Validators.required],
    bidders: this.fb.array([this.bidder()]),
  });
  protected readonly bidders = this.form.controls.bidders;
  private readonly changes = toSignal(this.form.valueChanges);
  protected readonly catalog = signal<CatalogItem[]>([]);
  protected readonly loadingCatalog = signal(false);
  protected readonly catalogError = signal('');
  protected readonly busy = signal<'preview' | 'register' | null>(null);
  protected readonly error = signal('');
  protected readonly submitted = signal(false);
  protected readonly registered = signal<RegisteredOffer | null>(null);
  private readonly calculatedPreview = signal<{ code: string; total: string; result: DiscountPreview } | null>(null);
  protected readonly selected = computed(() => {
    this.changes();
    return this.catalog().find(item => item.code === this.form.controls.brpCode.value);
  });
  protected readonly finances = computed(() => {
    this.changes();
    return reconcile(this.bidders.getRawValue());
  });
  protected readonly total = computed(() => decimal(this.finances().total));
  protected readonly preview = computed(() => {
    const result = this.calculatedPreview();
    return result && result.code === this.selected()?.code && result.total === this.total()
      ? result.result : null;
  });

  ngOnInit(): void {
    void this.loadCatalog();
  }

  protected async loadCatalog(): Promise<void> {
    if (this.loadingCatalog() || this.busy()) return;
    this.loadingCatalog.set(true);
    this.catalogError.set('');
    this.calculatedPreview.set(null);
    try {
      const items = await firstValueFrom(this.api.catalog());
      this.catalog.set(items);
      if (!this.selected()) this.form.controls.brpCode.reset();
    } catch (error) {
      this.catalogError.set(registrationError(error));
    } finally {
      this.loadingCatalog.set(false);
    }
  }

  protected addBidder(): void {
    if (this.bidders.length < 5) this.bidders.push(this.bidder());
  }

  protected removeBidder(index: number): void {
    if (this.bidders.length > 1) this.bidders.removeAt(index);
  }

  protected addPayment(index: number): void {
    const payments = this.bidders.at(index).controls.payments;
    if (payments.length < 6) payments.push(this.payment());
  }

  protected removePayment(bidderIndex: number, paymentIndex: number): void {
    const payments = this.bidders.at(bidderIndex).controls.payments;
    if (payments.length > 1) payments.removeAt(paymentIndex);
  }

  protected async requestPreview(): Promise<void> {
    const item = this.selected();
    if (this.busy() || !item || this.loadingCatalog()) return;
    const total = this.total();
    if (this.finances().issues.length) {
      this.submitted.set(true);
      this.error.set('Corrige los importes y participaciones antes de consultar el descuento.');
      return;
    }
    this.error.set('');
    this.calculatedPreview.set(null);
    this.busy.set('preview');
    try {
      const result = await firstValueFrom(this.api.preview(item.code, total));
      this.calculatedPreview.set({ code: item.code, total, result });
    } catch (error) {
      this.error.set(registrationError(error));
    } finally {
      this.busy.set(null);
    }
  }

  protected async register(): Promise<void> {
    if (this.busy() || this.registered() || this.loadingCatalog()) return;
    this.submitted.set(true);
    this.form.markAllAsTouched();
    this.error.set('');
    const item = this.selected();
    const bidders = this.bidders.getRawValue();
    if (!item || this.form.invalid || this.finances().issues.length) {
      this.error.set('Revisa los campos y el resumen financiero antes de radicar.');
      return;
    }
    const documents = bidders.map(bidder => bidder.identificationNumber.trim());
    if (new Set(documents).size !== documents.length) {
      this.error.set('Cada oferente debe tener un número de identificación distinto.');
      return;
    }
    this.busy.set('register');
    try {
      const result = await firstValueFrom(this.api.register(item.code, bidders.map(bidder => ({
        ...bidder,
        identificationNumber: bidder.identificationNumber.trim(),
        contact: bidder.contact.trim() || undefined,
        percentage: decimal(share(bidder.percentage)!, 4),
        payments: bidder.payments.map(payment => ({
          ...payment,
          amount: decimal(money(payment.amount)!),
          lender: payment.method === 'PRESTAMO' ? payment.lender.trim() || undefined : undefined,
        })),
      }))));
      this.registered.set(result);
    } catch (error) {
      this.error.set(registrationError(error));
    } finally {
      this.busy.set(null);
    }
  }

  protected newOffer(): void {
    this.form.controls.brpCode.reset();
    this.bidders.clear();
    this.bidders.push(this.bidder());
    this.submitted.set(false);
    this.calculatedPreview.set(null);
    this.registered.set(null);
    void this.loadCatalog();
  }

  private bidder() {
    return this.fb.group({
      identificationType: this.fb.control<IdentificationType>('CC', Validators.required),
      identificationNumber: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(80)]],
      name: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(200)]],
      contact: ['', Validators.maxLength(254)],
      percentage: ['', Validators.required],
      payments: this.fb.array([this.payment()]),
    });
  }

  private payment() {
    return this.fb.group({
      method: this.fb.control<PaymentMethod>('CONTADO', Validators.required),
      date: ['', Validators.required],
      amount: ['', Validators.required],
      lender: ['', Validators.maxLength(200)],
    });
  }
}
