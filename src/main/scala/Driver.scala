import Chisel._
import Chisel.hwiotesters._
import firrtl._
import java.io._

import Chisel.internal.firrtl.Component



object Driver extends App {
  val s = Chisel.Driver.emit(() => new SampleDUT())
  val rootDir = new File(".").getCanonicalPath()
  //val dut = "Hello"
  val dut = "SampleDUT"
  val buildDir = s"${rootDir}/build"
  val verilogFile = s"${buildDir}/${dut}.v"
  val cppHarness = s"${rootDir}/chisel3/src/main/resources/top.cpp"

  // Run Chisel 3
  val a = Chisel.Driver.elaborateModule(() => new SampleDUT())
  println(s)

  // Parse circuit into FIRRTL
  val circuit = firrtl.Parser.parse(s.split("\n"))

  val writer = new PrintWriter(new File(verilogFile))
  // Compile to verilog
  firrtl.VerilogCompiler.run(circuit, writer)
  writer.close()
  runClassicTester(() => new SampleDUT(), verilogFile) {c => new SampleDUTTester(c)}
}