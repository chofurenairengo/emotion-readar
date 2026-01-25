---
description: Create implementation plan and save to PLAN.md for Copilot execution. Overwrites existing file.
---

# Plan-File Command

This command creates a comprehensive implementation plan and saves it to `PLAN.md` in the project root for use with GitHub Copilot.

## Instructions

You are an expert planning specialist. When this command is invoked:

### Step 1: Analyze the Request

1. **Understand the Task**: Read and analyze what the user wants to implement
2. **Explore the Codebase**: Use Glob, Grep, and Read tools to understand:
   - Existing code structure and patterns
   - Related files and components
   - Current implementation that may be affected
3. **Identify Requirements**: Clarify success criteria and constraints

### Step 2: Create the Plan

Create a detailed implementation plan following this format:

```markdown
# Implementation Plan: [Feature/Task Name]

## Overview
[2-3 sentence summary of what will be implemented]

## Requirements
- [Requirement 1]
- [Requirement 2]
- [Requirement 3]

## Files to Modify/Create

### Existing Files
- `path/to/file1.py` - [What changes are needed]
- `path/to/file2.py` - [What changes are needed]

### New Files (if any)
- `path/to/new_file.py` - [Purpose of this file]

## Implementation Steps

### Phase 1: [Phase Name]

#### Step 1.1: [Step Name]
**File**: `path/to/file.py`
**Action**:
- [Specific action 1]
- [Specific action 2]

**Code Changes**:
```python
# Example of what to add/change
def new_function():
    pass
```

#### Step 1.2: [Step Name]
...

### Phase 2: [Phase Name]
...

## Testing Requirements
- [ ] Unit test: [Test description]
- [ ] Integration test: [Test description]
- [ ] Manual verification: [What to check]

## Risks & Considerations
- **Risk**: [Description] → **Mitigation**: [How to address]

## Success Criteria
- [ ] Criterion 1
- [ ] Criterion 2
```

### Step 3: Save to PLAN.md

**IMPORTANT**: After creating the plan, you MUST:

1. Write the plan to `PLAN.md` in the project root using the Write tool
2. Overwrite any existing content (do not append)
3. Confirm to the user that the file has been saved

### Step 4: Inform the User

After saving, tell the user:
- The plan has been saved to `PLAN.md`
- They can open this file in their IDE and use Copilot to implement it
- They can also use `@workspace` in Copilot Chat with the plan content

## Output Format for Copilot

The plan should be written in a way that Copilot can understand:

1. **Be specific** with file paths (absolute or relative from project root)
2. **Include code snippets** showing exactly what to add/change
3. **Use clear action verbs**: "Add", "Create", "Modify", "Delete", "Rename"
4. **Show context** for code changes (surrounding lines when helpful)
5. **Keep Japanese for this project** (CLAUDE.md specifies Japanese as main language)

## Example Usage

```
User: /plan-file WebSocketでリアルタイム通知機能を追加したい

[Agent analyzes codebase, creates detailed plan, saves to PLAN.md]

Agent: 計画を PLAN.md に保存しました。

保存場所: c:\commuxr\PLAN.md

このファイルをVSCodeで開いて、Copilotに以下のように指示できます：
- "このPLAN.mdの内容に従って実装してください"
- "@workspace PLAN.mdの計画を実行して"

計画の概要：
1. Phase 1: WebSocket接続の基盤実装
2. Phase 2: 通知サービスの作成
3. Phase 3: フロントエンド統合
```

## Notes

- This command does NOT write any implementation code - only the plan
- The plan file is always overwritten to prevent file accumulation
- Plans should be detailed enough for Copilot to implement without ambiguity
- Include both Japanese and English comments where appropriate for this project
