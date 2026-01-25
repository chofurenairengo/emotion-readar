# Full Review Cycle Command

Combined workflow: Execute PLAN.md via Copilot + Review via Claude Code.

## Overview

This command runs two steps:
1. `/execute-plan` (Copilot) - Implement from PLAN.md
2. `/code-review-file` (Claude Code) - Review the implementation

## Workflow

### Step 1: Execute Plan in Copilot

In Copilot Chat:
```
@workspace /execute-plan
```

Wait for implementation to complete.

### Step 2: Run Review in Claude Code

In Claude Code chat:
```
/code-review-file
```

This generates `CODEREVIEW.md` with any issues found.

### Step 3: (Optional) Fix Issues

If issues are found, run in Copilot:
```
@workspace /execute-review
```

## Quick Reference

| Step | Tool        | Command                                  |
| ---- | ----------- | ---------------------------------------- |
| 1    | Copilot     | `@workspace /execute-plan`               |
| 2    | Claude Code | `/code-review-file`                      |
| 3    | Copilot     | `@workspace /execute-review` (if needed) |

## Important

- Copilot will ONLY implement what's in PLAN.md
- Claude Code will review uncommitted changes
- Fix CRITICAL/HIGH issues before committing
