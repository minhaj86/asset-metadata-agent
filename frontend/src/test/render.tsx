import type { ReactElement } from 'react'
import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, type RouteObject } from 'react-router'
import { RouterProvider } from 'react-router/dom'
import { QueryClient } from '@tanstack/react-query'
import { AppProviders } from '@/AppProviders'
import { routes } from '@/routes/routes'

function testQueryClient(): QueryClient {
  return new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity }, mutations: { retry: false } } })
}

/** Renders the real application routes at the given URL with fresh caches. */
export function renderRoute(path: string, routeObjects: RouteObject[] = routes) {
  const router = createMemoryRouter(routeObjects, { initialEntries: [path] })
  const client = testQueryClient()
  const user = userEvent.setup()
  const result = render(
    <AppProviders client={client}>
      <RouterProvider router={router} />
    </AppProviders>,
  )
  return { ...result, user, router, client }
}

/** Renders a single component inside providers and a memory router. */
export function renderWithProviders(element: ReactElement, path = '/') {
  return renderRoute(path, [{ path: '*', element }])
}
