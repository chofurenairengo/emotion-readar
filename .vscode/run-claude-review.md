# Run Claude Review Command

Execute code review using Claude Code's `/code-review-file` command.

## Instructions

This command triggers Claude Code to perform a code review.

### Steps

1. **Open Claude Code**
   - Open Command Palette (Ctrl+Shift+P)
   - Select "Claude: Open Chat"

2. **Run the command**
   In Claude Code chat:
   ```
   /code-review-file
   ```

3. **Check results**
   - `CODEREVIEW.md` will be generated
   - Review summary will be displayed

## What /code-review-file does

1. Detects uncommitted changes via `git diff`
2. Reviews each file for security and quality issues
3. Categorizes issues as CRITICAL / HIGH / MEDIUM / LOW
4. Saves detailed report to `CODEREVIEW.md`

## After Review

1. Check `CODEREVIEW.md`
2. Fix CRITICAL/HIGH issues
3. Run Copilot's `/execute-review` for auto-fix
4. Re-run review to verify
5. Commit when clean

## Note

- Run this in Claude Code, not GitHub Copilot
- Requires uncommitted changes to review
- Fix CRITICAL/HIGH issues before committing
