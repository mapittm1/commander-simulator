"""Fixed isolated entry point for companion construction-rule analysis."""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

# Fixed local checkout bootstrap; never imports a module named by a decklist.
# pylint: disable=wrong-import-position
from commander.engine.companion_worker import main

if __name__ == "__main__":
    main()
