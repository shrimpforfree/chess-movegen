"""
perft_positions.py - Known perft test positions with expected node counts.
"""

# List of test positions with expected perft results
PERFT_POSITIONS = [
    {
        'name': 'startpos',
        'fen': 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1',
        'expected': {
            # this is from the internet
            1: 20,
            2: 400,
            3: 8902,
            4: 197281,
            5: 4865609,
            6: 119060324,
        }
    },
]


def get_position(name: str):
    for pos in PERFT_POSITIONS:
        if pos['name'] == name:
            return pos
    return None


def get_all_position_names():
    return [pos['name'] for pos in PERFT_POSITIONS]
