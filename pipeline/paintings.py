#!/usr/bin/env python3
"""Telif süresi dolmuş ünlü tablolar listesi -> data/paintings.json

  python3 pipeline/paintings.py

Kaynak: Wikidata (tablo, görseli var, sanatçı 1925'ten önce ölmüş, çok dilde maddesi olan = ünlü),
görseller Wikimedia Commons. Bekleme salonu için çıplaklık içerenler elenir.
Liste nadiren değişir; gecelik çalıştırmaya gerek yok.
"""
import json
import time
import urllib.parse
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
UA = "PubMedTV/1.0 (https://github.com/drahmetokyay-coder/pubmed-tv; drahmetokyay@gmail.com)"
MIN_SITELINKS = 25
LIMIT = 300

NUDITY = ["Q40446", "Q2316088", "Q1305557", "Q10791"]  # nudity, female nudity, male nudity, nude (art)
# Bekleme salonuna uygun olmayanlar (Wikidata'da çıplaklık etiketi olmayan çıplak figürler, şiddet/vahşet)
DENY = {
    "Q1067616", "Q151047", "Q321303", "Q152867", "Q737062", "Q727875", "Q500812", "Q1978815",
    "Q2293953", "Q794077", "Q2011510", "Q2293936",
    "Q29530", "Q890678", "Q3869871", "Q153441", "Q930137", "Q1327676", "Q1473546",  # çıplaklık
    "Q212616", "Q1091086", "Q1150997", "Q644856", "Q2727560", "Q2293905", "Q2627056", "Q7536969",  # vahşet
}
# Wikidata etiketleri zaman zaman tahrip ediliyor
TITLE_FIX = {"Q512755": "Savurgan Oğlun Dönüşü"}

QUERY = """
SELECT ?p ?pLabel ?creatorLabel ?inception ?image ?links WHERE {
  ?p wdt:P31 wd:Q3305213; wdt:P18 ?image; wdt:P170 ?creator; wikibase:sitelinks ?links.
  ?creator wdt:P570 ?death.
  FILTER(YEAR(?death) < 1925)
  ?p wdt:P571 ?inception.
  FILTER(?links >= %d)
  SERVICE wikibase:label { bd:serviceParam wikibase:language "tr,en". }
} ORDER BY DESC(?links)
""" % MIN_SITELINKS

# bulunan eserler için: çıplaklık tasviri ve müze
DETAILS = """
SELECT ?p ?d ?collectionLabel WHERE {
  VALUES ?p { %s }
  OPTIONAL { ?p wdt:P180 ?d. VALUES ?d { %s } }
  OPTIONAL { ?p wdt:P195 ?collection. }
  SERVICE wikibase:label { bd:serviceParam wikibase:language "tr,en". }
}
"""


def _get(url, tries=4):
    req = urllib.request.Request(url, headers={"User-Agent": UA, "Accept": "application/sparql-results+json"})
    for i in range(tries):
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
                return json.loads(r.read())
        except Exception:  # Wikidata sorgu servisi ara ara 502/504 veriyor
            if i == tries - 1:
                raise
            time.sleep(20)


def image_sizes(files):
    """Commons dosya adı -> (genişlik, yükseklik)"""
    out = {}
    for i in range(0, len(files), 50):
        titles = "|".join("File:" + f for f in files[i:i + 50])
        q = urllib.parse.urlencode({"action": "query", "prop": "imageinfo", "iiprop": "size",
                                    "titles": titles, "format": "json", "formatversion": 2})
        for page in _get("https://commons.wikimedia.org/w/api.php?" + q)["query"]["pages"]:
            ii = page.get("imageinfo")
            if ii:
                out[page["title"][5:].replace(" ", "_")] = (ii[0]["width"], ii[0]["height"])
        time.sleep(0.5)
    return out


def main():
    rows = _get("https://query.wikidata.org/sparql?" + urllib.parse.urlencode({"query": QUERY}))["results"]["bindings"]
    seen, items = set(), []
    for r in rows:
        qid = r["p"]["value"].rsplit("/", 1)[1]
        if qid in seen or qid in DENY:
            continue
        seen.add(qid)
        file = urllib.parse.unquote(r["image"]["value"].rsplit("/", 1)[1]).replace(" ", "_")
        title = TITLE_FIX.get(qid, r["pLabel"]["value"])
        artist = r["creatorLabel"]["value"]
        surname = artist.split()[-1]
        if title.endswith(f" ({surname})"):  # "Salıncak (Fragonard)" -> "Salıncak"
            title = title[: -len(surname) - 3]
        if title.startswith("Q") and title[1:].isdigit():
            continue  # etiketsiz
        items.append({
            "id": qid, "title": title, "artist": artist,
            "year": r.get("inception", {}).get("value", "")[:4].lstrip("+"),
            "file": file,
        })
    nude, museum = set(), {}
    for i in range(0, len(items), 100):
        q = DETAILS % (" ".join("wd:" + x["id"] for x in items[i:i + 100]), " ".join("wd:" + n for n in NUDITY))
        for r in _get("https://query.wikidata.org/sparql?" + urllib.parse.urlencode({"query": q}))["results"]["bindings"]:
            qid = r["p"]["value"].rsplit("/", 1)[1]
            if "d" in r:
                nude.add(qid)
            if "collectionLabel" in r:
                museum.setdefault(qid, r["collectionLabel"]["value"])
    items = [{**x, "museum": museum.get(x["id"], "")} for x in items if x["id"] not in nude][:LIMIT]
    sizes = image_sizes([i["file"] for i in items])
    out = []
    for i in items:
        w, h = sizes.get(i["file"], (0, 0))
        if w < 1000 and h < 1000:
            continue  # TV için fazla küçük
        # Wikimedia standart küçük resim genişlikleri: yatay 1920, dikey 1280
        width = 1920 if w >= h else 1280
        url = "https://commons.wikimedia.org/wiki/Special:FilePath/" + urllib.parse.quote(i.pop("file")) + f"?width={width}"
        out.append({**i, "url": url, "w": w, "h": h})
    (ROOT / "data" / "paintings.json").write_text(
        json.dumps({"paintings": out}, ensure_ascii=False, indent=1) + "\n")
    print(f"{len(out)} tablo yazıldı")


if __name__ == "__main__":
    main()
