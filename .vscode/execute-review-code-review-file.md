# Execute Review + Code Review Cycle Command

Combined workflow: Fix issues via Copilot + Re-review via Claude Code.

## Overview

This command runs two steps:
1. `/execute-review` (Copilot) - Fix issues from CODEREVIEW.md
2. `/code-review-file` (Claude Code) - Re-review to verify fixes

## Workflow

### Step 1: Execute Review in Copilot

In Copilot Chat:
```
@workspace /execute-review
```

Wait for fixes to complete.

### Step 2: Run Review in Claude Code

In Claude Code chat:
```
/code-review-file
```

This regenerates `CODEREVIEW.md` to verify all issues are fixed.

### Step 3: Repeat if Needed

If new issues are found, repeat from Step 1.

## Quick Reference

| Step | Tool        | Command                      |
| ---- | ----------- | ---------------------------- |
| 1    | Copilot     | `@workspace /execute-review` |
| 2    | Claude Code | `/code-review-file`          |

## Important

- Copilot will ONLY fix issues listed in CODEREVIEW.md
- Claude Code will re-review all uncommitted changes
- Repeat until no CRITICAL/HIGH issues remain
