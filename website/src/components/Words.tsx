import { Children, cloneElement, isValidElement, type CSSProperties, type ReactElement, type ReactNode } from 'react';

/** A heading whose words rise in one by one when it scrolls into view (see useReveal). */
export function Words({ id, className = 'sec-h', children }: { id?: string; className?: string; children: ReactNode }) {
  let i = 0;
  const split = (node: ReactNode): ReactNode => {
    if (typeof node === 'string')
      return node.split(/(\s+)/).filter(Boolean).map((part, k) =>
        /^\s+$/.test(part) ? part : <span key={k} className="wd" style={{ '--i': i++ } as CSSProperties}>{part}</span>);
    if (isValidElement(node)) {
      const el = node as ReactElement<{ children?: ReactNode }>;
      return cloneElement(el, undefined, split(el.props.children));
    }
    if (Array.isArray(node)) return Children.map(node, split);
    return node;
  };
  return <h2 className={`${className} words`} id={id}>{split(children)}</h2>;
}
