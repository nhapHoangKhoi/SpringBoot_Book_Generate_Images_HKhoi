import { STEPS } from '../api/types'
import type { StepState } from '../api/types'

interface Props {
  completedSteps: number
  stepState: StepState
}

/**
 * All five steps at once: what is done, what is happening, what is still ahead.
 *
 * <p>Driven by `completedSteps` from the server rather than any client-side notion of order.
 */
export function Stepper({ completedSteps, stepState }: Props) {
  return (
    <ol className="stepper" aria-label="Pipeline progress">
      {STEPS.map((step, index) => {
        const done = index < completedSteps
        const current = index === completedSteps
        const failed = current && stepState === 'FAILED'
        const state = done ? 'done' : current ? 'current' : 'pending'
        return (
          <li key={step.key} style={{ display: 'contents' }}>
            <div className={`step ${state}`}>
              <span
                className={`num ${done ? 'done' : failed ? 'failed' : current ? '' : 'gray'}`}
                aria-hidden="true"
              >
                {done ? '✓' : failed ? '!' : index + 1}
              </span>
              <span className="lbl">{step.label}</span>
              <span className="sr-only" style={srOnly}>
                {`${step.label}: ${done ? 'done' : failed ? 'failed' : current ? 'current' : 'pending'}`}
              </span>
            </div>
            {index < STEPS.length - 1 && (
              <div className={`connector ${done ? 'done' : ''}`} aria-hidden="true" />
            )}
          </li>
        )
      })}
    </ol>
  )
}

const srOnly: React.CSSProperties = {
  position: 'absolute',
  width: 1,
  height: 1,
  overflow: 'hidden',
  clip: 'rect(0 0 0 0)',
  whiteSpace: 'nowrap',
}
