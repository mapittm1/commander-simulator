"""Fixed isolated entry point for native functional-printing preflight."""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

# Fixed local checkout bootstrap; deck uploads cannot choose modules.
# pylint: disable=wrong-import-position
from commander.engine.printing_worker import main

if __name__ == "__main__":
    main()
