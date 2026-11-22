#!/usr/bin/env python3
"""Swedish typing model (sv.hextm), validity list (sv.valid) and capitals sidecar (sv.capitals).

    python3 -m venv /tmp/venv && /tmp/venv/bin/pip install spylls
    /tmp/venv/bin/python -I build-swedish-model.py DOWNLOADS WORKDIR ASSETS_DIR

ASSETS_DIR must already hold en.hextm: subtitles and news quote English, so a word English uses
far more often than these texts do ("the", "you", "is") is left out of the Swedish model and
lists. Mixed Swedish and English typing reads it from the English model instead, and it then
tells the keyboard which language a sentence is in.

Google Books has no Swedish, so the counts come from running text instead:
- Leipzig Corpora Collection swe_news_2022_1M, swe_news_2023_1M and swe_wikipedia_2021_1M
  (CC BY 4.0, https://wortschatz.uni-leipzig.de), `<name>.tar.gz` in DOWNLOADS;
- OPUS OpenSubtitles v2018 Swedish monolingual text (http://www.opensubtitles.org, via
  https://opus.nlpl.eu), `opensubs-sv.txt.gz` in DOWNLOADS, for the everyday, spoken register
  a phone keyboard types;
- LibreOffice's sv_SE Hunspell dictionary (Göran Andersson, LGPL 3), `sv_SE.dic` and `sv_SE.aff`
  in DOWNLOADS, which keeps typos, foreign words and tokenizer debris out of the model. Hunspell
  knows Swedish compounds, so attested compounds ("sommarstuga") stay.

Words are maximal runs of Swedish letters (a-z å ä ö é ü), lowercased; a pair is two such words
with only whitespace between them. Pairs are counted over the model's vocabulary only. The model
is written by build-typing-model.py's writer (HXTM v3). The validity list has the dictionary's
words the text uses at least VALID_MIN_COUNT times outside the model; the capitals sidecar
follows build-capitals.py's rules, with each line's first word left out of the case counts.
Deterministic for the same inputs; every stage is cached in WORKDIR.
"""
import collections, importlib, math, multiprocessing, os, re, sys, tarfile, unicodedata

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
builder = importlib.import_module("build-typing-model")

LEIPZIG = ("swe_news_2022_1M", "swe_news_2023_1M", "swe_wikipedia_2021_1M")
SUBTITLES = "opensubs-sv.txt.gz"
LETTERS = "abcdefghijklmnopqrstuvwxyzåäöéü"
MAX_WORDS = 100_000
MIN_PAIR = 6
VALID_MIN_COUNT = 4
SINGLE_LETTERS = {"i", "å", "ö"}
MIN_TOTAL, MODEL_SHARE, NAME_SHARE, NAME_PER_MILLION, HELD = 40, 50, 90, 1, 94
TOKEN = re.compile(r"[^\W\d_]+|[^\w\s]+|[\d_]+")
WORD = re.compile("[" + LETTERS + "]+")
SENTENCE_END = set(".!?:-–—\"”»…")
ENGLISH_RATIO, ENGLISH_COST = 60.0, 10.0


def sources(downloads, work):
    out = []
    for name in LEIPZIG:
        path = os.path.join(work, name + ".txt")
        if not os.path.exists(path):
            with tarfile.open(os.path.join(downloads, name + ".tar.gz")) as t:
                member = t.extractfile(f"{name}/{name}-sentences.txt")
                with open(path + ".part", "wb") as f:
                    for line in member:
                        f.write(line.split(b"\t", 1)[-1])
            os.replace(path + ".part", path)
        out.append(path)
    path = os.path.join(work, "opensubs.txt")
    if not os.path.exists(path):
        import gzip, shutil
        with gzip.open(os.path.join(downloads, SUBTITLES)) as src, open(path + ".part", "wb") as dst:
            shutil.copyfileobj(src, dst, 1 << 24)
        os.replace(path + ".part", path)
    out.append(path)
    return out


