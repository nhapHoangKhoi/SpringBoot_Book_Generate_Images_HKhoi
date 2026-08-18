# Plan — Book Illustration Studio

This document is the working implementation plan for the assignment. It records
what the application must do and the order in which I intend to build it.
`DECISIONS.md` records why significant technical choices were made.

> Written before implementation and revised after running the reference notebook,
> which settled the Gemini interaction design in §7.

## 1. Goal

Build a local full-stack application that turns book text into character
portraits and one chapter illustration by following steps 1–5 of Google's
Book Illustration notebook.

The pipeline is:

1. Style
2. Characters
3. Portraits
4. Chapters
5. Illustrations

Every step requires an explicit user action and must complete before the next
step can start.

Out of scope: the notebook's later sections (Veo animation, Lyria music, TTS
narration, media mixing) and any deployment. The application runs locally only.

## 2. Required behavior

The application must:

- Sign in users using name and email without passwords.
- Allow one user to own multiple projects.
- Accept pasted text or a `.txt` file.
- Preserve project state across refresh, logout, and server restart.
- Prevent duplicate Gemini calls from double-clicks or multiple tabs.
- Show the exact running step.
- Persist failures and allow the user to retry only the failed step.
- Recover steps stranded by a server restart.
- Never automatically retry Gemini.
- Send the full book content to Gemini once and reuse its context.
- Enforce a maximum of two adult characters and one chapter on the server.
- Save book text and generated images on the local filesystem.

## 3. Technical approach

### Backend

- Java 21
- Spring Boot
- Spring MVC controllers
- Spring `RestClient` for Gemini REST calls
- JUnit, Mockito, and MockMvc for tests

### Frontend

- React
- TypeScript
- Vite
- React Testing Library and Vitest

### Persistence

Use filesystem-backed persistence rather than a database:

    data/
      users/{userId}/user.json
      users/{userId}/projects/{projectId}/project.json
      users/{userId}/projects/{projectId}/book.txt
      users/{userId}/projects/{projectId}/images/*.png

Project JSON contains lightweight pipeline state. Book text and images use
separate files.



Write project JSON by serialising to a temporary file and renaming it into
place. A reader then sees either the previous document or the new one, never a
partially written file.

## 4. Pipeline state

Use two project-level fields:

- `status`: the last successfully completed pipeline step.
- `stepState`: whether the current step is `IDLE`, `RUNNING`, or `FAILED`.

Derive the current step from `status`. Do not persist a separate current-step
field that could contradict completed progress.

Characters and chapters also have an image state:

- `PENDING`
- `RUNNING`
- `DONE`
- `FAILED`

This allows image results to be persisted individually.

## 5. Concurrency plan

Use one in-memory lock per project.

Starting a step performs the following operation under the project lock:

1. Read the latest project.
2. Derive the legal current step.
3. Reject an out-of-order request.
4. Reject a request if a step is already running.
5. Change `stepState` to `RUNNING`.
6. Persist the project.
7. Release the lock.

The Gemini call must happen after the lock is released. This allows the frontend
to poll project state while Gemini is working.


## 6. Resume and recovery plan

Browser refresh and logout do not stop work because the backend owns execution.

On normal failure:

- Preserve the last completed status.
- Set the current step to `FAILED`.
- Save a user-readable error.
- Wait for an explicit retry.

On server startup:

- Find projects left in `RUNNING`.
- Change them to `FAILED`.
- Explain that the previous server process was interrupted.
- Allow the user to retry.

Also expose manual recovery when a step has remained `RUNNING` beyond the stale
threshold of five minutes. A stale step is not implicitly runnable. The user must
reset it first, because taking over a call that may still be alive would pay for
the same Gemini request twice.

## 7. Gemini plan

Use two Gemini interaction chains.

### Text interaction

- Upload the book once.
- Open an interaction that references the uploaded file.
- Persist the returned interaction ID.
- Continue Style, Characters, and Chapters through interaction IDs.
- Request structured JSON for Characters and Chapters.

### Image interaction

- Open a separate image interaction using the selected art style.
- Generate portraits sequentially.
- Persist the new image interaction ID after every portrait.
- Continue chapter illustrations from the latest portrait interaction so the
  model retains character context.

Do not automatically retry failed Gemini calls.

### Running without an API key

Put both clients behind one interface and select between them with a single
property, `gemini.mode`:

- `real`: call the Gemini API.
- `simulate`: return generated placeholder text and drawn images after realistic
  delays, with no API key and no quota use.


## 8. API plan

| Method | Endpoint | Purpose |
|---|---|---|
| POST | `/api/session` | Create or load a user |
| GET | `/api/projects` | List the user's projects |
| POST | `/api/projects` | Create a project |
| GET | `/api/projects/{id}` | Get current pipeline state |
| GET | `/api/projects/{id}/book` | Read the full book |
| POST | `/api/projects/{id}/steps/{step}/run` | Start a step |
| POST | `/api/projects/{id}/steps/{step}/reset` | Recover a stranded step |
| GET | `/api/projects/{id}/images/{file}` | Serve a generated image |

The run endpoint returns after claiming the step. The frontend polls the detail
endpoint while `stepState` is `RUNNING`.

Return every JSON response in one envelope, `{success, message, data}`, so the
client never has to guess the shape. A refused run returns `409` with the reason
code in `data.code`, which is what the frontend branches on. Image bytes are the
one route outside the envelope.

## 9. Testing plan

### Backend

Test:

- Step ordering
- Duplicate-run rejection
- Failure and retry
- Stale-step recovery
- Startup recovery
- Server-side character and chapter caps
- Per-image retry
- Concurrent filesystem updates
- Atomic JSON replacement
- Gemini request and response mapping
- Full five-step integration flow with fake Gemini

The per-image retry test must assert the number of image calls, not only the
final state. Counting the calls is what proves a retry redraws the failed image
alone and does not pay for the one that already succeeded.

### Frontend

Test:

- Empty project list
- Loading state
- Running-step state
- Failed-step state
- Stale-step recovery
- Per-image progress
- Handling `ALREADY_RUNNING` by reloading the project

Live Gemini calls are excluded from the normal test suite to avoid quota use.

## 10. Implementation order

1. Run and understand the reference notebook.
2. Define the pipeline state model and pure ordering rules.
3. Implement filesystem persistence and project locks.
4. Implement identity and project endpoints.
5. Implement the pipeline service using fake Gemini.
6. Add failure, retry, and startup recovery.
7. Implement the real Gemini REST client.
8. Implement the React screens and polling.
9. Add backend and frontend tests.
10. Add start/test scripts and final documentation.

## 11. Completion criteria

The implementation is complete when:

- A user can run all five steps.
- Refreshing during a step shows the same running state.
- A second tab cannot start the same step twice.
- Completed images survive a retry.
- Restarting the server exposes an interrupted step as retryable.
- The two-character and one-chapter caps are enforced by the backend.
- Both test suites pass.
- A fresh clone starts and tests with one documented command each.
- No API key or runtime project data is tracked by Git.
