package Chisel.classictester

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import java.nio.channels.FileChannel
import scala.collection.mutable.{ArrayBuffer, LinkedHashMap}
import scala.sys.process.{Process, ProcessLogger}

class CppEmulatorInterface(val cmd: String, val inputSignalToChunkSizeMap: LinkedHashMap[String, Int], val outputSignalToChunkSizeMap: LinkedHashMap[String, Int], testerSeed: Int) {
  private object SIM_CMD extends Enumeration {
    val RESET, STEP, UPDATE, POKE, PEEK, FORCE, GETID, GETCHK, SETCLK, FIN = Value }
  implicit def cmdToId(cmd: SIM_CMD.Value) = cmd.id

  /* state variables */
  private val inputSignalValueMap = LinkedHashMap[String, BigInt]()
  private val outputSignalValueMap = LinkedHashMap[String, BigInt]()
  private val _logs = new ArrayBuffer[String]()
  private var simTime = 0L // simulation time
  private var isStale = false
  private val rnd = new Random(testerSeed)
  /* state variables */

  /* standalone util functions */
  implicit def longToInt(x: Long) = x.toInt
  /** Convert a Boolean to BigInt */
  private def int(x: Boolean): BigInt = if (x) 1 else 0
  /** Convert an Int to BigInt */
  private def int(x: Int):     BigInt = (BigInt(x >>> 1) << 1) | x & 1
  /** Convert a Long to BigInt */
  private def int(x: Long):    BigInt = (BigInt(x >>> 1) << 1) | x & 1
  /* standalone util functions */

  /* state modification functions */
  private def incTime(n: Int) { simTime += n }
  private def printfs = _logs.toVector
  private def throwExceptionIfDead(exitValue: Future[Int]) {
    if (exitValue.isCompleted) {
      val exitCode = Await.result(exitValue, Duration(-1, SECONDS))
      // We assume the error string is the last log entry.
      val errorString = if (_logs.size > 0) {
        _logs.last
      } else {
        "test application exit"
      } + " - exit code %d".format(exitCode)
      throw new TestApplicationException(exitCode, errorString)
    }
  }
  // A busy-wait loop that monitors exitValue so we don't loop forever if the test application exits for some reason.
  private def mwhile(block: => Boolean)(loop: => Unit) {
    while (!exitValue.isCompleted && block) {
      loop
    }
    // If the test application died, throw a run-time error.
    throwExceptionIfDead(exitValue)
  }

  private def sendCmd(data: Int) = {
    cmdChannel.aquire
    val ready = cmdChannel.ready
    if (ready) {
      cmdChannel(0) = data
      cmdChannel.produce
    }
    cmdChannel.release
    ready
  }
  private def sendCmd(data: String) = {
    cmdChannel.aquire
    val ready = cmdChannel.ready
    if (ready) {
      cmdChannel(0) = data
      cmdChannel.produce
    }
    cmdChannel.release
    ready
  }
  private def recvResp = {
    outChannel.aquire
    val valid = outChannel.valid
    val resp = if (!valid) None else {
      outChannel.consume
      Some(outChannel(0).toInt)
    }
    outChannel.release
    resp
  }
  private def sendValue(value: BigInt, chunk: Int) = {
    inChannel.aquire
    val ready = inChannel.ready
    if (ready) {
      (0 until chunk) foreach (i => inChannel(i) = (value >> (64*i)).toLong)
      inChannel.produce
    }
    inChannel.release
    ready
  }
  private def recvValue(chunk: Int) = {
    outChannel.aquire
    val valid = outChannel.valid
    val value = if (!valid) None else {
      outChannel.consume
      Some(((0 until chunk) foldLeft BigInt(0))(
        (res, i) => res | (int(outChannel(i)) << (64*i))))
    }
    outChannel.release
    value
  }

