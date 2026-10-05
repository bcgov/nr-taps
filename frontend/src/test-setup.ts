import '@testing-library/jest-dom'
import { beforeEach } from 'vitest'

Object.defineProperty(window, 'scrollTo', { configurable: true, writable: true, value: () => {} })

const mediaQueries = new Set<TestMediaQueryList>()

function matchesQuery(query: string) {
  const minimum = /min-width:\s*([\d.]+)px/.exec(query)
  const maximum = /max-width:\s*([\d.]+)px/.exec(query)
  if (query.includes('prefers-reduced-motion')) return query.includes('no-preference')
  if (!minimum && !maximum) return query === 'all' || query === 'screen'
  return (
    (!minimum || window.innerWidth >= Number(minimum[1])) &&
    (!maximum || window.innerWidth <= Number(maximum[1]))
  )
}

class TestMediaQueryList extends EventTarget implements MediaQueryList {
  readonly media: string
  onchange: MediaQueryList['onchange'] = null

  constructor(query: string) {
    super()
    this.media = query
    mediaQueries.add(this)
  }

  get matches() {
    return matchesQuery(this.media)
  }

  addListener(callback: (event: MediaQueryListEvent) => void) {
    this.addEventListener('change', callback as EventListener)
  }

  removeListener(callback: (event: MediaQueryListEvent) => void) {
    this.removeEventListener('change', callback as EventListener)
  }

  dispatchChange() {
    const event = Object.assign(new Event('change'), {
      matches: this.matches,
      media: this.media,
    }) as MediaQueryListEvent
    this.dispatchEvent(event)
    this.onchange?.call(this, event)
  }
}

Object.defineProperty(window, 'matchMedia', {
  configurable: true,
  writable: true,
  value: (query: string) => new TestMediaQueryList(query),
})

export function setViewportWidth(width: number) {
  const previous = new Map([...mediaQueries].map((media) => [media, media.matches]))
  Object.defineProperty(window, 'innerWidth', { configurable: true, writable: true, value: width })
  mediaQueries.forEach((media) => {
    if (media.matches !== previous.get(media)) media.dispatchChange()
  })
  window.dispatchEvent(new Event('resize'))
}

const resizeObservers = new Set<TestResizeObserver>()

class TestResizeObserver implements ResizeObserver {
  readonly targets = new Set<Element>()
  private readonly callback: ResizeObserverCallback

  constructor(callback: ResizeObserverCallback) {
    this.callback = callback
    resizeObservers.add(this)
  }

  observe(target: Element) {
    this.targets.add(target)
  }

  unobserve(target: Element) {
    this.targets.delete(target)
  }

  disconnect() {
    this.targets.clear()
    resizeObservers.delete(this)
  }

  notify(target: Element) {
    if (!this.targets.has(target)) return
    const contentRect = target.getBoundingClientRect()
    const size = [{ inlineSize: contentRect.width, blockSize: contentRect.height }]
    this.callback(
      [
        {
          target,
          contentRect,
          borderBoxSize: size,
          contentBoxSize: size,
          devicePixelContentBoxSize: size,
        },
      ],
      this,
    )
  }
}

Object.defineProperty(globalThis, 'ResizeObserver', {
  configurable: true,
  writable: true,
  value: TestResizeObserver,
})

export function notifyResize(target: Element) {
  resizeObservers.forEach((observer) => observer.notify(target))
}

beforeEach(() => {
  mediaQueries.clear()
  resizeObservers.clear()
  setViewportWidth(1024)
})
