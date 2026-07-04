# Session Delete & Enhanced Sequence Number Reset — Design Spec

**Date:** 2026-07-04
**Branch:** Modernisation

---

## Overview

Two related enhancements to the Session Dashboard:

1. **Delete Session** — allow removing a session from the list and from `FixEngine.cfg`, with automatic stop/disconnect before removal.
2. **Enhanced Sequence Number Reset** — replace the simple "reset to 1" confirmation with a modal that shows current sequence numbers, supports explicit value setting, and warns about the risks of manual changes.

---

## 1. Delete Session

### Backend

**`SessionConfigService.removeSession(String sessionId)`**
- Parses the sessionId to extract BeginString/SenderCompID/TargetCompID (reuse existing `parseSessionId`).
- Finds the `[session]` block bounds using existing `findSessionBounds`.
- Removes the block (and any trailing blank line) from `FixEngine.cfg`.
- Throws `IllegalArgumentException` if session not found in config.

**`SessionController` — `DELETE /api/sessions/{sessionId}`**
- Calls `fixEngineService.stopSession(sessionId)` to auto-stop/disconnect (handles both initiator and acceptor, sends LOGOUT then disconnects).
- Calls `sessionConfigService.removeSession(sessionId)`.
- Calls `fixEngineService.restartConnectorForSession(connectionType)` — connection type is read from `fixEngineService.settings.getString(sessionId, "ConnectionType")` **before** calling `removeSession`, since the config entry is gone afterward.
- Returns updated `List<SessionStatusDto>`.

### Frontend

- Add a trash/delete icon button (`Trash2` from lucide-react) to each session row's action buttons area — rightmost position, styled with rose hover (matching the Stop button aesthetic but more subtle at rest).
- On click: open a delete confirmation dialog (same modal pattern as reset — dark overlay, icon, session ID displayed).
- Confirmation dialog text: *"This will stop the session, remove it from config, and restart the connector. This cannot be undone."*
- On confirm: `DELETE /api/sessions/{encodedSessionId}`, then update sessions list from response.
- On success: if the deleted session was selected, clear `selectedSessionId`.

---

## 2. Enhanced Sequence Number Modal

### Backend

**`POST /api/sessions/{sessionId}/setSeqNums`**
- Request body: `{ "senderSeqNum": number, "targetSeqNum": number }` (new DTO or record).
- Looks up the QuickFIX/J session via `Session.lookupSession(sessionId)`.
- Calls `session.setNextSenderMsgSeqNum(senderSeqNum)` and `session.setNextTargetMsgSeqNum(targetSeqNum)`.
- Returns 204 No Content.
- If session not found: returns 404.

The existing `POST /reset` endpoint remains unchanged (hard reset to 1 via `session.reset()`).

### Frontend — Reset Modal Redesign

The existing `confirmResetSession` state and modal are replaced with a richer dialog with two modes.

**Current sequence numbers** displayed at the top of the dialog:
- "Out (Sender): X" and "In (Target): Y" — taken from `confirmResetSession.expectedSenderNum` / `expectedTargetNum` already present on `SessionStatus`.

**Mode toggle** — two radio-button style tabs:
- **Reset to 1** (default): resets both to 1. Same behavior as today. Confirm button calls `POST /reset`.
- **Set explicit values**: reveals two number inputs (min=1, integer only) pre-filled with current seq nums. Confirm button calls `POST /setSeqNums` with the entered values.

**Warning block** (shown in both modes, amber styling):
> "Manually changing sequence numbers can cause the counterparty to reject messages or trigger a ResendRequest if the numbers are out of sync. Only do this when coordinated with the counterparty, or in a test environment where you control both sides."

**Validation:** In explicit mode, both inputs must be ≥ 1 and be valid integers. Confirm button is disabled otherwise.

---

## Data Flow

```
User clicks delete
  → DELETE /api/sessions/{sessionId}
    → stopSession() [QuickFIX/J logout + disconnect]
    → removeSession() [edit FixEngine.cfg]
    → restartConnectorForSession() [restart initiator or acceptor]
  ← List<SessionStatusDto> (without deleted session)
  → UI removes session row, clears selection if needed

User opens seq num modal → clicks "Set explicit values"
  → enters values → clicks Confirm
    → POST /api/sessions/{sessionId}/setSeqNums { senderSeqNum, targetSeqNum }
      → session.setNextSenderMsgSeqNum(n)
      → session.setNextTargetMsgSeqNum(n)
    ← 204
  → refetchSessions() to update displayed seq nums
```

---

## Error Handling

- Delete: if backend returns error, show a toast or error state in the dialog (don't auto-close).
- Set seq nums: if session not found (404) or validation fails (400), keep modal open and show error message inline.
- Both operations: network errors surfaced as inline error text in the modal.

---

## What Is Not Changing

- The inline Out/In seq num display on each session row — already exists and is correct.
- The `POST /reset` endpoint — kept as-is for hard reset to 1.
- The session config (edit) modal — not touched.
- Test runner, message log, dictionary — not touched.
