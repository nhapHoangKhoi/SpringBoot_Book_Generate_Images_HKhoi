import { imageUrl } from '../api/client'
import type { ItemView } from '../api/types'

interface Props {
  item: ItemView
  projectId: string
  kind: 'character' | 'chapter'
}

/**
 * One character or chapter, with its image once it exists.
 *
 * <p>Reads the item's own `imageState`, so during a run each picture flips independently — the
 * user watches portraits land one at a time instead of staring at a single blocking spinner.
 */
export function EntityCard({ item, projectId, kind }: Props) {
  const artClass = `art ${kind === 'chapter' ? 'wide' : ''}`

  return (
    <article className="entity-card">
      <div className={artClass}>
        {item.imageState === 'DONE' && item.imageFile ? (
          <img src={imageUrl(projectId, item.imageFile)} alt={`${item.name} — generated artwork`} />
        ) : item.imageState === 'RUNNING' ? (
          <div style={{ textAlign: 'center' }}>
            <span className="spinner" role="status" aria-label={`Generating ${item.name}`} />
            <div className="gen-caption">Generating {item.name}…</div>
          </div>
        ) : item.imageState === 'FAILED' ? (
          <div style={{ textAlign: 'center', padding: '0 12px' }}>
            <span className="label" style={{ color: 'var(--danger)' }}>
              Failed
            </span>
            {item.error && <div className="gen-caption">{item.error}</div>}
          </div>
        ) : (
          <span className="label">Not generated yet</span>
        )}
      </div>
      <div className="body">
        <h5>{item.name}</h5>
        <p>{item.prompt}</p>
      </div>
    </article>
  )
}
