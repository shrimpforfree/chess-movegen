package chesslab.core

/**
 * Transposition table — caches search results for positions already evaluated.
 * Uses Zobrist hash as the key. Resizable array with replacement.
 */
object TransTable:

  enum Flag:
    case Exact      // score is the exact minimax value
    case LowerBound // score is a lower bound (beta cutoff)
    case UpperBound // score is an upper bound (failed to raise alpha)

  case class Entry(
    key: Long,
    depth: Int,
    score: Int,
    flag: Flag,
    bestFrom: Int,
    bestTo: Int
  )

  // ~40 bytes per entry → entries per MB ≈ 25,000
  private val EntriesPerMb = 25_000

  private var table: Array[Entry | Null] = new Array[Entry | Null](16 * EntriesPerMb)

  /** Resize the table. Called before a search if config changes. */
  def resize(sizeMb: Int): Unit =
    val newSize = math.max(1, sizeMb) * EntriesPerMb
    if newSize != table.length then
      table = new Array[Entry | Null](newSize)

  private def index(key: Long): Int =
    ((key & 0x7fffffffffffffffL) % table.length).toInt

  def probe(key: Long): Option[Entry] =
    val entry = table(index(key))
    if entry != null && entry.key == key then Some(entry)
    else None

  def store(key: Long, depth: Int, score: Int, flag: Flag, bestFrom: Int, bestTo: Int): Unit =
    val idx = index(key)
    val existing = table(idx)
    if existing == null || existing.key == key || depth >= existing.depth then
      table(idx) = Entry(key, depth, score, flag, bestFrom, bestTo)

  def clear(): Unit =
    java.util.Arrays.fill(table.asInstanceOf[Array[Object]], null)
