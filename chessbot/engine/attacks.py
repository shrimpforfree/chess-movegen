"""
attacks.py - Square attack detection.

NOTE: kept seperated from legal, so can be used by eval or search in the future
"""

from .board import Board, OFFBOARD, EMPTY, WHITE, BLACK

# Direction offsets
# Row increases downward (rank 8 is row 2, rank 1 is row 9)
DIR_N = -10   # North (toward rank 8)
DIR_S = 10    # South (toward rank 1)
DIR_E = 1     # East (toward h-file)
DIR_W = -1    # West (toward a-file)
DIR_NE = -9   # Northeast
DIR_NW = -11  # Northwest
DIR_SE = 11   # Southeast
DIR_SW = 9    # Southwest

KNIGHT_OFFSETS = [-21, -19, -12, -8, 8, 12, 19, 21] # specificly for knight
KING_OFFSETS = [DIR_N, DIR_S, DIR_E, DIR_W, DIR_NE, DIR_NW, DIR_SE, DIR_SW]
BISHOP_DIRS = [DIR_NE, DIR_NW, DIR_SE, DIR_SW]
ROOK_DIRS = [DIR_N, DIR_S, DIR_E, DIR_W]
QUEEN_DIRS = [DIR_N, DIR_S, DIR_E, DIR_W, DIR_NE, DIR_NW, DIR_SE, DIR_SW]

def is_square_attacked(board: Board, square: int, by_color: str) -> bool:
    """
    Check if a square is attacked "by a given color".
    NOTE: it is checking from the attacked piece's perspective, not from the attacking pieces perspectives

    it scan if the attack piece can be at the position to attack it
    """

    # Non-sliding pieces: check specific offsets
    offset_piece_pairs: list[tuple[str, list]] = []

    pawn_offsets = [DIR_SE, DIR_SW] if by_color == WHITE else [DIR_NE, DIR_NW]
    pawn = 'P' if by_color == WHITE else 'p'
    offset_piece_pairs.append((pawn, pawn_offsets))

    knight = 'N' if by_color == WHITE else 'n'
    offset_piece_pairs.append((knight, KNIGHT_OFFSETS))

    king = 'K' if by_color == WHITE else 'k'
    offset_piece_pairs.append((king, KING_OFFSETS))

    # Check all non-sliding pieces
    for piece, offsets in offset_piece_pairs:
        for offset in offsets:
            test_sq = square + offset
            if board.squares[test_sq] == piece:
                return True

    # Check for sliding piece attacks (bishop, rook, queen)
    bishop_piece = 'B' if by_color == WHITE else 'b'
    queen_piece = 'Q' if by_color == WHITE else 'q'

    # Bishop and queen diagonal attacks
    for direction in BISHOP_DIRS:
        test_sq = square + direction
        while board.squares[test_sq] != OFFBOARD:
            piece = board.squares[test_sq]
            if piece != EMPTY:
                if piece == bishop_piece or piece == queen_piece:
                    return True
                break  # Blocked by another piece
            test_sq += direction

    # Rook and queen orthogonal attacks
    rook_piece = 'R' if by_color == WHITE else 'r'

    for direction in ROOK_DIRS:
        test_sq = square + direction
        while board.squares[test_sq] != OFFBOARD:
            piece = board.squares[test_sq]
            if piece != EMPTY:
                if piece == rook_piece or piece == queen_piece:
                    return True
                break
            test_sq += direction

    return False


def is_in_check(board: Board, color: str) -> bool:
    """
    Check if the given color's king is in check.
    """
    king_sq = board.white_king_sq if color == WHITE else board.black_king_sq

    if king_sq is None:
        return False  # No king on board (shouldn't happen in valid positions)

    opponent_color = BLACK if color == WHITE else WHITE
    return is_square_attacked(board, king_sq, opponent_color)
