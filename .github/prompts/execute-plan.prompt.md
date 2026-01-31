# Execute Plan Command

Execute the implementation plan defined in PLAN.md.

## CRITICAL RULE

**DO NOT implement anything not explicitly specified in PLAN.md.**

- Only create/modify files listed in PLAN.md
- Only add code described in PLAN.md
- Do NOT add extra features, improvements, or "nice-to-haves"
- Do NOT refactor unrelated code
- If something seems missing, ASK before implementing

## Instructions

1. Read `PLAN.md` file in the project root
2. Review the Overview and Requirements sections
3. Follow Implementation Steps phase by phase, in order
4. For each step:
   - Create or modify ONLY the specified files
   - Implement EXACTLY the code described in Code Changes
   - Maintain consistency with existing code patterns
5. Run tests specified in Testing Requirements
6. Verify all Success Criteria are met

## Issue Independence Rule (MANDATORY)

**Each issue MUST be implemented independently.** Unless dependencies are explicitly specified:
- Do NOT reference or depend on code from other issues that may not be merged yet
- Do NOT assume other issues' implementations exist
- Implement based ONLY on the current state of the codebase plus the current issue's PLAN.md

## Important Notes

- If PLAN.md does not exist, notify the user
- Ask for clarification if any step is unclear
- Report progress after completing each phase
- Fix errors before proceeding to the next step

## Usage

In Copilot Chat:
```
@workspace /execute-plan
```
