import scala.collection.mutable.LinkedHashMap
import Chisel._
import Chisel.hwiotesters._

class innerBundle extends Bundle {
  val c = UInt(INPUT, width=8)
  val d = UInt(OUTPUT, width=8)
}

class SampleDUT extends Module {
  val io = new Bundle {
    val in = UInt(INPUT, width=65)
    val out = UInt(OUTPUT, width=65)
    val delayed_out = UInt(OUTPUT, width=65)
    val in_bundle = new Bundle {
      val a = UInt(INPUT, width=8)
      val b = UInt(OUTPUT, width=8)
    }
    val test_vec_in = Vec(3, UInt(INPUT, width=8))
    val test_vec_out = Vec(3, UInt(OUTPUT, width=8))
    val test_vec_inner = Vec(3, new innerBundle())
  }
  val delayed_in = Reg(UInt(width=65))
  delayed_in := io.in
  io.out := io.in
  io.delayed_out := delayed_in
  io.in_bundle.b := io.in_bundle.a
  io.test_vec_out := io.test_vec_in

  io.test_vec_inner(0).d := io.test_vec_inner(0).c
}

class SampleDUTTester(c: SampleDUT) extends ClassicTester(c) {
  poke(c.io.in, BigInt("10000000000000000", 16))
  peek(c.io.out)
  expect(c.io.out, BigInt("10000000000000000", 16))
  peek(c.io.delayed_out)

  poke(c.io.in, BigInt("10000000000000001", 16))
  step(1)
  peek(c.io.out)
  expect(c.io.out, BigInt("10000000000000001", 16))
  peek(c.io.delayed_out)

  poke(c.io.in_bundle.a, 10)
  peek(c.io.in_bundle.b)

  poke(c.io.test_vec_in(0), 10)
  peek(c.io.test_vec_out(0))

  poke(c.io.test_vec_inner(0).c, 10)
  peek(c.io.test_vec_inner(0).d)
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
