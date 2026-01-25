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

## Output Guidelines

**IMPORTANT - Token Optimization**:
- Do NOT repeat the full plan content in the chat response
- Only output a brief summary after saving to file
- Keep chat response under 200 tokens

Example chat output after saving:
```
Saved to `PLAN.md`

Overview: [1-2 sentence summary]
Phases: 3
Files affected: 5

Use Copilot to execute: @PLAN.md /execute-plan
```

## Notes

- This command does NOT write any implementation code - only the plan
- The plan file is always overwritten to prevent file accumulation
- Plans should be detailed enough for Copilot to implement without ambiguity