  private def recvOutputs = {
    outputSignalValueMap.clear
    outChannel.aquire
    val valid = outChannel.valid
    if (valid) {
      (outputSignalToChunkSizeMap.keys.toList foldLeft 0){case (off, out) =>
        val chunk = outputSignalToChunkSizeMap(out)
        outputSignalValueMap(out) = ((0 until chunk) foldLeft BigInt(0))(
          (res, i) => res | (int(outChannel(off + i)) << (64 * i))
        )
        off + chunk
      }
      outChannel.consume
    }
    outChannel.release
    valid
  }
  private def sendInputs = {
    inChannel.aquire
    val ready = inChannel.ready
    if (ready) {
      (inputSignalToChunkSizeMap.keys.toList foldLeft 0){case (off, in) =>
        val chunk = inputSignalToChunkSizeMap(in)
        val value = inputSignalValueMap getOrElse (in, BigInt(0))
        (0 until chunk) foreach (i => inChannel(off + i) = (value >> (64 * i)).toLong)
        off + chunk
      }
      inChannel.produce
    }
    inChannel.release
    ready
  }

  private def reset(n: Int = 1) {
    for (i <- 0 until n) {
      mwhile(!sendCmd(SIM_CMD.RESET)) { }
      mwhile(!recvOutputs) { }
    }
  }
  private def update {
    mwhile(!sendCmd(SIM_CMD.UPDATE)) { }
    mwhile(!sendInputs) { }
    mwhile(!recvOutputs) { }
    isStale = false
  }
  private def takeStep {
    mwhile(!sendCmd(SIM_CMD.STEP)) { }
    mwhile(!sendInputs) { }
    mwhile(!recvOutputs) { }
    isStale = false
  }
  private def getId(path: String) = {
    mwhile(!sendCmd(SIM_CMD.GETID)) { }
    mwhile(!sendCmd(path)) { }
    if (exitValue.isCompleted) {
      0
    } else {
      (for {
        _ <- Stream.from(1)
        data = recvResp
        if data != None
      } yield data.get).head
    }
  }
  private def getChunk(id: Int) = {
    mwhile(!sendCmd(SIM_CMD.GETCHK)) { }
    mwhile(!sendCmd(id)) { }
    if (exitValue.isCompleted){
      0
    } else {
      (for {
        _ <- Stream.from(1)
        data = recvResp
        if data != None
      } yield data.get).head
    }
  }
  def start() {
    mwhile(!recvOutputs) { }
    reset(5)
    for (i <- 0 until 5) {
      mwhile(!sendCmd(SIM_CMD.RESET)) { }
      mwhile(!recvOutputs) { }
    }
  }
  def poke(signalName: String, value: BigInt) = {
    assert(inputSignalValueMap.contains(signalName))
    inputSignalValueMap(signalName) = value
    isStale = true
  }
  def peek(signalName: String): BigInt = {
    assert(inputSignalValueMap.contains(signalName) || outputSignalValueMap.contains(signalName))
    if (isStale) {
      update
    }
    var result: BigInt = -1
    if (inputSignalValueMap.contains(signalName)) {
      result = inputSignalValueMap(signalName)
    } else if(outputSignalValueMap.contains(signalName)) {
      result = outputSignalValueMap(signalName)
    }
    result
  }
  def step(n: Int) {
    (0 until n) foreach (_ => takeStep)
    incTime(n)
  }
  /** Complete the simulation and inspect all tests */
  def finish(): Boolean = {
    try {
      mwhile(!sendCmd(SIM_CMD.FIN)) { }
      while(!exitValue.isCompleted) { }
    }
    catch {
      // Depending on load and timing, we may get a TestApplicationException
      //  when the test application exits.
      //  Check the exit value.
      //  Anything other than 0 is an error.
      case e: TestApplicationException => if (e.exitVal != 0) fail
    }
    _logs.clear
    inChannel.close
    outChannel.close
    cmdChannel.close
    ok
  }
  /** Indicate a failure has occurred.  */
  private var failureTime = -1L
  private var ok = true
  private def fail = if (ok) {
    failureTime = simTime
    ok = false
  }
  /* state modification functions */

  /*constructor logic*/
  //initialize inputSignalValueMap and outputSignalValueMap
  inputSignalToChunkSizeMap.foreach { case (signalName, chunkSize) => inputSignalValueMap(signalName) = 0 }
  outputSignalToChunkSizeMap.foreach { case (signalName, chunkSize) => outputSignalValueMap(signalName) = BigInt(rnd.nextInt) }

