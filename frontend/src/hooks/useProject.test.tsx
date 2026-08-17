import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { aProject } from '../test/factories'

class FakeApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly currentStep: string | null = null,
  ) {
    super(message)
  }
  get isAlreadyRunning() {
    return this.code === 'ALREADY_RUNNING'
  }
  get isOutOfOrder() {
    return this.code === 'OUT_OF_ORDER'
  }
}

vi.mock('../api/client', () => ({
  api: { getProject: vi.fn(), runStep: vi.fn(), resetStep: vi.fn() },
  ApiError: FakeApiError,
}))

const { api } = await import('../api/client')
const { useProject } = await import('./useProject')

describe('useProject', () => {
  afterEach(() => vi.clearAllMocks())

  it('loads the project the server has, not one the client remembers', async () => {
    vi.mocked(api.getProject).mockResolvedValue(aProject({ status: 'STYLE_SET', completedSteps: 1 }))

    const { result } = renderHook(() => useProject('tlv2u592'))

    await waitFor(() => expect(result.current.project?.completedSteps).toBe(1))
  })

  /**
   * The duplicate-click guard as the user experiences it: a second tab already owns the step, so
   * the refusal is not an error to show — it is a cue to display the state that actually exists.
   */
  it('treats an already-running refusal as a reason to re-read, not an error', async () => {
    const running = aProject({ stepState: 'RUNNING', stepStartedAt: '2026-08-16T10:00:00Z' })
    vi.mocked(api.getProject).mockResolvedValue(running)
    vi.mocked(api.runStep).mockRejectedValue(
      new FakeApiError(409, 'ALREADY_RUNNING', 'STYLE is already running.', 'STYLE'),
    )

    const { result } = renderHook(() => useProject('tlv2u592'))
    await waitFor(() => expect(result.current.project).not.toBeNull())
    await act(async () => {
      await result.current.runStep('STYLE')
    })

    expect(result.current.actionError).toBeNull()
    expect(result.current.project?.stepState).toBe('RUNNING')
  })

  it('surfaces a refusal the user does need to know about', async () => {
    vi.mocked(api.getProject).mockResolvedValue(aProject({ status: 'DONE', currentStep: null }))
    vi.mocked(api.runStep).mockRejectedValue(
      new FakeApiError(409, 'PIPELINE_COMPLETE', 'Every step of this project is already done.'),
    )

    const { result } = renderHook(() => useProject('tlv2u592'))
    await waitFor(() => expect(result.current.project).not.toBeNull())
    await act(async () => {
      await result.current.runStep('ILLUSTRATIONS')
    })

    expect(result.current.actionError).toMatch(/already done/i)
  })

  it('reports a server it cannot reach', async () => {
    vi.mocked(api.getProject).mockRejectedValue(new TypeError('Failed to fetch'))

    const { result } = renderHook(() => useProject('tlv2u592'))

    await waitFor(() => expect(result.current.loadError).toMatch(/could not reach/i))
  })
})
