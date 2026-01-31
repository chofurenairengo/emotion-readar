---
description: Run code review on uncommitted changes and save report to CODEREVIEW.md. Overwrites existing file.
---

# Code-Review-File Command

This command performs a comprehensive code review of uncommitted changes and saves the report to `CODEREVIEW.md` in the project root.

## Instructions

You are an expert code reviewer. When this command is invoked:

### Step 1: Get Changed Files

1. Run `git diff --name-only HEAD` to get list of uncommitted changes
2. If no changes found, also check `git diff --cached --name-only` for staged changes
3. If still no changes, inform the user and exit

### Step 1.5: Check Issue and Plan Scope

**IMPORTANT — Scope Verification (MUST DO)**:

1. **Identify the current issue**: Check the current Git branch name (e.g., `feature/#95`) to determine the issue number being worked on
2. **Read the issue**: Use `gh issue view <number>` to understand the expected scope of work
3. **Read PLAN.md**: If `PLAN.md` exists in the project root, read it to understand the implementation plan
4. **Verify scope compliance**: During the review, check that ALL changes are within the scope of the issue and plan. Flag any changes that:
   - Add functionality not described in the issue or plan
   - Modify files unrelated to the issue or plan
   - Introduce refactoring or improvements outside the stated scope
   - Add dependencies not specified in the plan

If scope violations are found, report them as **HIGH** severity issues under a new category: **Scope Violation**.

### Step 2: Analyze Each Changed File

For each changed file, use `git diff HEAD -- <file>` to get the actual changes, then check for:

#### Security Issues (CRITICAL)
- Hardcoded credentials, API keys, tokens, secrets
- SQL injection vulnerabilities
- XSS vulnerabilities
- Missing input validation
- Insecure dependencies
- Path traversal risks
- Sensitive data exposure

#### Code Quality (HIGH)
- Functions > 50 lines
- Files > 800 lines
- Nesting depth > 4 levels
- Missing error handling
- console.log / print statements left in
- TODO/FIXME comments
- Missing docstrings/JSDoc for public APIs
- Unused imports or variables

#### Best Practices (MEDIUM)
- Mutation patterns (use immutable instead)
- Emoji usage in code/comments
- Missing tests for new code
- Accessibility issues (a11y)
- Code duplication
- Poor naming conventions

#### Style Issues (LOW)
- Inconsistent formatting
- Missing type hints (Python)
- Long lines (> 120 chars)
- Trailing whitespace

### Step 3: Create the Report

Create a detailed code review report following this format:

```markdown
# Code Review Report

**Date**: [YYYY-MM-DD]
**Branch**: [current branch name]
**Reviewer**: Claude Code

---

## Summary

| Severity | Count   |
| -------- | ------- |
| CRITICAL | [count] |
| HIGH     | [count] |
| MEDIUM   | [count] |
| LOW      | [count] |

**Recommendation**: [BLOCK / NEEDS FIXES / APPROVED WITH NOTES / APPROVED]

---

## Changed Files

- `path/to/file1.py` - [brief description of changes]
- `path/to/file2.py` - [brief description of changes]

---

## Issues Found

### CRITICAL Issues

#### [Issue Title]
**File**: `path/to/file.py`
**Line**: [line number(s)]
**Category**: [Security / ...]

**Problem**:
[Description of the issue]

**Current Code**:
```python
# problematic code
```

**Suggested Fix**:
```python
# fixed code
```

---

### HIGH Issues

[Same format as above]

---

### MEDIUM Issues

[Same format as above]

---

### LOW Issues

[Same format as above]

---

## Scope Violations

[List any changes that deviate from the issue scope or PLAN.md. If none, write "No scope violations found."]

---

## Positive Observations

- [Good practices observed]
- [Well-written code sections]

---

## Recommendations

1. [Action item 1]
2. [Action item 2]

---

## Conclusion

[Overall assessment and next steps]
```

### Step 4: Save to CODEREVIEW.md

**IMPORTANT**: After creating the report, you MUST:

1. Write the report to `CODEREVIEW.md` in the project root using the Write tool
2. Overwrite any existing content (do not append)
3. If `CODEREVIEW.md` does not exist, create it automatically — do NOT ask for permission
4. Do NOT ask for permission to overwrite an existing `CODEREVIEW.md` — always overwrite silently
5. Confirm to the user that the file has been saved

### Step 5: Inform the User

After saving, tell the user:
- The review has been saved to `CODEREVIEW.md`
- Summary of critical/high issues found
- Whether the changes should be blocked or approved
- They can share this file for team review

## Severity Guidelines

### CRITICAL (Block Commit)
- Security vulnerabilities that could be exploited
- Data exposure risks
- Authentication/authorization bypasses
- Production secrets in code

### HIGH (Fix Before Merge)
- Missing error handling that could cause crashes
- Performance issues that could impact users
- Missing validation on user inputs
- Breaking changes without migration

### MEDIUM (Should Fix)
- Code quality issues that affect maintainability
- Missing tests for complex logic
- Poor documentation
- Suboptimal patterns

### LOW (Nice to Fix)
- Style inconsistencies
- Minor refactoring opportunities
- Documentation improvements

## Output Guidelines

**CRITICAL - Token Optimization (MUST FOLLOW)**:

After saving the report to `CODEREVIEW.md`:

1. **DO NOT** output the report content to terminal
2. **DO NOT** list individual issues in terminal
3. **DO NOT** show code snippets in terminal
4. **DO NOT** repeat anything that's already in CODEREVIEW.md

**ONLY output this minimal format (under 100 tokens)**:

```
Saved to `CODEREVIEW.md`

Summary: CRITICAL=[n], HIGH=[n], MEDIUM=[n], LOW=[n]
Recommendation: [BLOCK/NEEDS FIXES/APPROVED WITH NOTES/APPROVED]

[One-line description of main changes, max 80 chars]
```

**Example**:
```
Saved to `CODEREVIEW.md`

Summary: CRITICAL=0, HIGH=1, MEDIUM=2, LOW=3
Recommendation: NEEDS FIXES

主な変更: APIエンドポイントの認証処理追加
```

**Rationale**: Full details are in CODEREVIEW.md. Terminal output is for quick status only.

## Notes

- This command does NOT modify any source code - only creates the review report
- The report file is always overwritten to prevent file accumulation
- CRITICAL and HIGH issues should block the commit
- Always explain WHY something is an issue, not just WHAT
- Include positive observations to balance the review
