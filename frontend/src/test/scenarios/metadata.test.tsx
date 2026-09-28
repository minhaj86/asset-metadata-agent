import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { aCatalogItem, anAssetMetadata, anEntry, ASSET_ID, aSystemStatus, defaultEntries } from '../fixtures'
import { renderRoute } from '../render'
import { server } from '../server'

function rowOf(fieldName: string): HTMLElement {
  return screen.getByRole('button', { name: `Inspect ${fieldName}` }).closest('tr')!
}

describe('metadata editor', () => {
  it('shows provenance for every value and distinguishes AI-applied from authoritative metadata', async () => {
    renderRoute(`/assets/${ASSET_ID}?tab=metadata`)

    await screen.findByRole('button', { name: 'Inspect Background type' })
    const aiRow = rowOf('Background type')
    expect(within(aiRow).getByText('WHITE')).toBeInTheDocument()
    expect(within(aiRow).getByText('AI generated')).toBeInTheDocument()
    expect(within(aiRow).getByText('AI-applied')).toBeInTheDocument()
    expect(within(aiRow).getByText('96%')).toBeInTheDocument()

    const authoritativeRow = rowOf('Product ID')
    expect(within(authoritativeRow).getByText('Product system')).toBeInTheDocument()
    expect(within(authoritativeRow).getByText('Authoritative')).toBeInTheDocument()

    const missingRow = rowOf('Angle facing')
    expect(within(missingRow).getByText('Not set')).toBeInTheDocument()
  })

  it('opens the inspector with the AI origin: agent, explanation and evidence', async () => {
    const { user } = renderRoute(`/assets/${ASSET_ID}?tab=metadata`)

    await user.click(await screen.findByRole('button', { name: 'Inspect Background type' }))

    const inspector = await screen.findByRole('dialog')
    expect(within(inspector).getByText('From AI suggestion')).toBeInTheDocument()
    expect(within(inspector).getAllByText('Metadata Extraction Agent').length).toBeGreaterThan(0)
    expect(within(inspector).getByText('Image analysis reports a plain white studio background.')).toBeInTheDocument()
    expect(within(inspector).getByText('imageAnalysis.background=white')).toBeInTheDocument()
    expect(within(inspector).getByText('Applied by approval policy', { exact: false })).toBeInTheDocument()
  })

  it('renders backend validation errors in the edit form', async () => {
    server.use(
      http.put('/api/assets/:assetId/metadata/:field', () =>
        HttpResponse.json(
          {
            status: 422,
            title: 'Unprocessable Content',
            code: 'METADATA_VALIDATION_FAILED',
            detail: 'Metadata validation failed',
            issues: [{ severity: 'ERROR', code: 'LENGTH_OUT_OF_RANGE', field: 'altText', message: "Field 'altText' must be at most 250 characters" }],
          },
          { status: 422, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      ),
    )
    const { user } = renderRoute(`/assets/${ASSET_ID}?tab=metadata`)

    await user.click(await screen.findByRole('button', { name: 'Edit Alt text' }))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByRole('textbox', { name: 'Alt text' }), 'Front view of a light blue shirt on white')
    await user.click(within(dialog).getByRole('button', { name: 'Save' }))

    expect(await within(dialog).findByText("Field 'altText' must be at most 250 characters")).toBeInTheDocument()
    expect(within(dialog).getByRole('textbox', { name: 'Alt text' })).toHaveAttribute('aria-invalid', 'true')
  })

  it('validates required input on the client before calling the backend', async () => {
    const { user } = renderRoute(`/assets/${ASSET_ID}?tab=metadata`)

    await user.click(await screen.findByRole('button', { name: 'Edit Alt text' }))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByRole('textbox', { name: 'Alt text' }), 'too short')
    await user.click(within(dialog).getByRole('button', { name: 'Save' }))

    expect(await within(dialog).findByText('At least 15 characters')).toBeInTheDocument()
  })
})

describe('AI outage', () => {
  it('keeps manual metadata editing available while AI is unavailable', async () => {
    let saved: unknown
    server.use(
      http.get('/api/system/status', () =>
        HttpResponse.json(
          aSystemStatus({
            ai: { state: 'UNAVAILABLE', detail: 'OPENAI_API_KEY is not set', provider: 'openai', model: 'gpt-4.1-mini', embeddingModel: null, timeoutSeconds: 180, maxToolInvocations: 5 },
          }),
        ),
      ),
      http.get('/api/catalog/assets/:assetId', () => HttpResponse.json(aCatalogItem())),
      http.put('/api/assets/:assetId/metadata/:field', async ({ request, params }) => {
        saved = await request.json()
        return HttpResponse.json(anEntry({ field: String(params.field), value: 'FRONT', source: 'MANUAL', updatedBy: 'jane.doe' }))
      }),
      http.get('/api/assets/:assetId/metadata', () => HttpResponse.json(anAssetMetadata(defaultEntries()))),
    )
    const { user } = renderRoute(`/assets/${ASSET_ID}?tab=metadata`)

    const runButton = await screen.findByRole('button', { name: /Run AI enrichment/ })
    expect(runButton).toBeDisabled()
    expect(await screen.findByText('Unavailable', { exact: false })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Edit Angle facing' }))
    const dialog = await screen.findByRole('dialog')
    await user.click(within(dialog).getByRole('combobox'))
    await user.click(await screen.findByRole('option', { name: 'Front' }))
    await user.click(within(dialog).getByRole('button', { name: 'Save' }))

    expect(await screen.findByText('Metadata saved')).toBeInTheDocument()
    expect(saved).toEqual({ value: 'FRONT' })
  })
})
