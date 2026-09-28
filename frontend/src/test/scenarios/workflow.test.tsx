import { act, screen, waitFor, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { WorkflowRun } from '@/types/api'
import { FakeEventSource } from '../FakeEventSource'
import { ASSET_ID, aStage, aWorkflowRun, WORKFLOW_ID } from '../fixtures'
import { renderRoute } from '../render'
import { server } from '../server'

function stageStatus(name: string): string | null {
  const stages = screen.getByRole('list', { name: 'Workflow stages' })
  return within(stages).getByText(name, { selector: 'span' }).closest('li[data-status]')?.getAttribute('data-status') ?? null
}

const parallelRunning: WorkflowRun = aWorkflowRun({
  currentStage: 'Taxonomy Agent, Metadata Enrichment Agent',
  stages: [
    aStage('loadAssetContext', { name: 'Load asset context', kind: 'DETERMINISTIC_STEP', status: 'COMPLETED', durationMillis: 21 }),
    aStage('metadataExtractionAgent', { name: 'Metadata Extraction Agent', status: 'COMPLETED', durationMillis: 640, detail: '6 candidate(s)', attempts: 1 }),
    aStage('taxonomyAgent', { name: 'Taxonomy Agent', parallelGroup: 'parallelReasoning', conditional: true, status: 'RUNNING', attempts: 1 }),
    aStage('metadataEnrichmentAgent', { name: 'Metadata Enrichment Agent', parallelGroup: 'parallelReasoning', status: 'RUNNING', attempts: 1 }),
    aStage('recordResults', { name: 'Apply policy & record proposals', kind: 'DETERMINISTIC_STEP' }),
  ],
})

const completed: WorkflowRun = aWorkflowRun({
  status: 'COMPLETED',
  currentStage: null,
  finishedAt: '2026-10-03T09:30:03Z',
  durationMillis: 2600,
  summary: { autoApplied: 4, pendingReview: 8, lowConfidenceSuggestions: 0, rejectedByPolicy: 0, unchanged: 0 },
  stages: parallelRunning.stages.map((stage) => ({
    ...stage,
    status: 'COMPLETED',
    durationMillis: stage.durationMillis ?? 1200,
    toolCalls: stage.id === 'taxonomyAgent' ? ['searchTaxonomy'] : stage.toolCalls,
    detail: stage.id === 'taxonomyAgent' ? 'Fashion > Men > Clothing > Shirts (0.90)' : stage.detail,
  })),
})

describe('agentic workflow progress', () => {
  beforeEach(() => {
    FakeEventSource.reset()
    vi.stubGlobal('EventSource', FakeEventSource)
    server.use(
      http.get('/api/workflows', () => HttpResponse.json([aWorkflowRun()])),
      http.get('/api/workflows/:workflowId', () => HttpResponse.json(aWorkflowRun())),
    )
  })

  afterEach(() => vi.unstubAllGlobals())

  it('updates stage status live from server-sent events', async () => {
    renderRoute(`/assets/${ASSET_ID}/enrich?run=${WORKFLOW_ID}`)

    await screen.findByRole('list', { name: 'Workflow stages' })
    expect(stageStatus('Metadata Extraction Agent')).toBe('RUNNING')
    expect(stageStatus('Taxonomy Agent')).toBe('WAITING')
    expect(screen.getByText('Live updates via server-sent events')).toBeInTheDocument()
    const stream = FakeEventSource.latest()
    expect(stream.url).toBe(`/api/workflows/${WORKFLOW_ID}/events`)

    expect(screen.queryByRole('button', { name: /Run again/ })).not.toBeInTheDocument()

    act(() => stream.emit('workflow', parallelRunning))
    await waitFor(() => expect(stageStatus('Metadata Extraction Agent')).toBe('COMPLETED'))
    const history = screen.getByRole('region', { name: 'Run history' })
    expect(within(history).getByText('Taxonomy Agent, Metadata Enrichment Agent')).toBeInTheDocument()
    expect(stageStatus('Taxonomy Agent')).toBe('RUNNING')
    expect(stageStatus('Metadata Enrichment Agent')).toBe('RUNNING')
    expect(screen.getByText('Runs in parallel')).toBeInTheDocument()
    expect(screen.getByText('Running: Taxonomy Agent, Metadata Enrichment Agent', { selector: 'p' })).toBeInTheDocument()

    act(() => stream.emit('workflow', completed))
    await waitFor(() => expect(stageStatus('Taxonomy Agent')).toBe('COMPLETED'))
    expect(screen.getByText('Applied automatically')).toBeInTheDocument()
    expect(screen.getByText('Awaiting review')).toBeInTheDocument()
    expect(screen.getByText(/Fashion > Men > Clothing > Shirts \(0\.90\)/)).toBeInTheDocument()
    expect(within(history).getByText('4 applied · 8 for review')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Run again/ })).toBeEnabled()
    expect(stream.closed).toBe(true)
  })

  it('falls back to polling the run when the event stream fails', async () => {
    let polls = 0
    server.use(
      http.get('/api/workflows/:workflowId', () => {
        polls += 1
        return HttpResponse.json(polls > 1 ? completed : aWorkflowRun())
      }),
    )
    renderRoute(`/assets/${ASSET_ID}/enrich?run=${WORKFLOW_ID}`)
    await screen.findByRole('list', { name: 'Workflow stages' })

    act(() => FakeEventSource.latest().fail())

    expect(await screen.findByText('Updating by polling')).toBeInTheDocument()
    await waitFor(() => expect(stageStatus('Taxonomy Agent')).toBe('COMPLETED'), { timeout: 4000 })
    expect(polls).toBeGreaterThan(1)
  })
})
