import Chisel._
import Chisel.iotesters._
import java.io._

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
    val vcdFilePath = s"${testDirPath}/${dutName}.vcd"

    copyCppEmulatorHeaderFiles(testDirPath)

    val dut = Chisel.Driver.elaborateModule(dutGen)
    genCppHarness(dutGen, verilogFileName, cppHarnessFilePath, vcdFilePath)
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
  runClassicTester(() => new Accumulator(), cppFilePath) {(c, p) => new AccumulatorTests(c,emulBinPath=p)}



  //chiselMainTest(Array(), () => new SampleDUT()) {(c,p) => new SampleDUTTester(c, p)}
  //verilogFilePath = genVerilog(() => new Accumulator(), "Accumulator")
  //runClassicTester(() => new Accumulator(), verilogFilePath) {c => new AccumulatorTests(c)}
  //verilogFilePath = genVerilog(() => new LFSR16(), "LFSR16")
  //runClassicTester(() => new LFSR16(), verilogFilePath) {c => new LFSR16Tests(c)}
  //verilogFilePath = genVerilog(() => new VendingMachine(), "VendingMachine")
  //runClassicTester(() => new VendingMachine(), verilogFilePath) {c => new VendingMachineTests(c)}
}