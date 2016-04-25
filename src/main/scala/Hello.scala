package Chisel.classictester

import scala.collection.mutable.LinkedHashMap
import Chisel._
import Chisel.classictester._

class Hello extends Module {
  val io = new Bundle {
    val out = UInt(OUTPUT, width=65)
    val in = UInt(INPUT, width=65)
  }
  io.out := io.in
}

class HelloTester(c: Hello) extends ClassicTester(c) {
  poke(c.io.in, BigInt("10000000000000000", 16))
  peek(c.io.out)
  expect(c.io.out, BigInt("10000000000000000", 16))
}

/*
object StandaloneCPPDriver {
  def main(args: Array[String]) = {
    val tester = new CppEmulatorInterface("./src/main/resources/Hello", LinkedHashMap("Hello__io_in" -> 1), LinkedHashMap("Hello__io_out" -> 1), 0)
    tester.start
    tester.poke("Hello__io_in", 1)
    tester.step(1)
    println(tester.peek("Hello__io_out"))
    tester.poke("Hello__io_in", 2)
    tester.step(1)
    println(tester.peek("Hello__io_out"))
    tester.finish
    println("done")
  }
}
*/
