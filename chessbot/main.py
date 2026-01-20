"""
main.py - CLI interface for chess engine.
"""

import argparse
import time
from .engine.board import Board, STARTPOS_FEN
from .engine.perft import perft, perft_divide
from .engine.perft_positions import get_position, get_all_position_names


def cmd_perft(args):
    """Run perft test on a position."""
    # Get position
    if args.name:
        pos = get_position(args.name)
        if not pos:
            print(f"Unknown position: {args.name}")
            print(f"Available positions: {', '.join(get_all_position_names())}")
            return
        fen = pos['fen']
        print(f"Position: {args.name}")
    elif args.fen:
        if args.fen.lower() == 'startpos':
            fen = STARTPOS_FEN
        else:
            fen = args.fen
    else:
        print("Error: Must specify one of: --name or --fen")
        return

    # Load position
    board = Board()
    board.load_fen(fen)

    print(f"FEN: {fen}")
    print(board)
    print()

    # Run perft
    start_time = time.time()

    if args.divide:
        print(f"Running perft divide at depth {args.depth}...")
        print()

        results = perft_divide(board, args.depth)
        total_nodes = results.pop('TOTAL', 0)

        # Print per-move breakdown
        for move in sorted(results.keys()):
            print(f"{move}: {results[move]}")
        print()
    else:
        print(f"Running perft at depth {args.depth}...")
        total_nodes = perft(board, args.depth)

    elapsed = time.time() - start_time

    # Print results
    print(f"Nodes: {total_nodes}")
    print(f"Time: {elapsed:.3f}s")

    # Check against expected if available
    if args.name:
        pos = get_position(args.name)
        if pos and args.depth in pos['expected']:
            expected = pos['expected'][args.depth]
            status = "✓ PASS" if total_nodes == expected else "✗ FAIL"
            print(f"\n{status}: expected {expected}, got {total_nodes}")


def main():
    parser = argparse.ArgumentParser(description='Chess Engine CLI')
    subparsers = parser.add_subparsers(dest='command', help='Command to run')

    # Perft command
    perft_parser = subparsers.add_parser('perft', help='Run perft test')
    perft_parser.add_argument('--name', type=str, help='Test position name in my db (e.g., startpos)')
    perft_parser.add_argument('--fen', type=str, help='FEN string (or "startpos")')
    perft_parser.add_argument('--depth', type=int, default=1, help='Search depth (default: 1)')
    perft_parser.add_argument('--divide', action='store_true', help='Show per-move breakdown')

    args = parser.parse_args()

    if args.command == 'perft':
        cmd_perft(args)
    else:
        parser.print_help()


if __name__ == '__main__':
    main()
