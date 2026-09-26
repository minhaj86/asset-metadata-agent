import { useMutation } from '@tanstack/react-query'
import { BookOpen, Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { askKnowledge } from '@/api/knowledge'
import { errorMessage } from '@/api/errors'
import { isAiAvailable, useSystemStatus } from '@/hooks/useSystemStatus'

/**
 * Asks the governance knowledge base (RAG over metadata documentation) about a field. Answers are grounded in the
 * retrieved documents and list their sources; the assistant says UNKNOWN when the documentation does not cover it.
 */
export function KnowledgeHint({ question }: { question: string }) {
  const { data: status } = useSystemStatus()
  const ask = useMutation({ mutationFn: () => askKnowledge(question) })

  return (
    <div className="flex flex-col gap-2">
      <Button
        variant="outline"
        size="sm"
        className="self-start"
        onClick={() => ask.mutate()}
        disabled={ask.isPending || !isAiAvailable(status)}
        title={isAiAvailable(status) ? undefined : 'AI service unavailable'}
      >
        {ask.isPending ? <Loader2 aria-hidden className="animate-spin" /> : <BookOpen aria-hidden />}
        Ask governance docs: “{question}”
      </Button>
      <div aria-live="polite">
        {ask.isError && <p className="text-sm text-red-700 dark:text-red-300">{errorMessage(ask.error)}</p>}
        {ask.data && (
          <div className="space-y-2 rounded-md border bg-muted/30 p-3 text-sm">
            <p className="whitespace-pre-line">{ask.data.answer}</p>
            <p className="text-xs text-muted-foreground">
              {ask.data.grounded ? 'Grounded in governance documentation' : 'Not covered by the documentation'} · {ask.data.model}
            </p>
            {ask.data.sources.length > 0 && (
              <ul className="space-y-0.5 text-xs text-muted-foreground">
                {ask.data.sources.map((source) => (
                  <li key={`${source.documentId}-${source.section}`}>
                    Source: {source.title} › {source.section}
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
      </div>
    </div>
  )
}
