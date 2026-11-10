/* The GitHub star button with the live count. It opens the repo in a new tab, where the Star button is:
   a website can't star a repo for someone, only their own signed-in GitHub account can. */
import { REPO } from '../links';
import { formatStars, useStars } from '../lib/useStars';

const Star = () => <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12 2.5l2.9 6.2 6.7.8-4.9 4.6 1.3 6.7L12 17.5l-6 3.3 1.3-6.7L2.4 9.5l6.7-.8z" /></svg>;

export function StarButton({ className = 'btn soft sm star', label, where }: { className?: string; label?: string; where: string }) {
  const stars = useStars();
  const count = stars === null ? null : formatStars(stars);
  return (
    <a className={className} href={REPO} target="_blank" rel="noopener" data-ev="star_click" data-where={where} data-detail={stars === null ? '' : String(stars)}
      aria-label={`Star Mode on GitHub${stars === null ? '' : `: ${stars} stars`} (opens GitHub)`}>
      <Star />
      {label ? <>{label}{count !== null && <span className="cnt pill">{count}</span>}</> : <span className="cnt">{count ?? 'Star'}</span>}
    </a>
  );
}
