import { http, HttpResponse } from 'msw'
import { setupServer } from 'msw/node'
import {
  aCatalogItem,
  aDashboard,
  anAssetMetadata,
  aPage,
  aQualityReport,
  aSchema,
  aSystemStatus,
} from './fixtures'

/** Default handlers: a healthy backend with one asset. Tests override individual endpoints with server.use(). */
export const defaultHandlers = [
  http.get('/api/system/status', () => HttpResponse.json(aSystemStatus())),
  http.get('/api/dashboard', () => HttpResponse.json(aDashboard())),
  http.get('/api/catalog/assets', () => HttpResponse.json(aPage([aCatalogItem()]))),
  http.get('/api/catalog/assets/:assetId', () => HttpResponse.json(aCatalogItem())),
  http.get('/api/assets/:assetId/metadata', () => HttpResponse.json(anAssetMetadata())),
  http.get('/api/metadata/schema/:assetType', () => HttpResponse.json(aSchema())),
  http.get('/api/assets/:assetId/quality', () => HttpResponse.json(aQualityReport())),
  http.get('/api/metadata/proposals', () => HttpResponse.json([])),
  http.get('/api/workflows', () => HttpResponse.json([])),
  http.get('/api/assets/:assetId/metadata/history', () => HttpResponse.json([])),
  http.get('/api/assets/:assetId/duplicates', () => HttpResponse.json([])),
  http.get('/api/assets/:assetId/recommendations', () => HttpResponse.json([])),
  http.get('/api/taxonomy/nodes', () => HttpResponse.json([])),
  http.get('/api/catalog/taxonomy-usage', () => HttpResponse.json([])),
]

export const server = setupServer(...defaultHandlers)
