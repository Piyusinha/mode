/** Mo, Mode's character, as SVG. Faces follow ModeMo.swift (120-pt grid). */
export type Mood = 'idle' | 'working' | 'yourturn' | 'done' | 'listening' | 'sleep';

const FACE: Record<Mood, string> = {
  idle: '<ellipse cx="47" cy="58" rx="5" ry="8" fill="#1A1A1F"/><ellipse cx="73" cy="58" rx="5" ry="8" fill="#1A1A1F"/><path d="M52 76 Q60 83 68 76" fill="none" stroke="#1A1A1F" stroke-width="4" stroke-linecap="round"/>',
  working: '<path d="M41 52 L55 52 M67 52 L81 52" stroke="#1A1A1F" stroke-width="4" stroke-linecap="round"/><ellipse cx="50" cy="62" rx="5" ry="5.5" fill="#1A1A1F"/><ellipse cx="76" cy="62" rx="5" ry="5.5" fill="#1A1A1F"/><circle cx="52.5" cy="61" r="1.8" fill="#fff"/><circle cx="78.5" cy="61" r="1.8" fill="#fff"/><path d="M57 79 Q62 81 67 79" fill="none" stroke="#1A1A1F" stroke-width="3.5" stroke-linecap="round"/>',
  yourturn: '<circle cx="46" cy="59" r="8" fill="#1A1A1F"/><circle cx="74" cy="59" r="8" fill="#1A1A1F"/><circle cx="49" cy="56" r="2.6" fill="#fff"/><circle cx="77" cy="56" r="2.6" fill="#fff"/><ellipse cx="60" cy="80" rx="5" ry="4" fill="#1A1A1F"/>',
  done: '<path d="M40 61 Q47 51 54 61 M66 61 Q73 51 80 61" fill="none" stroke="#1A1A1F" stroke-width="5" stroke-linecap="round"/><path d="M47 73 Q60 89 73 73 Z" fill="#1A1A1F"/><path d="M103 12 L106 21 L115 24 L106 27 L103 36 L100 27 L91 24 L100 21 Z" fill="#F2B705"/>',
  listening: '<ellipse cx="49" cy="52" rx="5" ry="8" fill="#1A1A1F"/><ellipse cx="75" cy="52" rx="5" ry="8" fill="#1A1A1F"/><ellipse cx="62" cy="78" rx="4" ry="4" fill="#1A1A1F"/><path d="M110 46Q117 60 110 74" fill="none" stroke="#1A1A1F" stroke-opacity="0.45" stroke-width="3.5" stroke-linecap="round"/><path d="M114 38Q124 60 114 82" fill="none" stroke="#1A1A1F" stroke-opacity="0.25" stroke-width="3.5" stroke-linecap="round"/>',
  sleep: '<path d="M39 60 Q46.5 67 54 60 M66 60 Q73.5 67 81 60" fill="none" stroke="#1A1A1F" stroke-width="4.5" stroke-linecap="round"/><path d="M55 79 Q60 82 65 79" fill="none" stroke="#1A1A1F" stroke-width="3.5" stroke-linecap="round"/>',
};

export const moSvg = (m: Mood): string =>
  '<svg viewBox="0 0 120 120" width="100%" height="100%" aria-hidden="true"><path d="M60 12 C90 12 106 30 106 60 C106 92 88 108 60 108 C32 108 14 92 14 60 C14 30 30 12 60 12 Z" fill="#FF6B3D"/>' +
  (FACE[m] ?? FACE.idle) + '</svg>';
