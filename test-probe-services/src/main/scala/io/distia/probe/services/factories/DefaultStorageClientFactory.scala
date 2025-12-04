package io.distia.probe
package services
package factories

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.storage.{Storage, StorageOptions}

import java.nio.file.{Files, Path}

/**
 * Default production implementation of StorageClientFactory
 *
 * Creates GCP Cloud Storage clients using standard Google SDK configuration.
 * Supports both explicit service account credentials and Application Default
 * Credentials (ADC). This is the implementation used in production environments.
 */
class DefaultStorageClientFactory extends StorageClientFactory:

  override def createClient(projectId: String, serviceAccountKeyPath: Option[String]): Storage =
    val builder = StorageOptions.newBuilder().setProjectId(projectId)

    serviceAccountKeyPath match
      case Some(keyPath) if keyPath.nonEmpty =>
        val credentials = GoogleCredentials.fromStream(Files.newInputStream(Path.of(keyPath)))
        builder.setCredentials(credentials)
      case _ =>
        // Use default credentials (ADC - Application Default Credentials)

    builder.build().getService
