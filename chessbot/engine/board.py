"""
board.py - 120-square mailbox (10×12) board representation with FEN parsing.
use this for easy off board checking

Board layout (10 columns × 12 rows = 120 squares):
  Columns: 0-9
  Rows: 0-11

  Playable squares (8×8 chess board) are embedded in the middle:
  - Columns 1-8 = files a-h
  - Rows 2-9 = ranks 8-1 (rank 8 at top, rank 1 at bottom)

  Visual layout:
       Col:  0   1   2   3   4   5   6   7   8   9
    Row  0:  ?   ?   ?   ?   ?   ?   ?   ?   ?   ?
    Row  1:  ?   ?   ?   ?   ?   ?   ?   ?   ?   ?
    Row  2:  ?  a8  b8  c8  d8  e8  f8  g8  h8   ?  <- Rank 8 black
    Row  3:  ?  a7  b7  c7  d7  e7  f7  g7  h7   ?  <- Rank 7 black
    Row  4:  ?  a6  b6  c6  d6  e6  f6  g6  h6   ?  <- Rank 6
    Row  5:  ?  a5  b5  c5  d5  e5  f5  g5  h5   ?  <- Rank 5
    Row  6:  ?  a4  b4  c4  d4  e4  f4  g4  h4   ?  <- Rank 4
    Row  7:  ?  a3  b3  c3  d3  e3  f3  g3  h3   ?  <- Rank 3
    Row  8:  ?  a2  b2  c2  d2  e2  f2  g2  h2   ?  <- Rank 2 white
    Row  9:  ?  a1  b1  c1  d1  e1  f1  g1  h1   ?  <- Rank 1 white
    Row 10:  ?   ?   ?   ?   ?   ?   ?   ?   ?   ?
    Row 11:  ?   ?   ?   ?   ?   ?   ?   ?   ?   ?

  Square index = Row * 10 + Col
  Example mappings:
    Square 21 = Row 2, Col 1 = a8
    Square 28 = Row 2, Col 8 = h8
    Square 91 = Row 9, Col 1 = a1
    Square 98 = Row 9, Col 8 = h1

  Surrounding squares are offboard squares marked '?'
"""

from dataclasses import dataclass
from typing import Optional, Tuple
import copy


# Piece encoding: chars
EMPTY = '.'
OFFBOARD = '?'
PIECES = 'PNBRQKpnbrqk'

# Colors
WHITE = 'w'
BLACK = 'b'

