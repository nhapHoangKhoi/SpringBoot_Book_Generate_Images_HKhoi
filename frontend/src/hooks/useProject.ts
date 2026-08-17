import { useCallback, useEffect, useRef, useState } from 'react'
import { api, ApiError } from '../api/client'
import type { ProjectDetail, Step } from '../api/types'

/** Long enough not to hammer the server, short enough that a portrait landing feels immediate. */
const POLL_MS = 2000

interface UseProject {
  project: ProjectDetail | null
  loadError: string | null
  /** Set when a run request itself was refused, as opposed to a step failing. */
  actionError: string | null
  starting: boolean
  runStep: (step: Step, style?: string) => Promise<void>
  resetStep: (step: Step) => Promise<void>
}

/**
 * Loads a project and keeps it fresh while a step is running.
 *
 * <p>Polling is what makes this app resumable: the server owns the truth, so a refresh, a second
 * tab or a restart all converge on the same state without the client remembering anything.
 */
export function useProject(projectId: string): UseProject {
  const [project, setProject] = useState<ProjectDetail | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [starting, setStarting] = useState(false)
  const running = project?.stepState === 'RUNNING'
  const mounted = useRef(true)

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  const refresh = useCallback(async () => {
    try {
      const fresh = await api.getProject(projectId)
      if (mounted.current) {
        setProject(fresh)
        setLoadError(null)
      }
    } catch (error) {
      if (mounted.current) {
        setLoadError(error instanceof ApiError ? error.message : 'Could not reach the server.')
      }
    }
  }, [projectId])

  useEffect(() => {
    void refresh()
  }, [refresh])

  useEffect(() => {
    if (!running) return
    const timer = setInterval(() => void refresh(), POLL_MS)
    return () => clearInterval(timer)
  }, [running, refresh])

  const runStep = useCallback(
    async (step: Step, style?: string) => {
      setStarting(true)
      setActionError(null)
      try {
        const updated = await api.runStep(projectId, step, style)
        if (mounted.current) setProject(updated)
      } catch (error) {
        if (!(error instanceof ApiError)) {
          if (mounted.current) setActionError('Could not reach the server.')
          return
        }
        // A refusal is not a failure to show the user: it means another tab (or an earlier
        // click) already owns this step. Re-reading the project shows what is actually true.
        if (error.isAlreadyRunning || error.isOutOfOrder) {
          await refresh()
        } else if (mounted.current) {
          setActionError(error.message)
        }
      } finally {
        if (mounted.current) setStarting(false)
      }
    },
    [projectId, refresh],
  )

  const resetStep = useCallback(
    async (step: Step) => {
      setStarting(true)
      setActionError(null)
      try {
        const updated = await api.resetStep(projectId, step)
        if (mounted.current) setProject(updated)
      } catch (error) {
        if (mounted.current) {
          setActionError(
            error instanceof ApiError ? error.message : 'Could not reach the server.',
          )
        }
        await refresh()
      } finally {
        if (mounted.current) setStarting(false)
      }
    },
    [projectId, refresh],
  )

  return { project, loadError, actionError, starting, runStep, resetStep }
}
