import { lazy } from 'react'
import type { RouteObject } from 'react-router'
import { AppLayout } from '@/layouts/AppLayout'
import { NotFoundPage, RouteErrorPage } from '@/pages/ErrorPages'

const DashboardPage = lazy(() => import('@/pages/DashboardPage'))
const AssetsPage = lazy(() => import('@/pages/AssetsPage'))
const AssetDetailPage = lazy(() => import('@/pages/AssetDetailPage'))
const AssetEnrichmentPage = lazy(() => import('@/pages/AssetEnrichmentPage'))
const SearchPage = lazy(() => import('@/pages/SearchPage'))
const EnrichmentPage = lazy(() => import('@/pages/EnrichmentPage'))
const ApprovalsPage = lazy(() => import('@/pages/ApprovalsPage'))
const TaxonomyPage = lazy(() => import('@/pages/TaxonomyPage'))
const MetadataSchemaPage = lazy(() => import('@/pages/MetadataSchemaPage'))
const AuditPage = lazy(() => import('@/pages/AuditPage'))
const SettingsPage = lazy(() => import('@/pages/SettingsPage'))

export const routes: RouteObject[] = [
  {
    path: '/',
    element: <AppLayout />,
    errorElement: <RouteErrorPage />,
    children: [
      { index: true, element: <DashboardPage /> },
      { path: 'assets', element: <AssetsPage /> },
      { path: 'assets/:assetId', element: <AssetDetailPage /> },
      { path: 'assets/:assetId/enrich', element: <AssetEnrichmentPage /> },
      { path: 'search', element: <SearchPage /> },
      { path: 'enrichment', element: <EnrichmentPage /> },
      { path: 'approvals', element: <ApprovalsPage /> },
      { path: 'taxonomy', element: <TaxonomyPage /> },
      { path: 'metadata-schema', element: <MetadataSchemaPage /> },
      { path: 'audit', element: <AuditPage /> },
      { path: 'settings', element: <SettingsPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]
