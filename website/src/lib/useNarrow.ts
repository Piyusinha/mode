import { useSyncExternalStore } from 'react';

/** True on phone-width screens (≤ 900 px): they get the vertical film, placed after the tour. */
const QUERY = '(max-width: 900px)';

export function useNarrow(): boolean {
  return useSyncExternalStore(
    (onChange) => {
      const mq = matchMedia(QUERY);
      mq.addEventListener('change', onChange);
      return () => mq.removeEventListener('change', onChange);
    },
    () => matchMedia(QUERY).matches,
    () => false,
  );
}
