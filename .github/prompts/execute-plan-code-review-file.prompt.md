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

### Step 2: Run Claude Code review

After implementation is complete, run the following command in the terminal (from workspace root):

```bash
claude --allowedTools "Write" -p "/code-review-file"
```

This automatically:
- Invokes Claude CLI
- Executes `/code-review-file` slash command
- Saves results to `CODEREVIEW.md` (auto-overwrite enabled)

### Step 3: Report results

Report the review summary to the user.

## Important

- Copilot will ONLY implement what's in PLAN.md
- Claude CLI must be installed
- `--allowedTools "Write"` enables automatic file overwrite
- Fix CRITICAL/HIGH issues before committing
