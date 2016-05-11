import Chisel._
import Chisel.iotesters._

class Hello extends Module {
  val io = new Bundle { 
    val out = UInt(OUTPUT, width = 8)
    val in = UInt(INPUT, width = 8)
  }
  io.out := io.in
}

class HelloTests(c: Hello) extends ClassicTester(c) {
  step(1)
  expect(c.io.out, 42)
}

