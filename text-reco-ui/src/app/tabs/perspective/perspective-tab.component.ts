import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';
import { interval, Subscription, switchMap } from 'rxjs';

@Component({
  selector: 'app-perspective-tab',
  imports: [CommonModule, FormsModule],
  template: `
    <div class="refresh-note">Auto-refresh every 1s</div>
    <div class="card" style="margin-bottom:12px">
      <h4>Perspective Frame</h4>
      <img class="frame-img" [src]="src" *ngIf="src" />
    </div>
    <div class="card">
      <h4>Corner Points (relative 0..1)</h4>
      <div *ngFor="let i of [0,1,2,3]">
        <label>Corner {{ i + 1 }}: X <input type="number" step="0.01" min="0" max="1" [(ngModel)]="xRel[i]" style="width:80px" />
          Y <input type="number" step="0.01" min="0" max="1" [(ngModel)]="yRel[i]" style="width:80px" />
        </label>
      </div>
      <button (click)="apply()">Apply</button>
    </div>
  `
})
export class PerspectiveTabComponent implements OnInit, OnDestroy {
  src: string | null = null;
  xRel = [0.0, 1.0, 1.0, 0.0];
  yRel = [0.0, 0.0, 1.0, 1.0];

  private sub!: Subscription;

  constructor(private api: ApiService) {}

  ngOnInit() {
    this.sub = interval(1000).pipe(
      switchMap(() => this.api.getFrame('perspective'))
    ).subscribe(r => { if (r.base64Png) this.src = 'data:image/png;base64,' + r.base64Png; });
  }

  ngOnDestroy() { this.sub?.unsubscribe(); }

  apply() {
    this.api.setPerspective(this.xRel, this.yRel).subscribe();
  }
}
