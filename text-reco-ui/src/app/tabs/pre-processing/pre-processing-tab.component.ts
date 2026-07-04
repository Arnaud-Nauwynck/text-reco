import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { interval, Subscription, switchMap } from 'rxjs';
import { ApiService } from '../../services/api.service';

const FRAME_TYPES = [
  'binary', 'morph-horiz', 'morph-vert', 'morph-diag-fwd', 'morph-diag-bwd',
  'close-horiz', 'close-vert', 'close-diag-fwd', 'close-diag-bwd'
];

@Component({
  selector: 'app-pre-processing-tab',
  imports: [CommonModule],
  template: `
    <div class="refresh-note">Auto-refresh every 2s</div>
    <div class="grid">
      @for (type of frameTypes; track type) {
        <div class="card">
          <h4>{{ type }}</h4>
          <img class="frame-img" [src]="frames[type]" *ngIf="frames[type]" />
        </div>
      }
    </div>
  `
})
export class PreProcessingTabComponent implements OnInit, OnDestroy {
  frameTypes = FRAME_TYPES;
  frames: Record<string, string> = {};

  private subs: Subscription[] = [];

  constructor(private api: ApiService) {}

  ngOnInit() {
    for (const type of FRAME_TYPES) {
      const sub = interval(2000).pipe(
        switchMap(() => this.api.getFrame(type))
      ).subscribe(r => { if (r.base64Png) this.frames[type] = 'data:image/png;base64,' + r.base64Png; });
      this.subs.push(sub);
    }
  }

  ngOnDestroy() { this.subs.forEach(s => s.unsubscribe()); }
}
