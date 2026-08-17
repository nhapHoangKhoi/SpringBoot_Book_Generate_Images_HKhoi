import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api, ApiError } from '../api/client'
import { ProgressMini, StatusPill } from '../components/StatusPill'
import { stepLabel } from '../api/types'
import type { ProjectSummary } from '../api/types'

export function ProjectListPage() {
  const navigate = useNavigate()
  const [projects, setProjects] = useState<ProjectSummary[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api
      .listProjects()
      .then(setProjects)
      .catch((problem) => {
        setError(problem instanceof ApiError ? problem.message : 'Could not reach the server.')
        setProjects([])
      })
  }, [])

  if (projects === null) {
    return (
      <div className="page">
        <div className="status-line">
          <span className="spinner small" aria-hidden="true" /> Loading your projects…
        </div>
      </div>
    )
  }

  return (
    <div className="page">
      <div className="list-head">
        <h2>Your projects</h2>
        <button type="button" className="btn btn-primary" onClick={() => navigate('/projects/new')}>
          + New project
        </button>
      </div>

      {error && <p className="banner">{error}</p>}

      {projects.length === 0 ? (
        <div className="empty-state">
          <p style={{ margin: 0 }}>No projects yet.</p>
          <button type="button" className="btn btn-primary" onClick={() => navigate('/projects/new')}>
            + New project
          </button>
        </div>
      ) : (
        <div className="project-list">
          {projects.map((project) => (
            <button
              key={project.id}
              type="button"
              className="project-row"
              onClick={() => navigate(`/projects/${project.id}`)}
            >
              <div className="title">
                <h4>{project.title}</h4>
                <span className="meta">
                  Created {new Date(project.createdAt).toLocaleDateString()} · {subtitle(project)}
                </span>
              </div>
              <ProgressMini completedSteps={project.completedSteps} />
              <StatusPill status={project.status} stepState={project.stepState} />
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

function subtitle(project: ProjectSummary): string {
  if (project.stepState === 'FAILED') return `${stepLabel(project.currentStep)} needs a retry`
  if (project.stepState === 'RUNNING') return `${stepLabel(project.currentStep)} is running`
  if (project.status === 'DONE') return 'All 5 steps complete'
  if (project.completedSteps === 0) return 'Book text saved · style not yet generated'
  return `${project.completedSteps} of 5 steps done · next up ${stepLabel(project.currentStep)}`
}
