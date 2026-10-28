"""Start only this checkout's fixed native catalog worker under isolated Python."""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

# pylint: disable-next=wrong-import-position
from commander.engine.catalog_probe_worker import main

if __name__ == "__main__":
    main()