def square_to_coords(sq: int) -> Tuple[int, int]:
    """Convert mailbox square index to (row, col)."""
    return (sq // 10, sq % 10)

def coords_to_square(row: int, col: int) -> int:
    """Convert (row, col) to mailbox square index."""
    return row * 10 + col


def algebraic_to_square(alg: str) -> int:
    """Convert algebraic notation (e.g. 'e4') to mailbox square.

    File: a=1, b=2, ..., h=8 (column offset)
    Rank: 1=9, 2=8, ..., 8=2 (row offset from top)
    """
    file_char = alg[0]
    rank_char = alg[1]

    file_idx = ord(file_char) - ord('a') + 1  # a=1, b=2, ..., h=8
    rank_idx = int(rank_char)  # 1-8

    # Rank 8 is row 2, rank 7 is row 3, ..., rank 1 is row 9
    row = 10 - rank_idx
    col = file_idx

    return coords_to_square(row, col)


def square_to_algebraic(sq: int) -> str:
    """Convert mailbox square to algebraic notation."""
    row, col = square_to_coords(sq)

    # offboard
    if col < 1 or col > 8 or row < 2 or row > 9:
        return "??"

    file_char = chr(ord('a') + col - 1)
    rank = 10 - row  # row 2 -> rank 8, row 9 -> rank 1

    return f"{file_char}{rank}"


@dataclass
class Move:
    """Move representation: (from_sq, to_sq, promo, flags)"""
    from_sq: int
    to_sq: int
    promo: Optional[str] = None  # Promotion piece: 'Q', 'R', 'B', 'N' (uppercase for white, lowercase for black)
    flags: int = 0  # Bit flags for special moves (ep capture, castling, etc.)

    # Flag constants
    FLAG_NONE = 0
    FLAG_EP_CAPTURE = 1
    FLAG_CASTLING = 2

    def __str__(self):
        s = f"{square_to_algebraic(self.from_sq)}{square_to_algebraic(self.to_sq)}"
        if self.promo:
            s += self.promo.lower()
        return s


class Board:
    def __init__(self):
        # Initialize empty board with offboard sentinels
        self.squares = [OFFBOARD] * 120
        for row in range(2, 10):  # ranks 8-1
            for col in range(1, 9):  # files a-h
                sq = coords_to_square(row, col)
                self.squares[sq] = EMPTY

        # Game state
        self.side_to_move = WHITE
        self.castling_rights = ''  # KQkq format
        self.ep_square = None  # En passant target square (or None)
        self.halfmove_clock = 0
        self.fullmove_number = 1

        # this is just to speed up king locate
        self.white_king_sq = None
        self.black_king_sq = None

    def load_fen(self, fen: str):
        """Parse FEN string and set up the board."""
        parts = fen.split()

        if len(parts) < 4:
            raise ValueError(f"Invalid FEN: {fen}")

        piece_placement = parts[0]
        self.side_to_move = parts[1]
        self.castling_rights = parts[2]
        ep_target = parts[3]
        self.halfmove_clock = int(parts[4]) if len(parts) > 4 else 0
        self.fullmove_number = int(parts[5]) if len(parts) > 5 else 1

        # Parse en passant square
        if ep_target == '-':
            self.ep_square = None
        else:
            self.ep_square = algebraic_to_square(ep_target)

        # Parse piece placement
        rank = 8
        file = 1

        for char in piece_placement:
            if char == '/':
                rank -= 1
                file = 1
            # number of space
            elif char.isdigit():
                file += int(char)
            elif char in PIECES:
                row = 10 - rank  # rank 8 -> row 2, rank 1 -> row 9
                sq = coords_to_square(row, file)
                self.squares[sq] = char

                # Track king positions
                if char == 'K':
                    self.white_king_sq = sq
                elif char == 'k':
                    self.black_king_sq = sq

                file += 1

    def copy(self):
        new_board = Board()
        new_board.squares = self.squares.copy()
        new_board.side_to_move = self.side_to_move
        new_board.castling_rights = self.castling_rights
        new_board.ep_square = self.ep_square
        new_board.halfmove_clock = self.halfmove_clock
        new_board.fullmove_number = self.fullmove_number
        new_board.white_king_sq = self.white_king_sq
        new_board.black_king_sq = self.black_king_sq
        return new_board

    def make_move(self, move: Move):
        """
        Apply a move to the board (modifies in place). assume the move is legal!
        """
        from_sq = move.from_sq
        to_sq = move.to_sq
        piece = self.squares[from_sq]

        # Move the piece
        self.squares[to_sq] = piece
        self.squares[from_sq] = EMPTY

        # Update king positions
        if piece == 'K':
            self.white_king_sq = to_sq
        elif piece == 'k':
            self.black_king_sq = to_sq

        # Special Moves
        if move.promo:
            if self.side_to_move == WHITE:
                self.squares[to_sq] = move.promo.upper()
            else:
                self.squares[to_sq] = move.promo.lower()

        if move.flags == Move.FLAG_EP_CAPTURE:
            # Remove pawn
            if self.side_to_move == WHITE:
                captured_sq = to_sq + 10
            else:
                captured_sq = to_sq - 10
            self.squares[captured_sq] = EMPTY

        if move.flags == Move.FLAG_CASTLING:
            # Move rook
            from_row, from_col = square_to_coords(from_sq)
            to_row, to_col = square_to_coords(to_sq)

            if to_col > from_col:  # Kingside
                rook_from = coords_to_square(from_row, 8)
                rook_to = coords_to_square(from_row, 6)
            else:  # Queenside
                rook_from = coords_to_square(from_row, 1)
                rook_to = coords_to_square(from_row, 4)

            self.squares[rook_to] = self.squares[rook_from]
            self.squares[rook_from] = EMPTY

        # Board States
        # Updates en passant square
        self.ep_square = None

        piece_type = piece.lower()
        if piece_type == 'p':
            from_row, from_col = square_to_coords(from_sq)
            to_row, to_col = square_to_coords(to_sq)

            if abs(from_row - to_row) == 2:
                ep_row = (from_row + to_row) // 2
                self.ep_square = coords_to_square(ep_row, from_col)

        # Update castling rights
        # NOTE: I refer to the wiki rule
        # Neither king nor rook moved

        # Remove rights if king or rook moves
        if piece == 'K':
            self.castling_rights = self.castling_rights.replace('K', '').replace('Q', '')
        elif piece == 'k':
            self.castling_rights = self.castling_rights.replace('k', '').replace('q', '')
        elif piece == 'R':
            if from_sq == algebraic_to_square('h1'):
                self.castling_rights = self.castling_rights.replace('K', '')
            elif from_sq == algebraic_to_square('a1'):
                self.castling_rights = self.castling_rights.replace('Q', '')
        elif piece == 'r':
            if from_sq == algebraic_to_square('h8'):
                self.castling_rights = self.castling_rights.replace('k', '')
            elif from_sq == algebraic_to_square('a8'):
                self.castling_rights = self.castling_rights.replace('q', '')

        if to_sq == algebraic_to_square('h1'):
            self.castling_rights = self.castling_rights.replace('K', '')
        elif to_sq == algebraic_to_square('a1'):
            self.castling_rights = self.castling_rights.replace('Q', '')
        elif to_sq == algebraic_to_square('h8'):
            self.castling_rights = self.castling_rights.replace('k', '')
        elif to_sq == algebraic_to_square('a8'):
            self.castling_rights = self.castling_rights.replace('q', '')

        # Switch side
        self.side_to_move = BLACK if self.side_to_move == WHITE else WHITE

        # Counters
        if self.side_to_move == WHITE:
            self.fullmove_number += 1

    def __str__(self):
        # Board pretty print
        lines = []
        lines.append("  a b c d e f g h")

        for rank in range(8, 0, -1):
            row = 10 - rank
            line = [str(rank)]

            for file in range(1, 9):
                col = file
                sq = coords_to_square(row, col)
                piece = self.squares[sq]
                line.append(piece)

            lines.append(' '.join(line))

        lines.append(f"\nSide to move: {self.side_to_move}")
        lines.append(f"Castling: {self.castling_rights or '-'}")
        lines.append(f"En passant: {square_to_algebraic(self.ep_square) if self.ep_square else '-'}")

        return '\n'.join(lines)


# Standard starting position FEN
STARTPOS_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
