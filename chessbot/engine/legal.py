"""
legal.py - Filter pseudo-legal moves to legal moves.
"""

from typing import List
from .board import Board, Move
from .attacks import is_in_check
from .movegen import generate_pseudo_legal_moves


def is_legal_move(board: Board, move: Move) -> bool:
    """
    Check if a move is legal by making it on a copy and checking for check.
    """
    moving_color = board.side_to_move

    # Make the move on a copy
    board_copy = board.copy()
    board_copy.make_move(move)

    return not is_in_check(board_copy, moving_color)


def generate_legal_moves(board: Board) -> List[Move]:
    """
    Generate all legal moves for the current position.

    This filters pseudo-legal moves to only those that don't leave
    the king in check.
    """
    pseudo_legal = generate_pseudo_legal_moves(board)
    legal = []

    for move in pseudo_legal:
        if is_legal_move(board, move):
            legal.append(move)

    return legal
