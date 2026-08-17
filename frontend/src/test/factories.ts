import type { ItemView, ProjectDetail } from '../api/types'

/** A project in its initial state; override only what a test is actually about. */
export function aProject(overrides: Partial<ProjectDetail> = {}): ProjectDetail {
  return {
    id: 'tlv2u592',
    title: 'Hello A',
    createdAt: '2026-08-16T10:00:00Z',
    status: 'CREATED',
    stepState: 'IDLE',
    currentStep: 'STYLE',
    completedSteps: 0,
    stale: false,
    stepStartedAt: null,
    stepError: null,
    style: null,
    characters: [],
    chapters: [],
    ...overrides,
  }
}

export function anItem(overrides: Partial<ItemView> = {}): ItemView {
  return {
    name: 'The Mole',
    prompt: 'A shy mole in a velvet waistcoat',
    imageState: 'PENDING',
    imageFile: null,
    error: null,
    ...overrides,
  }
}
