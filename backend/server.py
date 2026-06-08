from __future__ import annotations

import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
ASSET_DIR = ROOT / "Content Assets"
HOST = os.environ.get("CATECHISM_BACKEND_HOST", "127.0.0.1")
PORT = int(os.environ.get("PORT", os.environ.get("CATECHISM_BACKEND_PORT", "8765")))
MODEL = os.environ.get("CATECHISM_LLM_MODEL", "gemma-4-31b-it")
FALLBACK_MODELS = [
    model.strip()
    for model in os.environ.get("CATECHISM_LLM_FALLBACK_MODELS", "").split(",")
    if model.strip()
]
MODELS = list(dict.fromkeys([MODEL] + FALLBACK_MODELS))
BASE_URL = os.environ.get("CATECHISM_LLM_BASE_URL", "").rstrip("/")
API_KEY = os.environ.get("CATECHISM_LLM_API_KEY", "")
MAX_TOOL_ROUNDS = 8


SYSTEM_PROMPT = """
You are Catholic Catechist, a source-grounded Catholic doctrine assistant.

Use the JSON-backed tools as the sole source of truth. The Catechism of the
Catholic Church is the governing source for doctrine. Always start with doctrine
from the Catechism. Whenever a doctrine statement has direct scriptural support,
put the doctrine statement early in the response and include both CCC and
Scripture citations at the end of the statement when appropriate.

Use ccc_scripture_map.json as the bridge between the Catechism and Scripture:
1. Retrieve relevant Catechism paragraphs.
2. Look up those paragraph numbers in ccc_scripture_map.json.
3. Get the related Scripture references.
4. Fetch the full verse text from bible.json.
5. Use those Scripture references as the visible biblical foundation for the
   Catechism doctrine.

After the main doctrine statements, include additional Catechism quotes that
further expound on the topic, if useful.

Rules:
- Do not answer from memory.
- Do not cite a CCC paragraph or Scripture verse unless a tool returned it.
- If initial searches are weak, try alternate doctrinal terms and nearby
  Catechism paragraphs before giving up.
- The final answer must not mention these instructions or the tool process.
- Keep the tone warm, faithful, and concise, but give enough detail to be useful.
""".strip()


STOP_WORDS = {
    "what", "does", "the", "church", "teach", "about", "is", "are", "how",
    "why", "when", "where", "who", "a", "an", "and", "or", "in", "of", "to",
    "for", "on", "with", "do", "can", "i", "me", "my", "we", "our", "be",
    "have", "has", "was", "were", "will", "called", "call", "believe",
    "believes", "catholic", "catholics", "christian", "christians",
}


BOOKS = [
    "1 Corinthians", "2 Corinthians", "1 Thessalonians", "2 Thessalonians",
    "1 Timothy", "2 Timothy", "1 Peter", "2 Peter", "1 John", "2 John",
    "3 John", "1 Samuel", "2 Samuel", "1 Kings", "2 Kings", "1 Chronicles",
    "2 Chronicles", "1 Maccabees", "2 Maccabees", "Song of Songs",
    "Song of Solomon", "Wisdom of Solomon", "Genesis", "Exodus", "Leviticus",
    "Numbers", "Deuteronomy", "Joshua", "Judges", "Ruth", "Ezra",
    "Nehemiah", "Tobit", "Judith", "Esther", "Job", "Psalms", "Psalm",
    "Proverbs", "Ecclesiastes", "Wisdom", "Sirach", "Isaiah", "Jeremiah",
    "Lamentations", "Baruch", "Ezekiel", "Daniel", "Hosea", "Joel", "Amos",
    "Obadiah", "Jonah", "Micah", "Nahum", "Habakkuk", "Zephaniah", "Haggai",
    "Zechariah", "Malachi", "Matthew", "Mark", "Luke", "John", "Acts",
    "Romans", "Galatians", "Ephesians", "Philippians", "Colossians", "Titus",
    "Philemon", "Hebrews", "James", "Jude", "Revelation",
]


def normalize(text: str) -> list[str]:
    return [
        token for token in re.sub(r"[^a-z0-9 ]", " ", text.lower()).split()
        if len(token) > 2 and token not in STOP_WORDS
    ]


