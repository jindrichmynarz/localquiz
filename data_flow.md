# Data flow

## Overview

The application is server-driven: the server owns all state (in Datahike) and pushes HTML patches to connected clients via Server-Sent Events (SSE). Clients send actions via HTTP POST; the server updates the database; a transaction listener fans those changes out to the relevant SSE streams.

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
    Note over B: Datastar reads __Host-csrf cookie into $csrf signal
    B->>B: Open SSE connection to /sse or /sse/:game-id
```

The shim page contains no game content — just the Datastar runtime and a `data-on:load` attribute that immediately opens the SSE connection. All content arrives via SSE.

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
    SSE->>V: Render current view
    V->>B: SSE patch-elements! (full initial HTML)
    Note over SSE,B: Connection stays open
```

The SSE handler keeps a virtual thread looping on a throttled channel (≤ 1 update per 100 ms). It hashes the rendered HTML and only sends a patch when the hash changes.

---

## 3. Real-time update pipeline

Every database transaction triggers this pipeline, delivering UI updates to all connected clients for the affected game.

```mermaid
flowchart LR
    TX[d/transact] --> DH[(Datahike)]
    DH --> DL[db_listener\nfind-updated-game]
    DL --> RC[refresh-channel\ndropping-buffer 1]
    RC --> RP[refresh-pub\npub by :game-id]
    RP --> |per-game sub| CH[<ch\ndropping-buffer 1]
    CH --> TH[throttle\n100 ms]
    TH --> SSE[sse/handler loop]
    SSE --> |hash changed?| PE[patch-elements!\nBrotli-compressed]
    PE --> B[Browser]
```

---

## 4. Game state machine

```mermaid
stateDiagram-v2
    [*] --> new : POST /create
    new --> question : POST /question
    question --> show_answers : all answered\nor timeout (45 s)
    show_answers --> question : POST /question\n(questions remain)
    show_answers --> leaderboard : POST /leaderboard\n(no questions remain)
    leaderboard --> question : POST /question\n(next round)
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

    B->>H: POST /create (question source + count)
    H->>A: create-game!
    A->>A: Parse + shuffle questions
    A->>DB: transact {game/id, game/state :new, game/questions}
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
```

---

## 7. Answer evaluation

Triggered either when all players answer or when the 45-second timeout fires.

```mermaid
flowchart TD
    T[Timeout future fires\nor all-players-answered?]
    T --> CA[cancel-timeout!]
    CA --> CQ[current-question]
    CQ --> GA[get-answers]
    GA --> SA[score-answers\nmultimethod on question type]
    SA --> SS[scale-scores-by-answer-times]
    SS --> TX["transact:\nanswer scores + correctness\nplayer scores\nstate = :show-answers"]
```

---

## 8. Session and CSRF flow

```mermaid
sequenceDiagram
    participant B as Browser
    participant MW as wrap-session

    B->>MW: GET (no cookies)
    MW->>MW: Generate random SID (160-bit)
    MW->>MW: CSRF = HMAC-SHA256(secret, SID:epoch)
    MW->>B: Set-Cookie: __Host-sid (HttpOnly), __Host-csrf

    B->>MW: POST (with X-Csrf-Token header + sid cookie)
    MW->>MW: Recompute expected CSRF for current + previous epoch
    alt token matches
        MW->>MW: Attach :sid to request, proceed
    else token mismatch
        MW->>B: 403 Forbidden
    end
```
