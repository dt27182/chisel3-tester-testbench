lazy val buildSettings = Seq (
    scalaVersion := "2.11.7"
)
lazy val root = (project in file(".")).settings(buildSettings).dependsOn(chisel, chiselTesters, firrtl)

lazy val chisel = project in file("chisel3")
lazy val chiselTesters = project in file("chisel-testers")
lazy val firrtl = project in file("firrtl")
