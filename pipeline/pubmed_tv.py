#!/usr/bin/env python3
"""PubMed TV veri hattı.

  python3 pipeline/pubmed_tv.py fetch    # aday topla, sırala, seç -> data/candidates.json + data/pending.json
  (Claude: data/pending.json'daki her makale için data/cache/summaries.json'a Türkçe cümle yazar)
  python3 pipeline/pubmed_tv.py publish  # birleştir + doğrula -> data/articles.json

Yalnızca standart kütüphane. Kaynaklar: NCBI E-utilities (PubMed), NIH iCite (atıf).
"""
import json
import re
import sys
import time
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from datetime import date, datetime
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "data"
CACHE = DATA / "cache"
EUTILS = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/"
ICITE = "https://icite.od.nih.gov/api/pubs"
TOOL = "&tool=pubmed-tv&email=drahmetokyay@gmail.com"

N_YEAR, N_NEW = 70, 30
PUNCH_MAX = 140

TOPICS = {
    "Venöz hastalık": '"Varicose Veins"[Mesh] OR "Venous Insufficiency"[Mesh] OR varicose[tiab] OR endovenous[tiab] OR sclerotherapy[tiab]',
    "DVT ve pulmoner emboli": '"Venous Thrombosis"[Mesh] OR "Pulmonary Embolism"[Mesh] OR "deep vein thrombosis"[tiab]',
    "Periferik arter hastalığı": '"Peripheral Arterial Disease"[Mesh] OR "limb-threatening ischemia"[tiab] OR "critical limb ischemia"[tiab]',
    "Aort": '"Aortic Aneurysm"[Mesh] OR "Aortic Dissection"[Mesh] OR EVAR[tiab] OR TEVAR[tiab]',
    "Karotis": '"Carotid Stenosis"[Mesh] OR "carotid endarterectomy"[tiab] OR "carotid stenting"[tiab]',
    "Diyabetik ayak": '"Diabetic Foot"[Mesh] OR "diabetic foot"[tiab]',
    "Koroner bypass": '"Coronary Artery Bypass"[Mesh] OR CABG[tiab]',
    "Kalp kapak cerrahisi": '"Heart Valve Prosthesis Implantation"[Mesh] OR "Transcatheter Aortic Valve Replacement"[Mesh] OR TAVI[tiab] OR TAVR[tiab]',
    "Diyaliz erişimi": '"Arteriovenous Shunt, Surgical"[Mesh] OR ("arteriovenous fistula"[tiab] AND dialysis[tiab])',
}

# kaba etki kademesi (0-10); kesin impact factor değil, sıralama ağırlığı
JOURNALS = {
    "N Engl J Med": 10, "Lancet": 9, "JAMA": 8, "Circulation": 7, "Eur Heart J": 7,
    "J Vasc Surg": 4, "Eur J Vasc Endovasc Surg": 4, "J Thorac Cardiovasc Surg": 4,
    "J Vasc Surg Venous Lymphat Disord": 3, "Ann Thorac Surg": 3, "Eur J Cardiothorac Surg": 3,
    "J Endovasc Ther": 3, "Ann Vasc Surg": 2, "Phlebology": 2,
}

STUDY_TYPES = [  # (PubMed yayın türü, ekran etiketi, ağırlık) — ilk eşleşen kazanır
    ("Practice Guideline", "Kılavuz", 1.7), ("Guideline", "Kılavuz", 1.7),
    ("Meta-Analysis", "Meta-analiz", 1.6), ("Systematic Review", "Sistematik derleme", 1.6),
    ("Randomized Controlled Trial", "RCT", 1.5), ("Multicenter Study", "Çok merkezli", 1.2),
    ("Review", "Derleme", 1.1),
]
EXCLUDE = 'NOT (Comment[pt] OR Editorial[pt] OR Letter[pt] OR Erratum[pt] OR "Retracted Publication"[pt])'


# ---------- ağ ----------

def _get(url, tries=4):
    for i in range(tries):
        try:
            with urllib.request.urlopen(url, timeout=60) as r:
                return r.read()
        except Exception:
            if i == tries - 1:
                raise
            time.sleep(2 * (i + 1))


def _eutils(endpoint, **params):
    time.sleep(0.4)  # anahtarsız sınır: 3 istek/sn
    return _get(EUTILS + endpoint + "?" + urllib.parse.urlencode(params) + TOOL)


