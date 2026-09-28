import { useRef, type KeyboardEvent } from 'react'
import { ChevronRight, Folder, FolderOpen, Tag } from 'lucide-react'
import { cn } from '@/lib/utils'
import { visibleNodes, type TreeNode } from './treeModel'

interface TaxonomyTreeProps {
  roots: TreeNode[]
  expanded: Set<string>
  selectedId: string | null
  onToggle: (nodeId: string) => void
  onSelect: (nodeId: string) => void
}

/** WAI-ARIA tree with roving focus: arrows move, Right/Left expand/collapse, Enter/Space selects. */
export function TaxonomyTree({ roots, expanded, selectedId, onToggle, onSelect }: TaxonomyTreeProps) {
  const refs = useRef(new Map<string, HTMLDivElement>())
  const flat = visibleNodes(roots, expanded)
  const focusId = selectedId && flat.some((item) => item.node.id === selectedId) ? selectedId : flat[0]?.node.id

  const focus = (nodeId: string | undefined) => {
    if (nodeId) {
      refs.current.get(nodeId)?.focus()
    }
  }

  const onKeyDown = (event: KeyboardEvent, item: TreeNode) => {
    const index = flat.findIndex((candidate) => candidate.node.id === item.node.id)
    const isOpen = expanded.has(item.node.id)
    switch (event.key) {
      case 'ArrowDown':
        focus(flat[index + 1]?.node.id)
        break
      case 'ArrowUp':
        focus(flat[index - 1]?.node.id)
        break
      case 'ArrowRight':
        if (item.children.length && !isOpen) {
          onToggle(item.node.id)
        } else {
          focus(item.children[0]?.node.id)
        }
        break
      case 'ArrowLeft':
        if (isOpen) {
          onToggle(item.node.id)
        } else {
          focus(item.node.parentId ?? undefined)
        }
        break
      case 'Home':
        focus(flat[0]?.node.id)
        break
      case 'End':
        focus(flat.at(-1)?.node.id)
        break
      case 'Enter':
      case ' ':
        onSelect(item.node.id)
        break
      default:
        return
    }
    event.preventDefault()
  }

  const renderItems = (items: TreeNode[], level: number) =>
    items.map((item) => {
      const isOpen = expanded.has(item.node.id)
      const hasChildren = item.children.length > 0
      const Icon = hasChildren ? (isOpen ? FolderOpen : Folder) : Tag
      return (
        <li key={item.node.id} role="none">
          <div
            ref={(element) => {
              if (element) {
                refs.current.set(item.node.id, element)
              } else {
                refs.current.delete(item.node.id)
              }
            }}
            role="treeitem"
            aria-level={level}
            aria-expanded={hasChildren ? isOpen : undefined}
            aria-selected={selectedId === item.node.id}
            tabIndex={item.node.id === focusId ? 0 : -1}
            onKeyDown={(event) => onKeyDown(event, item)}
            onClick={() => onSelect(item.node.id)}
            className={cn(
              'flex h-7 cursor-pointer items-center gap-1.5 rounded-md pr-2 text-sm outline-none hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring',
              selectedId === item.node.id && 'bg-muted font-medium',
            )}
            style={{ paddingLeft: `${(level - 1) * 16 + 4}px` }}
          >
            {hasChildren ? (
              <button
                type="button"
                tabIndex={-1}
                aria-hidden
                onClick={(event) => {
                  event.stopPropagation()
                  onToggle(item.node.id)
                }}
                className="rounded p-0.5 hover:bg-background"
              >
                <ChevronRight className={cn('size-3.5 transition-transform', isOpen && 'rotate-90')} />
              </button>
            ) : (
              <span className="w-4.5" />
            )}
            <Icon aria-hidden className="size-4 text-muted-foreground" />
            <span className="truncate">{item.node.name}</span>
            {item.totalAssetCount > 0 && (
              <span className="ml-auto text-xs text-muted-foreground tabular" aria-label={`${item.totalAssetCount} assets`}>
                {item.totalAssetCount}
              </span>
            )}
          </div>
          {hasChildren && isOpen && (
            <ul role="group" className="flex flex-col gap-0.5">
              {renderItems(item.children, level + 1)}
            </ul>
          )}
        </li>
      )
    })

  return (
    <ul role="tree" aria-label="Taxonomy" className="flex flex-col gap-0.5">
      {renderItems(roots, 1)}
    </ul>
  )
}
