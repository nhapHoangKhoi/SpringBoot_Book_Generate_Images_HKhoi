import { useRef, useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { api, ApiError } from '../api/client'

/**
 * Upload or paste — both end up as the same string in one request, so the server has one code
 * path rather than a multipart branch it would only ever use from this screen.
 */
export function NewProjectPage() {
  const navigate = useNavigate()
  const fileInput = useRef<HTMLInputElement>(null)
  const [title, setTitle] = useState('')
  const [bookText, setBookText] = useState('')
  const [fileName, setFileName] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const readFile = (file: File | undefined) => {
    if (!file) return
    const reader = new FileReader()
    reader.onload = () => {
      setBookText(String(reader.result ?? ''))
      setFileName(file.name)
      setError(null)
    }
    reader.onerror = () => setError('That file could not be read.')
    reader.readAsText(file)
  }

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    if (!title.trim() || !bookText.trim()) {
      setError('Give the project a title and provide the book text (paste or upload).')
      return
    }
    setBusy(true)
    setError(null)
    try {
      const created = await api.createProject(title.trim(), bookText)
      navigate(`/projects/${created.id}`)
    } catch (problem) {
      setError(problem instanceof ApiError ? problem.message : 'Could not reach the server.')
      setBusy(false)
    }
  }

  return (
    <div className="page narrow">
      <Link to="/projects" className="back-link">
        ← Back to projects
      </Link>
      <h3 style={{ fontSize: 20 }}>Start a new illustration project</h3>
      <p className="meta" style={{ marginBottom: 20 }}>
        Give it a title, then paste the book&rsquo;s text or upload a .txt file.
      </p>

      <form onSubmit={submit} noValidate>
        <div className="field">
          <label htmlFor="title">
            Project title <span className="req">*</span>
          </label>
          <input
            id="title"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            placeholder="e.g. The Wind in the Willows — cottage-core"
          />
        </div>

        <div className="field" style={{ marginTop: 16 }}>
          <label htmlFor="book-text">
            Book text <span className="req">*</span>
          </label>
          <button
            type="button"
            className={`dropzone ${fileName ? 'has-file' : ''}`}
            onClick={() => fileInput.current?.click()}
          >
            <div style={{ fontSize: 13, fontWeight: 600 }}>
              {fileName ? `✓ ${fileName} loaded` : 'Click to choose a .txt file'}
            </div>
            <div className="meta" style={{ marginTop: 8 }}>
              Plain text only · sent to Gemini once, then reused by every step
            </div>
          </button>
          <input
            ref={fileInput}
            type="file"
            accept=".txt,text/plain"
            style={{ display: 'none' }}
            data-testid="file-input"
            onChange={(event) => readFile(event.target.files?.[0])}
          />
          <div className="divider-or">or paste text</div>
          <textarea
            id="book-text"
            rows={6}
            value={bookText}
            onChange={(e) => setBookText(e.target.value)}
            placeholder="Once upon a time, in a small burrow by the river…"
          />
        </div>

        {error && (
          <p className="error-text" role="alert">
            {error}
          </p>
        )}

        <button type="submit" className="btn btn-primary btn-block" style={{ marginTop: 20 }} disabled={busy}>
          {busy ? 'Creating…' : 'Create project →'}
        </button>
      </form>
    </div>
  )
}
