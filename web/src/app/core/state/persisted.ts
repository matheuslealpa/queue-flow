import { DestroyRef, effect, inject, signal, type WritableSignal } from '@angular/core';

function read<T>(key: string, fallback: T): T {
  try {
    const raw = localStorage.getItem(key);
    return raw == null ? fallback : (JSON.parse(raw) as T);
  } catch {
    return fallback;
  }
}

/**
 * Signal gravado no localStorage e mantido em sincronia entre abas (evento `storage`), para que
 * painel, totem e guichês abertos em janelas diferentes enxerguem o mesmo ambiente e as mesmas chamadas.
 */
export function persistedSignal<T>(key: string, fallback: T): WritableSignal<T> {
  const state = signal<T>(read(key, fallback));
  let lastWritten = JSON.stringify(state());

  effect(() => {
    const json = JSON.stringify(state());
    if (json === lastWritten) return;
    lastWritten = json;
    try {
      localStorage.setItem(key, json);
    } catch {
      // armazenamento indisponível (aba anônima, cota): segue só em memória
    }
  });

  const onStorage = (event: StorageEvent) => {
    if (event.key !== key) return;
    lastWritten = event.newValue ?? JSON.stringify(fallback);
    state.set(event.newValue == null ? fallback : (JSON.parse(event.newValue) as T));
  };
  window.addEventListener('storage', onStorage);
  inject(DestroyRef).onDestroy(() => window.removeEventListener('storage', onStorage));

  return state;
}
