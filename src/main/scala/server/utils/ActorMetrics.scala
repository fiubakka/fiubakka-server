package server.utils

class ActorMetrics {
  private var msgCount = 0
  private var msgCountStartTimeMillis = System.currentTimeMillis()
  private val secondsToRestartMetric = 5;

  def incrementMessageCount(): Unit = {
    msgCount += 1
  }

  def messageRatePerSecond: Float = {
    val elapsedTimeMillis = System.currentTimeMillis() - msgCountStartTimeMillis
    val msgRatePerSecond = (msgCount.toFloat / elapsedTimeMillis) * 1000
    if elapsedTimeMillis > (1000 * secondsToRestartMetric) then {
      msgCount = 0
      msgCountStartTimeMillis = System.currentTimeMillis()
    }
    msgRatePerSecond
  }
}
