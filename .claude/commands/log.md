---
description: Manage project changelog with JIRA integration
argument-hint: [action] [type] [ticket?] [description]
---

Manage the CHANGELOG.md file with smart date indexing and JIRA ticket tracking.

## Usage Patterns

**Add entries:**
- `/log add [type] [description]` - Add entry without ticket
- `/log add [type] [JIRA-123] [description]` - Add entry with ticket
- `/log add [type] [description] in File.scala:line` - Add with file reference

**View/Search entries:**
- `/log show [date]` - Display entries for date (supports partial: "2024-01")
- `/log search [term]` - Search across all changelog entries
- `/log ticket [JIRA-123]` - Find all entries for specific ticket

**Maintenance:**
- `/log update-index` - Regenerate the index with current counts
- `/log state [section] [description]` - Update Current State section

## Entry Types
Use these standard types: **Added**, **Changed**, **Deprecated**, **Removed**, **Fixed**, **Security**

## Current State Sections
- `known-issues` - Document blocking problems
- `active-work` - Track work in progress
- `tech-debt` - Note areas needing improvement

## Smart Features

The command will automatically:
- **Detect JIRA tickets** in any argument position (format: XXXX-NNN)
- **Parse file references** and wrap in backticks for clickability
- **Create date sections** in reverse chronological order (today if not specified)
- **Update the index** with entry counts and ticket references
- **Maintain Current State** based on Fixed/Added entries for context
- **Add impact notes** when entries contain " - " for solution details
- **Track authorship** with git user email and Claude session ID for regulatory compliance

## Example Usage

```
/log add Fixed PROB-124 TestState JSON serialization in ModelsSpec.scala:25 - Added explicit Format[TestState] implicit
/log add Added OAuth2 authentication flow to KafkaTestRunner.scala - Impacts credential management
/log state known-issues Test compilation fails due to TestState serialization
/log search PROB-124
/log show 2024-01
```

## Format Output

Entries will be formatted as:
```markdown
- **Type** [TICK-123] Description in `File.scala:line`
  - Author: user@example.com | Claude:3af31ee9
  - Impact/Solution: Brief explanatory note
```

## Author Identification

For regulatory compliance, every entry automatically includes:
- **Git User**: Retrieved via `git config --get user.email`
- **Claude Session**: Shortened session ID from `$TERM_SESSION_ID` environment variable
- **Format**: `Author: {git_email} | Claude:{session_id_first_8_chars}`

Before adding entries, the command will:
1. Run `git config --get user.email || echo "unknown@user"` to get current git user
2. Extract first 8 characters of `$TERM_SESSION_ID` for Claude session tracking
3. Include author line in every changelog entry for full traceability

This creates a changelog optimized for both human readability, future Claude context building, and regulatory audit requirements.