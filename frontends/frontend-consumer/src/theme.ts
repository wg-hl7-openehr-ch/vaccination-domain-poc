// Shared design tokens. Kept tiny and flat so screens read cleanly.

export const colors = {
  bg: '#F3F5F8',
  surface: '#FFFFFF',
  border: '#E3E8EE',
  borderStrong: '#D2D9E1',

  text: '#111820',
  textMuted: '#5B6672',
  textFaint: '#8A95A2',

  primary: '#0B4A7A',
  primarySoft: '#E8F0F7',

  swissRed: '#D8232A',

  success: '#0F8E7E',
  successSoft: '#E5F4F1',

  shadow: '#0B1F33',
} as const;

export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 32,
} as const;

export const radius = {
  sm: 8,
  md: 12,
  lg: 16,
  pill: 999,
} as const;

/** Below this window width the app bar switches to its compact (phone) layout. */
export const WIDE_LAYOUT_MIN_WIDTH = 600;

export const font = {
  display: 24,
  title: 19,
  body: 15,
  label: 13,
  caption: 11,
} as const;

/** Deterministic avatar palette so a given person always gets the same colour. */
const avatarPalettes: ReadonlyArray<readonly [string, string]> = [
  ['#E8F0F7', '#0B4A7A'],
  ['#E5F4F1', '#0F8E7E'],
  ['#FBF2E2', '#B45309'],
  ['#FBEAE7', '#B42318'],
  ['#EDE7F6', '#5E35B1'],
];

export function avatarPalette(seed: string): { bg: string; fg: string } {
  let sum = 0;
  for (let i = 0; i < seed.length; i++) sum += seed.charCodeAt(i);
  const [bg, fg] = avatarPalettes[sum % avatarPalettes.length];
  return { bg, fg };
}
