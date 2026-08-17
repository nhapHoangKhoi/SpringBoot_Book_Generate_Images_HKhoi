export type Step = 'STYLE' | 'CHARACTERS' | 'PORTRAITS' | 'CHAPTERS' | 'ILLUSTRATIONS'

export type ProjectStatus =
  | 'CREATED'
  | 'STYLE_SET'
  | 'CHARACTERS_GENERATED'
  | 'PORTRAITS_GENERATED'
  | 'CHAPTERS_GENERATED'
  | 'DONE'

export type StepState = 'IDLE' | 'RUNNING' | 'FAILED'

export type ItemState = 'PENDING' | 'RUNNING' | 'DONE' | 'FAILED'

export interface User {
  id: string
  name: string
  email: string
}

export interface ItemView {
  name: string
  prompt: string
  imageState: ItemState
  imageFile: string | null
  error: string | null
}

export interface ProjectSummary {
  id: string
  title: string
  createdAt: string
  status: ProjectStatus
  stepState: StepState
  /** Null once every step is done. Derived by the server so the client never re-implements order. */
  currentStep: Step | null
  completedSteps: number
}

export interface ProjectDetail extends ProjectSummary {
  /** Server's verdict that a step has been running implausibly long. */
  stale: boolean
  stepStartedAt: string | null
  stepError: string | null
  style: string | null
  characters: ItemView[]
  chapters: ItemView[]
}

/** The five steps in order, with the wording the UI uses for each. */
export const STEPS: { key: Step; label: string; runningCaption: string }[] = [
  { key: 'STYLE', label: 'Style', runningCaption: 'Reading your book and defining an art style' },
  { key: 'CHARACTERS', label: 'Characters', runningCaption: 'Finding the main characters' },
  { key: 'PORTRAITS', label: 'Portraits', runningCaption: 'Painting character portraits' },
  { key: 'CHAPTERS', label: 'Chapters', runningCaption: 'Writing the chapter illustration prompt' },
  {
    key: 'ILLUSTRATIONS',
    label: 'Illustrations',
    runningCaption: 'Painting the chapter illustration',
  },
]

export function stepLabel(step: Step | null): string {
  return STEPS.find((s) => s.key === step)?.label ?? ''
}