def esearch(term, reldate):
    out = json.loads(_eutils("esearch.fcgi", db="pubmed", term=term, retmax=10000, retmode="json",
                             datetype="edat", reldate=reldate))
    return out["esearchresult"]["idlist"]


def esummary(pmids):
    res = {}
    for i in range(0, len(pmids), 200):
        out = json.loads(_eutils("esummary.fcgi", db="pubmed", id=",".join(pmids[i:i + 200]), retmode="json"))
        for p in out["result"].get("uids", []):
            res[p] = out["result"][p]
    return res


def icite(pmids):
    res = {}
    for i in range(0, len(pmids), 200):
        url = ICITE + "?pmids=" + ",".join(pmids[i:i + 200]) + "&fl=pmid,citation_count"
        for d in json.loads(_get(url))["data"]:
            res[str(d["pmid"])] = d.get("citation_count") or 0
    return res


def efetch(pmids):
    res = {}
    for i in range(0, len(pmids), 100):
        root = ET.fromstring(_eutils("efetch.fcgi", db="pubmed", id=",".join(pmids[i:i + 100]), retmode="xml"))
        for art in root.iter("PubmedArticle"):
            res[art.findtext(".//PMID")] = art
    return res


# ---------- saf mantık (test edilir) ----------

def journal_weight(source):
    return JOURNALS.get(source, 1)


def study_type(pubtypes):
    for pt, label, w in STUDY_TYPES:
        if pt in pubtypes:
            return label, w
    return None, 1.0


def months_since(d, today):
    return max(1.0, (today - d).days / 30.44)


def year_score(cites, pub_date, today):
    """atıf hızı (atıf/ay)"""
    return cites / months_since(pub_date, today)


def new_score(source, pubtypes):
    return journal_weight(source) * study_type(pubtypes)[1]


def select(year_pool, new_pool, n_year=N_YEAR, n_new=N_NEW):
    """year_pool / new_pool: skora göre azalan sıralı pmid listeleri.
    Önce yeni havuzdan n_new, sonra yıllık havuzdan tekrarsız n_year seçer."""
    chosen_new = new_pool[:n_new]
    seen = set(chosen_new)
    chosen_year = [p for p in year_pool if p not in seen][:n_year]
    return chosen_year, chosen_new


_SENT = re.compile(r"(?<=[.!?])\s+(?=[A-Z0-9])")


def split_sentences(text):
    return [s.strip() for s in _SENT.split(text.strip()) if s.strip()]


_FUNDING = re.compile(r"\s*\((Funded|Supported) by[^()]*(\([^()]*\)[^()]*)*\)\.?\s*$")


def conclusion(sections):
    """sections: [(label|None, nlm_category|None, text)]. Kelimesi kelimesine sonuç cümlesi
    (sondaki finansman/kayıt künyesi hariç)."""
    return _conclusion(sections)


def _conclusion(sections):
    concl = [t for lab, cat, t in sections
             if (cat or "").upper() == "CONCLUSIONS" or re.match(r"(?i)conclusions?|interpretation", lab or "")]
    if concl:
        text = _FUNDING.sub("", " ".join(concl))
        return text if len(text) <= 320 else split_sentences(text)[0]
    full = " ".join(t for _, _, t in sections)
    sents = split_sentences(full)
    return sents[-1] if sents else None


def parse_article(art):
    sections = []
    for a in art.iter("AbstractText"):
        sections.append((a.get("Label"), a.get("NlmCategory"), "".join(a.itertext()).strip()))
    doi = next((e.text for e in art.iter("ArticleId") if e.get("IdType") == "doi"), None)
    return {
        "title": "".join(art.find(".//ArticleTitle").itertext()).strip(),
        "doi": doi,
        "conclusionEn": conclusion(sections) if sections else None,
    }


def parse_date(summary):
    """esummary sortpubdate: '2026/10/15 00:00'"""
    try:
        return datetime.strptime((summary.get("sortpubdate") or "")[:10], "%Y/%m/%d").date()
    except ValueError:
        return None


