import { Component, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-module-unavailable',
  standalone: true,
  templateUrl: './module-unavailable.component.html',
  styleUrl: './module-unavailable.component.scss',
})
export class ModuleUnavailableComponent {
  protected readonly title: string = inject(ActivatedRoute).snapshot.data['title'];
}
