import { describe, expect, it } from 'vitest'
import { confidenceLevel } from './confidence'

const thresholds = { autoApproveThreshold: 0.9, humanReviewThreshold: 0.6 }

describe('confidenceLevel', () => {
  it.each([
    [0.96, 'high'],
    [0.9, 'high'],
    [0.81, 'medium'],
    [0.6, 'medium'],
    [0.52, 'low'],
    [0, 'low'],
  ])('bands %s as %s using the backend governance thresholds', (confidence, level) => {
    expect(confidenceLevel(confidence, thresholds)).toBe(level)
  })

  it('follows changed backend thresholds instead of hard-coded cut-offs', () => {
    expect(confidenceLevel(0.85, { autoApproveThreshold: 0.8, humanReviewThreshold: 0.5 })).toBe('high')
  })

  it('does not invent a band before the thresholds are known', () => {
    expect(confidenceLevel(0.96, undefined)).toBeNull()
  })
})
