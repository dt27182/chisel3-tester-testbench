import Chisel._
import Chisel.hwiotesters._
import firrtl._
import java.io._

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
  var verilogFilePath = genVerilog(() => new SampleDUT(), "SampleDUT")
  //runClassicTester(() => new SampleDUT(), verilogFilePath) {c => new SampleDUTTester(c)}
  //verilogFilePath = genVerilog(() => new Accumulator(), "Accumulator")
  //runClassicTester(() => new Accumulator(), verilogFilePath) {c => new AccumulatorTests(c)}
  //verilogFilePath = genVerilog(() => new LFSR16(), "LFSR16")
  //runClassicTester(() => new LFSR16(), verilogFilePath) {c => new LFSR16Tests(c)}
  verilogFilePath = genVerilog(() => new VendingMachine(), "VendingMachine")
  runClassicTester(() => new VendingMachine(), verilogFilePath) {c => new VendingMachineTests(c)}
}