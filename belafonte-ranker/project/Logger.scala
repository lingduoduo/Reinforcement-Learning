import sbt.util._
case class DynamicLogger(underlying: Logger) extends Logger {
  private def getPrio(m: String) = {
    if (m.contains("ERR")) Level.Error
    else if (m.contains("WARN")) Level.Warn
    else if (m.contains("INFO")) Level.Info
    else Level.Debug
  }

  def trace(t: => Throwable): Unit = underlying.trace(t)
  def success(message: => String): Unit = underlying.success(message)
  def log(level: Level.Value, message: => String): Unit = underlying.log(getPrio(message), message)
}