def validate(articles):
    errors = []
    if len(articles) != N_YEAR + N_NEW:
        errors.append(f"makale sayısı {len(articles)} != {N_YEAR + N_NEW}")
    if len({a["pmid"] for a in articles}) != len(articles):
        errors.append("tekrarlanan pmid")
    for a in articles:
        for k in ("pmid", "url", "topic", "journal", "title", "punchTr", "conclusionEn"):
            if not a.get(k):
                errors.append(f"{a.get('pmid')}: {k} boş")
        if len(a.get("punchTr") or "") > PUNCH_MAX:
            errors.append(f"{a['pmid']}: punchTr {len(a['punchTr'])} > {PUNCH_MAX}")
    return errors


# ---------- komutlar ----------

def _journal_filter():
    return "(" + " OR ".join(f'"{j}"[ta]' for j in JOURNALS) + ")"


def cmd_fetch():
    today = date.today()
    topic_of, new_ids = {}, set()
    for topic, q in TOPICS.items():
        term = f"({q}) AND {_journal_filter()} AND hasabstract {EXCLUDE}"
        for p in esearch(term, 365):
            topic_of.setdefault(p, topic)
        new_ids.update(esearch(term, 30))
        print(f"{topic}: toplam {len(topic_of)}", file=sys.stderr)
    pmids = list(topic_of)
    summ = esummary(pmids)
    cites = icite(pmids)

    def info(p):
        s = summ.get(p, {})
        return s.get("source", ""), s.get("pubtype", []), parse_date(s) or today

    year_pool = sorted(pmids, key=lambda p: (year_score(cites.get(p, 0), info(p)[2], today),
                                             new_score(info(p)[0], info(p)[1])), reverse=True)
    new_pool = sorted([p for p in pmids if p in new_ids],
                      key=lambda p: (new_score(*info(p)[:2]), info(p)[2]), reverse=True)

    # sonuç cümlesi çıkmayanları elemek için yedekli çek
    y_ids, n_ids = select(year_pool, new_pool, N_YEAR + 30, N_NEW + 15)
    arts = {p: parse_article(a) for p, a in efetch(y_ids + n_ids).items()}
    ok = lambda p: p in arts and arts[p]["conclusionEn"]
    y_ids, n_ids = select([p for p in year_pool if p in y_ids and ok(p)],
                          [p for p in new_pool if p in n_ids and ok(p)])

    out = []
    for p in y_ids + n_ids:
        src, pts, d = info(p)
        label, _ = study_type(pts)
        a = arts[p]
        out.append({
            "pmid": p, "doi": a["doi"],
            "url": f"https://doi.org/{a['doi']}" if a["doi"] else f"https://pubmed.ncbi.nlm.nih.gov/{p}/",
            "topic": topic_of[p], "journal": src, "date": d.strftime("%Y-%m"),
            "citations": cites.get(p, 0), "isNew": p in n_ids, "studyType": label,
            "title": a["title"], "conclusionEn": a["conclusionEn"],
        })
    summaries = _load(CACHE / "summaries.json", {})
    pending = [{"pmid": a["pmid"], "title": a["title"], "conclusionEn": a["conclusionEn"]}
               for a in out if a["pmid"] not in summaries]
    _dump(DATA / "candidates.json", out)
    _dump(DATA / "pending.json", pending)
    print(f"seçildi: {len(y_ids)} yıllık + {len(n_ids)} yeni; özet bekleyen: {len(pending)}")


def cmd_publish():
    cand = _load(DATA / "candidates.json", [])
    summaries = _load(CACHE / "summaries.json", {})
    articles = [{**a, "punchTr": summaries.get(a["pmid"], "")} for a in cand]
    errors = validate(articles)
    if errors:
        print("DOĞRULAMA BAŞARISIZ, articles.json değiştirilmedi:\n  " + "\n  ".join(errors[:30]))
        sys.exit(1)
    _dump(DATA / "articles.json", {"updated": datetime.now().isoformat(timespec="minutes"), "articles": articles})
    (DATA / "pending.json").unlink(missing_ok=True)
    print(f"yayınlandı: {len(articles)} makale")


def _load(p, default):
    return json.loads(p.read_text()) if p.exists() else default


def _dump(p, obj):
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(obj, ensure_ascii=False, indent=1) + "\n")


if __name__ == "__main__":
    {"fetch": cmd_fetch, "publish": cmd_publish}[sys.argv[1]]()
