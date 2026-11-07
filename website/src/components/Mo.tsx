import type { CSSProperties } from 'react';
import { moSvg, type Mood } from '../lib/mo';

export function Mo({ mood = 'idle', className, style, id }: { mood?: Mood; className?: string; style?: CSSProperties; id?: string }) {
  return <span className={className} style={style} id={id} aria-hidden="true" dangerouslySetInnerHTML={{ __html: moSvg(mood) }} />;
}
