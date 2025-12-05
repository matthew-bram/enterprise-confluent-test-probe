package io.distia.probe.common.security

/**
 * Pure function utility for redacting sensitive information from log messages
 *
 * **Design**: Stateless, side-effect free, all dependencies passed as parameters
 *
 * **Redaction Rules**:
 * 1. Full jaasConfig obfuscation (except "jaasConfig=" prefix)
 * 2. Future: clientId, clientSecret, token endpoints (when added to other modules)
 *
 * **Usage**:
 * {{{
 * val safeMessage = SecretRedactor.redact("Fetched jaasConfig=org.apache.kafka...")
 * // Result: "Fetched jaasConfig=***REDACTED***"
 * }}}
 */
private[probe] object SecretRedactor {

  private val JaasConfigPattern = """jaasConfig=([^\s,})\]]+)""".r
  private val RedactedPlaceholder = "***REDACTED***"

  /**
   * Redacts sensitive security information from a log message
   *
   * @param message The raw log message containing potential secrets
   * @return Message with secrets replaced by ***REDACTED***
   */
  def redact(message: String): String = {
    JaasConfigPattern.replaceAllIn(message, m => s"jaasConfig=$RedactedPlaceholder")
  }

  /**
   * Checks if a message contains any unredacted secrets
   *
   * @param message The log message to validate
   * @return true if message contains secrets, false if clean
   */
  def containsSecrets(message: String): Boolean = {
    // Check if message contains unredacted jaasConfig with actual OAuth content
    JaasConfigPattern.findFirstIn(message).exists { matched =>
      !matched.contains(RedactedPlaceholder)
    }
  }
}
