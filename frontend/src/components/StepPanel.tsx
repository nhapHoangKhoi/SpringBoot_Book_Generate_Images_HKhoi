import { useState } from 'react'
import { STEPS } from '../api/types'
import type { ProjectDetail, Step } from '../api/types'

interface Props {
  project: ProjectDetail
  starting: boolean
  onRun: (step: Step, style?: string) => void
  onReset: (step: Step) => void
}

/**
 * The single action for wherever the project currently is.
 *
 * <p>Five mutually exclusive states, in priority order: finished, stranded, failed, running, ready.
 * Stranded is checked before failed and running because a step stuck in RUNNING still reports
 * itself as running — only the server's `stale` flag distinguishes "working" from "abandoned".
 */
export function StepPanel({ project, starting, onRun, onReset }: Props) {
  const [style, setStyle] = useState('')
  const current = project.currentStep
  const step = STEPS.find((s) => s.key === current)

  if (!current || !step) {
    return (
      <section className="step-panel">
        <div className="status-line" style={{ color: 'var(--grad-ink)' }}>
          <span className="num done" aria-hidden="true" style={{ width: 20, height: 20, fontSize: 11 }}>
            ✓
          </span>
          All five steps are complete.
        </div>
        <p className="help">
          Nothing regenerates on its own. Reopen this project any time — everything is saved.
        </p>
      </section>
    )
  }

  if (project.stale) {
    return (
      <section className="step-panel failed">
        <div className="status-line">{step.label} was interrupted and never finished.</div>
        <p className="help">
          Nothing before this step was affected — everything already generated is saved. Retrying is
          safe.
        </p>
        <button
          type="button"
          className="btn btn-secondary"
          disabled={starting}
          onClick={() => onReset(current)}
        >
          Recover {step.label}
        </button>
      </section>
    )
  }

  if (project.stepState === 'FAILED') {
    return (
      <section className="step-panel failed">
        <div className="status-line" role="alert">
          {step.label} failed
        </div>
        <p className="help">{project.stepError}</p>
        <button
          type="button"
          className="btn btn-primary"
          disabled={starting}
          onClick={() => onRun(current, style || undefined)}
        >
          Retry {step.label}
        </button>
      </section>
    )
  }

  if (project.stepState === 'RUNNING') {
    return (
      <section className="step-panel">
        <div className="status-line" aria-live="polite">
          <span className="spinner small" aria-hidden="true" />
          {step.runningCaption}…
        </div>
        <p className="help">
          This can take 30 seconds or more. You can leave this page — the work continues on the
          server, and reopening the project picks it up.
        </p>
        <button type="button" className="btn btn-primary" disabled>
          Generating {step.label}…
        </button>
      </section>
    )
  }

  return (
    <section className="step-panel">
      <div className="status-line" style={{ color: 'var(--grad-ink)' }}>
        Ready for the next step: <b>&nbsp;{step.label}</b>
      </div>
      {current === 'STYLE' && (
        <div className="field">
          <label htmlFor="style-input">Art style (optional)</label>
          <input
            id="style-input"
            value={style}
            placeholder="Leave blank to let Gemini choose a style from your book"
            onChange={(event) => setStyle(event.target.value)}
          />
        </div>
      )}
      <button
        type="button"
        className="btn btn-primary"
        disabled={starting}
        onClick={() => onRun(current, style || undefined)}
      >
        {starting ? 'Starting…' : `Generate ${step.label}`}
      </button>
    </section>
  )
}
