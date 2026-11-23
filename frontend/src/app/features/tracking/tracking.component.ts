import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, OnInit, signal, WritableSignal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { firstValueFrom, Observable, Subject, takeUntil } from 'rxjs';
import { TrackingApi, trackingError } from './tracking-api.service';
import { calendarDate, displayDecimal } from './tracking-format';
import { CatalogFreshness, TrackedOffer, TrackingCatalogItem } from './tracking.models';

type Tab = 'offers' | 'catalog';
type Query<T> =
  | { status: 'idle' | 'loading' }
  | { status: 'ready'; data: T }
  | { status: 'error'; message: string };

@Component({
  selector: 'app-tracking',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './tracking.component.html',
  styleUrl: './tracking.component.scss',
})
export class TrackingComponent implements OnInit {
  private readonly api = inject(TrackingApi);
  private readonly destroyRef = inject(DestroyRef);
  private readonly cancelQueries = new Subject<void>();
  protected readonly accessDenied = signal(false);
  protected readonly tab = signal<Tab>('offers');
  protected readonly offers = signal<Query<TrackedOffer[]>>({ status: 'idle' });
  protected readonly catalog = signal<Query<TrackingCatalogItem[]>>({ status: 'idle' });
  protected readonly freshness = signal<Query<CatalogFreshness>>({ status: 'idle' });
  protected readonly displayDecimal = displayDecimal;
  protected readonly calendarDate = calendarDate;

  ngOnInit(): void {
    this.refreshOffers();
    this.refreshFreshness();
  }

  protected select(tab: Tab): void {
    this.tab.set(tab);
    if (tab === 'catalog' && this.catalog().status === 'idle') this.refreshCatalog();
  }

  protected switchTab(tab: Tab, target: HTMLButtonElement, event: Event): void {
    event.preventDefault();
    this.select(tab);
    target.focus();
  }

  protected refreshOffers(): void {
    void this.load(this.offers, () => this.api.offers());
  }

  protected refreshCatalog(): void {
    void this.load(this.catalog, () => this.api.catalog());
  }

  protected refreshFreshness(): void {
    void this.load(this.freshness, () => this.api.freshness());
  }

  private async load<T>(state: WritableSignal<Query<T>>, request: () => Observable<T>): Promise<void> {
    if (this.destroyRef.destroyed || this.accessDenied() || state().status === 'loading') return;
    state.set({ status: 'loading' });
    try {
      const data = await firstValueFrom(request().pipe(
        takeUntil(this.cancelQueries),
        takeUntilDestroyed(this.destroyRef),
      ));
      if (!this.destroyRef.destroyed && !this.accessDenied()) state.set({ status: 'ready', data });
    } catch (error) {
      if (this.destroyRef.destroyed || this.accessDenied()) return;
      if (error instanceof HttpErrorResponse && error.status === 403) {
        this.accessDenied.set(true);
        this.offers.set({ status: 'idle' });
        this.catalog.set({ status: 'idle' });
        this.freshness.set({ status: 'idle' });
        this.cancelQueries.next();
        return;
      }
      state.set({ status: 'error', message: trackingError(error) });
    }
  }
}
