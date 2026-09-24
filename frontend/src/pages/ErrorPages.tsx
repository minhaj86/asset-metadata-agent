import { isRouteErrorResponse, Link, useRouteError } from 'react-router'
import { Compass, TriangleAlert } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { EmptyState } from '@/components/States'

export function NotFoundPage() {
  return (
    <EmptyState
      icon={Compass}
      title="Page not found"
      description="The page you are looking for does not exist."
      action={
        <Button asChild variant="outline" size="sm">
          <Link to="/">Go to dashboard</Link>
        </Button>
      }
    />
  )
}

/** Router-level error boundary: friendly message, no stack traces. */
export function RouteErrorPage() {
  const error = useRouteError()
  const description = isRouteErrorResponse(error)
    ? `${error.status} ${error.statusText}`
    : 'An unexpected error occurred while rendering this page.'
  return (
    <div className="flex h-dvh items-center justify-center p-6">
      <EmptyState
        icon={TriangleAlert}
        title="Something went wrong"
        description={description}
        action={
          <Button size="sm" onClick={() => window.location.assign('/')}>
            Reload application
          </Button>
        }
      />
    </div>
  )
}
