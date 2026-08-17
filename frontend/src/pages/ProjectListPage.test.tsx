import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { ProjectListPage } from './ProjectListPage'
import type { ProjectSummary } from '../api/types'

vi.mock('../api/client', () => ({
  api: { listProjects: vi.fn() },
  ApiError: class ApiError extends Error {},
}))

const { api } = await import('../api/client')

function renderList(projects: ProjectSummary[]) {
  vi.mocked(api.listProjects).mockResolvedValue(projects)
  render(
    <MemoryRouter>
      <ProjectListPage />
    </MemoryRouter>,
  )
}

const summary = (overrides: Partial<ProjectSummary> = {}): ProjectSummary => ({
  id: 'p1',
  title: 'Hello A',
  createdAt: '2026-08-16T10:00:00Z',
  status: 'CREATED',
  stepState: 'IDLE',
  currentStep: 'STYLE',
  completedSteps: 0,
  ...overrides,
})

describe('ProjectListPage', () => {
  afterEach(() => vi.clearAllMocks())

  it('invites the user to start one when they have none', async () => {
    renderList([])

    expect(await screen.findByText(/no projects yet/i)).toBeInTheDocument()
  })

  it('shows each project with its status and progress', async () => {
    renderList([
      summary({ title: 'Draft one' }),
      summary({
        id: 'p2',
        title: 'Half done',
        status: 'PORTRAITS_GENERATED',
        currentStep: 'CHAPTERS',
        completedSteps: 3,
      }),
      summary({ id: 'p3', title: 'Finished', status: 'DONE', currentStep: null, completedSteps: 5 }),
    ])

    expect(await screen.findByText('Draft one')).toBeInTheDocument()
    expect(screen.getByText('Draft')).toBeInTheDocument()
    expect(screen.getByText('Done')).toBeInTheDocument()
    expect(screen.getByText(/3 of 5 steps done · next up Chapters/i)).toBeInTheDocument()
    expect(screen.getByLabelText('5 of 5 steps complete')).toBeInTheDocument()
  })

  /** A project needing a retry has to be obvious from the list, not only from inside it. */
  it('flags a project whose step failed', async () => {
    renderList([summary({ stepState: 'FAILED', currentStep: 'CHARACTERS', completedSteps: 1 })])

    expect(await screen.findByText(/needs attention/i)).toBeInTheDocument()
    expect(screen.getByText(/characters needs a retry/i)).toBeInTheDocument()
  })

  it('shows which step is running', async () => {
    renderList([summary({ stepState: 'RUNNING', currentStep: 'PORTRAITS', completedSteps: 2 })])

    expect(await screen.findByText(/portraits is running/i)).toBeInTheDocument()
  })
})
