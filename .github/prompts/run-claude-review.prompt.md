# Run Claude Review Command

Automatically execute code review using Claude CLI.

## Instructions for Copilot

**When this prompt is executed, Copilot should run the following command in the terminal (from workspace root):**

```bash
claude --allowedTools "Write" -p "/code-review-file"
```

This command invokes Claude CLI and automatically executes the `/code-review-file` slash command.
The `--allowedTools "Write"` flag allows automatic file writes without permission prompts.

## What it does

1. Detects uncommitted changes via `git diff`
2. Reviews each file for security and quality issues
3. Categorizes issues as CRITICAL / HIGH / MEDIUM / LOW
4. Saves detailed report to `CODEREVIEW.md`

## After Review

1. Check `CODEREVIEW.md`
2. Fix CRITICAL/HIGH issues
3. Run Copilot's `/execute-review` for auto-fix
4. Re-run review to verify

## Notes

- Claude CLI must be installed (verify with `claude --version`)
- Reviews uncommitted changes only
- Fix CRITICAL/HIGH issues before committing
