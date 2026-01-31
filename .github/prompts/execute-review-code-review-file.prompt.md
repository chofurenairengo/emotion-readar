# Execute Review + Code Review Cycle Command

Combined workflow: Fix issues via Copilot + Re-review via Claude Code.

## Instructions for Copilot

**When this prompt is executed, Copilot should:**

### Step 1: Read and fix issues from CODEREVIEW.md

Read `CODEREVIEW.md` and fix all issues listed there.
Follow the same rules as `/execute-review`:
- ONLY fix issues documented in CODEREVIEW.md
- Do NOT add extra improvements or refactoring
- Fix in priority order: CRITICAL > HIGH > MEDIUM > LOW
- Ask if any fix is unclear

### Step 2: Wait for developer to accept changes

**IMPORTANT**: After fixes are complete:

1. **Inform the user** that fixes are done and they should review the changes
2. **Wait for the developer to accept/keep the changes** in their IDE before proceeding
3. **Do NOT automatically run Claude review** — ask the user to confirm they have accepted the changes first
4. Only after the user confirms, proceed to Step 3

### Step 3: Run Claude Code review

After the developer has confirmed they accepted the changes, run the following command in the terminal (from workspace root):

```bash
claude --allowedTools "Write" -p "/code-review-file"
```

This automatically:
- Invokes Claude CLI
- Executes `/code-review-file` slash command
- Regenerates `CODEREVIEW.md` to verify fixes (auto-overwrite enabled)

### Step 4: Report results

Report the review summary to the user.
If CRITICAL/HIGH issues remain, notify the user to run this command again.

## Important

- Copilot will ONLY fix issues listed in CODEREVIEW.md
- Claude CLI must be installed
- `--allowedTools "Write"` enables automatic file overwrite
- Repeat until no CRITICAL/HIGH issues remain
