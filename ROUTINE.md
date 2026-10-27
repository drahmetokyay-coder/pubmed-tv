# Gecelik görev talimatı (Claude Code)

Bu depo her gece güncellenir. Görevi çalıştıran ajan şu adımları izler:

1. `python3 pipeline/pubmed_tv.py fetch`
   - PubMed + iCite'tan 100 makale seçer, `data/candidates.json` ve `data/pending.json` yazar.
   - Hata verirse (ağ vb.) **hiçbir şeyi commit etme**, dur. TV dünkü listeyi göstermeye devam eder.
2. `data/pending.json` içindeki her makale için `data/cache/summaries.json` dosyasına
   `"<pmid>": "<Türkçe cümle>"` ekle. Kurallar:
   - Tek cümle, **en fazla 140 karakter**, Türkçe, hekim okuru için.
   - Yalnızca `conclusionEn` ve `title`'da yazanı söyle; yeni bilgi, abartı, kesinlik ekleme
     ("kanıtladı" değil "gösterdi"; "eğiliminde", "ilişkili" gibi nüansları koru).
   - Varsa sayıyı/çalışma adını koru (örn. "RECOVERY 10 yıl:", "%40").
   - Kısaltmalar Türkçe kullanımdaki gibi: DVT, PE, AAA, EVAR, TEVAR, TAVR/TAVI, CABG, KLTI, PAH, MY, TR.
   - Var olan özetlere dokunma.
3. `python3 pipeline/pubmed_tv.py publish` — doğrulama geçmezse düzelt ve tekrar çalıştır;
   düzeltilemiyorsa commit etme.
4. `cd pipeline && python3 -m unittest test_pubmed_tv`
5. `data/` değişikliklerini `Günlük güncelleme YYYY-MM-DD` mesajıyla `main`'e commit edip push et.
