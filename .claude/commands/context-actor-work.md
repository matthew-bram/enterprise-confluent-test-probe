---
description: Load Akka/Pekko actor development context
---

You are working with Akka/Pekko actors. Loading required context:

1. **Load scala-conventions-enforcer skill** - MANDATORY before writing Scala code
   ```
   Use the Skill tool to load: scala-conventions-enforcer
   ```

2. **Quick Reference (Read First):**
   - `.claude/quick-reference/SCALA-CHEATSHEET.md` - Ultra-condensed Scala patterns
   - `.claude/quick-reference/BEFORE-YOU-CODE.md` - Visibility pattern (critical!)

3. **Full Guides (If in doubt):**
   - `.claude/guides/ACTORS.md` - Akka patterns, FSM, message protocols
   - `.claude/styles/akka-patterns.md` - Akka best practices
   - `.claude/styles/scala-conventions.md` - Scala 3 coding standards

3. **Critical Patterns to Apply:**
   - **Visibility Pattern** - Keep methods PUBLIC, use `private[module]` at object level (prevents 20-40% coverage loss)
   - **Error Kernel Pattern** - Supervisor hierarchy for fault tolerance
   - **FSM Pattern** - Use `Behaviors.withStash` for state machines
   - **Typed Actors** - Use `ActorContext[T]` and typed references

4. **Before You Code:**
   - Review `.claude/quick-reference/BEFORE-YOU-CODE.md`
   - Check `.claude/skills/visibility-pattern-guardian/SKILL.md` for the visibility pattern

**Ready for actor development with full context loaded.**