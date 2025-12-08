---
allowed-tools: Bash(git status:*), Bash(git diff:*), Read
description: Show uncommitted work that needs changelog entries
---

Analyze current working directory and staged changes to identify work that should be documented in the changelog before committing.

## Process

**Step 1: Check Author Configuration & Uncommitted Work**
- Run `git config --get user.email || echo "unknown@user"` to show current git user
- Extract first 8 characters of `$TERM_SESSION_ID` for Claude session tracking
- Run `git status --porcelain` to see modified, staged, and untracked files
- Run `git diff --name-only` for unstaged changes
- Run `git diff --staged --name-only` for staged changes

**Step 2: Analyze Change Types**
For each modified file, determine:
- **New Features**: New files, new functions, new capabilities
- **Bug Fixes**: Error handling, test fixes, issue resolutions
- **Refactoring**: Code reorganization without functional changes
- **Configuration**: Build, deployment, or environment changes
- **Documentation**: README, comments, or API documentation

**Step 3: Suggest Changelog Entries**
Generate suggested `/log add` commands for the uncommitted work:

```markdown
## Pending Changes Ready for Changelog

### Author Configuration
- **Git User**: user@example.com
- **Claude Session**: 3af31ee9
- **Note**: All changelog entries will be attributed to: user@example.com | Claude:3af31ee9

### Suggested Entries:
`/log add Fixed Authentication timeout handling in AuthService.scala`
`/log add Added Rate limiting to API endpoints in ProbeRoutes.scala`
`/log add Changed Maven configuration for better test isolation`

### Files Modified:
- `src/main/scala/AuthService.scala` - 15 lines changed
- `src/main/scala/ProbeRoutes.scala` - 8 lines added
- `pom.xml` - 3 lines changed

### Next Steps:
1. Verify author configuration above is correct
2. Review the suggested changelog entries
3. Run the `/log add` commands for changes you want to document
4. Commit your changes: `git add -A && git commit -m "Your commit message"`
5. Consider running `/daily` after committing to capture today's full context

### Regulatory Compliance
All suggested changelog entries will automatically include full authorship tracking for audit purposes.
```

**Step 4: Highlight Important Changes**
Identify changes that are particularly important for future Claude sessions:
- **Architecture Changes**: Modifications that affect system design
- **Breaking Changes**: Updates that change APIs or behavior
- **Performance Impacts**: Changes affecting system performance
- **Security Updates**: Authentication, authorization, or data protection changes

## Smart Suggestions

The command will provide intelligent suggestions based on:
- **File Extensions**: Different handling for .scala, .md, .xml, .conf files
- **Directory Patterns**: Special attention to src/main vs src/test changes
- **Size of Changes**: Large changes get more detailed analysis
- **Critical Files**: Extra attention to configuration and main application files

## Usage

```bash
/pending        # Show all uncommitted work needing documentation
```

This command helps ensure that important development work gets properly documented before being committed, maintaining comprehensive changelog coverage.