export const AppleLogo = () => (
  <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
    <path d="M12.152 6.896c-.948 0-2.415-1.078-3.96-1.04-2.04.027-3.91 1.183-4.961 3.014-2.117 3.675-.546 9.103 1.519 12.09 1.013 1.454 2.208 3.09 3.792 3.039 1.52-.065 2.09-.987 3.935-.987 1.831 0 2.35.987 3.96.948 1.637-.026 2.676-1.48 3.676-2.948 1.156-1.688 1.636-3.325 1.662-3.415-.039-.013-3.182-1.221-3.22-4.857-.026-3.04 2.48-4.494 2.597-4.559-1.429-2.09-3.623-2.324-4.39-2.376-2-.156-3.675 1.09-4.61 1.09zM15.53 3.83c.843-1.012 1.4-2.427 1.245-3.83-1.207.052-2.662.805-3.532 1.818-.78.896-1.454 2.338-1.273 3.714 1.338.104 2.715-.688 3.559-1.701" />
  </svg>
);

export const AndroidLogo = () => (
  <svg viewBox="0 0 24 24" aria-hidden="true">
    <path d="M7.2 11.6L5.4 8.6M16.8 11.6l1.8-3" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" fill="none" />
    <path d="M3.5 19a8.5 8.5 0 0 1 17 0z" fill="currentColor" />
    <circle cx="8.6" cy="15.4" r="1.15" className="eye" /><circle cx="15.4" cy="15.4" r="1.15" className="eye" />
  </svg>
);

export const WindowsLogo = () => (
  <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M3 5.3l7.4-1v7.2H3zM11.3 4.1L21 2.8v8.7h-9.7zM3 12.5h7.4v7.2L3 18.7zM11.3 12.5H21v8.7l-9.7-1.3z" /></svg>
);

export const DownloadIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M12 4v11M7 10l5 5 5-5M5 20h14" /></svg>
);

export const ArrowDown = ({ size = 16 }: { size?: number }) => (
  <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d="M12 5v14M6 13l6 6 6-6" /></svg>
);
