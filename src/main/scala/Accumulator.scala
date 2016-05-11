import Chisel._
import Chisel.iotesters._

class Accumulator extends Module {
  val io = new Bundle {
    val in  = UInt(width = 1, dir = INPUT)
    val out = UInt(width = 8, dir = OUTPUT)
  }
  val accumulator = Reg(UInt(), init=UInt(0, width=8))
  accumulator := accumulator + io.in
  io.out := accumulator
}

class AccumulatorTests(c: Accumulator, emulBinPath: Option[String] = None) extends ClassicTester(c, emulBinPath = emulBinPath) {
  var tot = 0
  for (t <- 0 until 16) {
    val in = rnd.nextInt(2)
    poke(c.io.in, in)
    step(1)
    if (in == 1) tot += 1
    expect(c.io.out, tot)
    //expect(c.io.out, 10)
  }
}
