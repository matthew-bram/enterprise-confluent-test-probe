package io.distia.probe
package services
package fixtures

import io.distia.probe.services.factories.StorageClientFactory
import com.google.cloud.storage.{Storage, StorageOptions}

class FakeGcsStorageClientFactory(fakeGcsHost: String) extends StorageClientFactory:

  override def createClient(projectId: String, serviceAccountKeyPath: Option[String]): Storage =
    StorageOptions.newBuilder()
      .setProjectId(projectId)
      .setHost(fakeGcsHost)
      .build()
      .getService
