"""CLI JSON must preserve card names across Unicode and legacy output streams."""

import io
import json
import unittest

from json_output import write_json

# Test names express expected behavior.
# pylint: disable=missing-function-docstring


class JsonOutputTests(unittest.TestCase):
    """Representability checks choose native Unicode or lossless JSON escapes."""

    def test_legacy_encoding_round_trips_unrepresentable_names(self):
        value = {"name": "Ratonhnhaké꞉ton", "quantity": 1}
        with io.TextIOWrapper(
            io.BytesIO(), encoding="cp1252", write_through=True
        ) as stream:
            write_json(value, stream)
            rendered = stream.buffer.getvalue().decode("cp1252")
            self.assertIn("\\ua789", rendered)
            self.assertEqual(json.loads(rendered), value)

    def test_utf8_and_in_memory_streams_keep_native_unicode(self):
        value = {"name": "Card ꞉ name"}
        memory = io.StringIO()
        write_json(value, memory)
        self.assertIn("꞉", memory.getvalue())
        with io.TextIOWrapper(
            io.BytesIO(), encoding="utf-8", write_through=True
        ) as stream:
            write_json(value, stream)
            rendered = stream.buffer.getvalue().decode("utf-8")
            self.assertIn("꞉", rendered)
            self.assertEqual(json.loads(rendered), value)
