import unittest
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from server import STORE, clean_answer, parse_scripture_ref


class SourceStoreTest(unittest.TestCase):
    def test_search_catechism_returns_results(self):
        result = STORE.search_catechism("trinity father son holy spirit", limit=5)
        self.assertGreater(len(result["paragraphs"]), 0)

    def test_scripture_ref_parser_handles_numbered_books(self):
        self.assertEqual(parse_scripture_ref("1 Corinthians 3:15"), ("1 Corinthians", 3, 15))

    def test_get_bible_verses_returns_full_text(self):
        result = STORE.get_bible_verses(["Matthew 28:19"])
        self.assertEqual(result["verses"][0]["book"], "Matthew")

    def test_clean_answer_removes_reasoning_tags(self):
        result = clean_answer("<thought>hidden planning</thought>The answer.")
        self.assertEqual(result, "The answer.")

    def test_clean_answer_removes_orphan_reasoning_tags(self):
        result = clean_answer("</thought>Catholics recognize saints because...")
        self.assertEqual(result, "Catholics recognize saints because...")


if __name__ == "__main__":
    unittest.main()
