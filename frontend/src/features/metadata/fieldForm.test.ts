import { describe, expect, it } from 'vitest'
import { aFieldDefinition } from '@/test/fixtures'
import { fieldFormSchema, splitList, toApiValue, toFormValue } from './fieldForm'

const keywords = aFieldDefinition('keywords', { repeatable: true, validation: { pattern: null, minLength: null, maxLength: null, minValue: null, maxValue: null, maxItems: 2 } })
const modelCount = aFieldDefinition('modelCount', { dataType: 'INTEGER', validation: { pattern: null, minLength: null, maxLength: null, minValue: 0, maxValue: 10, maxItems: null } })
const modelPresent = aFieldDefinition('modelPresent', { dataType: 'BOOLEAN' })
const angleFacing = aFieldDefinition('angleFacing', { displayName: 'Angle facing', allowedValues: ['FRONT', 'BACK'] })
const usageRightsEnd = aFieldDefinition('usageRightsEnd', { dataType: 'DATE' })

function issues(field: ReturnType<typeof aFieldDefinition>, value: string | string[]): string[] {
  const result = fieldFormSchema(field).safeParse({ reason: '', value })
  return result.success ? [] : result.error.issues.map((issue) => issue.message)
}

describe('field form conversion', () => {
  it('maps API values to form inputs per data type', () => {
    expect(toFormValue(keywords, ['shirt', 'oxford'])).toEqual(['shirt', 'oxford'])
    expect(toFormValue(keywords, null)).toEqual([])
    expect(toFormValue(modelCount, 2)).toBe('2')
    expect(toFormValue(modelPresent, undefined)).toBe('false')
    expect(toFormValue(angleFacing, null)).toBe('')
  })

  it('maps form inputs back to typed JSON values', () => {
    expect(toApiValue(keywords, [' shirt ', '', 'oxford'])).toEqual(['shirt', 'oxford'])
    expect(toApiValue(modelCount, ' 3 ')).toBe(3)
    expect(toApiValue(modelPresent, 'true')).toBe(true)
    expect(toApiValue(angleFacing, ' FRONT ')).toBe('FRONT')
  })

  it('splits comma separated list input', () => {
    expect(splitList('shirt, oxford ,, blue')).toEqual(['shirt', 'oxford', 'blue'])
  })
})

describe('field form validation (immediate feedback only, the backend validates again)', () => {
  it('accepts valid values', () => {
    expect(issues(angleFacing, 'FRONT')).toEqual([])
    expect(issues(modelCount, '4')).toEqual([])
    expect(issues(usageRightsEnd, '2027-12-31')).toEqual([])
  })

  it('reports values outside the backend definition', () => {
    expect(issues(angleFacing, '')).toEqual(['Angle facing needs a value'])
    expect(issues(angleFacing, 'SIDE')).toEqual(['"SIDE" is not an allowed value'])
    expect(issues(modelCount, '11')).toEqual(['Must be at most 10'])
    expect(issues(modelCount, '1.5')).toEqual(['Must be a whole number'])
    expect(issues(usageRightsEnd, '31/12/2027')).toEqual(['Must be a date (YYYY-MM-DD)'])
    expect(issues(keywords, ['a', 'b', 'c'])).toEqual(['At most 2 values'])
  })
})
