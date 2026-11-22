#!/usr/bin/env python3
"""English's validity list (en.valid): spellings outside the keyboard model that are still real words,
so the decoder never splits them into words (iOS asks its spelling dictionary, UITextChecker). One
lowercase word per line, sorted by UTF-8 bytes, with a trailing newline; only words the model lacks.

    python3 build-valid-words.py en scowl-2020.12.07.tar.gz en.hextm en.valid

SCOWL spellings, sizes through 70.
"""
import hashlib, importlib, os, re, sys, tarfile
from pathlib import Path

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
capitals = importlib.import_module("build-capitals")

SCOWL_SHA256 = "5587667caa20c4891390c2d42dbb4d5c4c3f41bee77af1457ece3ba23fb859cc"


def write(output, words):
    data = "".join(w + "\n" for w in sorted(words, key=lambda w: w.encode("utf-8"))).encode("utf-8")
    Path(output).write_bytes(data)
    print(f"{len(words):,} spellings outside the model, {len(data):,} bytes; {hashlib.sha256(data).hexdigest()}")


def english(source, model, output):
    assert hashlib.sha256(Path(source).read_bytes()).hexdigest() == SCOWL_SHA256
    scored = set(capitals.model_words(model)[0])
    valid = set()
    with tarfile.open(source) as archive:
        for entry in archive:
            m = re.fullmatch(r".*/final/(english|british|american)-(words|contractions|proper-names|upper|abbreviations)\.(\d+)", entry.name)
            if m and int(m[3]) <= 70:
                for w in archive.extractfile(entry).read().decode("latin-1").lower().split():
                    if re.fullmatch("[a-z]{2,32}", w) and w not in scored:
                        valid.add(w)
    write(output, valid)


if __name__ == "__main__":
    lang = sys.argv[1] if len(sys.argv) > 1 else ""
    if lang == "en" and len(sys.argv) == 5:
        english(*sys.argv[2:5])
    else:
        sys.exit(__doc__)
