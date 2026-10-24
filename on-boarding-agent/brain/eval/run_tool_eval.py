#!/usr/bin/env python3
"""
Deterministic Tool Selection Accuracy harness.

For each labeled query in tool-selection-eval.json, sends it to brain's
/v1/chat/completions, reads back the chatcmpl id from the SSE stream, then
greps the brain log (one JSON line per turn, logger
"dev.voiceagent.brain.TurnEvents") for that exact id to see which tools
actually fired. Compares against expectedTools and reports accuracy.

Usage:
    python run_tool_eval.py [--brain-url http://localhost:7070] [--log-file /tmp/brain_out.log]
"""
import argparse
import json
import time
import urllib.error
import urllib.request
from pathlib import Path

EVAL_FILE = Path(__file__).parent / "tool-selection-eval.json"


def send_query(brain_url: str, query: str, retries: int = 5, retry_delay: float = 8.0) -> str:
    payload = json.dumps({
        "model": "brain",
        "messages": [{"role": "user", "content": query}],
        "stream": True,
    }).encode("utf-8")
    req = urllib.request.Request(
        f"{brain_url}/v1/chat/completions",
        data=payload,
        headers={"Content-Type": "application/json"},
    )
    for attempt in range(retries):
        try:
            chunk_id = None
            with urllib.request.urlopen(req, timeout=60) as resp:
                for raw_line in resp:
                    line = raw_line.decode("utf-8").strip()
                    if not line.startswith("data: ") or line == "data: [DONE]":
                        continue
                    data = json.loads(line[len("data: "):])
                    if chunk_id is None:
                        chunk_id = data.get("id")
            if chunk_id is None:
                raise RuntimeError(f"no chatcmpl id observed for query: {query!r}")
            return chunk_id
        except (urllib.error.HTTPError, urllib.error.URLError) as e:
            # Most likely Groq's account-level 8000 TPM rate limit (see
            # HrOnboardingAgent / PolicySearchTool comments) -- wait out the
            # window and retry rather than failing the whole eval run.
            if attempt == retries - 1:
                raise
            print(f"  (retry {attempt + 1}/{retries} after {e}; waiting {retry_delay}s)")
            time.sleep(retry_delay)
    raise RuntimeError("unreachable")
    return chunk_id


def find_turn_log(log_file: Path, chunk_id: str, attempts: int = 10, delay: float = 0.3):
    for _ in range(attempts):
        text = log_file.read_text(encoding="utf-8", errors="ignore")
        for line in reversed(text.splitlines()):
            idx = line.find("{\"requestId\"")
            if idx == -1:
                continue
            candidate = line[idx:]
            try:
                record = json.loads(candidate)
            except json.JSONDecodeError:
                continue
            if record.get("requestId") == chunk_id:
                return record
        time.sleep(delay)
    return None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--brain-url", default="http://localhost:7070")
    parser.add_argument("--log-file", default="/tmp/brain_out.log")
    parser.add_argument(
        "--pace-seconds", type=float, default=6.0,
        help="Delay between queries to stay under Groq's account-level TPM cap.",
    )
    args = parser.parse_args()

    log_file = Path(args.log_file)
    cases = json.loads(EVAL_FILE.read_text(encoding="utf-8"))

    results = []
    for i, case in enumerate(cases):
        if i > 0:
            time.sleep(args.pace_seconds)
        query = case["query"]
        expected = sorted(case["expectedTools"])
        print(f"[{i + 1}/{len(cases)}] {query!r}")
        chunk_id = send_query(args.brain_url, query)
        record = find_turn_log(log_file, chunk_id)
        if record is None:
            results.append((query, expected, None, False))
            continue
        actual = sorted(tc["name"] for tc in record.get("toolCalls", []))
        passed = actual == expected
        results.append((query, expected, actual, passed))

    print(f"{'PASS' if all(r[3] for r in results) else 'FAIL':<6} {len(results)} cases\n")
    for query, expected, actual, passed in results:
        mark = "PASS" if passed else "FAIL"
        print(f"[{mark}] {query!r}")
        print(f"       expected={expected}")
        print(f"       actual  ={actual if actual is not None else '<no log line found>'}")

    correct = sum(1 for r in results if r[3])
    print(f"\nTool Selection Accuracy: {correct}/{len(results)} ({100 * correct / len(results):.0f}%)")


if __name__ == "__main__":
    main()
