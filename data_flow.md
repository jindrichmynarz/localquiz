# Data flow

## Design summary

localquiz is a **server-driven, real-time multiplayer quiz**. The server owns all state (Datahike) and pushes HTML patches to connected clients via SSE. Clients never manage view state — they only send HTTP POST actions and receive SSE events.

**Two roles:**

- **Moderator** — identified by session ID, which doubles as the game ID. Controls quiz progression.
- **Player** — identified by session ID; associated with a game via the game ID in the URL path.

**Four data paths:**

1. **Initial page load** — server issues a session cookie and CSRF token, returns a minimal shim HTML page with the Datastar runtime. The shim immediately opens an SSE connection; all UI content arrives via that stream.

2. **SSE view-update path** — every Datahike transaction triggers `db_listener`, which identifies the affected game and publishes its ID onto `refresh-channel`. Each connected SSE handler receives the event (via `refresh-pub`), re-renders the **full view** on a CPU thread pool, hashes it, and sends the full HTML via `patch-elements!` if the hash changed. Datastar morphs it into the existing DOM client-side (fat-morph approach — no server-side diffing or partial fragments).

3. **Direct signal/redirect path** — actions that need per-session feedback (validation errors, redirect after leaving) call `refresh-event!`, which puts an event with a session-keyed payload directly onto `refresh-channel`. The SSE handler detects the session key, sends only signals or a redirect to that client, and skips the view re-render.

4. **State machine** — the game progresses through `:new → :question → :show-answers → :leaderboard` and back (or terminates). State transitions are triggered by moderator POST actions; the `:show-answers` transition fires automatically when all players answer or the 45-second timeout expires.

**Brotli gate:** `wrap-blocker` rejects all requests that do not accept Brotli (`br`) with HTTP 406.

**POST responses:** all POST action handlers return HTTP 204 (No Content). The actual view update is always delivered via SSE.

## Roles

- **Moderator** — the quiz host. Identified by their session ID, which doubles as the game ID.
- **Player** — a participant. Identified by their session ID; associated with a game via the game ID in the URL path.

---

## 1. Initial page load

```mermaid
sequenceDiagram
    participant B as Browser
    participant MW as Middleware
    participant V as views/shim-view

    B->>MW: GET / (moderator) or GET /play/:game-id (player)
    MW->>MW: Generate SID + CSRF token
    MW->>MW: Set __Host-sid and __Host-csrf cookies
    MW->>V: Forward request with :sid attached
    V->>B: 200 HTML (shim page with Datastar bootstrap)
    Note over B: Body data-signals:csrf wires __Host-csrf cookie into $csrf signal
    B->>B: Open SSE connection to /sse or /sse/:game-id (retryMaxCount: Infinity)
```

The shim page contains no game content — just the Datastar runtime and a `data-init` attribute that immediately opens the SSE connection. All content arrives via SSE. The `$csrf` signal is initialised from the `__Host-csrf` cookie by a JS expression on the `<body>` element. The connection is also re-opened on the `online` window event.

---

## 2. SSE connection and first render

```mermaid
sequenceDiagram
    participant B as Browser
    participant SSE as sse/handler
    participant Pub as refresh-pub (core.async)
    participant V as views/morph-view

    B->>SSE: GET /sse (persistent connection)
    SSE->>SSE: Subscribe to refresh-pub for game-id
    SSE->>SSE: Put :first-render onto channel
    SSE->>V: Render current view (on CPU thread pool)
    V->>B: SSE patch-elements! (full initial HTML)
    Note over SSE,B: Connection stays open
```

The SSE handler keeps a virtual thread looping on a throttled channel (≤ 1 update per 100 ms). View rendering is offloaded to a CPU thread pool. The handler hashes the rendered HTML and only sends an update when the hash changes — using Datastar's **fat-morph** approach: the full view HTML is sent each time and Datastar morphs it into the existing DOM client-side, so the server never diffs or produces partial fragments. A `<cancel>` poison-pill channel is used to stop the loop and close all associated channels when the client disconnects.

---

## 3. Real-time update pipeline

Every database transaction and every direct `refresh-event!` call flow through this pipeline. The SSE handler branches on whether the event carries a per-session payload.

```mermaid
flowchart LR
    TX[d/transact] --> DH[(Datahike)]
    DH --> DL["db_listener<br/>refresh-game"]
    DL --> RC["refresh-channel<br/>dropping-buffer 1"]

    AE["actions<br/>refresh-event!"] -->|session-keyed payload| RC

    RC --> RP["refresh-pub<br/>pub by :game-id"]
    RP --> |per-game sub| CH["&lt;ch<br/>dropping-buffer 1"]
    CH --> TH["throttle<br/>100 ms"]
    TH --> SSE[sse/handler loop]

    SSE --> |session payload present?| SP["patch-signals!<br/>or redirect!"]
    SSE --> |hash changed, no session payload| PE["patch-elements!<br/>full view, Brotli-compressed"]
    PE --> B[Browser]
    SP --> B
```

