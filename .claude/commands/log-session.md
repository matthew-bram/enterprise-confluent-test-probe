---
description: Summarize current conversation and generate changelog entries
argument-hint: [focus-area]
---

Analyze the current Claude Code conversation to extract key accomplishments, decisions, and learnings, then generate optimized changelog entries.

## Process

**Step 1: Session Context & Conversation Analysis**
Gather session metadata:
- Extract first 8 characters of `$TERM_SESSION_ID` for session tracking
- Run `git config --get user.email || echo "unknown@user"` to get current user
- Note conversation focus area (if provided via $ARGUMENTS)

Review the current conversation to identify:
- **Completed Tasks**: What was successfully implemented or fixed
- **Key Decisions**: Architectural choices, technology selections, approach changes
- **Failed Attempts**: What was tried but didn't work, with reasons
- **Discoveries**: New understanding about the codebase or requirements
- **Future Work**: Items identified for later implementation

**Step 2: Extract Development Context**
For each significant activity in the conversation:
- Identify the problem being solved
- Note the solution approach taken
- Capture any alternatives that were considered/rejected
- Extract error messages or issues encountered
- Identify files that were modified or created

**Step 3: Generate Learning-Oriented Entries**
Create changelog entries that maximize future Claude context with full authorship:

```markdown
- **[Type]** [TICKET-123] High-level description
  - Author: user@example.com | Claude:3af31ee9
  - Problem: What issue was being addressed
  - Solution: How it was resolved, key implementation details
  - Conversation-Context: What led to this approach, alternatives discussed
  - Files-Modified: Specific files and nature of changes
  - Learnings: Key insights for future sessions
```

**Step 4: Capture Conversation Metadata**
Include session-specific context:
- **Tools Used**: Which Claude Code tools were most effective
- **Workflow**: The sequence of steps that led to success
- **Blockers**: Issues that required multiple attempts or creative solutions
- **Efficiency Notes**: What worked well vs what was time-consuming

## Focus Areas

You can specify focus areas for targeted summaries:
- `implementation` - Focus on code changes and technical decisions
- `architecture` - Emphasize design decisions and system structure
- `debugging` - Highlight problem-solving approaches and fixes
- `planning` - Capture requirements analysis and feature planning
- `learning` - Focus on new discoveries about the codebase

## Example Output

```markdown
## 2024-01-15 - Session Summary
- **Added** Custom changelog system with JIRA integration
  - Author: developer@company.com | Claude:3af31ee9
  - Problem: Needed systematic way to track development activities for future Claude sessions
  - Solution: Created slash commands (/log, /log-summary) with date-indexed markdown format
  - Conversation-Context: User wanted balance of manual control and automated summaries
  - Files-Modified: `.claude/commands/log.md`, `CHANGELOG.md`, `CLAUDE.md` updated
  - Learnings: Claude Code commands use natural language instructions, not traditional code

- **Planned** Daily summary automation using git analysis
  - Author: developer@company.com | Claude:3af31ee9
  - Problem: Manual changelog entry is good but need automated end-of-day summaries
  - Solution: Git log analysis + file reading to detect changes and generate entries
  - Conversation-Context: Development ergonomics focus, leveraging Claude's summarization ability
  - Files-Modified: `.claude/commands/log-summary.md` created
  - Learnings: Hybrid manual/automated approach provides best developer experience

- **Discovered** Claude Code slash command architecture
  - Author: developer@company.com | Claude:3af31ee9
  - Problem: Understanding how custom commands work under the hood
  - Solution: Commands are markdown files with natural language instructions
  - Conversation-Context: User asked where "/log add" was implemented in code
  - Files-Modified: None (documentation/understanding)
  - Learnings: Claude interprets markdown instructions rather than executing traditional code

## Session Metadata
- **Session ID**: 3af31ee9 (full: 3af31ee9-9956-420e-b8e0-91206796eef7)
- **Human User**: developer@company.com
- **Focus Area**: implementation
- **Duration**: 2.5 hours of focused development
```

## Usage

```bash
/log-session                    # Full conversation summary
/log-session implementation     # Focus on code changes
/log-session debugging          # Focus on problem-solving
/log-session architecture       # Focus on design decisions
```

This command complements git-based analysis by capturing the reasoning, discussion, and decision-making process that led to code changes.