import { DatePipe } from '@angular/common';
import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { AuthService } from '../../../core/auth/auth.service';
import { AnalysisApi, analysisError } from '../analysis-api.service';
import {
  AnalysisAction, AnalysisFilter, availableAnalysisActions, matchesAnalysisFilter, WorkItem,
} from '../models/analysis.models';

@Component({
  selector: 'app-analysis-list',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './analysis-list.component.html',
  styleUrl: './analysis-list.component.scss',
})
export class AnalysisListComponent implements OnInit {
  private readonly api = inject(AnalysisApi);
  private readonly auth = inject(AuthService);
  private readonly rows = signal<WorkItem[]>([]);
  private refreshAfterActions = false;
  protected readonly loading = signal(false);
  protected readonly pending = signal<ReadonlySet<string>>(new Set());
  protected readonly filter = signal<AnalysisFilter>('all');
  protected readonly visible = computed(() => this.rows().filter(item => matchesAnalysisFilter(item, this.filter())));
  protected readonly loadError = signal('');
  protected readonly actionError = signal('');
  protected readonly notice = signal('');
  protected readonly confirmation = signal<{ item: WorkItem; action: 'escalate' | 'reject' } | null>(null);

  protected readonly filters: readonly { value: AnalysisFilter; label: string }[] = [
    { value: 'all', label: 'Todas' },
    { value: 'pending', label: 'Pendientes por tomar' },
    { value: 'assigned', label: 'Asignadas' },
    { value: 'returned', label: 'Devueltas' },
  ];
  protected readonly labels: Record<AnalysisAction, string> = {
    take: 'Tomar', escalate: 'Escalar', reject: 'Rechazar',
  };

  ngOnInit(): void {
    void this.reload();
  }

  protected async reload(): Promise<void> {
    if (this.loading() || this.pending().size > 0) return;
    this.loading.set(true);
    this.loadError.set('');
    this.confirmation.set(null);
    try {
      this.rows.set(await firstValueFrom(this.api.list()));
    } catch (error) {
      this.loadError.set(analysisError(error));
      if (error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403)) {
        this.rows.set([]);
      }
    } finally {
      this.loading.set(false);
    }
  }

  protected actions(item: WorkItem): readonly AnalysisAction[] {
    return availableAnalysisActions(item, this.auth.user()?.id);
  }

  protected select(action: AnalysisAction, item: WorkItem): void {
    if (this.loading() || this.pending().has(item.offer.registrationNumber)) return;
    if (action === 'take') {
      void this.perform(item, action);
    } else {
      this.confirmation.set({ item, action });
    }
  }

  protected confirm(): void {
    const command = this.confirmation();
    if (!command) return;
    this.confirmation.set(null);
    void this.perform(command.item, command.action);
  }

  protected absolutePercentage(value: string): string {
    return value.replace(/^-/, '');
  }

  private async perform(item: WorkItem, action: AnalysisAction): Promise<void> {
    const number = item.offer.registrationNumber;
    if (this.loading() || this.pending().has(number) || !this.actions(item).includes(action)) return;
    this.pending.update(current => new Set([...current, number]));
    this.actionError.set('');
    this.notice.set('');
    try {
      const updated = await firstValueFrom(this.api.transition(item, action));
      this.rows.update(rows => rows.flatMap(row => {
        if (row.offer.registrationNumber !== number) return [row];
        return updated.offer.status === 'EN_PROCESO' ? [updated] : [];
      }));
      this.notice.set(`Oferta ${updated.offer.registrationNumber}: ${updated.offer.status}.`);
    } catch (error) {
      this.actionError.set(analysisError(error));
      if (error instanceof HttpErrorResponse && error.status === 409) {
        this.refreshAfterActions = true;
      }
    } finally {
      this.pending.update(current => {
        const remaining = new Set(current);
        remaining.delete(number);
        return remaining;
      });
      if (this.refreshAfterActions && this.pending().size === 0) {
        this.refreshAfterActions = false;
        await this.reload();
      }
    }
  }
}
