package Chisel.classictester

import java.io.{File, IOException, PrintWriter}
import java.nio.file.{FileAlreadyExistsException, Files, Paths}
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

import Chisel._

import scala.collection.mutable.LinkedHashMap

class ClassicTester(dut: Module) {
  private val (inputNodeInfoMap, outputNodeInfoMap) = getNodeInfo(dut)
  private val inputSignalToChunkSizeMap = new LinkedHashMap[String, Int]()
  inputNodeInfoMap.toList.foreach(x => inputSignalToChunkSizeMap(x._2._1) = (x._2._2 - 1)/64 + 1)
  println(inputSignalToChunkSizeMap)
  private val outputSignalToChunkSizeMap = new LinkedHashMap[String, Int]()
  outputNodeInfoMap.toList.foreach(x => outputSignalToChunkSizeMap(x._2._1) = (x._2._2 - 1)/64 + 1)
  private val nodeToStringMap: Map[Data, String] = (inputNodeInfoMap.toList ++ outputNodeInfoMap.toList).map(x => (x._1, x._2._1)).toMap
  private val cppEmulatorInterface = new CppEmulatorInterface(genCppEmulatorBinaryPath(dut), inputSignalToChunkSizeMap, outputSignalToChunkSizeMap, 0)
  cppEmulatorInterface.start()//start cpp emulator


  def poke(signal: Data, value: BigInt) = {
    cppEmulatorInterface.poke(nodeToStringMap(signal), value)
  }
  def peek(signal: Data): BigInt = {
    cppEmulatorInterface.peek(nodeToStringMap(signal))
  }
  def expect (signal: Data, expected: BigInt, msg: => String = ""): Boolean = {
    cppEmulatorInterface.expect(nodeToStringMap(signal), expected, msg)
  }
  def step(n: Int) {
    cppEmulatorInterface.step(n)
  }
  def finish(): Bool = {
    cppEmulatorInterface.finish()
  }
}

object genCppEmulatorBinaryPath {
  def apply(dut: Module): String = {
    val rootDirPath = new File(".").getCanonicalPath()
    val testDirPath = s"${rootDirPath}/test_run_dir"
    testDirPath + "/V" + dut.name
  }
}

object getNodeInfo {
  def apply(dut: Module): (LinkedHashMap[Data, (String, Int)], LinkedHashMap[Data, (String, Int)]) = {
    val inputNodeInfoMap = new  LinkedHashMap[Data, (String, Int)]()
    val outputNodeInfoMap = new  LinkedHashMap[Data, (String, Int)]()

    def add_to_ports_by_direction(port: Data, name: String, width: Int): Unit = {
      port.dir match {
        case INPUT => inputNodeInfoMap(port) = (name, width)
        case OUTPUT => outputNodeInfoMap(port) = (name, width)
        case _ =>
      }
    }

    def parseBundle(b: Bundle, name: String = ""): Unit = {
      for ((n, e) <- b.namedElts) {
        val new_name = name + (if (name.length > 0) "_" else "") + n
        add_to_ports_by_direction(e, new_name, e.getWidth)

        e match {
          case bb: Bundle => parseBundle(bb, new_name)
          case vv: Vec[_] => parseVecs(vv, new_name)
          case ee: Element =>
          case _ =>
            throw new Exception(s"bad bundle member $new_name $e")
        }
      }
    }
    def parseVecs[T <: Data](b: Vec[T], name: String = ""): Unit = {
      for ((e, i) <- b.zipWithIndex) {
        val new_name = name + s"($i)"
        add_to_ports_by_direction(e, new_name, e.getWidth)

        e match {
          case bb: Bundle => parseBundle(bb, new_name)
          case vv: Vec[_] => parseVecs(vv, new_name)
          case ee: Element =>
          case _ =>
            throw new Exception(s"bad bundle member $new_name $e")
        }
      }
    }

    parseBundle(dut.io, "io")
    (inputNodeInfoMap, outputNodeInfoMap)
  }
}

