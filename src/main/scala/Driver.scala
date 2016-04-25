import Chisel._
import Chisel.classictester._
import firrtl._
import java.io._

import Chisel.internal.firrtl.Component



object Driver extends App {
  /*
  val dut = Chisel.Driver.elaborateModule(() => new Hello())
  val tester = new HelloTester(dut)
  tester.finish()
  */

  val s = Chisel.Driver.emit(() => new Hello())
  val rootDir = new File(".").getCanonicalPath()
  val dut = "Hello"
  val buildDir = s"${rootDir}/build"
  val verilogFile = s"${buildDir}/${dut}.v"
  val cppHarness = s"${rootDir}/chisel3/src/main/resources/top.cpp"

  // Run Chisel 3
  val a = Chisel.Driver.elaborateModule(() => new Hello())
  println(s)

  // Parse circuit into FIRRTL
  val circuit = firrtl.Parser.parse(s.split("\n"))

  val writer = new PrintWriter(new File(verilogFile))
  // Compile to verilog
  firrtl.VerilogCompiler.run(circuit, writer)
  writer.close()

  val dutMod = Chisel.Driver.elaborateModule(() => new Hello())
  val tester = new HelloTester(dutMod)
  tester.finish()
  genClassicTesterCppHarness(dutMod, "classic_tester_top.cpp", "Hello.v")

/*
  // Build executable
  println("Running Chisel.Driver.verilogToCpp")
  Chisel.Driver.verilogToCpp(dut, new File(buildDir), Seq(), new File(cppHarness)).!
  Chisel.Driver.cppToExe(dut, new File(buildDir)).!
  if (Chisel.Driver.executeExpectingSuccess(dut, new File(buildDir))) {
    println("Test Executed Successfully!")
  } else {
    println("ERROR: Test Failed")
  }
  */
}