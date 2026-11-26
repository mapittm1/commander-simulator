"""Resource limits must reject excess input without changing committed catalog/cache data."""

import gzip
import io
import json
import tempfile
import unittest
from contextlib import ExitStack
from dataclasses import replace
from pathlib import Path
from unittest.mock import patch

from commander.catalog.bulk_limits import DEFAULT_LIMITS, BulkLimits
from commander.catalog.database import connect, store_card
from commander.catalog.scryfall import (
    download_bulk,
    import_bulk,
    iter_cards,
    iter_json_lines,
)

# Test method names describe their behavior.
# pylint: disable=missing-function-docstring


class ChunkedText(io.StringIO):
    """Exercise partial reads without permitting an unbounded read request."""

    def read(self, size=-1):
        if size < 0:
            raise AssertionError("Parser requested an unbounded read")
        return super().read(min(size, 7))


class Response(io.BytesIO):
    """A real binary stream with optional HTTP length and recorded read bounds."""

    def __init__(self, payload, headers=None):
        super().__init__(payload)
        self.headers = headers or {}
        self.requests = []

    def read1(self, size=-1):
        if size < 0:
            raise AssertionError("Downloader requested an unbounded read")
        self.requests.append(size)
        return super().read1(size)


class BulkParsingTests(unittest.TestCase):
    """Handle boundaries, Unicode and escaped structural characters before storage."""

    def test_array_record_exact_limit_and_overflow(self):
        limits = replace(DEFAULT_LIMITS, record_characters=2)
        self.assertEqual(
            list(iter_cards(io.StringIO("[{},{}]"), limits=limits)), [{}, {}]
        )
        with self.assertRaisesRegex(ValueError, "character limit"):
            list(iter_cards(io.StringIO('[{"x":0}]'), limits=limits))

    def test_jsonl_exact_limit_crlf_and_overflow(self):
        limits = replace(DEFAULT_LIMITS, record_characters=2)
        self.assertEqual(
            list(iter_json_lines(io.StringIO("{}\r\n{}"), limits=limits)), [{}, {}]
        )
        with self.assertRaisesRegex(ValueError, "character limit"):
            list(iter_json_lines(io.StringIO('{"x":0}\n'), limits=limits))

    def test_unterminated_record_cannot_grow_without_bound(self):
        limits = replace(DEFAULT_LIMITS, record_characters=32)
        stream = io.StringIO('[{"text":"' + "x" * 10000)
        with self.assertRaisesRegex(ValueError, "character limit"):
            list(iter_cards(stream, limits=limits))
        # The opening export bracket is separate from the card and overflow probe.
        self.assertLessEqual(stream.tell(), 1 + limits.record_characters + 1)

    def test_depth_is_checked_in_arrays_and_lines(self):
        for depth in (64, 65):
            record = '{"nested":' * depth + "0" + "}" * depth
            for parse, text in (
                (iter_cards, "[" + record + "]"),
                (iter_json_lines, record),
            ):
                with self.subTest(depth=depth, parser=parse.__name__):
                    if depth == 64:
                        self.assertEqual(len(list(parse(io.StringIO(text)))), 1)
                    else:
                        with self.assertRaisesRegex(ValueError, "nesting"):
                            list(parse(io.StringIO(text)))

    def test_escaped_quotes_and_braces_across_chunks_are_not_nesting(self):
        card = {"text": 'é\\"' + "[{}]" * 100, "name": "Unicode"}
        text = json.dumps([card], ensure_ascii=False)
        self.assertEqual(list(iter_cards(ChunkedText(text))), [card])

    def test_limits_reject_zero_negative_boolean_and_fractional_values(self):
        for value in (0, -1, True, 1.5):
            with self.subTest(value=value), self.assertRaises(ValueError):
                BulkLimits(record_characters=value)


