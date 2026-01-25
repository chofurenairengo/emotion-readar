# Execute Review Command

Fix issues identified in CODEREVIEW.md.

## CRITICAL RULE

**DO NOT fix anything not explicitly listed in CODEREVIEW.md.**

- Only fix issues documented in CODEREVIEW.md
- Only modify files mentioned in the issues
- Do NOT add extra improvements or refactoring
- Do NOT fix issues you "notice" that aren't in the report
- If the suggested fix is unclear, ASK before implementing

## Instructions

1. Read `CODEREVIEW.md` file in the project root
2. Check the Summary section for issue overview
3. Fix issues in **priority order**:
   - **CRITICAL** - Fix immediately (security issues)
   - **HIGH** - Must fix (quality issues)
   - **MEDIUM** - Should fix (best practices)
   - **LOW** - Nice to fix (style issues)
4. For each issue:
   - Understand the Problem description
   - Review Current Code
   - Apply EXACTLY the Suggested Fix
5. Run related tests to verify fixes

## Important Notes

- If CODEREVIEW.md does not exist, notify the user
- CRITICAL and HIGH issues must be fixed
- Ask for clarification if fix is unclear
- Report what was fixed for each issue

## Usage

In Copilot Chat:
```
@workspace /execute-review
```

## 修正完了後

すべての修正が完了したら、以下を報告してください：

1. 修正した問題の一覧
2. 各問題に対して行った修正内容
3. 残っている問題（もしあれば）
4. 再レビューの推奨
