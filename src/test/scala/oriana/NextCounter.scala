package oriana

class NextCounter(limit: Int) extends Iterable[Int] {
  var nextCalls = 0
  override def iterator = new Iterator[Int]() {
    var i = 0
    override def hasNext = i < limit

    override def next() = {
      val result = i
      i += 1
      nextCalls += 1
      result
    }
  }
}
