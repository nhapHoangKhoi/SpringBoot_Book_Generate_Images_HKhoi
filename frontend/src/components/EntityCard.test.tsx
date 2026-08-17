import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { EntityCard } from './EntityCard'
import { anItem } from '../test/factories'

/** Per-item state is what lets the user watch portraits land one at a time (assessment §4.4). */
describe('EntityCard', () => {
  it('shows a placeholder before anything is generated', () => {
    render(<EntityCard item={anItem()} projectId="p1" kind="character" />)

    expect(screen.getByText(/not generated yet/i)).toBeInTheDocument()
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })

  it('spins for the item being drawn right now, naming it', () => {
    render(<EntityCard item={anItem({ imageState: 'RUNNING' })} projectId="p1" kind="character" />)

    expect(screen.getByRole('status', { name: /generating the mole/i })).toBeInTheDocument()
  })

  it('shows the finished picture with a useful alt text', () => {
    render(
      <EntityCard
        item={anItem({ imageState: 'DONE', imageFile: 'portrait-0.png' })}
        projectId="p1"
        kind="character"
      />,
    )

    const image = screen.getByRole('img', { name: /the mole/i })
    expect(image).toHaveAttribute('src', expect.stringContaining('/api/projects/p1/images/portrait-0.png'))
  })

  it('reports one failed image without hiding its siblings', () => {
    render(
      <EntityCard
        item={anItem({ imageState: 'FAILED', error: 'safety filter blocked the prompt' })}
        projectId="p1"
        kind="character"
      />,
    )

    expect(screen.getByText(/failed/i)).toBeInTheDocument()
    expect(screen.getByText(/safety filter blocked the prompt/i)).toBeInTheDocument()
  })

  it('always shows the prompt, since that is what the user judges', () => {
    render(<EntityCard item={anItem()} projectId="p1" kind="character" />)

    expect(screen.getByText(/velvet waistcoat/i)).toBeInTheDocument()
  })
})