class BulkImportTests(unittest.TestCase):
    """Enforce limits after real row updates and preserve all existing logical state."""

    def setUp(self):
        resources = ExitStack()
        self.addCleanup(resources.close)
        folder = resources.enter_context(
            tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parent)
        )
        self.root = Path(folder)
        self.db = connect(self.root / "cards.sqlite3")
        self.addCleanup(self.db.close)
        self.card = {"id": "printing", "oracle_id": "oracle", "name": "Original"}
        self.path = self.root / "input.bulk"
        self.path.write_text(json.dumps([self.card]), encoding="utf-8")
        import_bulk(self.db, self.path)
        self.before = tuple(self.db.iterdump())

    def assert_preserved(self):
        self.assertEqual(tuple(self.db.iterdump()), self.before)
        self.assertFalse(self.db.in_transaction)

    def test_record_overflow_rolls_back_prior_update_and_freshness(self):
        changed = {**self.card, "name": "Changed"}
        large = {**self.card, "id": "second", "oracle_text": "x" * 500}
        limits = replace(DEFAULT_LIMITS, record_characters=100)
        for text in (
            json.dumps([changed, large]),
            json.dumps(changed) + "\n" + json.dumps(large),
        ):
            with self.subTest(format=text[:1]):
                self.path.write_text(text, encoding="utf-8")
                with self.assertRaisesRegex(ValueError, "character limit"):
                    import_bulk(
                        self.db, self.path, "oracle_cards", "new-time", limits=limits
                    )
                self.assert_preserved()

    def test_record_count_overflow_rolls_back_rows_and_receipt(self):
        cards = [{**self.card, "name": "Changed"}, {**self.card, "id": "second"}]
        self.path.write_text(json.dumps(cards), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "record limit"):
            import_bulk(self.db, self.path, limits=replace(DEFAULT_LIMITS, records=1))
        self.assert_preserved()

    def test_compressed_expansion_is_bounded_before_parsing(self):
        payload = json.dumps([{**self.card, "oracle_text": "x" * 10000}]).encode()
        self.path.write_bytes(gzip.compress(payload))
        with self.assertRaisesRegex(ValueError, "byte limit"):
            import_bulk(
                self.db, self.path, limits=replace(DEFAULT_LIMITS, expanded_bytes=128)
            )
        self.assert_preserved()

    def test_input_file_limit_preserves_existing_data(self):
        with self.assertRaisesRegex(ValueError, "input byte limit"):
            import_bulk(
                self.db, self.path, limits=replace(DEFAULT_LIMITS, download_bytes=1)
            )
        self.assert_preserved()

    def test_exact_input_and_expansion_limits_accept_unicode_bom_and_gzip(self):
        payload = ("\ufeff" + json.dumps(self.card, ensure_ascii=False) + "\n").encode(
            "utf-8"
        )
        for compressed in (False, True):
            with self.subTest(compressed=compressed):
                encoded = gzip.compress(payload) if compressed else payload
                self.path.write_bytes(encoded)
                limits = replace(
                    DEFAULT_LIMITS,
                    download_bytes=len(encoded),
                    expanded_bytes=len(payload),
                )
                self.assertEqual(import_bulk(self.db, self.path, limits=limits), 1)

    def test_long_first_jsonl_record_survives_format_detection(self):
        card = {**self.card, "oracle_text": "x" * 100000}
        self.path.write_text(json.dumps(card) + "\n", encoding="utf-8")
        self.assertEqual(import_bulk(self.db, self.path), 1)
        self.assertEqual(
            self.db.execute("SELECT oracle_text FROM cards").fetchone()[0],
            card["oracle_text"],
        )

    def test_truncated_gzip_rolls_back_import(self):
        self.path.write_bytes(gzip.compress(json.dumps([self.card]).encode())[:-6])
        with self.assertRaises(ValueError):
            import_bulk(self.db, self.path)
        self.assert_preserved()

    def test_import_deadline_is_shared_across_records_and_storage(self):
        clock = [0]
        self.path.write_text(
            json.dumps([{**self.card, "name": "Changed"}]), encoding="utf-8"
        )

        def slow_store(*args, **kwargs):
            store_card(*args, **kwargs)
            clock[0] = 2

        with (
            patch(
                "commander.catalog.bulk_limits.monotonic", side_effect=lambda: clock[0]
            ),
            patch("commander.catalog.scryfall.store_card", side_effect=slow_store),
            self.assertRaisesRegex(ValueError, "time limit"),
        ):
            import_bulk(
                self.db, self.path, limits=replace(DEFAULT_LIMITS, import_seconds=1)
            )
        self.assert_preserved()

    def test_active_caller_transaction_is_not_committed_or_rolled_back(self):
        self.db.execute("INSERT INTO imports(source,records) VALUES ('pending',1)")
        with self.assertRaisesRegex(ValueError, "active transaction"):
            import_bulk(self.db, self.path)
        self.assertTrue(self.db.in_transaction)
        self.assertEqual(
            self.db.execute(
                "SELECT source FROM imports ORDER BY id DESC LIMIT 1"
            ).fetchone()[0],
            "pending",
        )
        self.db.rollback()
        self.assert_preserved()


