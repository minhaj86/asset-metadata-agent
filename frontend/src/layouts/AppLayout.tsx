import { Suspense, useState } from 'react'
import { Outlet, useNavigation } from 'react-router'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Skeleton } from '@/components/ui/skeleton'
import { SidebarNav } from './SidebarNav'
import { TopBar } from './TopBar'

function PageSkeleton() {
  return (
    <div className="space-y-4" aria-busy="true" aria-label="Loading page">
      <Skeleton className="h-8 w-64" />
      <Skeleton className="h-4 w-96" />
      <Skeleton className="h-64 w-full" />
    </div>
  )
}

/** Top bar + side navigation + main content. The sidebar collapses into a sheet below the lg breakpoint. */
export function AppLayout() {
  const [navigationOpen, setNavigationOpen] = useState(false)
  const navigation = useNavigation()

  return (
    <div className="flex h-dvh flex-col bg-background">
      <a
        href="#main-content"
        className="sr-only z-50 rounded bg-primary px-3 py-2 text-primary-foreground focus:not-sr-only focus:absolute focus:top-2 focus:left-2"
      >
        Skip to content
      </a>
      <TopBar onOpenNavigation={() => setNavigationOpen(true)} />
      <div className="flex min-h-0 flex-1">
        <aside className="hidden w-56 shrink-0 border-r bg-sidebar lg:block">
          <SidebarNav />
        </aside>
        <Sheet open={navigationOpen} onOpenChange={setNavigationOpen}>
          <SheetContent side="left" className="w-64 bg-sidebar p-0">
            <SheetHeader className="border-b">
              <SheetTitle>AI Metadata DAM</SheetTitle>
              <SheetDescription className="sr-only">Main navigation</SheetDescription>
            </SheetHeader>
            <SidebarNav onNavigate={() => setNavigationOpen(false)} />
          </SheetContent>
        </Sheet>
        <main
          id="main-content"
          tabIndex={-1}
          aria-busy={navigation.state === 'loading'}
          className="min-w-0 flex-1 overflow-y-auto px-4 py-5 outline-none lg:px-6"
        >
          <div className="mx-auto max-w-[1600px]">
            <Suspense fallback={<PageSkeleton />}>
              <Outlet />
            </Suspense>
          </div>
        </main>
      </div>
    </div>
  )
}
