import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { interval, Subscription, switchMap } from 'rxjs';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-grid-detect-tab',
  imports: [CommonModule],
  template: `
    <div class="refresh-note">Auto-refresh every 2s</div>
    <div class="grid">
      <div class="card">
        <h4>Valley Grid Result</h4>
        <pre>{{ grid | json }}</pre>
      </div>
      <div class="card">
        <h4>Correlation Grid Result</h4>
        <pre>{{ corrGrid | json }}</pre>
      </div>
    </div>
  `
})
export class GridDetectTabComponent implements OnInit, OnDestroy {
  grid: any = null;
  corrGrid: any = null;

  private subs: Subscription[] = [];

  constructor(private api: ApiService) {}

  ngOnInit() {
    this.subs.push(
      interval(2000).pipe(switchMap(() => this.api.getResultGrid()))
        .subscribe(r => this.grid = r),
      interval(2000).pipe(switchMap(() => this.api.getResultCorrGrid()))
        .subscribe(r => this.corrGrid = r)
    );
  }

  ngOnDestroy() { this.subs.forEach(s => s.unsubscribe()); }
}