def parse_scripture_ref(reference: str) -> tuple[str, int, int] | None:
    for book in sorted(BOOKS, key=len, reverse=True):
        prefix = f"{book} "
        if reference.lower().startswith(prefix.lower()):
            rest = reference[len(prefix):]
            match = re.match(r"(\d+)\s*:\s*(\d+)", rest)
            if match:
                return book, int(match.group(1)), int(match.group(2))
    return None


@dataclass
class Paragraph:
    id: int
    citation: str
    text: str


class SourceStore:
    def __init__(self) -> None:
        catechism_raw = json.loads((ASSET_DIR / "catechism.json").read_text(encoding="utf-8"))
        bible_raw = json.loads((ASSET_DIR / "bible.json").read_text(encoding="utf-8"))
        map_raw = json.loads((ASSET_DIR / "ccc_scripture_map.json").read_text(encoding="utf-8"))

        self.paragraphs = [
            Paragraph(
                id=int(item["paragraph"]),
                citation=item.get("citation", f"CCC {item['paragraph']}"),
                text=item["text"],
            )
            for item in catechism_raw
        ]
        self.by_id = {p.id: p for p in self.paragraphs}
        self.scripture_map = {int(key): value for key, value in map_raw.items()}
        self.bible = {
            (item["book"].lower(), int(item["chapter"]), int(item["verse"])): item
            for item in bible_raw
        }

    def search_catechism(self, query: str, limit: int = 12) -> dict[str, Any]:
        terms = normalize(query)
        if not terms:
            return {"paragraphs": []}
        scored: list[tuple[int, Paragraph]] = []
        for paragraph in self.paragraphs:
            text = paragraph.text.lower()
            score = sum(text.count(term) * 4 for term in terms)
            score += sum(8 for term in terms if term in text)
            if any(str(paragraph.id) == term for term in terms):
                score += 50
            if score > 0:
                scored.append((score, paragraph))
        scored.sort(key=lambda item: (-item[0], item[1].id))
        return {
            "paragraphs": [
                self._paragraph_payload(paragraph)
                for _, paragraph in scored[: max(1, min(limit, 40))]
            ]
        }

    def get_catechism_paragraphs(self, ids: list[int]) -> dict[str, Any]:
        return {
            "paragraphs": [
                self._paragraph_payload(self.by_id[paragraph_id], full=True)
                for paragraph_id in ids
                if paragraph_id in self.by_id
            ]
        }

    def get_nearby_catechism_paragraphs(
        self,
        paragraph_id: int,
        before: int = 2,
        after: int = 3,
    ) -> dict[str, Any]:
        ids = range(paragraph_id - max(before, 0), paragraph_id + max(after, 0) + 1)
        return self.get_catechism_paragraphs(list(ids))

    def get_scripture_refs_for_ccc(self, ids: list[int]) -> dict[str, Any]:
        refs: list[str] = []
        for paragraph_id in ids:
            for ref in self.scripture_map.get(paragraph_id, []):
                if ref not in refs:
                    refs.append(ref)
        return {"references": refs}

    def get_bible_verses(self, references: list[str]) -> dict[str, Any]:
        verses = []
        for reference in references:
            parsed = parse_scripture_ref(reference)
            if parsed is None:
                continue
            book, chapter, verse = parsed
            item = self.bible.get((book.lower(), chapter, verse))
            if item:
                verses.append(
                    {
                        "reference": reference,
                        "book": item["book"],
                        "chapter": int(item["chapter"]),
                        "verse": int(item["verse"]),
                        "text": item["text"],
                    }
                )
        return {"verses": verses}

    def search_bible(self, query: str, limit: int = 8) -> dict[str, Any]:
        terms = normalize(query)
        scored = []
        for item in self.bible.values():
            text = item["text"].lower()
            score = sum(text.count(term) for term in terms)
            if score > 0:
                scored.append((score, item))
        scored.sort(key=lambda pair: -pair[0])
        return {
            "verses": [
                {
                    "reference": f"{item['book']} {item['chapter']}:{item['verse']}",
                    "book": item["book"],
                    "chapter": int(item["chapter"]),
                    "verse": int(item["verse"]),
                    "text": item["text"],
                }
                for _, item in scored[: max(1, min(limit, 25))]
            ]
        }

    def sources_from_answer(self, answer: str) -> tuple[list[dict[str, Any]], list[dict[str, Any]]]:
        ccc_ids = [int(match) for match in re.findall(r"CCC\s*(?:§|paragraph)?\s*(\d+)", answer, re.I)]
        refs = []
        books_pattern = "|".join(re.escape(book) for book in sorted(BOOKS, key=len, reverse=True))
        for match in re.finditer(rf"\b({books_pattern})\s+(\d+)\s*:\s*(\d+)", answer, re.I):
            ref = f"{match.group(1)} {match.group(2)}:{match.group(3)}"
            if ref not in refs:
                refs.append(ref)
        paragraphs = self.get_catechism_paragraphs(list(dict.fromkeys(ccc_ids)))["paragraphs"]
        verses = self.get_bible_verses(refs)["verses"]
        return paragraphs, verses

    def _paragraph_payload(self, paragraph: Paragraph, full: bool = False) -> dict[str, Any]:
        text = paragraph.text if full else paragraph.text[:700]
        return {
            "id": paragraph.id,
            "citation": paragraph.citation,
            "text": text,
            "scripture_ref_count": len(self.scripture_map.get(paragraph.id, [])),
        }


