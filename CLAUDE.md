# Chesslab

A chess engine + web frontend. The Scala engine is the active codebase. The Python `chessbot/` directory is a legacy prototype — don't modify it.

## Project Structure

- `engine/` — Scala 3 chess engine with HTTP API (http4s + Cats Effect + Circe)
- `web/` — Next.js frontend (talks to the engine API on port 8080)
- `chessbot/` — Legacy Python prototype. Do not modify.

## Engine Architecture

The engine uses a **10x12 mailbox** board representation. Off-board sentinel squares eliminate bounds checking.

- `core/Types.scala` — Color, Piece, Square, Move, CastlingRights, Squares, Directions
- `core/Board.scala` — Immutable board state, FEN parsing, makeMove
- `core/Attacks.scala` — Square attack detection (defender's perspective)
- `core/MoveGen.scala` — Pseudo-legal move generation
- `core/Legal.scala` — Legality filter (make move, check if king in check)
- `core/Perft.scala` — Perft node counter for correctness testing
- `api/Routes.scala` — HTTP endpoints: /legal-moves, /make-move, /ai-move, /validate
- `api/Main.scala` — Ember server on port 8080 with CORS

## Commands

```bash
# Engine
cd engine && sbt compile    # compile check
cd engine && sbt test       # run tests (munit)
cd engine && sbt run        # start API server on port 8080

# Frontend
cd web && npm run dev       # dev server
```

## Code Style

- **Never use `var`**. Use `val`, recursive functions, `foldLeft`, or other functional patterns instead.
- Prefer immutability throughout — the Board is already immutable, keep it that way.
- Keep Scala idiomatic: pattern matching, `Option`/`Either` over nulls/exceptions, for-comprehensions for effects.
- No unnecessary abstractions. Simple and direct.

## Testing

- After any change to move generation, attacks, or board logic: **run `sbt compile` then `sbt test`** to verify against perft suites.
- Perft tests cover startpos, kiwipete, position 3, and position 4 up to depth 4.
- Add a test for any bug fix.

## Frontend

- The frontend communicates with the engine via its JSON API.
- Prefer maintainability and best practices over clever solutions.