  //initialize cpp process and memory mapped channels
  private val (process: Process, exitValue: Future[Int], inChannel, outChannel, cmdChannel) = {
    val processBuilder = Process(cmd)
    val processLogger = ProcessLogger(println, _logs += _) // don't log stdout
    val process = processBuilder run processLogger

    // Set up a Future to wait for (and signal) the test process exit.
    val exitValue: Future[Int] = Future {
      blocking {
        process.exitValue
      }
    }
    // Wait for the startup message
    // NOTE: There may be several messages before we see our startup message.
    val simStartupMessageStart = "sim start on "
    while (!_logs.exists(_ startsWith simStartupMessageStart) && !exitValue.isCompleted) { Thread.sleep(100) }
    // Remove the startup message (and any precursors).
    while (!_logs.isEmpty && !_logs.head.startsWith(simStartupMessageStart)) {
      println(_logs.remove(0))
    }
    if (!_logs.isEmpty) println(_logs.remove(0)) else println("<no startup message>")
    while (_logs.size < 3) {
      // If the test application died, throw a run-time error.
      throwExceptionIfDead(exitValue)
      Thread.sleep(100)
    }
    val in_channel_name = _logs.remove(0)
    val out_channel_name = _logs.remove(0)
    val cmd_channel_name = _logs.remove(0)
    val in_channel = new Channel(in_channel_name)
    val out_channel = new Channel(out_channel_name)
    val cmd_channel = new Channel(cmd_channel_name)

    println(s"inChannelName: ${in_channel_name}")
    println(s"outChannelName: ${out_channel_name}")
    println(s"cmdChannelName: ${cmd_channel_name}")

    in_channel.consume
    cmd_channel.consume
    in_channel.release
    out_channel.release
    cmd_channel.release
    simTime = 0

    (process, exitValue, in_channel, out_channel, cmd_channel)
  }
  /*constructor logic*/
}

case class TestApplicationException(exitVal: Int, lastMessage: String) extends RuntimeException(lastMessage)

class Channel(name: String) {
  private lazy val file = new java.io.RandomAccessFile(name, "rw")
  private lazy val channel = file.getChannel
  @volatile private lazy val buffer = {
    /* We have seen runs where buffer.put(0,0) fails with:
[info]   java.lang.IndexOutOfBoundsException:
[info]   at java.nio.Buffer.checkIndex(Buffer.java:532)
[info]   at java.nio.DirectByteBuffer.put(DirectByteBuffer.java:300)
[info]   at Chisel.Tester$Channel.release(Tester.scala:148)
[info]   at Chisel.Tester.start(Tester.scala:717)
[info]   at Chisel.Tester.<init>(Tester.scala:743)
[info]   at ArbiterSuite$ArbiterTests$8.<init>(ArbiterTest.scala:396)
[info]   at ArbiterSuite$$anonfun$testStableRRArbiter$1.apply(ArbiterTest.scala:440)
[info]   at ArbiterSuite$$anonfun$testStableRRArbiter$1.apply(ArbiterTest.scala:440)
[info]   at Chisel.Driver$.apply(Driver.scala:65)
[info]   at Chisel.chiselMain$.apply(hcl.scala:63)
[info]   ...
     */
    val size = channel.size
    assert(size > 16, "channel.size is bogus: %d".format(size))
    channel map (FileChannel.MapMode.READ_WRITE, 0, size)
  }
  implicit def intToByte(i: Int) = i.toByte
  val channel_data_offset_64bw = 4    // Offset from start of channel buffer to actual user data in 64bit words.
  def aquire {
    buffer put (0, 1)
    buffer put (2, 0)
    while((buffer get 1) == 1 && (buffer get 2) == 0) {}
  }
  def release { buffer put (0, 0) }
  def ready = (buffer get 3) == 0
  def valid = (buffer get 3) == 1
  def produce { buffer put (3, 1) }
  def consume { buffer put (3, 0) }
  def update(idx: Int, data: Long) { buffer putLong (8 * idx + channel_data_offset_64bw, data) }
  def update(base: Int, data: String) {
    data.zipWithIndex foreach {case (c, i) => buffer put (base + i + channel_data_offset_64bw, c) }
    buffer put (base + data.size + channel_data_offset_64bw, 0)
  }
  def apply(idx: Int): Long = buffer getLong (8 * idx + channel_data_offset_64bw)
  def close { file.close }
  buffer order java.nio.ByteOrder.nativeOrder
  new java.io.File(name).delete
}
