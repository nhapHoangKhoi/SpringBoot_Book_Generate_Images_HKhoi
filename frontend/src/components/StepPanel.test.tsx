import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { StepPanel } from './StepPanel'
import { aProject } from '../test/factories'

/**
 * The step panel is where every §4.3 behaviour becomes visible, so its five states are the
 * frontend tests worth having: ready, running, failed, stranded, finished.
 */
describe('StepPanel', () => {
  it('offers the current step and an optional style on step one', async () => {
    const onRun = vi.fn()
    render(<StepPanel project={aProject()} starting={false} onRun={onRun} onReset={vi.fn()} />)

    await userEvent.type(screen.getByLabelText(/art style/i), 'Bold woodcut prints')
    await userEvent.click(screen.getByRole('button', { name: /generate style/i }))

    expect(onRun).toHaveBeenCalledWith('STYLE', 'Bold woodcut prints')
  })

  it('leaves the style out when the user typed none, so Gemini picks one', async () => {
    const onRun = vi.fn()
    render(<StepPanel project={aProject()} starting={false} onRun={onRun} onReset={vi.fn()} />)

    await userEvent.click(screen.getByRole('button', { name: /generate style/i }))

    expect(onRun).toHaveBeenCalledWith('STYLE', undefined)
  })

  it('only offers a style field on step one', () => {
    render(
      <StepPanel
        project={aProject({ status: 'STYLE_SET', currentStep: 'CHARACTERS', completedSteps: 1 })}
        starting={false}
        onRun={vi.fn()}
        onReset={vi.fn()}
      />,
    )

    expect(screen.queryByLabelText(/art style/i)).not.toBeInTheDocument()
  })

  /** §4.3: the in-progress state must name the running step, not show a bare spinner. */
  it('names the running step and disables the button while it runs', () => {
    render(
      <StepPanel
        project={aProject({
          status: 'CHARACTERS_GENERATED',
          currentStep: 'PORTRAITS',
          completedSteps: 2,
          stepState: 'RUNNING',
          stepStartedAt: '2026-08-16T10:00:00Z',
        })}
        starting={false}
        onRun={vi.fn()}
        onReset={vi.fn()}
      />,
    )

    expect(screen.getByText(/painting character portraits/i)).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /generating portraits/i })).toBeDisabled()
  })

  it('shows the failure and retries only that step', async () => {
    const onRun = vi.fn()
    render(
      <StepPanel
        project={aProject({
          status: 'STYLE_SET',
          currentStep: 'CHARACTERS',
          completedSteps: 1,
          stepState: 'FAILED',
          stepError: 'Gemini returned 503',
        })}
        starting={false}
        onRun={onRun}
        onReset={vi.fn()}
      />,
    )

    expect(screen.getByRole('alert')).toHaveTextContent(/characters failed/i)
    expect(screen.getByText('Gemini returned 503')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /retry characters/i }))
    expect(onRun).toHaveBeenCalledWith('CHARACTERS', undefined)
  })

  /** A stranded step reports itself as RUNNING; only `stale` separates it from healthy work. */
  it('offers recovery for a stranded step rather than a spinner', async () => {
    const onReset = vi.fn()
    render(
      <StepPanel
        project={aProject({
          stepState: 'RUNNING',
          stale: true,
          stepStartedAt: '2026-08-16T09:00:00Z',
        })}
        starting={false}
        onRun={vi.fn()}
        onReset={onReset}
      />,
    )

    expect(screen.getByText(/interrupted and never finished/i)).toBeInTheDocument()
    expect(screen.queryByRole('status')).not.toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: /recover style/i }))
    expect(onReset).toHaveBeenCalledWith('STYLE')
  })

  it('offers nothing to run once every step is done', () => {
    render(
      <StepPanel
        project={aProject({ status: 'DONE', currentStep: null, completedSteps: 5 })}
        starting={false}
        onRun={vi.fn()}
        onReset={vi.fn()}
      />,
    )

    expect(screen.getByText(/all five steps are complete/i)).toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('disables the action while a start request is in flight', () => {
    render(<StepPanel project={aProject()} starting onRun={vi.fn()} onReset={vi.fn()} />)

    expect(screen.getByRole('button', { name: /starting/i })).toBeDisabled()
  })
})