object runClassicTester {
  private def setupTestDir(testDirPath: String, verilogFilePath: String): Unit = {
    val verilogFileName = verilogFilePath.split("/").last
    val emulatorHFilePath = Paths.get(testDirPath + "/emulator.h")
    val simApiHFilePath = Paths.get(testDirPath + "/sim_api.h")
    val verilatorApiHFilePath = Paths.get(testDirPath + "/verilator_api.h")
    val newVerilogFilePath = Paths.get(testDirPath + "/" + verilogFileName)
    try {
      Files.createDirectory(Paths.get(testDirPath))
      Files.createFile(emulatorHFilePath)
      Files.createFile(simApiHFilePath)
      Files.createFile(verilatorApiHFilePath)
      Files.createFile(newVerilogFilePath)
    } catch {
      case x: FileAlreadyExistsException =>
        System.out.format("")
      case x: IOException => {
        System.err.format("createFile error: %s%n", x)
      }
    }

    Files.copy(Paths.get("src/main/resources/emulator.h"), emulatorHFilePath, REPLACE_EXISTING)
    Files.copy(Paths.get("src/main/resources/sim_api.h"), simApiHFilePath, REPLACE_EXISTING)
    Files.copy(Paths.get("src/main/resources/verilator_api.h"), verilatorApiHFilePath, REPLACE_EXISTING)
    Files.copy(Paths.get(verilogFilePath), newVerilogFilePath, REPLACE_EXISTING)
  }

  def apply[T <: Module] (dutGen: () => T, verilogFilePath: String) (testerGen: T => ClassicTester): Unit = {
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

    val tester = testerGen(dut)
    tester.finish()
  }
}

