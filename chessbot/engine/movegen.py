"""
movegen.py - Pseudo-legal move generation.
"""

from typing import List
from .board import Board, Move, EMPTY, OFFBOARD, WHITE, BLACK
# movement direction from attacks
from .attacks import DIR_N, DIR_S, DIR_E, DIR_W, DIR_NE, DIR_NW, DIR_SE, DIR_SW
from .attacks import KNIGHT_OFFSETS, KING_OFFSETS, BISHOP_DIRS, ROOK_DIRS, QUEEN_DIRS


def is_enemy_piece(board: Board, square: int, friendly_color: str) -> bool:
    piece = board.squares[square]
    if piece == EMPTY or piece == OFFBOARD:
        return False

    piece_color = WHITE if piece.isupper() else BLACK
    return piece_color != friendly_color


def is_friendly_piece(board: Board, square: int, friendly_color: str) -> bool:
    piece = board.squares[square]
    if piece == EMPTY or piece == OFFBOARD:
        return False

    piece_color = WHITE if piece.isupper() else BLACK
    return piece_color == friendly_color


def generate_pawn_moves(board: Board, from_sq: int, moves: List[Move]):
    """
    Generate pseudo-legal pawn moves.
    """
    piece = board.squares[from_sq]
    is_white = piece.isupper()

    if is_white:
        push_dir = DIR_N  # White pawns move toward rank 8 (lower row numbers)
        capture_dirs = [DIR_NE, DIR_NW]
        friendly_color = WHITE
        starting_row = 8  # White pawns start on rank 2, which is row 8
    else:
        push_dir = DIR_S  # Black pawns move toward rank 1 (higher row numbers)
        capture_dirs = [DIR_SE, DIR_SW]
        friendly_color = BLACK
        starting_row = 3  # Black pawns start on rank 7, which is row 3

    # Single push
    to_sq = from_sq + push_dir
    if board.squares[to_sq] == EMPTY:
        moves.append(Move(from_sq, to_sq))

        # Double push (only if pawn is on starting rank and path is clear)
        from_row = from_sq // 10
        if from_row == starting_row:
            double_push_sq = to_sq + push_dir
            if board.squares[double_push_sq] == EMPTY:
                moves.append(Move(from_sq, double_push_sq))

    # Captures
    for capture_dir in capture_dirs:
        to_sq = from_sq + capture_dir
        if is_enemy_piece(board, to_sq, friendly_color):
            moves.append(Move(from_sq, to_sq))

    # En passant capture
    if board.ep_square is not None:
        for capture_dir in capture_dirs:
            to_sq = from_sq + capture_dir
            if to_sq == board.ep_square:
                moves.append(Move(from_sq, to_sq, flags=Move.FLAG_EP_CAPTURE))


def generate_knight_moves(board: Board, from_sq: int, moves: List[Move]):
    piece = board.squares[from_sq]
    friendly_color = WHITE if piece.isupper() else BLACK

    for offset in KNIGHT_OFFSETS:
        to_sq = from_sq + offset
        target = board.squares[to_sq]

        if target == OFFBOARD:
            continue

        if target == EMPTY or is_enemy_piece(board, to_sq, friendly_color):
            moves.append(Move(from_sq, to_sq))


def generate_sliding_moves(board: Board, from_sq: int, directions: List[int], moves: List[Move]):
    """For bishop, rook, queen"""
    piece = board.squares[from_sq]
    friendly_color = WHITE if piece.isupper() else BLACK

    for direction in directions:
        to_sq = from_sq + direction

        while board.squares[to_sq] != OFFBOARD:
            target = board.squares[to_sq]

            if target == EMPTY:
                moves.append(Move(from_sq, to_sq))
                to_sq += direction
            elif is_enemy_piece(board, to_sq, friendly_color):
                moves.append(Move(from_sq, to_sq))
                break  # Can't continue past a capture
            else:
                break  # Blocked by friendly piece


def generate_king_moves(board: Board, from_sq: int, moves: List[Move]):
    """
    Generate pseudo-legal king moves.
    """
    piece = board.squares[from_sq]
    friendly_color = WHITE if piece.isupper() else BLACK

    # Normal king moves
    for offset in KING_OFFSETS:
        to_sq = from_sq + offset
        target = board.squares[to_sq]

        if target == OFFBOARD:
            continue

        if target == EMPTY or is_enemy_piece(board, to_sq, friendly_color):
            moves.append(Move(from_sq, to_sq))

    # Castling
    from .attacks import is_square_attacked

    if friendly_color == WHITE:
        if 'K' in board.castling_rights:
            e1 = 95
            f1 = 96
            g1 = 97
            if board.squares[f1] == EMPTY and board.squares[g1] == EMPTY:
                # King not in check, doesn't pass through or land on attacked square
                if not is_square_attacked(board, e1, BLACK):
                    if not is_square_attacked(board, f1, BLACK):
                        if not is_square_attacked(board, g1, BLACK):
                            moves.append(Move(e1, g1, flags=Move.FLAG_CASTLING))

        if 'Q' in board.castling_rights:
            e1 = 95
            d1 = 94
            c1 = 93
            b1 = 92
            if board.squares[d1] == EMPTY and board.squares[c1] == EMPTY and board.squares[b1] == EMPTY:
                # King not in check, doesn't pass through or land on attacked square
                if not is_square_attacked(board, e1, BLACK):
                    if not is_square_attacked(board, d1, BLACK):
                        if not is_square_attacked(board, c1, BLACK):
                            moves.append(Move(e1, c1, flags=Move.FLAG_CASTLING))

    else:  # BLACK
        if 'k' in board.castling_rights:
            e8 = 25
            f8 = 26
            g8 = 27
            if board.squares[f8] == EMPTY and board.squares[g8] == EMPTY:
                if not is_square_attacked(board, e8, WHITE):
                    if not is_square_attacked(board, f8, WHITE):
                        if not is_square_attacked(board, g8, WHITE):
                            moves.append(Move(e8, g8, flags=Move.FLAG_CASTLING))

        if 'q' in board.castling_rights:
            e8 = 25
            d8 = 24
            c8 = 23
            b8 = 22
            if board.squares[d8] == EMPTY and board.squares[c8] == EMPTY and board.squares[b8] == EMPTY:
                if not is_square_attacked(board, e8, WHITE):
                    if not is_square_attacked(board, d8, WHITE):
                        if not is_square_attacked(board, c8, WHITE):
                            moves.append(Move(e8, c8, flags=Move.FLAG_CASTLING))
        


def generate_pseudo_legal_moves(board: Board) -> List[Move]:
    """
    Generate all pseudo-legal moves for the side to move. (disregard king checking rule)
    """
    moves = []
    friendly_color = board.side_to_move

    for sq in range(120):
        piece = board.squares[sq]

        if piece == EMPTY or piece == OFFBOARD:
            continue

        piece_color = WHITE if piece.isupper() else BLACK
        if piece_color != friendly_color:
            continue

        piece_type = piece.lower()

        if piece_type == 'p':
            generate_pawn_moves(board, sq, moves)
        elif piece_type == 'n':
            generate_knight_moves(board, sq, moves)
        elif piece_type == 'b':
            generate_sliding_moves(board, sq, BISHOP_DIRS, moves)
        elif piece_type == 'r':
            generate_sliding_moves(board, sq, ROOK_DIRS, moves)
        elif piece_type == 'q':
            generate_sliding_moves(board, sq, QUEEN_DIRS, moves)
        elif piece_type == 'k':
            generate_king_moves(board, sq, moves)

    return moves
