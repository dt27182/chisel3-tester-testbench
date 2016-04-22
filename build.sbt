lazy val root = (project in file(".")).settings(buildSettings).dependsOn(chisel)
lazy val chisel = project in file("chisel3")
lazy val buildSettings = Seq (
    scalaVersion := "2.11.7"
)
