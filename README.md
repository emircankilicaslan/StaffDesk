# StaffDesk – Personel İzin ve Zimmet Yönetimi API'si

![CI](https://github.com/emircankilicaslan/StaffDesk/actions/workflows/ci.yml/badge.svg) ![Lisans: MIT](https://img.shields.io/badge/Lisans-MIT-green.svg)

Kurumlarda personelin **yıllık izin/mazeret/hastalık izin** süreçlerini ve **demirbaş zimmetlerini** yöneten REST API.
**Java 21, Spring Boot 3, Spring Data JPA (Hibernate), Spring Security (JWT), Flyway, PostgreSQL, JUnit 5** ile geliştirildi.

## Özellikler
- **İzin yönetimi:** talep → onay/red/iptal akışı; iş günü hesabı (hafta sonu ve resmi tatiller sayılmaz), çakışma kontrolü, yıllık izin bakiyesi ve hak ediş kuralı.
- **Zimmet yönetimi:** demirbaş kaydı, personele zimmetleme, iade, kayıttan düşme; tüm geçmiş korunur, aynı demirbaş aynı anda iki kişiye verilemez.
- **Roller ve yetki:** `ADMIN`, `MANAGER`, `EMPLOYEE`; müdür yalnızca kendi birimindeki personeli görür ve onaylar, kimse kendi talebini onaylayamaz.
- **Raporlama:** birim bazında yıllık izin kullanımı (doğrudan SQL, `LEFT JOIN` + `GROUP BY`).
- **JWT kimlik doğrulama**, BCrypt şifreleme, RFC 7807 hata yanıtları, doğrulama (Bean Validation), sayfalama, OpenAPI/Swagger (`/swagger-ui.html`, sağ üstteki **Authorize** düğmesiyle JWT girilebilir), sağlık ucu (`/actuator/health`).

## Web arayüzü
Uygulama çalışırken kök adres (`/`) rol bazlı bir web arayüzü sunar. Ek bir derleme adımı yoktur; dosyalar `src/main/resources/static` altındadır (sade HTML, CSS ve JavaScript, harici bağımlılık yok).

| Rol | Gördükleri |
|---|---|
| **Personel** | Genel bakış (izin bakiyesi halka grafiği), izin talebi ve iptali, üzerindeki zimmetler |
| **Müdür** | Yukarıdakilere ek: kendi birimindeki talepler için onay kutusu (onayla / gerekçeyle reddet), birim personeli |
| **Yönetici** | Personel ve birim ekleme, pasife alma, demirbaş ekleme / zimmetleme / iade / kayıttan düşme, birim bazlı izin kullanım raporu |

Güvenlik: metinler `textContent` ile yazılır (`innerHTML` yok), sayfa `Content-Security-Policy` ile sunulur ve satır içi betik/stil kullanmaz; token `sessionStorage`'da tutulur ve sekme kapanınca silinir. Yetki kararları yine sunucuda verilir, arayüz yalnızca yetkili olunan seçenekleri gösterir.

![Giriş ekranı](docs/screenshots/arayuz-giris.jpg)

![Genel bakış: izin bakiyesi ve son talepler](docs/screenshots/arayuz-genel-bakis.jpg)

![Müdür onay kutusu](docs/screenshots/arayuz-onay-kutusu.jpg)

| Personel yönetimi | Demirbaş envanteri |
|---|---|
| ![Personel](docs/screenshots/arayuz-personel.jpg) | ![Demirbaşlar](docs/screenshots/arayuz-demirbas.jpg) |

![Birim bazlı izin kullanım raporu](docs/screenshots/arayuz-rapor.jpg)

| İzin talebi | Mobil görünüm |
|---|---|
| ![İzin talebi penceresi](docs/screenshots/arayuz-izin-talebi.jpg) | <img src="docs/screenshots/arayuz-mobil.jpg" width="260" alt="Mobil görünüm"> |

**Denemek için:** uygulamayı Docker ile başlatın (aşağıda), ardından demo verisini yükleyin. Betik birimler, yedi personel, farklı durumlarda izin talepleri ve demirbaşlar oluşturur (Node 18+ gerekir):
```bash
ADMIN_EMAIL=<yönetici e-postası> ADMIN_PASSWORD=<yönetici şifresi> node scripts/demo-verisi.mjs
```
Oluşan demo kullanıcılarının (ör. `kerem.aydin@ornek.test` müdür, `zeynep.kaya@ornek.test` personel) ortak şifresi betiğin başında yazar; yalnızca yerel denemede kullanın.

## API ekran görüntüleri

PostgreSQL üzerinde çalışan uygulamanın Swagger arayüzü (demo verisiyle). Sağ üstteki **Authorize** düğmesine `/api/auth/login` ile alınan token girilir:

![Swagger arayüzü](docs/screenshots/swagger.jpg)

Yönetici olarak çalıştırılan birim bazlı izin kullanım raporu (3 birim, onaylı izin günleri ve bekleyen talepler):

![Birim raporu](docs/screenshots/birim-raporu.jpg)
## İş kuralları
| Kural | Ayrıntı |
|---|---|
| Hak ediş (örnek) | Hizmet süresi 1 yıldan az: 0 gün, 1–10 yıl: 20 gün, 10 yıl ve üzeri: 30 gün (kamu personel rejimindeki kademeye benzer örnek kural; kurum mevzuatına göre uyarlanmalıdır) |
| İş günü | Cumartesi/Pazar ve `staffdesk.holidays` içindeki tarihler sayılmaz (dini bayramlar her yıl eklenmelidir) |
| Yıllık izin | Geçmiş tarih için talep edilemez; kalan bakiye (hak − onaylı − bekleyen) aşılamaz |
| Hastalık izni | En fazla 30 gün geriye dönük bildirilebilir |
| Çakışma | Bekleyen/onaylı izinle çakışan talep reddedilir |
| Durum akışı | `PENDING → APPROVED / REJECTED / CANCELLED`; yalnızca bekleyen talep üzerinde işlem yapılır |
| Zimmet | `AVAILABLE → ASSIGNED → AVAILABLE`, `AVAILABLE → RETIRED` |

## Mimari
```
api/         Controller'lar, DTO'lar, merkezi hata yönetimi (ProblemDetail), CurrentUser
service/     İş kuralları: LeaveService, AssetService, EmployeeService, AuthService, ReportService,
             saf hesaplayıcılar (LeaveEntitlementCalculator, BusinessDayCalculator), AccessPolicy
domain/      JPA varlıkları ve durum makineleri (LeaveRequest, Asset, ...)
repository/  Spring Data JPA arayüzleri (JPQL sorguları, satır kilidi)
config/      Güvenlik (JWT), ayarlar (@ConfigurationProperties), ilk yönetici
db/migration Flyway SQL migrasyonları
```

Tasarım kararları:
- **Şema yalnızca Flyway ile yönetilir**, Hibernate `ddl-auto: validate` ile eşleşmeyi doğrular. Migrasyon SQL'i taşınabilir olduğundan testler H2'de (PostgreSQL kipi), üretim PostgreSQL'de çalışır.
- **Eşzamanlılık:** zimmetlemede demirbaş satırı `PESSIMISTIC_WRITE` ile kilitlenir.
- **Zaman bağımlılığı** (`Clock`) enjekte edilir; testler tarihi sabitleyerek deterministik çalışır.
- **Yetki iki katmanlıdır:** uç nokta düzeyinde `@PreAuthorize`, kayıt düzeyinde `AccessPolicy` (birim kapsamı).
- Entity'ler API'ye doğrudan açılmaz (DTO kullanılır); `open-in-view` kapalıdır.

## API özeti
| Yöntem | Yol | Yetki |
|---|---|---|
| POST | `/api/auth/login` | herkese açık |
| POST/GET | `/api/departments` | ADMIN / giriş yapmış |
| POST/GET | `/api/employees` | ADMIN / ADMIN, MANAGER |
| GET | `/api/employees/me`, `/api/employees/{id}` | kendisi, yönetici, birim müdürü |
| GET | `/api/employees/{id}/leave-balance?year=` | kendisi, yönetici, birim müdürü |
| PATCH | `/api/employees/{id}/deactivate` | ADMIN |
| POST | `/api/leaves` | giriş yapmış (kendi adına) |
| GET | `/api/leaves/mine?page=&size=` | giriş yapmış (sayfalı, en yeni başlangıç önce) |
| GET | `/api/leaves?status=PENDING` | ADMIN, MANAGER (birim kapsamlı) |
| POST | `/api/leaves/{id}/approve`, `/reject` (isteğe bağlı `{"note": "..."}` gerekçesi) | ADMIN, MANAGER |
| POST | `/api/leaves/{id}/cancel` | talep sahibi |
| POST/GET | `/api/assets` | ADMIN / ADMIN, MANAGER |
| POST | `/api/assets/{id}/assign`, `/return`, `/retire` | ADMIN |
| GET | `/api/employees/{id}/assets` | kendisi, yönetici, birim müdürü |
| GET | `/api/reports/leave-usage?year=` | ADMIN |

## Çalıştırma
Gereksinimler: Java 21, Maven, PostgreSQL (veya Docker).

```bash
# Docker ile (PostgreSQL dahil)
cp .env.example .env     # DB_PASSWORD, JWT_SECRET ve ilk yönetici bilgilerini doldurun
docker compose up --build   # http://localhost:8080/swagger-ui.html

# Yerelde
export DB_URL=jdbc:postgresql://localhost:5432/staffdesk DB_USER=staffdesk DB_PASSWORD=...
export JWT_SECRET=<en-az-32-karakter> ADMIN_EMAIL=admin@ornek.com ADMIN_PASSWORD=<en-az-8-karakter>
mvn spring-boot:run
```
Veritabanında hiç `ADMIN` yoksa, `ADMIN_EMAIL`/`ADMIN_PASSWORD` ile açılışta ilk yönetici oluşturulur. `JWT_SECRET` yoksa uygulama başlamaz.

## Test ve CI
```bash
mvn verify      # 40 test
```
- Birim testleri: hak ediş hesabı, iş günü hesabı, durum makineleri.
- Uçtan uca testler (`MockMvc`, gerçek Spring bağlamı + Flyway + H2): kimlik doğrulama, rol/kapsam kontrolleri, izin kuralları (çakışma, bakiye, tatil, geçmiş tarih), onay akışı, zimmet yaşam döngüsü, rapor, doğrulama hataları.
- Uygulama ayrıca gerçek PostgreSQL üzerinde (Flyway + Hibernate doğrulaması + rapor sorgusu) elle denenmiştir.
- GitHub Actions her push ve pull request'te `mvn verify` çalıştırır.

## Bilinen sınırlar / sonraki adımlar
Token yenileme ve iptal (revocation) yok; pasif yapılan personelin mevcut token'ı süresi dolana kadar geçerlidir. Giriş denemeleri e-posta bazında sınırlıdır (art arda hatalı denemede geçici kilit), ancak sayaç bellekte tutulur ve yalnızca tek uygulama örneği için yeterlidir. Resmi tatiller yapılandırmadan elle girilir. Bildirim (e-posta) ve dosya eki (rapor/belge) desteği yok.

## Lisans

Bu proje [MIT Lisansı](LICENSE) ile paylaşılmaktadır.
