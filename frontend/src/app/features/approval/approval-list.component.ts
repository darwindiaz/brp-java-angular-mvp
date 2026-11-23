import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../core/auth/auth.service';
import { WorkItem } from '../../core/offers/offer-work-item';
import { ApprovalAction, ApprovalApi, approvalError } from './approval-api.service';

@Component({
  selector: 'app-approval-list',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './approval-list.component.html',
  styleUrl: './approval-list.component.scss',
})
export class ApprovalListComponent implements OnInit {
  private readonly api = inject(ApprovalApi);
  private readonly auth = inject(AuthService);
  protected readonly offers = signal<WorkItem[]>([]);
  protected readonly loading = signal(false);
  protected readonly processing = signal(false);
  protected readonly outcomeUncertain = signal(false);
  protected readonly decisionsBlocked = computed(() =>
    this.loading() || this.processing() || this.outcomeUncertain());
  protected readonly loadError = signal('');
  protected readonly actionError = signal('');
  protected readonly result = signal<WorkItem | null>(null);
  protected readonly confirmation = signal<{ item: WorkItem; action: ApprovalAction } | null>(null);

  protected readonly descriptions: Record<ApprovalAction, { label: string; question: string }> = {
    approve: { label: 'Aprobar', question: '¿Confirmas la aprobación de esta oferta?' },
    reject: { label: 'Rechazar', question: '¿Confirmas el rechazo de esta oferta?' },
    return: { label: 'Devolver', question: '¿Confirmas la devolución al analista asignado?' },
  };

  ngOnInit(): void {
    void this.reload();
  }

  protected async reload(): Promise<void> {
    if (this.loading() || this.processing()) return;
    this.loading.set(true);
    this.loadError.set('');
    this.confirmation.set(null);
    try {
      this.offers.set(await firstValueFrom(this.api.list()));
      this.outcomeUncertain.set(false);
    } catch (error) {
      this.offers.set([]);
      this.loadError.set(approvalError(error));
    } finally {
      this.loading.set(false);
    }
  }

  protected actions(item: WorkItem): readonly ApprovalAction[] {
    const actor = this.auth.user();
    if (item.offer.status !== 'ESCALADO' || !actor) return [];
    return item.analystId === String(actor.id) ? ['reject', 'return'] : ['approve', 'reject', 'return'];
  }

  protected ask(item: WorkItem, action: ApprovalAction): void {
    if (this.decisionsBlocked() || !this.actions(item).includes(action)) return;
    this.confirmation.set({ item, action });
  }

  protected async decide(): Promise<void> {
    const command = this.confirmation();
    if (!command || this.decisionsBlocked() || !this.actions(command.item).includes(command.action)) return;
    this.processing.set(true);
    this.confirmation.set(null);
    this.actionError.set('');
    this.result.set(null);
    let conflict = false;
    try {
      const result = await firstValueFrom(this.api.decide(command.item, command.action));
      this.result.set(result);
      this.removeOffer(command.item);
    } catch (error) {
      const status = error instanceof HttpErrorResponse ? error.status : undefined;
      if (status === 0 || status === 408 || status === 504) {
        this.outcomeUncertain.set(true);
      } else {
        this.actionError.set(approvalError(error));
      }
      conflict = status === 409;
      if (conflict) this.removeOffer(command.item);
    } finally {
      this.processing.set(false);
      if (conflict) await this.reload();
    }
  }

  private removeOffer(item: WorkItem): void {
    this.offers.update(items => items.filter(row => row.offer.registrationNumber !== item.offer.registrationNumber));
  }
}
