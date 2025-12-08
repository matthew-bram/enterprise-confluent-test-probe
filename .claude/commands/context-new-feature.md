---
description: Load feature-builder workflow context
---

You are now working on a new feature. Load the **feature-builder** skill to enforce the 8-phase feature workflow.

Execute the following:
```
Use the Skill tool to load: feature-builder
```

The feature-builder skill enforces:
- Phase 0: Architecture First (design before implementation)
- Phase 1: Implementation
- Phase 2: Code Review (scala-ninja)
- Phase 3: Testing
- Phase 4: Integration Testing
- Phase 5: Performance Review
- Phase 6: Security Review
- Phase 7: Documentation

**Architecture-First Development**: If you detect that implementation has started without Phase 0 architecture, the skill will enforce completing architecture first.

Reference materials available:
- `.claude/guides/DEVELOPMENT.md` - Development workflow
- `.claude/quick-reference/BEFORE-YOU-CODE.md` - Visual quick reference
- `docs/product/PRODUCT-REQUIREMENTS-DOCUMENT.md` - Full requirements