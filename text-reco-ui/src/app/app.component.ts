import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { InputTabComponent } from './tabs/input/input-tab.component';
import { PerspectiveTabComponent } from './tabs/perspective/perspective-tab.component';
import { PreProcessingTabComponent } from './tabs/pre-processing/pre-processing-tab.component';
import { GridDetectTabComponent } from './tabs/grid-detect/grid-detect-tab.component';
import { LineHeightTabComponent } from './tabs/line-height/line-height-tab.component';
import { CharWidthTabComponent } from './tabs/char-width/char-width-tab.component';
import { LineOffsetTabComponent } from './tabs/line-offset/line-offset-tab.component';
import { ColumnOffsetTabComponent } from './tabs/column-offset/column-offset-tab.component';
import { CharClassifierTabComponent } from './tabs/char-classifier/char-classifier-tab.component';
import { TessOcrTabComponent } from './tabs/tess-ocr/tess-ocr-tab.component';
import { SettingsTabComponent } from './tabs/settings/settings-tab.component';
import { PerfsTabComponent } from './tabs/perfs/perfs-tab.component';
import { ResultsTabComponent } from './tabs/results/results-tab.component';

type TabId = 'input' | 'perspective' | 'pre-processing' | 'grid-detect'
  | 'line-height' | 'char-width' | 'line-offset' | 'column-offset'
  | 'char-classifier' | 'tess-ocr' | 'settings' | 'perfs' | 'results';

interface Tab { id: TabId; label: string; }

@Component({
  selector: 'app-root',
  imports: [
    CommonModule,
    InputTabComponent, PerspectiveTabComponent, PreProcessingTabComponent,
    GridDetectTabComponent, LineHeightTabComponent, CharWidthTabComponent,
    LineOffsetTabComponent, ColumnOffsetTabComponent, CharClassifierTabComponent,
    TessOcrTabComponent, SettingsTabComponent, PerfsTabComponent, ResultsTabComponent
  ],
  template: `
    <div class="tab-bar">
      @for (tab of tabs; track tab.id) {
        <button [class.active]="activeTab === tab.id" (click)="activeTab = tab.id">
          {{ tab.label }}
        </button>
      }
    </div>
    <div class="tab-content">
      @switch (activeTab) {
        @case ('input')          { <app-input-tab /> }
        @case ('perspective')    { <app-perspective-tab /> }
        @case ('pre-processing') { <app-pre-processing-tab /> }
        @case ('grid-detect')    { <app-grid-detect-tab /> }
        @case ('line-height')    { <app-line-height-tab /> }
        @case ('char-width')     { <app-char-width-tab /> }
        @case ('line-offset')    { <app-line-offset-tab /> }
        @case ('column-offset')  { <app-column-offset-tab /> }
        @case ('char-classifier'){ <app-char-classifier-tab /> }
        @case ('tess-ocr')       { <app-tess-ocr-tab /> }
        @case ('settings')       { <app-settings-tab /> }
        @case ('perfs')          { <app-perfs-tab /> }
        @case ('results')        { <app-results-tab /> }
      }
    </div>
  `
})
export class AppComponent {
  activeTab: TabId = 'input';

  tabs: Tab[] = [
    { id: 'input',          label: 'Input' },
    { id: 'perspective',    label: 'Perspective' },
    { id: 'pre-processing', label: 'Pre-Processing' },
    { id: 'grid-detect',    label: 'Grid Detect' },
    { id: 'line-height',    label: 'Line Height' },
    { id: 'char-width',     label: 'Char Width' },
    { id: 'line-offset',    label: 'Line Offset' },
    { id: 'column-offset',  label: 'Column Offset' },
    { id: 'char-classifier',label: 'Char Classifier' },
    { id: 'tess-ocr',       label: 'Tess OCR' },
    { id: 'settings',       label: 'Settings' },
    { id: 'perfs',          label: 'Perfs' },
    { id: 'results',        label: 'Results' },
  ];
}
