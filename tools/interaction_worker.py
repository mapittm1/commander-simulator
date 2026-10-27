"""Bootstrap only this checkout's fixed interaction worker under isolated Python."""

import sys
from pathlib import Path

# -I excludes the script's checkout from imports; never add a user-selected directory.
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

# pylint: disable-next=wrong-import-position
from commander.engine.interactions.worker import main

if __name__ == "__main__":
    main()
