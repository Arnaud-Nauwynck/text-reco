import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { interval, Subscription, switchMap } from 'rxjs';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-tess-ocr-tab',
  imports: [CommonModule],
  template: `
    <div class="refresh-note">Auto-refresh every 2s</div>
    <button (click)="triggerOcr()">Run OCR once</button>
    <div class="grid" style="margin-top:12px">
      <div class="card">
        <h4>Template OCR</h4>
        <pre>{{ textResult?.ocrText || '(empty)' }}</pre>
      </div>
      <div class="card">
        <h4>Tesseract OCR</h4>
        <pre>{{ textResult?.tessOcrText || '(empty)' }}</pre>
      </div>
    </div>
  `
})
export class TessOcrTabComponent implements OnInit, OnDestroy {
  textResult: { ocrText: string; tessOcrText: string } | null = null;
  private sub!: Subscription;

  constructor(private api: ApiService) {}

  ngOnInit() {
    this.sub = interval(2000).pipe(switchMap(() => this.api.getResultText()))
      .subscribe(r => this.textResult = r);
  }

  ngOnDestroy() { this.sub?.unsubscribe(); }

  triggerOcr() { this.api.triggerOcr().subscribe(); }
}
