package chesslab.core

/**
 * Sq64 — 0..63 square index for bitboard representation.
 * a1=0, b1=1, ..., h1=7, a2=8, ..., h8=63
 * (little-endian rank-file mapping)
 */
opaque type Sq64 = Int

object Sq64:
  inline def apply(i: Int): Sq64 = i

  extension (sq: Sq64)
    inline def value: Int = sq
    inline def rank: Int = sq / 8
    inline def file: Int = sq % 8
    inline def toBit: Long = 1L << sq

  // Mailbox (10x12) index → Sq64 (-1 for off-board)
  val fromMailbox: IArray[Int] = IArray.tabulate(120) { idx =>
    val row = idx / 10
    val col = idx % 10
    if row >= 2 && row <= 9 && col >= 1 && col <= 8 then
      (9 - row) * 8 + (col - 1)
    else -1
  }

  // Sq64 → Mailbox (10x12) index
  val toMailbox: IArray[Int] = IArray.tabulate(64) { sq =>
    (9 - sq / 8) * 10 + (sq % 8 + 1)
  }

  // ---------------------------------------------------------------------------
  // Named squares
  // ---------------------------------------------------------------------------
  val A1: Sq64 = 0;  val B1: Sq64 = 1;  val C1: Sq64 = 2;  val D1: Sq64 = 3
  val E1: Sq64 = 4;  val F1: Sq64 = 5;  val G1: Sq64 = 6;  val H1: Sq64 = 7
  val A2: Sq64 = 8;  val B2: Sq64 = 9;  val C2: Sq64 = 10; val D2: Sq64 = 11
  val E2: Sq64 = 12; val F2: Sq64 = 13; val G2: Sq64 = 14; val H2: Sq64 = 15
  val A3: Sq64 = 16; val B3: Sq64 = 17; val C3: Sq64 = 18; val D3: Sq64 = 19
  val E3: Sq64 = 20; val F3: Sq64 = 21; val G3: Sq64 = 22; val H3: Sq64 = 23
  val A4: Sq64 = 24; val B4: Sq64 = 25; val C4: Sq64 = 26; val D4: Sq64 = 27
  val E4: Sq64 = 28; val F4: Sq64 = 29; val G4: Sq64 = 30; val H4: Sq64 = 31
  val A5: Sq64 = 32; val B5: Sq64 = 33; val C5: Sq64 = 34; val D5: Sq64 = 35
  val E5: Sq64 = 36; val F5: Sq64 = 37; val G5: Sq64 = 38; val H5: Sq64 = 39
  val A6: Sq64 = 40; val B6: Sq64 = 41; val C6: Sq64 = 42; val D6: Sq64 = 43
  val E6: Sq64 = 44; val F6: Sq64 = 45; val G6: Sq64 = 46; val H6: Sq64 = 47
  val A7: Sq64 = 48; val B7: Sq64 = 49; val C7: Sq64 = 50; val D7: Sq64 = 51
  val E7: Sq64 = 52; val F7: Sq64 = 53; val G7: Sq64 = 54; val H7: Sq64 = 55
  val A8: Sq64 = 56; val B8: Sq64 = 57; val C8: Sq64 = 58; val D8: Sq64 = 59
  val E8: Sq64 = 60; val F8: Sq64 = 61; val G8: Sq64 = 62; val H8: Sq64 = 63

  // ---------------------------------------------------------------------------
  // Square masks (single bit set)
  // ---------------------------------------------------------------------------
  val SqMask_A1: Long = 1L << 0;  val SqMask_B1: Long = 1L << 1;  val SqMask_C1: Long = 1L << 2;  val SqMask_D1: Long = 1L << 3
  val SqMask_E1: Long = 1L << 4;  val SqMask_F1: Long = 1L << 5;  val SqMask_G1: Long = 1L << 6;  val SqMask_H1: Long = 1L << 7
  val SqMask_A2: Long = 1L << 8;  val SqMask_B2: Long = 1L << 9;  val SqMask_C2: Long = 1L << 10; val SqMask_D2: Long = 1L << 11
  val SqMask_E2: Long = 1L << 12; val SqMask_F2: Long = 1L << 13; val SqMask_G2: Long = 1L << 14; val SqMask_H2: Long = 1L << 15
  val SqMask_A3: Long = 1L << 16; val SqMask_B3: Long = 1L << 17; val SqMask_C3: Long = 1L << 18; val SqMask_D3: Long = 1L << 19
  val SqMask_E3: Long = 1L << 20; val SqMask_F3: Long = 1L << 21; val SqMask_G3: Long = 1L << 22; val SqMask_H3: Long = 1L << 23
  val SqMask_A4: Long = 1L << 24; val SqMask_B4: Long = 1L << 25; val SqMask_C4: Long = 1L << 26; val SqMask_D4: Long = 1L << 27
  val SqMask_E4: Long = 1L << 28; val SqMask_F4: Long = 1L << 29; val SqMask_G4: Long = 1L << 30; val SqMask_H4: Long = 1L << 31
  val SqMask_A5: Long = 1L << 32; val SqMask_B5: Long = 1L << 33; val SqMask_C5: Long = 1L << 34; val SqMask_D5: Long = 1L << 35
  val SqMask_E5: Long = 1L << 36; val SqMask_F5: Long = 1L << 37; val SqMask_G5: Long = 1L << 38; val SqMask_H5: Long = 1L << 39
  val SqMask_A6: Long = 1L << 40; val SqMask_B6: Long = 1L << 41; val SqMask_C6: Long = 1L << 42; val SqMask_D6: Long = 1L << 43
  val SqMask_E6: Long = 1L << 44; val SqMask_F6: Long = 1L << 45; val SqMask_G6: Long = 1L << 46; val SqMask_H6: Long = 1L << 47
  val SqMask_A7: Long = 1L << 48; val SqMask_B7: Long = 1L << 49; val SqMask_C7: Long = 1L << 50; val SqMask_D7: Long = 1L << 51
  val SqMask_E7: Long = 1L << 52; val SqMask_F7: Long = 1L << 53; val SqMask_G7: Long = 1L << 54; val SqMask_H7: Long = 1L << 55
  val SqMask_A8: Long = 1L << 56; val SqMask_B8: Long = 1L << 57; val SqMask_C8: Long = 1L << 58; val SqMask_D8: Long = 1L << 59
  val SqMask_E8: Long = 1L << 60; val SqMask_F8: Long = 1L << 61; val SqMask_G8: Long = 1L << 62; val SqMask_H8: Long = 1L << 63

  // ---------------------------------------------------------------------------
  // File masks (all squares on a file)
  // ---------------------------------------------------------------------------
  val FileA: Long = 0x0101010101010101L
  val FileB: Long = 0x0202020202020202L
  val FileC: Long = 0x0404040404040404L
  val FileD: Long = 0x0808080808080808L
  val FileE: Long = 0x1010101010101010L
  val FileF: Long = 0x2020202020202020L
  val FileG: Long = 0x4040404040404040L
  val FileH: Long = 0x8080808080808080L

  // ---------------------------------------------------------------------------
  // Rank masks (all squares on a rank)
  // ---------------------------------------------------------------------------
  val Rank1: Long = 0x00000000000000FFL
  val Rank2: Long = 0x000000000000FF00L
  val Rank3: Long = 0x0000000000FF0000L
  val Rank4: Long = 0x00000000FF000000L
  val Rank5: Long = 0x000000FF00000000L
  val Rank6: Long = 0x0000FF0000000000L
  val Rank7: Long = 0x00FF000000000000L
  val Rank8: Long = 0xFF00000000000000L

  // ---------------------------------------------------------------------------
  // Not-file masks (for pawn attack shifts to prevent wrapping)
  // ---------------------------------------------------------------------------
  val NotFileA: Long = ~FileA
  val NotFileH: Long = ~FileH
  val NotFileAB: Long = ~(FileA | FileB)
  val NotFileGH: Long = ~(FileG | FileH)
