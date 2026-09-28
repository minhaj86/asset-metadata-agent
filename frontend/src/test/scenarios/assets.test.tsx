import { screen, within } from '@testing-library/react'
import { http, HttpResponse } from 'msw'
import { describe, expect, it } from 'vitest'
import { aCatalogItem, aPage, ASSET_ID } from '../fixtures'
import { renderRoute } from '../render'
import { server } from '../server'

describe('asset list', () => {
  it('renders catalog rows with key metadata, completeness and AI status', async () => {
    server.use(
      http.get('/api/catalog/assets', () =>
        HttpResponse.json(
          aPage([
            aCatalogItem(),
            aCatalogItem({ id: 'other', externalAssetId: 'DA-100002', filename: 'red-dress-model.jpg', aiStatus: 'REVIEW_PENDING', rightsStatus: 'EXPIRED' }),
          ]),
        ),
      ),
    )

    renderRoute('/assets')

    const table = await screen.findByRole('table')
    const firstRow = within(table).getByRole('link', { name: 'DA-100001' }).closest('tr')!
    expect(within(firstRow).getByText('blue-shirt-front.jpg')).toBeInTheDocument()
    expect(within(firstRow).getByText('42.9%')).toBeInTheDocument()
    expect(within(firstRow).getByText('2 required missing')).toBeInTheDocument()
    expect(within(firstRow).getByText('Not enriched')).toBeInTheDocument()
    expect(within(firstRow).getByText('12345')).toBeInTheDocument()
    const secondRow = within(table).getByRole('link', { name: 'DA-100002' }).closest('tr')!
    expect(within(secondRow).getByText('Review pending')).toBeInTheDocument()
    expect(within(secondRow).getByText('Rights expired')).toBeInTheDocument()
    expect(screen.getByText(/1–2 of 2 assets/)).toBeInTheDocument()
  })

  it('sends filters from the URL to the backend catalog query', async () => {
    let requested: URL | undefined
    server.use(
      http.get('/api/catalog/assets', ({ request }) => {
        requested = new URL(request.url)
        return HttpResponse.json(aPage([]))
      }),
    )

    renderRoute('/assets?missingRequired=true&color=BLUE&sort=qualityScore&direction=asc')

    expect(await screen.findByText('No assets match these filters')).toBeInTheDocument()
    expect(requested?.searchParams.get('missingRequired')).toBe('true')
    expect(requested?.searchParams.getAll('filter')).toEqual(['color:BLUE'])
    expect(requested?.searchParams.get('sort')).toBe('qualityScore')
    expect(requested?.searchParams.get('direction')).toBe('asc')
  })
})

describe('asset detail', () => {
  it('loads the asset with its deterministic metadata summary', async () => {
    renderRoute(`/assets/${ASSET_ID}`)

    expect(await screen.findByRole('heading', { level: 1, name: /DA-100001/ })).toBeInTheDocument()
    expect(screen.getByText('blue-shirt-front.jpg')).toBeInTheDocument()
    expect(screen.getByRole('meter', { name: 'Completeness' })).toHaveAttribute('aria-valuenow', '42.9')
    expect(screen.getByRole('meter', { name: 'Metadata quality' })).toHaveAttribute('aria-valuenow', '53')
    expect((await screen.findByText(/Missing required:/)).parentElement).toHaveTextContent('Missing required: Angle facing, Alt text')
    expect(screen.getByRole('img', { name: /Preview of blue-shirt-front.jpg/ })).toHaveAttribute(
      'src',
      'http://localhost:8080/mock-dam/assets/DA-100001/blue-shirt-front.jpg',
    )
    expect(screen.getByRole('tab', { name: 'Metadata' })).toBeInTheDocument()
  })
})
