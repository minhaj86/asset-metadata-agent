import type { TaxonomyNode, TaxonomyUsage } from '@/types/api'

export interface TreeNode {
  node: TaxonomyNode
  children: TreeNode[]
  assetCount: number
  totalAssetCount: number
}

/** Builds the hierarchy from the flat node list and attaches the backend's per-node asset counts. */
export function buildTree(nodes: TaxonomyNode[], usage: TaxonomyUsage[]): TreeNode[] {
  const counts = new Map(usage.map((entry) => [entry.nodeId, entry]))
  const byId = new Map<string, TreeNode>(
    nodes.map((node) => [
      node.id,
      { node, children: [], assetCount: counts.get(node.id)?.assetCount ?? 0, totalAssetCount: counts.get(node.id)?.totalAssetCount ?? 0 },
    ]),
  )
  const roots: TreeNode[] = []
  byId.forEach((treeNode) => {
    const parent = treeNode.node.parentId ? byId.get(treeNode.node.parentId) : undefined
    if (parent) {
      parent.children.push(treeNode)
    } else {
      roots.push(treeNode)
    }
  })
  const sort = (items: TreeNode[]) => {
    items.sort((a, b) => a.node.name.localeCompare(b.node.name))
    items.forEach((item) => sort(item.children))
  }
  sort(roots)
  return roots
}

/** Flattens the visible part of the tree (respecting expansion) for keyboard navigation. */
export function visibleNodes(roots: TreeNode[], expanded: Set<string>): TreeNode[] {
  const result: TreeNode[] = []
  const walk = (items: TreeNode[]) =>
    items.forEach((item) => {
      result.push(item)
      if (expanded.has(item.node.id)) {
        walk(item.children)
      }
    })
  walk(roots)
  return result
}

export function ancestorIds(nodes: TaxonomyNode[], nodeId: string): string[] {
  const byId = new Map(nodes.map((node) => [node.id, node]))
  const ids: string[] = []
  let current = byId.get(nodeId)?.parentId
  while (current) {
    ids.push(current)
    current = byId.get(current)?.parentId ?? null
  }
  return ids
}
