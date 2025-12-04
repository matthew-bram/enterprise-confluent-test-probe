package io.distia.probe.core.fixtures

import io.distia.probe.common.models.{BlockStorageDirective, KafkaSecurityDirective}
import io.distia.probe.core.models.TestExecutionResult
import io.distia.probe.core.services.cucumber.CucumberConfiguration

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

trait InterfaceFunctionsFixture:

  private val ec:  ExecutionContext = ExecutionContext.global

  def getSuccessfulBlockStorageFetchFunction(
    blockStorageDirective: BlockStorageDirective
  ): (UUID, String) => ExecutionContext ?=> Future[BlockStorageDirective] =
    (testId, bucket) => ec ?=> Future.successful(blockStorageDirective)
    
  def getFailedBlockStorageFetchFunction: (UUID, String) => ExecutionContext ?=> Future[BlockStorageDirective] = 
    (testId, bucket) => ec ?=> Future.failed(new Throwable())
  
  def getSuccessfulBlockStorageLoadFunction: (UUID, String, String) => ExecutionContext ?=> Future[Unit] =
    (testId, bucket, evidence) => ec ?=> Future.successful(())

  def getFailedBlockStorageLoadFunction: (UUID, String, String) => ExecutionContext ?=> Future[Unit] = 
    (testId, bucket, evidence) => ec ?=> Future.failed(new Throwable())
    
  def getSuccessfulSecurityDirectivesFetchFunction(
    kafkaSecurityDirectives: List[KafkaSecurityDirective]
  ): BlockStorageDirective => ExecutionContext ?=> Future[List[KafkaSecurityDirective]] =
    blockStorageDirective => ec ?=> Future.successful(kafkaSecurityDirectives)

  def getFailedSecurityDirectivesFetchFunction: BlockStorageDirective => ExecutionContext ?=> Future[List[KafkaSecurityDirective]] =
    blockStorageDirective => ec ?=> Future.failed(new Throwable())

  /**
   * Mock successful Cucumber execution function for testing
   *
   * @param testResult The TestExecutionResult to return
   * @return Function that immediately succeeds with the given result
   */
  def getSuccessfulCucumberExecutionFunction(
    testResult: TestExecutionResult
  ): (CucumberConfiguration, UUID, BlockStorageDirective) => ExecutionContext ?=> Future[TestExecutionResult] =
    (config, testId, directive) => ec ?=> Future.successful(testResult)

  /**
   * Mock failed Cucumber execution function for testing exception scenarios
   *
   * @return Function that immediately fails with a generic exception
   */
  def getFailedCucumberExecutionFunction: (CucumberConfiguration, UUID, BlockStorageDirective) => ExecutionContext ?=> Future[TestExecutionResult] =
    (config, testId, directive) => ec ?=> Future.failed(new Throwable("Cucumber execution failed"))