def chunks(paths, parts):
    jobs = []
    for path in paths:
        size = os.path.getsize(path)
        step = max(1, size // parts)
        for start in range(0, size, step):
            jobs.append((path, start, min(size, start + step)))
    return jobs


def lines(job):
    path, start, end = job
    with open(path, "rb") as f:
        if start:
            f.seek(start - 1)
            if f.read(1) != b"\n":
                f.readline()
        while f.tell() < end:
            line = f.readline()
            if not line:
                break
            yield line.decode("utf-8", "replace")


def words(line):
    start = True
    for m in TOKEN.finditer(line):
        text = m.group()
        lower = text.lower()
        if WORD.fullmatch(lower):
            yield lower, text, start
            start = False
        else:
            yield None, text, False
            start = start or text[-1] in SENTENCE_END


def count_words(job):
    uni, lower_mid, upper_mid = collections.Counter(), collections.Counter(), collections.Counter()
    for line in lines(job):
        for w, text, start in words(line):
            if w is None:
                continue
            uni[w] += 1
            if start:
                continue
            if text == w:
                lower_mid[w] += 1
            elif text[0].isupper() and text[1:] == w[1:]:
                upper_mid[w] += 1
    return uni, lower_mid, upper_mid


VOCAB = {}


def load_vocab(path):
    global VOCAB
    VOCAB = {line.split("\t", 1)[0]: i for i, line in enumerate(open(path, encoding="utf-8"))}


def count_pairs(job):
    vocab, pairs = VOCAB, collections.Counter()
    for line in lines(job):
        prev = -1
        for w, _, _ in words(line):
            i = vocab.get(w, -1) if w is not None else -1
            if prev >= 0 and i >= 0:
                pairs[prev * 262144 + i] += 1
            prev = i
    return {k: c for k, c in pairs.items() if c >= 2}


DICTIONARY = None


def check(batch):
    global DICTIONARY
    if DICTIONARY is None:
        from spylls.hunspell import Dictionary
        DICTIONARY = Dictionary.from_files(os.environ["SV_HUNSPELL"])
    out = []
    for w in batch:
        lowercase = DICTIONARY.lookup(w)
        if lowercase or DICTIONARY.lookup(w[0].upper() + w[1:]):
            out.append((w, lowercase))
    return out


def english_words(path):
    import struct
    data = open(path, "rb").read()
    magic, version, n, _, blob_bytes, alphabet = struct.unpack_from("<4sIIIII", data, 0)
    assert magic == b"HXTM" and version == 3
    chars = {}
    for i in range(alphabet):
        scalar, code, _, _ = struct.unpack_from("<IBBH", data, 28 + 8 * i)
        chars[code] = chr(scalar)
    starts = 28 + 8 * alphabet
    offsets = struct.unpack_from(f"<{n + 1}I", data, starts)
    uni = starts + 4 * (n + 1)
    blob = len(data) - blob_bytes
    return {"".join(chars[b] for b in data[blob + offsets[i]:blob + offsets[i + 1]]): math.exp(-data[uni + i] / 8)
            for i in range(n)}


def read_counts(path):
    out = {}
    for line in open(path, encoding="utf-8"):
        w, *c = line.rstrip("\n").split("\t")
        out[w] = tuple(map(int, c))
    return out


def main(downloads, work, assets):
    os.makedirs(work, exist_ok=True)
    os.environ["SV_HUNSPELL"] = os.path.join(downloads, "sv_SE")
    workers = int(os.environ.get("WORKERS", str(max(1, os.cpu_count() - 2))))
    jobs = chunks(sources(downloads, work), workers * 2)

    counts_path = os.path.join(work, "counts.tsv")
    if not os.path.exists(counts_path):
        uni, low, up = collections.Counter(), collections.Counter(), collections.Counter()
        with multiprocessing.Pool(workers) as pool:
            for u, l, c in pool.imap_unordered(count_words, jobs):
                uni.update(u); low.update(l); up.update(c)
        with open(counts_path + ".part", "w", encoding="utf-8") as f:
            for w in sorted(uni):
                f.write(f"{w}\t{uni[w]}\t{low[w]}\t{up[w]}\n")
        os.replace(counts_path + ".part", counts_path)
    counts = read_counts(counts_path)
    total = sum(c[0] for c in counts.values())

    checked_path = os.path.join(work, "checked.tsv")
    if not os.path.exists(checked_path):
        candidates = sorted((w for w, c in counts.items() if c[0] >= VALID_MIN_COUNT), key=lambda w: (-counts[w][0], w))
        batches = [candidates[i:i + 2000] for i in range(0, len(candidates), 2000)]
        with multiprocessing.Pool(workers) as pool:
            accepted = [r for batch in pool.map(check, batches) for r in batch]
        with open(checked_path + ".part", "w", encoding="utf-8") as f:
            for w, lowercase in sorted(accepted):
                f.write(f"{w}\t{int(lowercase)}\n")
        os.replace(checked_path + ".part", checked_path)
    dictionary = {w: bool(int(l)) for w, (l,) in read_counts(checked_path).items()}
    english = english_words(os.path.join(assets, "en.hextm"))
    quoted = {w for w in dictionary if english.get(w, 0) >= math.exp(-ENGLISH_COST) and
              english[w] > ENGLISH_RATIO * counts[w][0] / total}
    print(f"English left out: {len(quoted)}: {' '.join(sorted(quoted, key=lambda w: -english[w])[:60])}")
    dictionary = {w: l for w, l in dictionary.items() if w not in quoted}

    def allowed(w):
        return w in dictionary and (len(w) > 1 or w in SINGLE_LETTERS)

    ranked = sorted((w for w in dictionary if allowed(w)), key=lambda w: (-counts[w][0], w))
    vocab = {w: counts[w][0] for w in ranked[:MAX_WORDS]}
    vocab_path = os.path.join(work, "vocab.tsv")
    with open(vocab_path, "w", encoding="utf-8") as f:
        for w in vocab:
            f.write(f"{w}\t{vocab[w]}\n")

    pairs_path = os.path.join(work, "pairs.tsv")
    if not os.path.exists(pairs_path):
        pairs = collections.Counter()
        with multiprocessing.Pool(workers, initializer=load_vocab, initargs=(vocab_path,)) as pool:
            for part in pool.imap_unordered(count_pairs, jobs):
                pairs.update(part)
        names = list(vocab)
        with open(pairs_path + ".part", "w", encoding="utf-8") as f:
            for k, c in sorted(pairs.items()):
                if c >= MIN_PAIR:
                    f.write(f"{names[k // 262144]} {names[k % 262144]}\t{c}\n")
        os.replace(pairs_path + ".part", pairs_path)
    pairs = {}
    for line in open(pairs_path, encoding="utf-8"):
        k, c = line.rstrip("\n").split("\t")
        pairs[k.encode("utf-8")] = int(c)

    builder.MIN_PAIR = MIN_PAIR
    builder.write_model("sv", vocab, total, pairs, os.path.join(assets, "sv.hextm"))
    write_valid(os.path.join(assets, "sv.valid"), dictionary, vocab, counts, total)
    write_capitals(os.path.join(assets, "sv.capitals"), vocab, dictionary, counts, total)


def write_valid(path, dictionary, vocab, counts, total):
    rows = []
    for w in dictionary:
        if w in vocab or len(w) < 2 or counts[w][0] < VALID_MIN_COUNT:
            continue
        rows.append((w.encode("utf-8"), round(8 * math.log(total / counts[w][0]))))
    rows.sort()
    with open(path, "wb") as f:
        for w, cost in rows:
            f.write(w + b"\t" + str(cost).encode() + b"\n")
    print(f"{path}: {len(rows)} words")


def write_capitals(path, vocab, dictionary, counts, total):
    rows = []
    for w, (n, low, up) in counts.items():
        cased = low + up
        if cased < MIN_TOTAL or len(w) < 2:
            continue
        share = math.floor(100 * up / cased + 0.5)
        if w in vocab:
            if share < MODEL_SHARE:
                continue
        elif not (len(w) >= 3 and share >= NAME_SHARE and n * 1_000_000 >= NAME_PER_MILLION * total and w in dictionary):
            continue
        if dictionary.get(w):
            share = min(share, HELD)
        rows.append((w.encode("utf-8"), share))
    rows.sort()
    with open(path, "wb") as f:
        for w, share in rows:
            f.write(w + b"\t" + str(share).encode() + b"\n")
    print(f"{path}: {len(rows)} words")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    main(*sys.argv[1:])
