package io.buoyant.linkerd.failureAccrual

import com.twitter.finagle.liveness.FailureAccrualPolicy
import com.twitter.finagle.util.LoadService
import com.twitter.conversions.time._
import io.buoyant.config.Parser
import io.buoyant.linkerd.{FailureAccrualConfig, FailureAccrualInitializer}
import io.buoyant.test.FunSuite
import org.scalatest.Matchers

class ConsecutiveFailuresTest extends FunSuite
  with Matchers {
  def parse(yaml: String): FailureAccrualConfig = {
    val mapper = Parser.objectMapper(yaml, Seq(Seq(
      new ConsecutiveFailuresInitializer,
      new NoneInitializer,
      new SuccessRateInitializer,
      new SuccessRateWindowedInitializer
    )))
    mapper.readValue[FailureAccrualConfig](yaml)
  }

  test("sanity") {
    val failures = 2
    val config = ConsecutiveFailuresConfig(failures)
    assert(config.policy().isInstanceOf[FailureAccrualPolicy])
    assert(config.failures == failures)
  }

  test("service registration") {
    assert(LoadService[FailureAccrualInitializer].exists(_.isInstanceOf[ConsecutiveFailuresInitializer]))
  }

  test("constant backoff") {
    val yaml = """|kind: io.l5d.consecutiveFailures
                  |failures: 5
                  |backoff:
                  |  kind: constant
                  |  ms: 10000""".stripMargin
    val policy = parse(yaml).policy()
    val probeDelays = Stream.continually(policy.markDeadOnFailure()).take(20)
    probeDelays.take(4) should contain only None
    probeDelays.drop(4) should contain only Some(10000.millis)
  }

  test("default (exponential) backoff") {
    val yaml =
      """|kind: io.l5d.consecutiveFailures
         |failures: 5""".stripMargin

    val policy = parse(yaml).policy()
    val probeDelays = Stream.continually(policy.markDeadOnFailure()).take(20)
    probeDelays shouldBe sorted // todo: better assertion that the increase is exponential
  }
}
