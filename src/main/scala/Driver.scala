import Chisel._
import Chisel.iotesters._
import firrtl._
import java.io._

/*
val dut = Chisel.Driver.elaborateModule(dutGen)
val rootDirPath = new File(".").getCanonicalPath()
val testDirPath = s"${rootDirPath}/test_run_dir"
val verilogFileName = verilogFilePath.split("/").last
val cppHarnessFileName = "classic_tester_top.cpp"
val cppHarnessFilePath = s"${testDirPath}/${cppHarnessFileName}"
assert(genCppEmulatorBinaryPath(dut) == testDirPath + "/V" + verilogFilePath.split("/").last.split("\\.")(0))//check that the cpp binary path generated here is the same as the binary path expected by the ClassicTester
setupTestDir(testDirPath, verilogFilePath)
genClassicTesterCppHarness(dut, verilogFileName, cppHarnessFilePath)
Chisel.Driver.verilogToCpp(verilogFileName.split("\\.")(0), new File(testDirPath), Seq(), new File(cppHarnessFilePath)).!
Chisel.Driver.cppToExe(verilogFileName.split("\\.")(0), new File(testDirPath)).!
*/

object genCpp {
  def apply(dutGen: ()=> Chisel.Module, dutName: String) = {
    val dutModule = Chisel.Driver.emit(dutGen)
    val rootDirPath = new File(".").getCanonicalPath()
    val testDirPath = s"${rootDirPath}/test_run_dir"
    val verilogFilePath = s"${testDirPath}/${dutName}.v"

    // Parse circuit into FIRRTL
    val circuit = firrtl.Parser.parse(dutModule.split("\n"))

    val writer = new PrintWriter(new File(verilogFilePath))
    // Compile to verilog
    firrtl.VerilogCompiler.run(circuit, writer)
    writer.close()

    val verilogFileName = verilogFilePath.split("/").last
    val cppHarnessFileName = "classic_tester_top.cpp"
    val cppHarnessFilePath = s"${testDirPath}/${cppHarnessFileName}"
    val cppBinaryPath = s"${testDirPath}/V${dutName}"

    val dut = Chisel.Driver.elaborateModule(dutGen)
    genClassicTesterCppHarness(dut, verilogFileName, cppHarnessFilePath)
    Chisel.Driver.verilogToCpp(verilogFileName.split("\\.")(0), new File(testDirPath), Seq(), new File(cppHarnessFilePath)).!
    Chisel.Driver.cppToExe(verilogFileName.split("\\.")(0), new File(testDirPath)).!
    cppBinaryPath
  }
}

object genVerilog {
  def apply(dutGen: ()=> Chisel.Module, dutName: String) = {
    val dutModule = Chisel.Driver.emit(dutGen)
    val rootDir = new File(".").getCanonicalPath()
    val buildDir = s"${rootDir}/build"
    val verilogFilePath = s"${buildDir}/${dutName}.v"

    // Parse circuit into FIRRTL
    val circuit = firrtl.Parser.parse(dutModule.split("\n"))

    val writer = new PrintWriter(new File(verilogFilePath))
    // Compile to verilog
    firrtl.VerilogCompiler.run(circuit, writer)
    writer.close()

    verilogFilePath
  }
}
object Driver extends App {
  val cppFilePath = genCpp(() => new Accumulator(), "Accumulator")
  runClassicTester(() => new Accumulator(), cppFilePath) {(c,p) => new AccumulatorTests(c,p)}



  //chiselMainTest(Array(), () => new SampleDUT()) {(c,p) => new SampleDUTTester(c, p)}
  //verilogFilePath = genVerilog(() => new Accumulator(), "Accumulator")
  //runClassicTester(() => new Accumulator(), verilogFilePath) {c => new AccumulatorTests(c)}
  //verilogFilePath = genVerilog(() => new LFSR16(), "LFSR16")
  //runClassicTester(() => new LFSR16(), verilogFilePath) {c => new LFSR16Tests(c)}
  //verilogFilePath = genVerilog(() => new VendingMachine(), "VendingMachine")
  //runClassicTester(() => new VendingMachine(), verilogFilePath) {c => new VendingMachineTests(c)}
}