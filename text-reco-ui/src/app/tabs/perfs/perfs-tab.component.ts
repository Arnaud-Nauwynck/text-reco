import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { interval, Subscription, switchMap } from 'rxjs';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-perfs-tab',
  imports: [CommonModule],
  template: `
    <div class="refresh-note">Auto-refresh every 2s</div>
    <div class="card">
      <h4>Frame Stats</h4>
      <pre>{{ stats | json }}</pre>
    </div>
  `
})
export class PerfsTabComponent implements OnInit, OnDestroy {
  stats: any = null;
  private sub!: Subscription;

  constructor(private api: ApiService) {}

  ngOnInit() {
    this.sub = interval(2000).pipe(switchMap(() => this.api.getResultStats()))
      .subscribe(r => this.stats = r);
  }

  ngOnDestroy() { this.sub?.unsubscribe(); }
}
