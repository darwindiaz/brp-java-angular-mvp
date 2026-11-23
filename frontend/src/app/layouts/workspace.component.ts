import { Component, computed, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService, Role } from '../core/auth/auth.service';
import { canOpenModule, moduleRoutes } from '../core/auth/navigation';

@Component({
  selector: 'app-workspace',
  standalone: true,
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './workspace.component.html',
  styleUrl: './workspace.component.scss',
})
export class WorkspaceComponent {
  protected readonly auth = inject(AuthService);
  protected readonly modules = computed(() => moduleRoutes.filter(module => canOpenModule(this.auth.user(), module)));
  protected readonly roleNames: Record<Role, string> = {
    RADICADOR: 'Radicador', ANALISTA: 'Analista', JEFE: 'Jefe', ADMINISTRADOR: 'Administrador',
  };
}
