/**
 * Minimal EventSource double for workflow progress tests: tests push server-sent `workflow` events manually.
 */
export class FakeEventSource {
  static instances: FakeEventSource[] = []

  readonly url: string
  onerror: ((event: Event) => void) | null = null
  closed = false
  private listeners = new Map<string, ((event: MessageEvent<string>) => void)[]>()

  constructor(url: string) {
    this.url = url
    FakeEventSource.instances.push(this)
  }

  addEventListener(type: string, listener: (event: MessageEvent<string>) => void) {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener])
  }

  removeEventListener() {}

  close() {
    this.closed = true
  }

  emit(type: string, data: unknown) {
    const event = new MessageEvent<string>(type, { data: JSON.stringify(data) })
    this.listeners.get(type)?.forEach((listener) => listener(event))
  }

  fail() {
    this.onerror?.(new Event('error'))
  }

  static latest(): FakeEventSource {
    const latest = FakeEventSource.instances.at(-1)
    if (!latest) {
      throw new Error('No EventSource was opened')
    }
    return latest
  }

  static reset() {
    FakeEventSource.instances = []
  }
}
