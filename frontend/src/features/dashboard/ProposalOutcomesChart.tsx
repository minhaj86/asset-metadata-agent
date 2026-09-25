import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import type { Dashboard } from '@/types/api'

const SERIES = [
  { key: 'autoApproved', label: 'Auto-applied', color: '#7c3aed' },
  { key: 'humanApproved', label: 'Approved', color: '#059669' },
  { key: 'humanRejected', label: 'Rejected', color: '#dc2626' },
  { key: 'rejectedByPolicy', label: 'Rejected by policy', color: '#9f1239' },
  { key: 'superseded', label: 'Superseded', color: '#a1a1aa' },
  { key: 'pending', label: 'Pending', color: '#d97706' },
] as const

/** AI proposal outcomes as a bar chart plus an equivalent data table for assistive technology. */
export function ProposalOutcomesChart({ outcomes }: { outcomes: Dashboard['proposals']['outcomes'] }) {
  const data = SERIES.map((series) => ({ name: series.label, value: outcomes[series.key], color: series.color }))
  return (
    <figure className="flex flex-col gap-2">
      <div aria-hidden className="h-56">
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: -16 }}>
            <CartesianGrid vertical={false} strokeDasharray="3 3" className="stroke-border" />
            <XAxis dataKey="name" tick={{ fontSize: 11 }} interval={0} tickLine={false} />
            <YAxis allowDecimals={false} tick={{ fontSize: 11 }} tickLine={false} axisLine={false} />
            <Tooltip cursor={{ fillOpacity: 0.08 }} contentStyle={{ fontSize: 12 }} />
            <Bar dataKey="value" radius={[3, 3, 0, 0]} label={{ position: 'top', fontSize: 11 }}>
              {data.map((entry) => (
                <Cell key={entry.name} fill={entry.color} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
      <table className="sr-only">
        <caption>AI proposal outcomes</caption>
        <tbody>
          {data.map((entry) => (
            <tr key={entry.name}>
              <th scope="row">{entry.name}</th>
              <td>{entry.value}</td>
            </tr>
          ))}
        </tbody>
      </table>
      <figcaption className="flex flex-wrap gap-x-3 gap-y-1 text-xs text-muted-foreground">
        {data.map((entry) => (
          <span key={entry.name} className="inline-flex items-center gap-1">
            <span aria-hidden className="size-2 rounded-sm" style={{ backgroundColor: entry.color }} />
            {entry.name}: <span className="font-medium text-foreground tabular">{entry.value}</span>
          </span>
        ))}
      </figcaption>
    </figure>
  )
}
