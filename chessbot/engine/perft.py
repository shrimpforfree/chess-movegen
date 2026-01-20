"""
perft.py - Performance test (perft) for move generation correctness.

Perft counts the number of leaf nodes at a given depth to verify
move generation is correct.
"""

from typing import Dict
from .board import Board
from .legal import generate_legal_moves


def perft(board: Board, depth: int) -> int:
    """
    Count leaf nodes at a given depth.
    """
    if depth == 0:
        return 1

    moves = generate_legal_moves(board)
    nodes = 0

    for move in moves:
        # Make move on a copy
        board_copy = board.copy()
        board_copy.make_move(move)

        # Recursively count nodes
        nodes += perft(board_copy, depth - 1)

    return nodes


def perft_divide(board: Board, depth: int) -> Dict[str, int]:
    """
    Perft with divide - shows node count for each root move.

    Returns:
        Dictionary mapping move strings to node counts
    """
    if depth == 0:
        return {}

    moves = generate_legal_moves(board)
    results = {}
    total = 0

    for move in moves:
        # Make move on a copy
        board_copy = board.copy()
        board_copy.make_move(move)

        # Count nodes for this move
        count = perft(board_copy, depth - 1)
        results[str(move)] = count
        total += count

    # Add total
    results['TOTAL'] = total

    return results
