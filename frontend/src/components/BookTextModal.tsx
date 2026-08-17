import { useEffect, useRef } from 'react'

interface Props {
  text: string
  onClose: () => void
}

/** The book, readable in full at any point in the pipeline (assessment §4.4). */
export function BookTextModal({ text, onClose }: Props) {
  const closeButton = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    closeButton.current?.focus()
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  return (
    <div
      className="modal-overlay"
      onClick={(event) => {
        if (event.target === event.currentTarget) onClose()
      }}
    >
      <div className="modal-box" role="dialog" aria-modal="true" aria-labelledby="book-modal-title">
        <div className="modal-head">
          <h4 id="book-modal-title">Full book text</h4>
          <button
            ref={closeButton}
            type="button"
            className="btn-ghost"
            onClick={onClose}
            aria-label="Close"
          >
            ✕
          </button>
        </div>
        <div className="modal-body">{text}</div>
      </div>
    </div>
  )
}
