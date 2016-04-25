package Chisel.classictester

import java.io.{File, PrintWriter}

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
  //private val cppEmulatorInterface = new CppEmulatorInterface(genCppEmulatorBinaryPath(dut), inputSignalToChunkSizeMap, outputSignalToChunkSizeMap, 0)
  private val cppEmulatorInterface = new CppEmulatorInterface("src/main/resources/VHello", inputSignalToChunkSizeMap, outputSignalToChunkSizeMap, 0)
  cppEmulatorInterface.start()//start cpp emulator


  def poke(signal: Data, value: BigInt) = {
    cppEmulatorInterface.poke(nodeToStringMap(signal), value)
  }
  def peek(signal: Data): BigInt = {
    cppEmulatorInterface.peek(nodeToStringMap(signal))
  }
  def step(n: Int) {
    cppEmulatorInterface.step(n)
  }
  def finish(): Unit = {
    cppEmulatorInterface.finish()
  }
}

object genCppEmulatorBinaryPath {
  def apply(dut: Module): String = {
    "V" + dut.name
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
  def apply(dut: Module, tester: ClassicTester, verilogFilePath: String ): Unit = {
  }
}
object genClassicTesterCppHarness {
  def apply(dut: Module, cppHarnessFilePath: String, verilogFileName: String): Unit = {
    val (dutInputNodeInfo, dutOutputNodeInfo) = getNodeInfo(dut)
    val dutName = verilogFileName.split("\\.")(0)
    val dutApiClassName = dutName + "_api_t"
    val dutVerilatorClassName = "V" + dutName
    val fileWriter = new PrintWriter(new File(cppHarnessFilePath))

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
    fileWriter.close()
    println("DEBUG")
  }
}
