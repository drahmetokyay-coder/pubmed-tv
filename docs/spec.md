# PubMed TV — tasarım

**Amaç:** TCL TV boştayken (ekran koruyucu) kalp-damar cerrahisinin güncel ve öne çıkan 100 PubMed makalesini
slayt olarak göstermek. Her slaytta Türkçe vurucu sonuç cümlesi, orijinal İngilizce sonuç cümlesi ve
telefondan okutulup makaleye gidilen QR kod bulunur.

## Veri hattı (her gece, Claude Code zamanlanmış görevi)
- Aday: 9 konu × 14 dergi PubMed sorgusu (yorum/editöryal/mektup hariç, özeti olanlar), son 365 gün ve son 30 gün (Entrez tarihi).
- Sıralama: Altmetric artık ücretli anahtar istediği için (Kasım 2025) kullanılmıyor.
  - Yıllık havuz: NIH iCite atıf sayısı / ay (atıf hızı); eşitlikte dergi kademesi × çalışma türü.
  - Yeni havuz (30 gün): dergi kademesi × çalışma türü ağırlığı (kılavuz 1.7, meta-analiz 1.6, RCT 1.5, çok merkezli 1.2, derleme 1.1).
- Seçim: önce 30 yeni, sonra tekrarsız 70 yıllık. Sonuç cümlesi çıkarılamayanlar elenir.
- Sonuç cümlesi: CONCLUSIONS bölümü kelimesi kelimesine (320 karakteri aşarsa ilk cümle; finansman künyesi atılır),
  yapılandırılmamış özette son cümle.
- Türkçe cümle: yalnızca listeye yeni girenler için ajan yazar (`ROUTINE.md`), `data/cache/summaries.json`'da saklanır.
- Yayın: `publish` doğrulaması (100 makale, tekrarsız, boş alan yok, ≤140 karakter) geçerse `data/articles.json` commit edilir;
  geçmezse dünkü dosya kalır.

## TV uygulaması (`android/`)
- Ayrı APK, Android DreamService (ekran koruyucu) + elle açmak için launcher etkinliği; ikisi aynı slayt görünümünü kullanır.
- `articles.json` jsDelivr CDN üzerinden açılışta ve 6 saatte bir çekilir; son başarılı liste cihazda saklanır.
- Slayt 30 sn, yumuşak geçiş, her tur karışık sıra ve tekrarsız. Tasarım A: beyaz zemin, #E5383B vurgu, Inter yazı tipi.
- QR cihazda üretilir: DOI varsa doi.org, yoksa PubMed sayfası.
- Ekran koruyucu adb ile atanır (Google TV menüde üçüncü parti ekran koruyucu göstermiyor), 5 dk boşta kalınca başlar.
- Kumanda: herhangi bir tuş ekran koruyucudan çıkar. AirPlay bağlantısı geldiğinde AirPlay uygulaması öne gelir.

## Kapsam dışı
Favori/beğeni, konu filtresi menüsü, sesli okuma, Altmetric skoru (anahtar alınırsa eklenebilir).
