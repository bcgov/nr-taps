import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Accordion, AccordionItem } from '@carbon/react'
import { useRef, useState } from 'react'
import { expect, test, vi } from 'vitest'
import type { AuditDetailPage, AuditEvent } from '@/contracts/audit'
import type { EcasAuditApi } from '@/service/audit-service'
import { setViewportWidth } from '@/test-setup'
import DetailSidePanel from '../../DetailSidePanel'
import EcasAuditDetails from '../EcasAuditDetails'

vi.mock('@/context/auth/AuthContext', () => ({
  useAuth: () => ({ reloadSession: vi.fn() }),
}))

const event: AuditEvent = {
  eventId: '60001',
  userId: 'IDIR\\SYNTHETIC',
  eventDate: '2026-01-01T12:34:56',
  action: { code: 'UPD', description: 'Update' },
  sentToUserId: null,
  submittedFileId: null,
  fileName: null,
  commentPreview: 'Stored comment',
  hasMoreComment: false,
  commentsSuppressed: false,
}

function AuditPanel({ api }: { api: EcasAuditApi }) {
  const launcherRef = useRef<HTMLButtonElement>(null)
  const [open, setOpen] = useState(false)
  const [auditOpen, setAuditOpen] = useState(false)
  return (
    <main id="main-content">
      <button ref={launcherRef} onClick={() => setOpen(true)}>
        Open reference
      </button>
      <DetailSidePanel
        open={open}
        title="Reference details"
        onClose={() => setOpen(false)}
        launcherRef={launcherRef}
        initialFocusSelector="#read-detail-heading"
      >
        <h2 id="read-detail-heading" tabIndex={-1}>
          Read details
        </h2>
        <Accordion>
          <AccordionItem
            title="Audit history"
            onHeadingClick={({ isOpen }) => setAuditOpen(isOpen)}
          >
            {auditOpen && <EcasAuditDetails ecasId="1001" api={api} />}
          </AccordionItem>
        </Accordion>
      </DetailSidePanel>
    </main>
  )
}

test.each([1440, 768])(
  'audit focus survives a child loading animation inside the real panel at %ipx',
  async (width) => {
    setViewportWidth(width)
    const user = userEvent.setup()
    let resolveDetails!: (result: AuditDetailPage) => void
    const api: EcasAuditApi = {
      history: vi.fn().mockResolvedValue({ ecasId: '1001', items: [event], total: 1, page: 0 }),
      details: vi.fn().mockImplementation(
        () =>
          new Promise((resolve) => {
            resolveDetails = resolve
          }),
      ),
    }
    render(<AuditPanel api={api} />)
    await user.click(screen.getByRole('button', { name: 'Open reference' }))
    const panel = screen.getByRole(width >= 1312 ? 'complementary' : 'dialog', {
      name: 'Reference details',
    })
    await waitFor(() =>
      expect(within(panel).getByRole('heading', { name: 'Read details' })).toHaveFocus(),
    )
    await user.click(within(panel).getByRole('button', { name: 'Audit history' }))
    const viewEvent = await within(panel).findByRole('button', { name: 'View event 60001' })
    await user.click(viewEvent)
    const heading = within(panel).getByRole('heading', { name: 'Event 60001' })
    expect(heading).toHaveFocus()
    const loading = within(panel).getByText('Loading event details…')
    // JSDOM does not run Carbon's CSS animations. Dispatch their bubbling events while
    // the asynchronous details are pending, then let IBM's deferred focus callback run.
    fireEvent.animationStart(loading)
    fireEvent.animationEnd(loading)
    // React's JSDOM feature detection can select the WebKit-prefixed event names.
    fireEvent(loading, new Event('webkitAnimationStart', { bubbles: true }))
    fireEvent(loading, new Event('webkitAnimationEnd', { bubbles: true }))
    await act(async () => {
      resolveDetails({
        ecasId: '1001',
        event,
        comment: 'Full stored comment',
        commentTruncated: false,
        items: [],
        total: 0,
        page: 0,
      })
      await new Promise((resolve) => setTimeout(resolve, 10))
    })
    expect(await within(panel).findByText('Full stored comment')).toBeInTheDocument()
    expect(heading).toHaveFocus()
    await user.click(within(panel).getByRole('button', { name: 'Close event details' }))
    expect(viewEvent).toHaveFocus()
    await user.keyboard('{Escape}')
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Open reference' })).toHaveFocus(),
    )
  },
)
