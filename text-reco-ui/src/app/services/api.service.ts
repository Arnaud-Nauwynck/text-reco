import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

export interface FrameResponse { base64Png: string | null; }

export interface TextResult { ocrText: string; tessOcrText: string; }

export interface CameraDevice { index: number; name: string; }

@Injectable({ providedIn: 'root' })
export class ApiService {

  constructor(private http: HttpClient) {}

  getFrame(type: string): Observable<FrameResponse> {
    return this.http.get<FrameResponse>(`/api/frame/${type}`);
  }

  getLineFrame(n: number): Observable<FrameResponse> {
    return this.http.get<FrameResponse>(`/api/frame/line/${n}`);
  }

  getSettings(): Observable<any> {
    return this.http.get<any>('/api/settings');
  }

  putSettings(settings: any): Observable<void> {
    return this.http.put<void>('/api/settings', settings);
  }

  getResultText(): Observable<TextResult> {
    return this.http.get<TextResult>('/api/result/text');
  }

  getResultGrid(): Observable<any> {
    return this.http.get<any>('/api/result/grid');
  }

  getResultCorrGrid(): Observable<any> {
    return this.http.get<any>('/api/result/corr-grid');
  }

  getResultStats(): Observable<any> {
    return this.http.get<any>('/api/result/stats');
  }

  getResultLines(): Observable<any> {
    return this.http.get<any>('/api/result/lines');
  }

  getCameras(): Observable<CameraDevice[]> {
    return this.http.get<CameraDevice[]>('/api/cameras');
  }

  triggerOcr(): Observable<void> {
    return this.http.post<void>('/api/control/ocr', null);
  }

  toggleFreeze(): Observable<{ frozen: boolean }> {
    return this.http.post<{ frozen: boolean }>('/api/control/freeze', null);
  }

  selectCamera(index: number): Observable<void> {
    return this.http.post<void>(`/api/control/camera/${index}`, null);
  }

  loadImage(file: File): Observable<void> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<void>('/api/control/load-image', form);
  }

  setPerspective(xRel: number[], yRel: number[]): Observable<void> {
    return this.http.post<void>('/api/control/perspective', { xRel, yRel });
  }
}