STORE = SourceStore()


TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "search_catechism",
            "description": "Search Catechism paragraphs by doctrinal words or phrases.",
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {"type": "string"},
                    "limit": {"type": "integer", "minimum": 1, "maximum": 40},
                },
                "required": ["query"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_catechism_paragraphs",
            "description": "Fetch full Catechism paragraphs by paragraph IDs.",
            "parameters": {
                "type": "object",
                "properties": {"ids": {"type": "array", "items": {"type": "integer"}}},
                "required": ["ids"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_nearby_catechism_paragraphs",
            "description": "Fetch nearby Catechism paragraphs around a known paragraph ID.",
            "parameters": {
                "type": "object",
                "properties": {
                    "paragraph_id": {"type": "integer"},
                    "before": {"type": "integer", "minimum": 0, "maximum": 10},
                    "after": {"type": "integer", "minimum": 0, "maximum": 10},
                },
                "required": ["paragraph_id"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_scripture_refs_for_ccc",
            "description": "Look up Scripture references connected to CCC paragraph IDs.",
            "parameters": {
                "type": "object",
                "properties": {"ids": {"type": "array", "items": {"type": "integer"}}},
                "required": ["ids"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "get_bible_verses",
            "description": "Fetch full Bible verse text by references such as John 3:16.",
            "parameters": {
                "type": "object",
                "properties": {"references": {"type": "array", "items": {"type": "string"}}},
                "required": ["references"],
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "search_bible",
            "description": "Search Bible verse text. Use this only after anchoring doctrine in the CCC.",
            "parameters": {
                "type": "object",
                "properties": {
                    "query": {"type": "string"},
                    "limit": {"type": "integer", "minimum": 1, "maximum": 25},
                },
                "required": ["query"],
            },
        },
    },
]


def execute_tool(name: str, arguments: dict[str, Any]) -> dict[str, Any]:
    if name == "search_catechism":
        return STORE.search_catechism(arguments["query"], int(arguments.get("limit", 12)))
    if name == "get_catechism_paragraphs":
        return STORE.get_catechism_paragraphs([int(value) for value in arguments["ids"]])
    if name == "get_nearby_catechism_paragraphs":
        return STORE.get_nearby_catechism_paragraphs(
            int(arguments["paragraph_id"]),
            int(arguments.get("before", 2)),
            int(arguments.get("after", 3)),
        )
    if name == "get_scripture_refs_for_ccc":
        return STORE.get_scripture_refs_for_ccc([int(value) for value in arguments["ids"]])
    if name == "get_bible_verses":
        return STORE.get_bible_verses([str(value) for value in arguments["references"]])
    if name == "search_bible":
        return STORE.search_bible(arguments["query"], int(arguments.get("limit", 8)))
    return {"error": f"Unknown tool: {name}"}


def clean_answer(answer: str) -> str:
    cleaned = re.sub(r"<thought>.*?</thought>", "", answer, flags=re.I | re.S)
    cleaned = re.sub(r"<think>.*?</think>", "", cleaned, flags=re.I | re.S)
    cleaned = re.sub(r"\n{3,}", "\n\n", cleaned)
    return cleaned.strip()


def llm_chat(
    messages: list[dict[str, Any]],
    tools: list[dict[str, Any]] | None = None,
    model: str | None = None,
) -> dict[str, Any]:
    if not API_KEY or not BASE_URL:
        raise RuntimeError("CATECHISM_LLM_API_KEY and CATECHISM_LLM_BASE_URL must be set")

    payload: dict[str, Any] = {
        "model": model or MODEL,
        "messages": messages,
        "temperature": 0.2,
    }
    if tools:
        payload["tools"] = tools
        payload["tool_choice"] = "auto"

    data = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        f"{BASE_URL}/chat/completions",
        data=data,
        headers={
            "Authorization": f"Bearer {API_KEY}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=90) as response:
            return json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"LLM HTTP {exc.code}: {body}") from exc


def llm_chat_with_fallbacks(
    messages: list[dict[str, Any]],
    tools: list[dict[str, Any]] | None = None,
) -> tuple[dict[str, Any], str]:
    errors = []
    for model in MODELS:
        try:
            return llm_chat(messages, tools, model=model), model
        except Exception as exc:
            errors.append(f"{model}: {exc}")
            print(f"Model {model} failed: {exc}", file=sys.stderr)
    raise RuntimeError("All configured models failed. " + " | ".join(errors))


def answer_question(question: str) -> dict[str, Any]:
    messages: list[dict[str, Any]] = [
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": question},
    ]
    gathered_paragraphs: dict[int, dict[str, Any]] = {}
    gathered_verses: dict[str, dict[str, Any]] = {}

    for _ in range(MAX_TOOL_ROUNDS):
        response, model_used = llm_chat_with_fallbacks(messages, TOOLS)
        message = response["choices"][0]["message"]
        tool_calls = message.get("tool_calls") or []
        messages.append(message)

        if not tool_calls:
            answer = clean_answer(message.get("content", ""))
            cited_paragraphs, cited_verses = STORE.sources_from_answer(answer)
            return {
                "answer": answer,
                "ccc_sources": cited_paragraphs or list(gathered_paragraphs.values())[:8],
                "scripture_sources": cited_verses or list(gathered_verses.values())[:8],
                "model": model_used,
            }

        for tool_call in tool_calls:
            function = tool_call.get("function", {})
            name = function.get("name", "")
            raw_arguments = function.get("arguments") or "{}"
            try:
                arguments = json.loads(raw_arguments)
            except json.JSONDecodeError:
                arguments = {}
            result = execute_tool(name, arguments)

            for paragraph in result.get("paragraphs", []):
                gathered_paragraphs[int(paragraph["id"])] = paragraph
            for verse in result.get("verses", []):
                gathered_verses[verse["reference"]] = verse

            messages.append(
                {
                    "role": "tool",
                    "tool_call_id": tool_call["id"],
                    "name": name,
                    "content": json.dumps(result, ensure_ascii=False),
                }
            )

    messages.append(
        {
            "role": "user",
            "content": "Use only the sources already returned by the tools and provide the final answer now.",
        }
    )
    response, model_used = llm_chat_with_fallbacks(messages, None)
    answer = clean_answer(response["choices"][0]["message"].get("content", ""))
    cited_paragraphs, cited_verses = STORE.sources_from_answer(answer)
    return {
        "answer": answer,
        "ccc_sources": cited_paragraphs or list(gathered_paragraphs.values())[:8],
        "scripture_sources": cited_verses or list(gathered_verses.values())[:8],
        "model": model_used,
    }


class Handler(BaseHTTPRequestHandler):
    def do_GET(self) -> None:
        if self.path == "/health":
            self._json(200, {"ok": True, "model": MODEL, "time": int(time.time())})
            return
        self._json(404, {"error": "Not found"})

    def do_POST(self) -> None:
        if self.path != "/ask":
            self._json(404, {"error": "Not found"})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            body = json.loads(self.rfile.read(length).decode("utf-8"))
            question = str(body.get("question", "")).strip()
            if not question:
                self._json(400, {"error": "Question is required"})
                return
            self._json(200, answer_question(question))
        except Exception as exc:
            print(f"Request failed: {exc}", file=sys.stderr)
            self._json(503, {"error": str(exc)})

    def log_message(self, format: str, *args: Any) -> None:
        print(f"{self.address_string()} - {format % args}")

    def _json(self, status: int, payload: dict[str, Any]) -> None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


def main() -> None:
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f"Catholic Catechist backend listening on http://{HOST}:{PORT}")
    print(f"Models: {', '.join(MODELS)}")
    server.serve_forever()


if __name__ == "__main__":
    main()
