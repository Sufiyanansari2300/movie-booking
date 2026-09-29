---
name: verify-and-commit
description: Safely verify, secret-check, commit and push a change in the movie-booking repo. Use before every commit.
---

# Verify and commit

1. **Don't kill the running app.** If `lsof -iTCP:8090 -sTCP:LISTEN` shows the developer's app, never run
   `./mvnw clean …`. Deleting `target/classes` breaks the DevTools restart. Plain `./mvnw verify` is fine.
2. **Build and test.** `./mvnw verify` must pass: unit + integration tests and the JaCoCo floor (95% lines / 88%
   branches). A single class: `./mvnw verify -Dit.test=SomeIT -Dtest=none -Dsurefire.failIfNoSpecifiedTests=false`.
3. **New migration?** Run the `verify-on-mysql` skill as well.
4. **Secret check of the staged diff.** No real secret may be committed, not even as a `${VAR:default}`:
   ```bash
   git add -A && for x in "$(sed -n 's/^spring.datasource.password=//p' local.properties)" "$(sed -n 's/^app.jwt.secret=//p' local.properties)" "$(sed -n 's/^app.admin.password=//p' local.properties)" "$(python3 -c "import json;print(json.load(open('http/http-client.private.env.json'))['local'].get('adminPassword',''))")"; do [ -n "$x" ] && git diff --cached | grep -qF "$x" && { echo "SECRET FOUND"; exit 1; }; done; echo "secret check: clean"
   ```
   `local.properties`, `.env` and `http/http-client.private.env.json` must stay git-ignored. Passwords never go into
   `http/http-client.env.json` (it is committed).
5. **Commit small and descriptive.** A summary line, then bullets of what changed and why, ending with the
   co-author line. One logical change per commit, so reviewers can read the history phase by phase.
6. **Push** to the existing `origin` (the developer's personal GitHub). Never create repos or push with any other
   account.
7. **Keep docs current.** Update `docs/PLAN.md` phase status and `CLAUDE.md` conventions in the same or the next
   commit.
