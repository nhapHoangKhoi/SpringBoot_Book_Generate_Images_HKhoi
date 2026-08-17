import type { ProjectStatus, StepState } from '../api/types'

interface Props {
  status: ProjectStatus
  stepState: StepState
}

/** Draft / In progress / Done / Needs attention, in one glance. */
export function StatusPill({ status, stepState }: Props) {
  if (stepState === 'FAILED') return <span className="pill danger">Needs attention</span>
  if (stepState === 'RUNNING') {
    return (
      <span className="pill">
        <span className="dot" aria-hidden="true" />
        Working…
      </span>
    )
  }
  if (status === 'DONE') return <span className="pill ink">Done</span>
  if (status === 'CREATED') return <span className="pill gray">Draft</span>
  return <span className="pill">In progress</span>
}

export function ProgressMini({ completedSteps }: { completedSteps: number }) {
  return (
    <div className="progress-mini" aria-label={`${completedSteps} of 5 steps complete`}>
      {[0, 1, 2, 3, 4].map((index) => (
        <span key={index} className={`seg ${index < completedSteps ? 'on' : ''}`} />
      ))}
    </div>
  )
}
