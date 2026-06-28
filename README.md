# Localquiz

> Bringing the cozy localhost atmosphere to pub quizzes.

An engine for data-driven multiplayer quizzes, a.k.a. pub quizzes.

Much of the ideas and code in this application are taken from Anders Murphy's [hyperlith](https://github.com/andersmurphy/hyperlith).

## Data format for the questions

A quiz is an [EDN](https://github.com/edn-format/edn) file describing a set of questions. Localquiz provides few built-in quizzes, but you can also upload your own quiz that follows the format defined next.

Quizzes are formatted as a map with one required and two optional keys:

```clj
{:questions #{,,,}     ; Required. A set of questions (at least one, all distinct).
 :creators  #{,,,}     ; Optional. Who made the quiz.
 :defs      {,,,}}     ; Optional. Reusable values referenced from questions.
```

`:creators` is a collection of maps with a required `:name` and an optional `:url` (which must be an `http`/`https` URL):

```clj
:creators #{{:name "Jindřich Mynarz"
             :url  "https://mynarz.net/#jindrich"}}
```

### Questions

Every question is a map sharing these keys:

| Key        | Required | Description                                                              |
|------------|----------|--------------------------------------------------------------------------|
| `:type`    | Yes      | One of the question types below.                                         |
| `:text`    | Yes      | The prompt, as a string or [Hiccup](#text-formatting).                   |
| `:note`    | No       | Extra context revealed after the answers, as a string or Hiccup.        |
| `:scoring` | No       | Overrides the default scoring with `:consensus` or `:majority` (see [Scoring](#scoring)). |

### Question types

#### `:multiple`: multiple choice

Requires `:choices`: a collection of at least two distinct `{:text ,,,}` maps. Mark the right one with `:correct? true`, which you may omit for crowd-scored questions.

```clj
{:type :multiple
 :text "Which decade does this music come from?"
 :choices [{:text "1950s" :correct? true}
           {:text "1970s"}
           {:text "1990s"}
           {:text "2000s"}]}
```

#### `:yesno`: true / false

Set `:correct?` to `true` (yes) or `false` (no). Omit it for crowd-scored questions.

```clj
{:type :yesno
 :text "Brian Eno pioneered ambient music."
 :correct? true}
```

#### `:open`: free-text answer

Requires the expected `:answer`. Player input is matched fuzzily ignoring case and diacritics, so minor typos are ignored.

```clj
{:type :open
 :text "Name the non-profit running the bistro staffed by homeless cooks."
 :answer "Jako doma"}
```

#### `:percent-range`: a percentage from 0 to 100

Requires `:percentage` (the correct value). An optional `:threshold` (a non-negative number that defaults to `5`) sets the tolerance in percentage points. Answers closer to the target score higher.

```clj
{:type :percent-range
 :text "By how much more often are women seriously injured in car crashes?"
 :percentage 47
 :threshold 5}
```

#### `:sort`: order the items

Requires `:items`: a vector of at least 2 distinct `{:text ,,, :sort-value <number>}` maps. Items are shown shuffled and the players reorder them. The correct order is the ascending order based on the `:sort-value`.

```clj
{:type :sort
 :text "Sort these events chronologically:"
 :items [{:text "First woman wins a Nobel Prize"          :sort-value 1903}
         {:text "First woman in space"                    :sort-value 1963}
         {:text "First woman wins an Oscar for directing" :sort-value 2009}]}
```

#### `:player-choice`: pick a fellow player

A question with no objectively correct answer: players pick among each other. It must use `:scoring :consensus` and needs only `:text`.

```clj
{:type :player-choice
 :scoring :consensus
 :text "Who has the highest cultural capital?"}
```

### Scoring

By default a question is scored on **correctness** as described per type above. Scores fall in `[0, 1]` and are scaled by how quickly a player answered. Setting `:scoring` replaces correctness with a crowd-based rule:

- `:consensus`: there is no correct answer; you score by how many of the other players gave the same answer (full agreement scores `1`, no agreement scores `0`).
- `:majority`: you score `1` if your answer matches the majority (more than half of the players), otherwise `0`.

Crowd scoring can be applied to any type whose answers can be compared (e.g., a `:multiple` opinion poll), and `:player-choice` always uses `:consensus`.

### Text formatting

Wherever a string is accepted (`:text`, `:note`, and a choice's or item's `:text`) you may instead use [Hiccup](https://github.com/weavejester/hiccup) to embed markup, images, audio, or video:

```clj
{:type :multiple
 :text [:div
        [:audio {:src "/audio/ambient-quiz/brian_eno_alternative_3.ogg"}]
        [:p "Which decade does this music come from?"]]
 :choices [{:text "1950s"}
           {:text "1970s" :correct? true}
           {:text "1990s"}]}
```

For safety, question markup is validated against an allowlist: the elements `<script>`, `<iframe>`, `<object>`, `<embed>`, `<applet>`, `<base>`, `<meta>`, `<link>`, `<style>`, `<frame>`, `<frameset>`, `<form>`, and `<svg>`, any `on*`/`data-*` attribute, and `javascript:` URLs are rejected. A file containing them fails validation.

### Reusable definitions

To avoid repeating a value, define it under `:defs` and reference it with `{:ref <id>}`. References are resolved before the questions are used. For example, a shared list of choices:

```clojure
{:defs {:genres [{:text "Acid House"} {:text "Ambient"} {:text "Big Beat"} ,,,]}
 :questions
 #{{:type :multiple
    :scoring :consensus
    :text [:div
           [:audio {:src "/audio/offshore.mp3"}]
           [:p "What genre fits this track the best?"]]
    :choices {:ref :genres}}}}
```

A namespaced reference such as `{:ref :genres/ambient}` does a two-level look-up (`:genres` then `:ambient`). Referencing an unknown id is an error.

## Dependencies

- Java 22+
- liblmdb native library

## Testing

Run `make test` to run the tests once. Run `make retest` to run the tests on each change of the source code.

## Build and deploy

To build an uber-JAR with the application, run `make build`.

## License

Copyright © 2025-2026 Jindřich Mynarz

Distributed under the terms of the Eclipse Public License 2.0.