class BulkDownloadTests(unittest.TestCase):
    """Exercise actual transfer boundaries without network access or provider trust assumptions."""

    def setUp(self):
        resources = ExitStack()
        self.addCleanup(resources.close)
        folder = resources.enter_context(
            tempfile.TemporaryDirectory(dir=Path(__file__).resolve().parent)
        )
        self.root = Path(folder)
        self.path = self.root / "oracle.bulk"
        self.path.write_bytes(b"previous cache")
        self.metadata = {
            "data": [
                {
                    "type": "oracle_cards",
                    "updated_at": "2026-11-26T00:00:00+00:00",
                    "download_uri": "https://data.scryfall.io/cards.json",
                }
            ]
        }

    def download(self, payload, *, limits=DEFAULT_LIMITS, headers=None, metadata=None):
        responses = [
            Response(json.dumps(metadata or self.metadata).encode()),
            Response(payload, headers),
        ]
        with patch("commander.catalog.scryfall.open_scryfall", side_effect=responses):
            result = download_bulk(self.path, limits=limits)
        return result, responses

    def assert_preserved(self):
        self.assertEqual(self.path.read_bytes(), b"previous cache")
        self.assertEqual(list(self.root.glob("*.partial")), [])

    def test_success_at_exact_byte_limit_replaces_cache_atomically(self):
        result, responses = self.download(
            b"[]",
            limits=replace(DEFAULT_LIMITS, download_bytes=2),
            headers={"Content-Length": "2"},
        )
        self.assertEqual(self.path.read_bytes(), b"[]")
        self.assertEqual(result, self.metadata["data"][0])
        self.assertTrue(
            all(size > 0 for response in responses for size in response.requests)
        )
        self.assertEqual(list(self.root.glob("*.partial")), [])

    def test_actual_transfer_is_bounded_without_content_length(self):
        with self.assertRaisesRegex(ValueError, "byte limit"):
            self.download(b"12345", limits=replace(DEFAULT_LIMITS, download_bytes=4))
        self.assert_preserved()

    def test_declared_excess_is_rejected_before_writing(self):
        with self.assertRaisesRegex(ValueError, "byte limit"):
            self.download(
                b"x",
                limits=replace(DEFAULT_LIMITS, download_bytes=4),
                headers={"Content-Length": "5"},
            )
        self.assert_preserved()

    def test_truncated_response_cannot_replace_cache(self):
        with self.assertRaisesRegex(ValueError, "Incomplete"):
            self.download(b"abc", headers={"Content-Length": "4"})
        self.assert_preserved()

    def test_empty_response_cannot_replace_cache(self):
        with self.assertRaisesRegex(ValueError, "empty"):
            self.download(b"")
        self.assert_preserved()

    def test_metadata_size_is_bounded_before_json_parsing(self):
        with self.assertRaisesRegex(ValueError, "byte limit"):
            self.download(b"[]", limits=replace(DEFAULT_LIMITS, metadata_bytes=10))
        self.assert_preserved()

    def test_invalid_length_headers_are_rejected(self):
        for value in ("-1", "+1", "1,1", "n/a", "1" * 100):
            with self.subTest(value=value), self.assertRaises(ValueError):
                self.download(b"x", headers={"Content-Length": value})
            self.assert_preserved()

    def test_redirect_credentials_port_and_host_checks_are_not_bypassed(self):
        for url in (
            "http://data.scryfall.io/cards",
            "https://user@data.scryfall.io/cards",
            "https://data.scryfall.io:444/cards",
            "https://api.scryfall.com/cards",
        ):
            metadata = {"data": [{**self.metadata["data"][0], "download_uri": url}]}
            with self.subTest(url=url), self.assertRaises(ValueError):
                self.download(b"[]", metadata=metadata)
            self.assert_preserved()

    def test_duplicate_export_metadata_is_rejected(self):
        metadata = {"data": self.metadata["data"] * 2}
        with self.assertRaisesRegex(ValueError, "exactly one"):
            self.download(b"[]", metadata=metadata)
        self.assert_preserved()

    def test_unknown_kind_never_opens_network(self):
        with patch("commander.catalog.scryfall.open_scryfall") as request:
            with self.assertRaisesRegex(ValueError, "Unsupported"):
                download_bulk(self.path, "untrusted")
        request.assert_not_called()
        self.assert_preserved()

    def test_download_deadline_does_not_reset_after_metadata(self):
        clock = [0]

        def response(url, **_kwargs):
            if url == "https://api.scryfall.com/bulk-data":
                stream = Response(json.dumps(self.metadata).encode())
                original_close = stream.close

                def close():
                    clock[0] = 2
                    original_close()

                stream.close = close
                return stream
            return Response(b"[]")

        with (
            patch(
                "commander.catalog.bulk_limits.monotonic", side_effect=lambda: clock[0]
            ),
            patch("commander.catalog.scryfall.open_scryfall", side_effect=response),
            self.assertRaisesRegex(ValueError, "time limit"),
        ):
            download_bulk(self.path, limits=replace(DEFAULT_LIMITS, download_seconds=1))
        self.assert_preserved()
