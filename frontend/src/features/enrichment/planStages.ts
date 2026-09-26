import type { StageDefinition, WorkflowStage } from '@/types/api'

/** The published workflow plan as not-yet-started stages (for showing the topology before a run). */
export function planToStages(plan: StageDefinition[]): WorkflowStage[] {
  return plan.map((stage) => ({
    id: stage.id,
    name: stage.displayName,
    kind: stage.kind,
    description: stage.description,
    parallelGroup: stage.parallelGroup,
    conditional: stage.conditional,
    status: 'WAITING',
    startedAt: null,
    durationMillis: null,
    inputTokens: null,
    outputTokens: null,
    toolCalls: [],
    detail: null,
    attempts: 0,
  }))
}
