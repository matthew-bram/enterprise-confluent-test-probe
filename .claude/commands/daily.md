---
description: Quick daily summary (shorthand for log-summary)
argument-hint: [hours-back]
---

Shorthand command for `/log-summary` with sensible defaults for end-of-day workflow.

This command will:
1. Gather author identification (git user + Claude session ID)
2. Analyze git changes from the last 8 hours (or specified hours)
3. Generate Claude-optimized changelog entries with full authorship tracking
4. Update the Current State section
5. Focus on practical context for future sessions

Usage:
- `/daily` - Analyze last 8 hours
- `/daily 12` - Analyze last 12 hours
- `/daily 4` - Analyze last 4 hours

This is equivalent to running `/log-summary [hours]` but with a shorter, more ergonomic command name for daily development workflow.