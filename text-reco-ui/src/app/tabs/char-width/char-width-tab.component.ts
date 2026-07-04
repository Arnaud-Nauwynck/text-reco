import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { interval, Subscription, switchMap } from 'rxjs';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-char-width-tab',
  imports: [CommonModule],
  template: `
    <div class="refresh-note">Auto-refresh every 2s</div>
    <div class="card">
      <h4>Correlation — Char Width</h4>
      <table *ngIf="data">
        <tr><td>smoothedCharWidth</td><td>{{ data.smoothedCharWidth | number:'1.2-2' }}</td></tr>
        <tr><td>rawCharWidth</td><td>{{ data.rawCharWidth | number:'1.2-2' }}</td></tr>
      </table>
      <pre *ngIf="!data">loading...</pre>
    </div>
  `
})
export class CharWidthTabComponent implements OnInit, OnDestroy {
  data: any = null;
  private sub!: Subscription;

  constructor(private api: ApiService) {}

  ngOnInit() {
    this.sub = interval(2000).pipe(switchMap(() => this.api.getResultCorrGrid()))
      .subscribe(r => this.data = r);
  }

  ngOnDestroy() { this.sub?.unsubscribe(); }
}
