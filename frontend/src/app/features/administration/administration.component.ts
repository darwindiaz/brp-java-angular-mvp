import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { AbstractControl, FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { firstValueFrom, Observable, Subject, takeUntil } from 'rxjs';
import { AuthService, Module, Role } from '../../core/auth/auth.service';
import { AdministrationApi, administrationError, errorStatus, FieldError } from './administration-api.service';
import { AdministrationUser, ModuleOption, normalizeLogin, roles } from './models/administration.models';

type SyncTarget = { id: string | null; modulesOnly?: boolean };

@Component({
  selector: 'app-administration',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './administration.component.html',
  styleUrl: './administration.component.scss',
})
export class AdministrationComponent implements OnInit {
  private readonly api = inject(AdministrationApi);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly cancel = new Subject<void>();

  protected readonly users = signal<AdministrationUser[]>([]);
  protected readonly modules = signal<ModuleOption[]>([]);
  protected readonly selectedId = signal<string | null>(null);
  protected readonly selected = computed(() => this.users().find(user => user.id === this.selectedId()));
  protected readonly loadingUsers = signal(false);
  protected readonly loadingModules = signal(false);
  protected readonly usersError = signal('');
  protected readonly modulesError = signal('');
  protected readonly accessDenied = signal(false);
  protected readonly busy = signal(false);
  protected readonly pendingSync = signal<SyncTarget | null>(null);
  protected readonly notice = signal('');
  protected readonly error = signal('');
  protected readonly fields = signal<FieldError[]>([]);
  private readonly fieldScope = signal<'create' | 'edit'>('create');
  protected readonly roleOptions = roles;
  protected readonly blocked = computed(() =>
    this.busy() || this.loadingUsers() || this.pendingSync() !== null || this.accessDenied());

  protected readonly createForm = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(120), Validators.pattern(/\S/)]],
    login: ['', (control: AbstractControl<string>) => /^[a-z0-9][a-z0-9._-]{0,79}$/.test(normalizeLogin(String(control.value)))
      ? null : { loginFormat: true }],
    password: ['', [
      Validators.required, Validators.minLength(8), Validators.pattern(/\S/),
      (control: AbstractControl<string>) => new TextEncoder().encode(String(control.value)).length <= 72
        ? null : { passwordBytes: true },
    ]],
    role: this.fb.control<Role | ''>('', [Validators.required, Validators.pattern(/^(ADMINISTRADOR|RADICADOR|ANALISTA|JEFE)$/)]),
  });
  protected readonly role = this.fb.control<Role | ''>('');

  constructor() {
    this.destroyRef.onDestroy(() => this.createForm.controls.password.reset());
  }

  ngOnInit(): void {
    void this.reloadUsers();
    void this.reloadModules();
  }

  protected async reloadUsers(): Promise<void> {
    if (!this.canRead() || this.loadingUsers() || this.busy()) return;
    this.loadingUsers.set(true);
    this.usersError.set('');
    try {
      await this.loadUsers();
    } catch (error) {
      if (this.canRead()) this.usersError.set(administrationError(error).message);
    } finally {
      if (this.canRead()) this.loadingUsers.set(false);
    }
  }

  protected async reloadModules(): Promise<void> {
    if (!this.canRead() || this.loadingModules()) return;
    this.loadingModules.set(true);
    this.modulesError.set('');
    try {
      const modules = await this.request(() => this.api.modules());
      if (this.canRead()) this.modules.set(modules);
    } catch (error) {
      if (this.canRead()) this.modulesError.set(administrationError(error).message);
    } finally {
      if (this.canRead()) this.loadingModules.set(false);
    }
  }

  protected select(user: AdministrationUser): void {
    if (this.blocked()) return;
    this.selectUser(user);
  }

  protected owns(user: AdministrationUser): boolean {
    return user.login === this.auth.user()?.login;
  }

  protected moduleName(code: Module): string {
    return this.modules().find(module => module.code === code)?.name ?? code;
  }

  protected fieldError(field: string, scope: 'create' | 'edit' = 'create'): string | undefined {
    return this.fieldScope() === scope ? this.fields().find(item => item.field === field)?.message : undefined;
  }

  protected async create(): Promise<void> {
    if (this.blocked() || !this.canRead()) return;
    this.createForm.markAllAsTouched();
    const { name, login, password, role } = this.createForm.getRawValue();
    if (this.createForm.invalid || !role) return;
    try {
      await this.write({ id: null }, () => this.api.create({ name, login: normalizeLogin(login), password, role }),
        user => {
          this.updateUser(user);
          this.selectUser(user);
          this.createForm.reset();
          return 'Usuario creado. Puedes asignarle módulos.';
        });
    } finally {
      if (this.canRead()) this.createForm.controls.password.reset();
    }
  }

  protected async saveRole(): Promise<void> {
    const user = this.selected();
    const role = this.role.value;
    if (!user || !role || !roles.includes(role) || role === user.role || this.owns(user)) return;
    await this.write({ id: user.id }, () => this.api.changeRole(user.id, role), () => 'Rol actualizado.');
  }

  protected async toggleModule(module: Module): Promise<void> {
    const user = this.selected();
    if (!user) return;
    const grant = !user.modules.includes(module);
    if (!grant && module === 'ADMINISTRACION' && this.owns(user)) return;
    await this.write({ id: user.id, modulesOnly: true }, () => this.api.changeModule(user.id, module, grant),
      () => grant ? 'Módulo concedido.' : 'Módulo retirado.');
  }

  protected async retrySync(): Promise<void> {
    const target = this.pendingSync();
    if (!target || this.busy() || !this.canRead()) return;
    this.busy.set(true);
    try {
      await this.synchronize(target);
    } finally {
      if (this.canRead()) this.busy.set(false);
    }
  }

  private async write<T>(target: SyncTarget, send: () => Observable<T>, confirmed: (value: T) => string): Promise<void> {
    if (this.blocked() || !this.canRead()) return;
    this.busy.set(true);
    this.notice.set('');
    this.error.set('');
    this.fields.set([]);
    this.fieldScope.set(target.id === null ? 'create' : 'edit');
    try {
      const result = await this.request(send);
      if (!this.canRead()) return;
      this.notice.set(confirmed(result));
      await this.synchronize(target);
    } catch (error) {
      if (!this.canRead()) return;
      const status = errorStatus(error);
      const details = administrationError(error);
      this.error.set(details.message);
      this.fields.set(details.fields);
      if (status === 409 || status === 404) {
        await this.synchronize({ id: status === 404 ? null : target.id });
      } else if (status === undefined || status === 0 || status === 408 || status >= 500) {
        this.error.set('No fue posible confirmar el resultado. Comprueba los datos antes de repetir la operación.');
        await this.synchronize({ id: target.id });
      }
    } finally {
      if (this.canRead()) this.busy.set(false);
    }
  }

  private async synchronize(target: SyncTarget): Promise<void> {
    this.pendingSync.set(target);
    try {
      if (target.id === null) {
        await this.loadUsers();
      } else if (target.modulesOnly) {
        const id = target.id;
        const assigned = await this.request(() => this.api.assignedModules(id));
        if (!this.canRead()) return;
        this.users.update(users => users.map(user => user.id === id ? { ...user, modules: assigned } : user));
      } else {
        const id = target.id;
        const user = await this.request(() => this.api.user(id));
        if (!this.canRead()) return;
        this.updateUser(user);
        if (this.selectedId() === user.id) this.role.setValue(user.role);
      }
      if (this.canRead()) this.pendingSync.set(null);
    } catch (error) {
      if (!this.canRead()) return;
      if (errorStatus(error) === 404 && target.id !== null) {
        this.error.set(administrationError(error).message);
        await this.synchronize({ id: null });
      }
    }
  }

  private async loadUsers(): Promise<void> {
    const users = await this.request(() => this.api.users());
    if (!this.canRead()) return;
    this.users.set(users);
    this.usersError.set('');
    this.selectUser(this.selected());
  }

  private selectUser(user: AdministrationUser | undefined): void {
    this.selectedId.set(user?.id ?? null);
    this.role.setValue(user?.role ?? '');
  }

  private updateUser(user: AdministrationUser): void {
    this.users.update(users => users.some(item => item.id === user.id)
      ? users.map(item => item.id === user.id ? user : item)
      : [...users, user]);
  }

  private async request<T>(send: () => Observable<T>): Promise<T> {
    try {
      return await firstValueFrom(send().pipe(takeUntil(this.cancel), takeUntilDestroyed(this.destroyRef)));
    } catch (error) {
      if (this.canRead() && errorStatus(error) === 403) {
        this.accessDenied.set(true);
        this.users.set([]);
        this.modules.set([]);
        this.selectUser(undefined);
        this.createForm.reset();
        this.notice.set('');
        this.error.set('');
        this.fields.set([]);
        this.pendingSync.set(null);
        this.cancel.next();
      }
      throw error;
    }
  }

  private canRead(): boolean {
    return !this.destroyRef.destroyed && !this.accessDenied();
  }
}
