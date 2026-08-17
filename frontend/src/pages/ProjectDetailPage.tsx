import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../api/client'
import { BookTextModal } from '../components/BookTextModal'
import { EntityCard } from '../components/EntityCard'
import { StepPanel } from '../components/StepPanel'
import { Stepper } from '../components/Stepper'
import { StatusPill } from '../components/StatusPill'
import { useProject } from '../hooks/useProject'

export function ProjectDetailPage() {
  const { projectId = '' } = useParams()
  const { project, loadError, actionError, starting, runStep, resetStep } = useProject(projectId)
  const [bookText, setBookText] = useState<string | null>(null)
  const [showBook, setShowBook] = useState(false)

  useEffect(() => {
    if (!projectId) return
    api
      .getBookText(projectId)
      .then(setBookText)
      .catch(() => setBookText(null))
  }, [projectId])

  if (loadError && !project) {
    return (
      <div className="page">
        <Link to="/projects" className="back-link">
          ← Back to projects
        </Link>
        <p className="banner" role="alert">
          {loadError}
        </p>
      </div>
    )
  }

  if (!project) {
    return (
      <div className="page">
        <div className="status-line">
          <span className="spinner small" aria-hidden="true" /> Loading project…
        </div>
      </div>
    )
  }

  const excerpt = bookText ? bookText.replace(/\s+/g, ' ').trim().slice(0, 220) : ''

  return (
    <div className="page">
      <Link to="/projects" className="back-link">
        ← Back to projects
      </Link>

      <div className="list-head" style={{ marginBottom: 4 }}>
        <h2 style={{ fontSize: 22 }}>{project.title}</h2>
        <StatusPill status={project.status} stepState={project.stepState} />
      </div>
      <p className="meta" style={{ marginBottom: 24 }}>
        Created {new Date(project.createdAt).toLocaleDateString()}
      </p>

      <Stepper completedSteps={project.completedSteps} stepState={project.stepState} />

      {actionError && (
        <p className="banner" role="alert">
          {actionError}
        </p>
      )}

      <div className="detail-grid">
        <div>
          <StepPanel project={project} starting={starting} onRun={runStep} onReset={resetStep} />

          {project.chapters.length > 0 && (
            <section style={{ marginTop: 28 }}>
              <div className="panel-title">
                <h3>Chapters ({project.chapters.length})</h3>
              </div>
              <div className="entity-grid single">
                {project.chapters.map((chapter) => (
                  <EntityCard
                    key={chapter.name}
                    item={chapter}
                    projectId={project.id}
                    kind="chapter"
                  />
                ))}
              </div>
            </section>
          )}

          {project.characters.length > 0 && (
            <section style={{ marginTop: 28 }}>
              <div className="panel-title">
                <h3>Characters ({project.characters.length})</h3>
              </div>
              <div className="entity-grid">
                {project.characters.map((character) => (
                  <EntityCard
                    key={character.name}
                    item={character}
                    projectId={project.id}
                    kind="character"
                  />
                ))}
              </div>
            </section>
          )}
        </div>

        <aside>
          {project.style && (
            <div className="side-note">
              <h5>Style</h5>
              <p>{project.style}</p>
            </div>
          )}
          <div className="side-note">
            <h5>Book text</h5>
            <p style={{ fontStyle: 'italic' }}>
              {excerpt ? `${excerpt}…` : 'Loading the book text…'}
            </p>
            {bookText && (
              <button
                type="button"
                className="btn-ghost"
                style={{ marginTop: 8 }}
                onClick={() => setShowBook(true)}
              >
                Read full text →
              </button>
            )}
          </div>
        </aside>
      </div>

      {showBook && bookText && (
        <BookTextModal text={bookText} onClose={() => setShowBook(false)} />
      )}
    </div>
  )
}
