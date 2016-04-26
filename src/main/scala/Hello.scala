import scala.collection.mutable.LinkedHashMap
import Chisel._
import Chisel.hwiotesters._

class Hello extends Module {
  val io = new Bundle {
    val in = UInt(INPUT, width=65)
    val out = UInt(OUTPUT, width=65)
    val delayed_out = UInt(OUTPUT, width=65)
  }
  val delayed_in = Reg(UInt(width=65))
  delayed_in := io.in
  io.out := io.in
  io.delayed_out := delayed_in
}

class HelloTester(c: Hello) extends ClassicTester(c) {
  poke(c.io.in, BigInt("10000000000000000", 16))
  peek(c.io.out)
  expect(c.io.out, BigInt("10000000000000000", 16))
  peek(c.io.delayed_out)

  poke(c.io.in, BigInt("10000000000000001", 16))
  step(1)
  peek(c.io.out)
  expect(c.io.out, BigInt("10000000000000000", 16))
  peek(c.io.delayed_out)
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
