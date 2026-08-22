---
name: GitHub secret protection
description: GitHub push protection can reject secret-bearing commit objects even after the working tree is redacted.
---

GitHub push protection may identify an earlier rejected commit object during later pushes. Redacting the current files is not sufficient if that commit remains reachable; rebuild the branch from the clean remote base with the sanitized tree before pushing.

**Why:** The attached project brief contained a server credential, and GitHub rejected multiple pushes until the branch history was flattened.

**How to apply:** Never use a secret-bypass link. Rotate exposed server credentials, redact tracked copies, verify the target commit contains no secret patterns, then push only a clean history.