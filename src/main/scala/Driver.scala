import Chisel._
import Chisel.iotesters._
import java.io._

object genCpp {
  def apply(dutGen: ()=> Chisel.Module, dutName: String) = {
    val rootDirPath = new File(".").getCanonicalPath()
    val testDirPath = s"${rootDirPath}/test_run_dir"

    val circuit = Chisel.Driver.elaborate(dutGen)
    // Dump FIRRTL for debugging
    val firrtlIRFilePath = s"${testDirPath}/${circuit.name}.ir"
    Chisel.Driver.dumpFirrtl(circuit, Some(new File(firrtlIRFilePath)))
    // Parse FIRRTL
    //val ir = firrtl.Parser.parse(Chisel.Driver.emit(dutGen) split "\n")
    // Generate Verilog
    val verilogFilePath = s"${testDirPath}/${circuit.name}.v"
    //val v = new PrintWriter(new File(s"${dir}/${circuit.name}.v"))
    firrtl.Driver.compile(firrtlIRFilePath, verilogFilePath, new firrtl.VerilogCompiler())

    val verilogFileName = verilogFilePath.split("/").last
    val cppHarnessFileName = "classic_tester_top.cpp"
    val cppHarnessFilePath = s"${testDirPath}/${cppHarnessFileName}"
    val cppBinaryPath = s"${testDirPath}/V${dutName}"
    val vcdFilePath = s"${testDirPath}/${dutName}.vcd"

    copyCppEmulatorHeaderFiles(testDirPath)

    genCppHarness(dutGen, verilogFileName, cppHarnessFilePath, vcdFilePath)
    Chisel.Driver.verilogToCpp(verilogFileName.split("\\.")(0), new File(testDirPath), Seq(), new File(cppHarnessFilePath)).!
    Chisel.Driver.cppToExe(verilogFileName.split("\\.")(0), new File(testDirPath)).!
    cppBinaryPath
  }
}

object Driver extends App {
  val cppFilePath = genCpp(() => new Accumulator(), "Accumulator")
  runClassicTester(() => new Accumulator(), cppFilePath) {(c, p) => new AccumulatorTests(c,emulBinPath=p)}



  //chiselMainTest(Array("--test"), () => new SampleDUT()) {(c) => new SampleDUTTester(c)}
}