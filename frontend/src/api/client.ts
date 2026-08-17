import { loadUser } from '../auth/session'
import type { ProjectDetail, ProjectSummary, Step, User } from './types'

/** A failure the server described. `code` is what the UI branches on. */
export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly currentStep: Step | null = null,
  ) {
    super(message)
    this.name = 'ApiError'
  }

  /** True when the server refused because that step is already in flight somewhere else. */
  get isAlreadyRunning() {
    return this.code === 'ALREADY_RUNNING'
  }

  /** True when this client's idea of the current step was stale — `currentStep` says the truth. */
  get isOutOfOrder() {
    return this.code === 'OUT_OF_ORDER'
  }
}

/**
 * The envelope every JSON endpoint answers with: `{ success, message, data }`.
 *
 * <p>On failure `data` carries the machine-readable error, which is what the UI branches on.
 */
interface ApiEnvelope<T> {
  success: boolean
  message: string
  data: T
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const user = loadUser()
  const headers = new Headers(init.headers)
  if (user) headers.set('X-User-Id', user.id)
  if (init.body) headers.set('Content-Type', 'application/json')

  const response = await fetch(path, { ...init, headers })
  // JSON by contract, but a proxy or a crash can still hand back HTML.
  const body = (await response.json().catch(() => null)) as ApiEnvelope<unknown> | null

  if (!response.ok || body?.success === false) {
    const error = body?.data as { code?: string; currentStep?: Step } | null
    throw new ApiError(
      response.status,
      error?.code ?? 'UNKNOWN',
      body?.message ?? `Request failed (${response.status})`,
      error?.currentStep ?? null,
    )
  }

  return body?.data as T
}

export const api = {
  signIn: (name: string, email: string) =>
    request<User>('/api/session', { method: 'POST', body: JSON.stringify({ name, email }) }),

  listProjects: () => request<ProjectSummary[]>('/api/projects'),

  createProject: (title: string, bookText: string) =>
    request<ProjectDetail>('/api/projects', {
      method: 'POST',
      body: JSON.stringify({ title, bookText }),
    }),

  getProject: (id: string) => request<ProjectDetail>(`/api/projects/${id}`),

  getBookText: (id: string) => request<string>(`/api/projects/${id}/book`),

  runStep: (id: string, step: Step, style?: string) =>
    request<ProjectDetail>(`/api/projects/${id}/steps/${step}/run`, {
      method: 'POST',
      body: JSON.stringify({ style: style ?? null }),
    }),

  resetStep: (id: string, step: Step) =>
    request<ProjectDetail>(`/api/projects/${id}/steps/${step}/reset`, { method: 'POST' }),
}

/**
 * URL for a generated image.
 *
 * <p>An `img` tag cannot send the X-User-Id header, so the id travels as a query parameter here.
 * It is a hash of the email rather than a secret, and identity in this app is explicitly
 * unauthenticated — the server still scopes the lookup to that user's directory.
 */
export function imageUrl(projectId: string, fileName: string): string {
  const user = loadUser()
  const suffix = user ? `?userId=${encodeURIComponent(user.id)}` : ''
  return `/api/projects/${projectId}/images/${fileName}${suffix}`
}
