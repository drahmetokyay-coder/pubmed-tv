# PubMed TV

Kalp-damar cerrahisinde öne çıkan güncel 100 PubMed makalesini TV ekran koruyucusunda slayt olarak gösterir:
Türkçe vurucu sonuç cümlesi + orijinal sonuç cümlesi + makaleye giden QR kod.

- `data/articles.json` — TV'nin okuduğu liste (her gece güncellenir)
- `pipeline/` — PubMed (NCBI E-utilities) + NIH iCite veri hattı, yalnızca Python standart kütüphanesi
- `android/` — Android TV uygulaması (ekran koruyucu)
- `ROUTINE.md` — gecelik güncelleme talimatı, `docs/spec.md` — tasarım

Türkçe cümleler makale özetlerinin sonuç bölümünden yapay zekâ ile üretilmiştir; klinik karar için
orijinal makaleye başvurun.
