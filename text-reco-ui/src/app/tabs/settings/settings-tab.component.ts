import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ApiService } from '../../services/api.service';

@Component({
  selector: 'app-settings-tab',
  imports: [CommonModule, FormsModule],
  template: `
    <div *ngIf="settings">
      <button (click)="save()">Save</button>
      <button (click)="load()">Reload</button>
      <div class="grid" style="margin-top:12px">
        <div class="card">
          <h4>App</h4>
          <label>darkTheme <input type="checkbox" [(ngModel)]="settings.app.darkTheme" /></label>
        </div>
        <div class="card">
          <h4>Edge Detector</h4>
          <label>cannyThreshold1 <input type="number" [(ngModel)]="settings.edge.cannyThreshold1" /></label>
          <label>cannyThreshold2 <input type="number" [(ngModel)]="settings.edge.cannyThreshold2" /></label>
        </div>
        <div class="card">
          <h4>Pre-Processing</h4>
          <label>binarizationMethod
            <select [(ngModel)]="settings.preProcessing.binarizationMethod">
              <option>TOPHAT</option><option>ADAPTIVE</option><option>THRESHOLD</option>
            </select>
          </label>
          <label>tophatRadius <input type="number" [(ngModel)]="settings.preProcessing.tophatRadius" /></label>
          <label>tophatThreshold <input type="number" [(ngModel)]="settings.preProcessing.tophatThreshold" /></label>
          <label>adaptiveBlock <input type="number" [(ngModel)]="settings.preProcessing.adaptiveBlock" /></label>
          <label>adaptiveC <input type="number" [(ngModel)]="settings.preProcessing.adaptiveC" /></label>
          <label>seHalfLen <input type="number" [(ngModel)]="settings.preProcessing.seHalfLen" /></label>
        </div>
        <div class="card">
          <h4>Grid Detector Mode</h4>
          <label>
            <select [(ngModel)]="settings.gridDetectorMode">
              <option>VALLEY</option><option>CORRELATION</option>
            </select>
          </label>
        </div>
        <div class="card">
          <h4>Valley Grid Detector</h4>
          <label>minLineH <input type="number" [(ngModel)]="settings.gridDetector.minLineH" /></label>
          <label>maxLineH <input type="number" [(ngModel)]="settings.gridDetector.maxLineH" /></label>
          <label>minCharW <input type="number" [(ngModel)]="settings.gridDetector.minCharW" /></label>
          <label>maxCharW <input type="number" [(ngModel)]="settings.gridDetector.maxCharW" /></label>
          <label>forceLineH <input type="checkbox" [(ngModel)]="settings.gridDetector.forceLineH" /></label>
          <label>forcedLineH <input type="number" [(ngModel)]="settings.gridDetector.forcedLineH" /></label>
        </div>
        <div class="card">
          <h4>Correlation Grid Detector</h4>
          <label>minLineHeight <input type="number" [(ngModel)]="settings.correlationGridDetector.minLineHeight" /></label>
          <label>maxLineHeight <input type="number" [(ngModel)]="settings.correlationGridDetector.maxLineHeight" /></label>
          <label>smoothingAlpha <input type="number" step="0.01" [(ngModel)]="settings.correlationGridDetector.smoothingAlpha" /></label>
          <label>forceLineHeight <input type="checkbox" [(ngModel)]="settings.correlationGridDetector.forceLineHeight" /></label>
          <label>forcedLineHeight <input type="number" [(ngModel)]="settings.correlationGridDetector.forcedLineHeight" /></label>
        </div>
      </div>
    </div>
    <div *ngIf="!settings">loading...</div>
  `
})
export class SettingsTabComponent implements OnInit {
  settings: any = null;

  constructor(private api: ApiService) {}

  ngOnInit() { this.load(); }

  load() { this.api.getSettings().subscribe(s => this.settings = s); }

  save() {
    const flat: any = {
      darkTheme: this.settings.app.darkTheme,
      cannyThreshold1: this.settings.edge.cannyThreshold1,
      cannyThreshold2: this.settings.edge.cannyThreshold2,
      binarizationMethod: this.settings.preProcessing.binarizationMethod,
      tophatRadius: this.settings.preProcessing.tophatRadius,
      tophatThreshold: this.settings.preProcessing.tophatThreshold,
      adaptiveBlock: this.settings.preProcessing.adaptiveBlock,
      adaptiveC: this.settings.preProcessing.adaptiveC,
      seHalfLen: this.settings.preProcessing.seHalfLen,
      gridDetectorMode: this.settings.gridDetectorMode,
      minLineH: this.settings.gridDetector.minLineH,
      maxLineH: this.settings.gridDetector.maxLineH,
      minCharW: this.settings.gridDetector.minCharW,
      maxCharW: this.settings.gridDetector.maxCharW,
      forceLineH: this.settings.gridDetector.forceLineH,
      forcedLineH: this.settings.gridDetector.forcedLineH,
      minLineHeight: this.settings.correlationGridDetector.minLineHeight,
      maxLineHeight: this.settings.correlationGridDetector.maxLineHeight,
      smoothingAlpha: this.settings.correlationGridDetector.smoothingAlpha,
      forceLineHeight: this.settings.correlationGridDetector.forceLineHeight,
      forcedLineHeight: this.settings.correlationGridDetector.forcedLineHeight,
    };
    this.api.putSettings(flat).subscribe();
  }
}