---

## 4. Game state machine

```mermaid
stateDiagram-v2
    [*] --> new : POST /create
    new --> question : POST /question
    question --> show_answers : all answered or timeout (45 s)
    show_answers --> question : POST /question (questions remain)
    show_answers --> leaderboard : POST /leaderboard (no questions remain)
    leaderboard --> question : POST /question (next round)
    leaderboard --> [*] : POST /end
```

---

## 5. Moderator actions

```mermaid
sequenceDiagram
    participant B as Browser (Datastar)
    participant H as handler
    participant A as actions/moderator
    participant DB as Datahike

    B->>H: POST /create/validate (question source or file)
    H->>A: validate-questions!
    A->>A: Parse questions
    A->>A: refresh-event! {signals: {error: ... or false, numberOfQuestions: N}}

    B->>H: POST /create (question source + count)
    H->>A: create-game!
    A->>A: Parse + shuffle questions, take N
    A->>DB: transact {game/id, game/state :new, game/questions, game/questions-total}
    DB-->>B: (via SSE pipeline) Render lobby view

    B->>H: POST /question
    H->>A: next-question!
    A->>DB: transact state=:question, current-question, retract old answers
    A->>A: schedule-timeout (45 s future)
    DB-->>B: (via SSE pipeline) Render question view

    B->>H: POST /leaderboard
    H->>A: leaderboard!
    A->>DB: transact state=:leaderboard
    DB-->>B: (via SSE pipeline) Render leaderboard view

    B->>H: POST /end
    H->>A: end-game!
    A->>DB: retractEntity game
    DB-->>B: (via SSE pipeline) Render end view
```

---

## 6. Player actions

```mermaid
sequenceDiagram
    participant B as Browser (Datastar)
    participant H as handler
    participant A as actions/player
    participant DB as Datahike

    B->>H: POST /join/:game-id/validate {player-name}
    H->>A: validate-player-name!
    A->>A: Check name (length, uniqueness)
    A->>A: refresh-event! {signals: {error: ... or false}}

    B->>H: POST /join/:game-id {player-name}
    H->>A: join-game!
    A->>A: validate-player-name! (length, uniqueness)
    A->>DB: transact game/players += {player/id, player/name}
    DB-->>B: (via SSE pipeline) Lobby updated for all

    B->>H: POST /answer/:game-id {answer}
    H->>A: answer-question!
    A->>A: Check timeout active + not already answered
    A->>DB: transact game/answers += {answer/player, answer/answer}
    alt all players answered
        A->>A: evaluate-answers! (score + state=:show-answers)
        A->>DB: transact scores + state
    end
    DB-->>B: (via SSE pipeline) Answer view updated

    B->>H: POST /leave/:game-id
    H->>A: leave-game!
    A->>DB: retractEntity player
    A->>A: refresh-event! {redirect: "/"}
    DB-->>B: (via SSE pipeline) Lobby updated for remaining players
```

---

## 7. Answer evaluation

Triggered either when all players answer or when the 45-second timeout fires. For `:consensus` scoring, answer times do not affect the score, so `scale-scores-by-answer-times` is skipped.

```mermaid
flowchart TD
    T["Timeout future fires<br/>or all-players-answered?"]
    T --> CA[cancel-timeout!]
    CA --> CQ[current-question]
    CQ --> GA[get-answers]
    GA --> SA["score-answers<br/>multimethod on question type"]
    SA --> CS{scoring == :consensus?}
    CS -->|No| SS[scale-scores-by-answer-times]
    CS -->|Yes| TX
    SS --> TX["transact:<br/>answer scores + correctness<br/>player scores<br/>state = :show-answers"]
```

---

## 8. Session and CSRF flow

```mermaid
sequenceDiagram
    participant B as Browser
    participant MW as wrap-session

    B->>MW: GET (no cookies)
    MW->>MW: Generate random SID (160-bit, URL-safe base64)
    MW->>MW: CSRF = HMAC-SHA256(secret, SID:epoch) where epoch = floor(ms / 1 hour)
    MW->>B: Set-Cookie: __Host-sid (HttpOnly), __Host-csrf (JS-readable)

    B->>MW: POST (with X-Csrf-Token header or $csrf signal + sid cookie)
    MW->>MW: Recompute expected CSRF for current + previous epoch
    alt token matches
        MW->>MW: Attach :sid to request, proceed
    else token mismatch
        MW->>B: 403 Forbidden
    end
```

The CSRF token is valid for the current and the preceding 1-hour epoch, giving a validity window of up to two hours. It can be sent either as an `X-Csrf-Token` HTTP header or as the `$csrf` Datastar signal (accepted from `signals` map). The `__Host-csrf` cookie is not `HttpOnly` so that client-side JS can read it into the Datastar signal store on page load.
