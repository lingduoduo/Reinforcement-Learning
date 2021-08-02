import sbt._
import Keys._
import com.spotify.data.sbt._
import com.spotify.data.sbt.dependencies._
import com.spotify.data.sbt.Python

lazy val commonSettings = Seq(
  organization := "com.spotify.data.BelafonteContentRanker",
  // Use semantic versioning http://semver.org/
  version := "0.1.0-SNAPSHOT",
  publish / skip := true,
  coverageMinimum := 80,
  coverageFailOnMinimum := true
)

lazy val root: Project = project
  .in(file("."))
  .settings(commonSettings)
  .settings(name := "belafonte-ranker-parent")
  .aggregate(`belafonte-ranker-schemas`, `belafonte-ranker`)

lazy val `belafonte-ranker-schemas`: Project = project
  .in(file("belafonte-ranker-schemas"))
  .settings(commonSettings)
  .settings(
    name := "belafonte-ranker-schemas"
  )
  .enablePlugins(SpotifySchemasPlugin)

val scioVersion = "0.9.0"
val scioLibVersion = "0.0.216"
val scioCommonVersion = scioVersion + "-lib" + scioLibVersion

lazy val `belafonte-ranker`: Project = project
  .in(file("belafonte-ranker"))
  .settings(commonSettings)
  .settings(
    name := "belafonte-ranker",
    description := "belafonte-ranker scio pipeline",
    libraryDependencies ++= Seq(
      Libraries.Slf4jApi,
      Libraries.BeamSdksJavaCore,
      Libraries.BeamRunnersGoogleCloudDataflowJava,
      Libraries.BeamRunnersDirectJava,
      Libraries.CoreDataPipelinesSchemas,
      Libraries.SpotifyDataSchemas,
      Libraries.SpotifyScioCommon,
      Libraries.ScioAvro,
      Libraries.ScioCounters,
      Libraries.SpotifyUri,
      Libraries.ScioTest % Test,
      Libraries.RatatoolInternalScalacheck % Test
    ),
    dockerRepository := s"gcr.io/acmacquisition/${name.value}",
    dockerMaintainerName := Some("life-acquatic"),
    dockerMaintainerEmail := Some("life-acquatic-private@spotify.com"),
    luigiDockerPipRequirements := new File(s"${root.base}/requirements.txt"),
    pythonVersion := Python.V3,
    docker / dockerfile := {
      (docker / dockerfile).value
        .runRaw(
          """apt-get update -y && apt-get install build-essential -y && apt-get install gcc gfortran libgomp1 -y"""
        )
    }
  )
  .enablePlugins(SpotifyLuigiPlugin)
  .dependsOn(`belafonte-ranker-schemas`)

lazy val `scio-repl`: Project =
  ScioRepl
    .settings(commonSettings)
    .settings(
      description := "Scio REPL for belafonte-ranker. To start: `sbt scio-repl/run`."
    )
    .dependsOn(`belafonte-ranker`)
