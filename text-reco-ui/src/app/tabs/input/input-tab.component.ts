import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ApiService, CameraDevice } from '../../services/api.service';
import { interval, Subscription, switchMap } from 'rxjs';

@Component({
  selector: 'app-input-tab',
  imports: [CommonModule],
  template: `
    <div class="refresh-note">Auto-refresh every 1s</div>
    <div class="grid">
      <div class="card">
        <h4>Raw Frame</h4>
        <img class="frame-img" [src]="rawSrc" *ngIf="rawSrc" />
      </div>
      <div class="card">
        <h4>Edge Frame</h4>
        <img class="frame-img" [src]="edgeSrc" *ngIf="edgeSrc" />
      </div>
    </div>
    <div style="margin-top:12px">
      <button (click)="freeze()">{{ frozen ? 'Unfreeze' : 'Freeze' }}</button>
      <label style="display:inline;margin-left:12px">
        Load image: <input type="file" accept="image/*" (change)="onFileChange($event)" />
      </label>
    </div>
    <div style="margin-top:12px" *ngIf="cameras.length">
      <span>Camera: </span>
      @for (cam of cameras; track cam.index) {
        <button (click)="selectCamera(cam.index)">{{ cam.name || 'Cam ' + cam.index }}</button>
      }
    </div>
  `
})
export class InputTabComponent implements OnInit, OnDestroy {
  rawSrc: string | null = null;
  edgeSrc: string | null = null;
  frozen = false;
  cameras: CameraDevice[] = [];

  private sub!: Subscription;

  constructor(private api: ApiService) {}

  ngOnInit() {
    this.api.getCameras().subscribe(c => this.cameras = c);
    this.sub = interval(1000).pipe(
      switchMap(() => this.api.getFrame('raw'))
    ).subscribe(r => { if (r.base64Png) this.rawSrc = 'data:image/png;base64,' + r.base64Png; });

    interval(1000).pipe(
      switchMap(() => this.api.getFrame('edge'))
    ).subscribe(r => { if (r.base64Png) this.edgeSrc = 'data:image/png;base64,' + r.base64Png; });
  }

  ngOnDestroy() { this.sub?.unsubscribe(); }

  freeze() {
    this.api.toggleFreeze().subscribe(r => this.frozen = r.frozen);
  }

  selectCamera(index: number) {
    this.api.selectCamera(index).subscribe();
  }

  onFileChange(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (file) this.api.loadImage(file).subscribe();
  }
}
