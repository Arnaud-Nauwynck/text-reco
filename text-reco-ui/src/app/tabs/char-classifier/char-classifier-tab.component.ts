import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { interval, Subscription, switchMap } from 'rxjs';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-char-classifier-tab',
  imports: [CommonModule],
  template: `
    <div class="refresh-note">Auto-refresh every 2s</div>
    <div *ngIf="lines" style="margin-bottom:8px">{{ lines.lineCount }} lines detected</div>
    <div class="grid">
      @for (n of lineIndices; track n) {
        <div class="card">
          <h4>Line {{ n }}</h4>
          <img class="frame-img" [src]="lineFrames[n]" *ngIf="lineFrames[n]" />
        </div>
      }
    </div>
  `
})
export class CharClassifierTabComponent implements OnInit, OnDestroy {
  lines: any = null;
  lineIndices: number[] = [];
  lineFrames: Record<number, string> = {};

  private subs: Subscription[] = [];

  constructor(private api: ApiService) {}

  ngOnInit() {
    this.subs.push(
      interval(2000).pipe(switchMap(() => this.api.getResultLines()))
        .subscribe(r => {
          this.lines = r;
          const count = r?.lineCount ?? 0;
          this.lineIndices = Array.from({ length: count }, (_, i) => i);
          for (let n = 0; n < count; n++) {
            const sub = this.api.getLineFrame(n).subscribe(fr => {
              if (fr.base64Png) this.lineFrames[n] = 'data:image/png;base64,' + fr.base64Png;
            });
            this.subs.push(sub);
          }
        })
    );
  }

  ngOnDestroy() { this.subs.forEach(s => s.unsubscribe()); }
}
