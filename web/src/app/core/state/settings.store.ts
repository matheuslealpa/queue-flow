import { computed, effect, Injectable } from '@angular/core';
import { persistedSignal } from './persisted';

export type Theme = 'system' | 'light' | 'dark';

export interface Settings {
  pollingSeconds: number;
  kioskChannel: string;
  theme: Theme;
  boardSound: boolean;
}

export const DEFAULT_SETTINGS: Settings = {
  pollingSeconds: 3,
  kioskChannel: 'web-totem',
  theme: 'system',
  boardSound: true,
};

@Injectable({ providedIn: 'root' })
export class SettingsStore {
  readonly settings = persistedSignal<Settings>('queueflow.web.settings', DEFAULT_SETTINGS);

  readonly pollingMs = computed(() => Math.max(1, this.settings().pollingSeconds) * 1000);
  readonly kioskChannel = computed(() => this.settings().kioskChannel.trim() || DEFAULT_SETTINGS.kioskChannel);

  constructor() {
    const media = window.matchMedia?.('(prefers-color-scheme: dark)');
    const apply = () => {
      const theme = this.settings().theme;
      const dark = theme === 'dark' || (theme === 'system' && !!media?.matches);
      document.documentElement.classList.toggle('dark', dark);
    };
    media?.addEventListener('change', apply);
    effect(apply);
  }

  update(patch: Partial<Settings>): void {
    this.settings.update((s) => ({ ...s, ...patch }));
  }
}
