# Decisions

This file explains the main technical decisions in the project. It also explains
where I accepted, changed, or rejected Claude's suggestions.

## 1. Java Spring Boot and React

I chose Java 21 and Spring Boot because Java is the backend language I know best.
Claude suggested Node.js because it has an official Gemini SDK. I kept Spring
Boot because Gemini also provides a REST API. Changing to another backend
language only for the SDK would have added more risk.

Claude suggested React and Vite for the frontend. I agreed because the
application has many UI states, such as loading, running, failed, empty, and
complete. React Testing Library also makes these states easy to test.

The cost is that the project has two build systems: Maven and npm. I hide this
difference behind the start and test scripts, so a reviewer still needs only one
command.


## 2. Separate completed progress from current execution

The project stores completed progress and current execution in different fields.

`ProjectStatus` records the last completed step:

`CREATED → STYLE_SET → CHARACTERS_GENERATED → PORTRAITS_GENERATED →
CHAPTERS_GENERATED → DONE`

`StepState` records whether the current step is `IDLE`, `RUNNING`, or `FAILED`.

Claude suggested following the same split used by the reference application. I
agreed after checking why it was needed. One field cannot clearly represent
“Characters are complete, but Portraits are running.”

Claude's first image model used a boolean such as `imageReady`. I changed it to
`PENDING`, `RUNNING`, `DONE`, or `FAILED`. A boolean could not show image
progress or tell a retry which image had already completed.

The current step is calculated from `ProjectStatus` instead of being stored.
This removes one value that could disagree with the other fields. The cost is
that the application still has several related states that must be updated
carefully.


## 3. Fail clearly and retry only after user action

Claude proposed checking for interrupted work when the server starts. I agreed
because a project left in `RUNNING` after a restart no longer has a worker that
can finish it. The startup check changes that step to `FAILED`, so the user can
retry it.

Normal Gemini failures also change only the current step to `FAILED`. They do not
remove earlier results or move the project back to the beginning. Gemini calls
are never retried automatically. The user must press Retry.

The application also treats a step as stale after five minutes. I kept stale
recovery manual because the old Gemini request may still be running. Starting a
new request automatically could pay for the same work twice.

The cost is that a slow request may require the user to wait before recovery.
Manual recovery also has a small duplicate-call risk if the original call is
slow instead of dead.

## 4. Follow the notebook for Gemini instead of trusting the first AI draft (AI output was wrong)

Claude wrote the first Gemini client from documentation before I ran the required
notebook. That version was wrong in several important ways. It sent the book as
inline text, used the wrong field for structured output, sent portrait bytes
again for the chapter image, and used model IDs that had not been verified.

I did not keep that implementation. I ran the notebook and compared its requests
with Claude's code. I then corrected the client to upload the book through the
Files API and store the returned interaction reference. Character and chapter
data use structured JSON output.

Images use a separate conversation. Each portrait continues from the previous
image interaction. The chapter illustration continues from the latest portrait
interaction, which helps keep the characters consistent.

The full book is not sent again for every step. If Gemini expires a stored
interaction, the application shows a failure and waits for the user to retry.
There is no automatic retry.

The cost is that the application depends on Gemini's stored interactions and
their retention period. The REST client also needs its own request building and
response parsing because I did not change the backend language to use an SDK.

## 5. Spring profiles for a simulating GEMINI, when one property line was enough (AI was overcomplicated)

Claude's first version of the offline mode was a `fake` Spring profile with its own client wired by
`@Profile`, started with `-Dspring-boot.run.profiles=fake`. I removed it entirely. Then I asked for
the same capability with a stated constraint: I wanted to switch by editing a few obvious lines, not
by remembering a launch flag.

The result is one property, `gemini.mode`, in a commented block at the top of
`application.properties`, with two `@ConditionalOnProperty` annotations as the only other moving
parts. Same capability, less machinery, and it's discoverable by opening the config file rather than
by knowing the incantation. I also renamed everything from "stub" to "simulate", because "stub"
describes the technique and "simulate" describes the purpose.

Claude's earlier version had used `@ConditionalOnMissingBean` to pick the client. That one it
corrected itself, correctly: outside auto-configuration, that condition depends on bean-registration
order, which is the kind of thing that works until it doesn't.



## If I had one more day

I would add an attempt history for every pipeline step. It would record when
each attempt started, completed, or failed. This would make retries easier to
understand and would preserve earlier error messages instead of replacing them.