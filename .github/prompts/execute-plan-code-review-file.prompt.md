# Execute Plan + Code Review Cycle Command

Combined workflow: Execute PLAN.md via Copilot + Review via Claude Code.

## Instructions for Copilot

**When this prompt is executed, Copilot should:**

### Step 1: Read and execute PLAN.md

Read `PLAN.md` and implement all tasks listed there.
Follow the same rules as `/execute-plan`:
- ONLY implement what's in PLAN.md
- Do NOT add extra improvements or refactoring
- Ask if anything is unclear

### Step 2: Wait for developer to accept changes

**IMPORTANT**: After implementation is complete:

1. **Inform the user** that implementation is done and they should review the changes
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
- Saves results to `CODEREVIEW.md` (auto-overwrite enabled)

### Step 4: Report results

Report the review summary to the user.

## Important

- Copilot will ONLY implement what's in PLAN.md
- Claude CLI must be installed
- `--allowedTools "Write"` enables automatic file overwrite
- Fix CRITICAL/HIGH issues before committing