object genClassicTesterCppHarness {
  def apply(dut: Module, verilogFileName: String, cppHarnessFilePath: String): Unit = {
    val (dutInputNodeInfo, dutOutputNodeInfo) = getNodeInfo(dut)
    val dutName = verilogFileName.split("\\.")(0)
    val dutApiClassName = dutName + "_api_t"
    val dutVerilatorClassName = "V" + dutName
    val fileWriter = new PrintWriter(new File(cppHarnessFilePath))

    fileWriter.write("#include \"" + dutVerilatorClassName + ".h\"\n")
    fileWriter.write("#include \"verilated.h\"\n")
    fileWriter.write("#include \"verilator_api.h\"\n")
    fileWriter.write("#include <iostream>\n")

    fileWriter.write(s"class ${dutApiClassName}: public sim_api_t<VerilatorDataWrapper*> {\n")
    fileWriter.write("public:\n")
    fileWriter.write(s"    ${dutApiClassName}(${dutVerilatorClassName}* _dut) {\n")
    fileWriter.write("        dut = _dut;\n")
    fileWriter.write("        is_exit = false;\n")
    fileWriter.write("    }\n")
    fileWriter.write("    void init_sim_data() {\n")
    fileWriter.write("        sim_data.inputs.clear();\n")
    fileWriter.write("        sim_data.outputs.clear();\n")
    fileWriter.write("        sim_data.signals.clear();\n")
    dutInputNodeInfo.toList.foreach { x =>
      val nodeName = x._2._1
      val nodeWidth = x._2._2
      if (nodeWidth <= 8) {
        fileWriter.write(s"        sim_data.inputs.push_back(new VerilatorCData(&(dut->${nodeName})));\n")
      } else if (nodeWidth <= 16) {
        fileWriter.write(s"        sim_data.inputs.push_back(new VerilatorSData(&(dut->${nodeName})));\n")
      } else if (nodeWidth <= 32) {
        fileWriter.write(s"        sim_data.inputs.push_back(new VerilatorIData(&(dut->${nodeName})));\n")
      } else if (nodeWidth <= 64) {
        fileWriter.write(s"        sim_data.inputs.push_back(new VerilatorQData(&(dut->${nodeName})));\n")
      } else {
        val numWords = (nodeWidth - 1)/32 + 1
        fileWriter.write(s"        sim_data.inputs.push_back(new VerilatorWData(dut->${nodeName}, ${numWords}));\n")
      }
    }
    dutOutputNodeInfo.toList.foreach { x =>
      val nodeName = x._2._1
      val nodeWidth = x._2._2
      if (nodeWidth <= 8) {
        fileWriter.write(s"        sim_data.outputs.push_back(new VerilatorCData(&(dut->${nodeName})));\n")
      } else if (nodeWidth <= 16) {
        fileWriter.write(s"        sim_data.outputs.push_back(new VerilatorSData(&(dut->${nodeName})));\n")
      } else if (nodeWidth <= 32) {
        fileWriter.write(s"        sim_data.outputs.push_back(new VerilatorIData(&(dut->${nodeName})));\n")
      } else if (nodeWidth <= 64) {
        fileWriter.write(s"        sim_data.outputs.push_back(new VerilatorQData(&(dut->${nodeName})));\n")
      } else {
        val numWords = (nodeWidth - 1)/32 + 1
        fileWriter.write(s"        sim_data.outputs.push_back(new VerilatorWData(dut->${nodeName}, ${numWords}));\n")
      }
    }
    fileWriter.write("    }\n")
    fileWriter.write("    inline bool exit() { return is_exit; }\n")
    fileWriter.write("protected:\n")
    fileWriter.write(s"    ${dutVerilatorClassName}* dut;\n")
    fileWriter.write("private:\n")
    fileWriter.write("    bool is_exit;\n")
    fileWriter.write("    virtual inline size_t put_value(VerilatorDataWrapper* &sig, uint64_t* data, bool force=false) {\n")
    fileWriter.write("        return sig->put_value(data);\n")
    fileWriter.write("    }\n")
    fileWriter.write("    virtual inline size_t get_value(VerilatorDataWrapper* &sig, uint64_t* data) {\n")
    fileWriter.write("        return sig->get_value(data);\n")
    fileWriter.write("    }\n")
    fileWriter.write("    virtual inline size_t get_chunk(VerilatorDataWrapper* &sig) {\n")
    fileWriter.write("        return sig->get_num_words();\n")
    fileWriter.write("    } \n")
    fileWriter.write("    virtual inline void reset() {\n")
    fileWriter.write("        dut->reset = 1;\n")
    fileWriter.write("        dut->eval();\n")
    fileWriter.write("        dut->reset = 0;\n")
    fileWriter.write("    }\n")
    fileWriter.write("    virtual inline void start() { }\n")
    fileWriter.write("    virtual inline void finish() {\n")
    fileWriter.write("        dut->eval();\n")
    fileWriter.write("        is_exit = true;\n")
    fileWriter.write("    }\n")
    fileWriter.write("    virtual inline void step() {\n")
    fileWriter.write("        dut->clk = 0;\n")
    fileWriter.write("        dut->eval();\n")
    fileWriter.write("        dut->clk = 1;\n")
    fileWriter.write("        dut->eval();\n")
    fileWriter.write("        dut->clk = 0;\n")
    fileWriter.write("        dut->eval();\n")
    fileWriter.write("    }\n")
    fileWriter.write("    virtual inline void update() {\n")
    fileWriter.write("        dut->eval();\n")
    fileWriter.write("    }\n")
    fileWriter.write("};\n")
    fileWriter.write("int main(int argc, char **argv, char **env) {\n")
    fileWriter.write("    Verilated::commandArgs(argc, argv);\n")
    fileWriter.write(s"    ${dutVerilatorClassName}* top = new ${dutVerilatorClassName};\n")
    fileWriter.write(s"    ${dutApiClassName} api(top);\n")
    fileWriter.write("    api.init_sim_data();\n")
    fileWriter.write("    api.init_channels();\n")
    fileWriter.write("    while(!api.exit()) api.tick();\n")
    fileWriter.write("    delete top;\n")
    fileWriter.write("    exit(0);\n")
    fileWriter.write("}\n")
    fileWriter.close()
    println(s"ClassicTester CppHarness generated at ${cppHarnessFilePath}")
  }
}
