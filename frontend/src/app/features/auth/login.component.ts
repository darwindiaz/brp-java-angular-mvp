import { Component, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  protected readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  protected readonly busy = signal(false);
  protected readonly error = signal('');
  protected readonly form = inject(FormBuilder).nonNullable.group({
    login: ['', [Validators.required, Validators.maxLength(100), Validators.pattern(/\S/)]],
    password: ['', [Validators.required, Validators.maxLength(72), Validators.pattern(/\S/)]],
  });

  protected async submit(): Promise<void> {
    if (this.busy()) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.busy.set(true);
    this.error.set('');
    try {
      await this.auth.login(this.form.getRawValue());
      await this.router.navigateByUrl('/inicio', { replaceUrl: true });
    } catch (error) {
      const status = error instanceof HttpErrorResponse ? error.status : 0;
      this.error.set(status === 401 ? 'El usuario o la contraseña son incorrectos.'
        : status === 429 ? 'Demasiados intentos. Inténtalo de nuevo más tarde.'
        : status === 400 ? 'Revisa los datos ingresados e inténtalo de nuevo.'
        : 'No fue posible iniciar sesión. Inténtalo de nuevo.');
    } finally {
      this.form.controls.password.reset();
      this.busy.set(false);
    }
  }
}
