package io.distia.probe.core.testutil

import org.apache.kafka.clients.admin.{AdminClient, AdminClientConfig, NewTopic}
import org.testcontainers.containers.{GenericContainer, KafkaContainer, Network}
import org.testcontainers.utility.DockerImageName

import java.net.{HttpURLConnection, URL}
import java.util.Properties
import scala.collection.mutable
import scala.jdk.CollectionConverters.*
import scala.util.Try

private[core] object TestcontainersManager {

  case class ClusterInfo(
    kafka: KafkaContainer,
    schemaRegistry: GenericContainer[_],
    network: Network,
    bootstrapServers: String,
    schemaRegistryUrl: String
  )

  private val clusters: mutable.Map[String, ClusterInfo] = mutable.Map.empty

  sys.addShutdownHook {
    println(s"[TestcontainersManager] JVM shutdown detected, cleaning up ${clusters.size} cluster(s)...")
    cleanupAll()
  }

  def getOrCreateCluster(name: String): ClusterInfo = synchronized {
    clusters.getOrElseUpdate(name, createCluster(name))
  }

  def start(): Unit = synchronized {
    if clusters.contains("default") then
      println(s"[TestcontainersManager] Already started, reusing containers")
      return

    getOrCreateCluster("default")
  }

  def getKafkaBootstrapServers: String = getOrCreateCluster("default").bootstrapServers

  def getSchemaRegistryUrl: String = getOrCreateCluster("default").schemaRegistryUrl

  def isRunning: Boolean = clusters.nonEmpty

  def cleanSchemaRegistry(): Try[Int] = cleanSchemaRegistry("default")

  def cleanSchemaRegistry(clusterName: String): Try[Int] = Try {
    val cluster: ClusterInfo = clusters.getOrElse(clusterName,
      throw new IllegalStateException(s"Cluster '$clusterName' not started - call start() or getOrCreateCluster() first"))

    val subjectsUrl = new URL(s"${cluster.schemaRegistryUrl}/subjects")
    val subjectsConnection: HttpURLConnection = subjectsUrl.openConnection().asInstanceOf[HttpURLConnection]
    subjectsConnection.setRequestMethod("GET")
    subjectsConnection.setConnectTimeout(5000)
    subjectsConnection.setReadTimeout(5000)

    val subjectsResponseCode: Int = subjectsConnection.getResponseCode
    if subjectsResponseCode != 200 then
      throw new RuntimeException(s"Failed to get subjects from Schema Registry: HTTP $subjectsResponseCode")

    val subjectsJson: String = scala.io.Source.fromInputStream(subjectsConnection.getInputStream).mkString
    subjectsConnection.disconnect()

    val subjects: Array[String] = subjectsJson
      .stripPrefix("[")
      .stripSuffix("]")
      .split(",")
      .map(_.trim.stripPrefix("\"").stripSuffix("\""))
      .filter(_.nonEmpty)

    println(s"[TestcontainersManager] Found ${subjects.length} schemas to delete in cluster '$clusterName'")

    var deletedCount: Int = 0
    subjects.foreach { subject =>
      try {
        val deleteUrl = new URL(s"${cluster.schemaRegistryUrl}/subjects/$subject?permanent=true")
        val deleteConnection: HttpURLConnection = deleteUrl.openConnection().asInstanceOf[HttpURLConnection]
        deleteConnection.setRequestMethod("DELETE")
        deleteConnection.setConnectTimeout(5000)
        deleteConnection.setReadTimeout(5000)

        val deleteResponseCode: Int = deleteConnection.getResponseCode
        if deleteResponseCode == 200 || deleteResponseCode == 404 then
          deletedCount += 1
          println(s"[TestcontainersManager] Deleted schema: $subject")
        else
          println(s"[TestcontainersManager] Warning: Failed to delete $subject (HTTP $deleteResponseCode)")

        deleteConnection.disconnect()
      } catch {
        case e: Exception =>
          println(s"[TestcontainersManager] Warning: Error deleting $subject: ${e.getMessage}")
      }
    }

    println(s"[TestcontainersManager] Schema cleanup complete: deleted $deletedCount schemas in cluster '$clusterName'")
    deletedCount
  }

  def stopCluster(name: String): Unit = synchronized {
    clusters.remove(name).foreach { info =>
      println(s"[TestcontainersManager] Stopping cluster: $name")
      info.schemaRegistry.stop()
      info.kafka.stop()
      info.network.close()
      println(s"[TestcontainersManager] Cluster '$name' stopped")
    }
  }

  def cleanupAll(): Unit = synchronized {
    clusters.foreach { case (name, info) =>
      println(s"[TestcontainersManager] Stopping cluster: $name")
      info.schemaRegistry.stop()
      info.kafka.stop()
      info.network.close()
    }
    clusters.clear()
    println("[TestcontainersManager] All clusters cleaned up")
  }

  def createCluster(name: String): ClusterInfo = {
    println(s"[TestcontainersManager] Creating cluster '$name'...")

    val network: Network = Network.newNetwork()

    val kafka: KafkaContainer = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
      .withNetwork(network)
      .withNetworkAliases(s"kafka-$name")
    kafka.start()
    val bootstrapServers: String = kafka.getBootstrapServers
    println(s"[TestcontainersManager] Kafka started for cluster '$name': $bootstrapServers")

    val schemaRegistry = new GenericContainer(DockerImageName.parse("confluentinc/cp-schema-registry:7.5.0"))
    schemaRegistry.withNetwork(network)
    schemaRegistry.withExposedPorts(8081)
    schemaRegistry.withEnv("SCHEMA_REGISTRY_HOST_NAME", s"schema-registry-$name")
    schemaRegistry.withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
    schemaRegistry.withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", s"PLAINTEXT://kafka-$name:9092")
    schemaRegistry.start()
    val schemaRegistryUrl: String = s"http://${schemaRegistry.getHost}:${schemaRegistry.getMappedPort(8081)}"
    println(s"[TestcontainersManager] Schema Registry started for cluster '$name': $schemaRegistryUrl")

    waitForSchemaRegistry(schemaRegistryUrl, name)

    // Create test topics with leaders elected
    // Include all topics used by integration tests
    createTopics(bootstrapServers, List("test-events", "test-events-json", "order-events-avro"), name)

    ClusterInfo(
      kafka = kafka,
      schemaRegistry = schemaRegistry,
      network = network,
      bootstrapServers = bootstrapServers,
      schemaRegistryUrl = schemaRegistryUrl
    )
  }

  def createTopics(bootstrapServers: String, topicNames: List[String], clusterName: String): Unit = {
    println(s"[TestcontainersManager] Creating ${topicNames.size} topic(s) for cluster '$clusterName'...")

    val props = new Properties()
    props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
    props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "10000")

    val adminClient = AdminClient.create(props)

    try {
      val newTopics = topicNames.map { topicName =>
        new NewTopic(topicName, 1, 1.toShort) // 1 partition, replication factor 1
      }

      val createResult = adminClient.createTopics(newTopics.asJava)
      createResult.all().get() // Wait for creation to complete

      println(s"[TestcontainersManager] Topics created successfully for cluster '$clusterName': ${topicNames.mkString(", ")}")

      // Wait for topic metadata to be available (leaders elected)
      val maxAttempts = 30
      var attempt = 0
      var allReady = false

      while (!allReady && attempt < maxAttempts) {
        try {
          val metadata = adminClient.describeTopics(topicNames.asJava).allTopicNames().get()
          allReady = metadata.values().asScala.forall { topicDesc =>
            topicDesc.partitions().asScala.forall(_.leader() != null)
          }

          if (!allReady) {
            attempt += 1
            Thread.sleep(500)
          }
        } catch {
          case _: Exception =>
            attempt += 1
            Thread.sleep(500)
        }
      }

      if (allReady) {
        println(s"[TestcontainersManager] All topics have elected leaders for cluster '$clusterName'")
      } else {
        println(s"[TestcontainersManager] Warning: Topics may not be fully ready for cluster '$clusterName'")
      }

    } catch {
      case e: Exception =>
        println(s"[TestcontainersManager] Error creating topics for cluster '$clusterName': ${e.getMessage}")
        throw e
    } finally {
      adminClient.close()
    }
  }

  def waitForSchemaRegistry(schemaRegistryUrl: String, clusterName: String): Unit = {
    val maxAttempts: Int = 30
    var attempt: Int = 0
    var ready: Boolean = false

    println(s"[TestcontainersManager] Waiting for Schema Registry to be ready for cluster '$clusterName'...")

    while (!ready && attempt < maxAttempts) {
      try {
        val url = new URL(s"$schemaRegistryUrl/subjects")
        val connection: HttpURLConnection = url.openConnection().asInstanceOf[HttpURLConnection]
        connection.setRequestMethod("GET")
        connection.setConnectTimeout(1000)
        connection.setReadTimeout(1000)
        val responseCode: Int = connection.getResponseCode

        if responseCode == 200 then
          ready = true
          println(s"[TestcontainersManager] Schema Registry is ready for cluster '$clusterName'!")

        connection.disconnect()
      } catch {
        case _: Exception =>
          attempt += 1
          Thread.sleep(1000)
      }
    }

    if !ready then
      throw new RuntimeException(s"Schema Registry failed to start for cluster '$clusterName' after $maxAttempts attempts")
  }
}
