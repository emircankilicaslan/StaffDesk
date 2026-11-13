// Demo verisi yükler: birimler, personel, izin talepleri (onaylı/bekleyen/reddedilen) ve demirbaşlar.
// Kullanım: ADMIN_EMAIL=... ADMIN_PASSWORD=... node scripts/demo-verisi.mjs [http://localhost:8080]
const B = process.argv[2] || 'http://localhost:8080';
const YONETICI_EPOSTA = process.env.ADMIN_EMAIL, YONETICI_SIFRE = process.env.ADMIN_PASSWORD;
if (!YONETICI_EPOSTA || !YONETICI_SIFRE) { console.error('ADMIN_EMAIL ve ADMIN_PASSWORD gerekli (uygulamayı başlatırken verdiğiniz değerler).'); process.exit(1); }
// Tarihler bugüne göre hesaplanır (geçmiş tarihe yıllık izin talep edilemez). Resmi tatil tanımı olan bir yıl içinde çalıştırın.
const gun = (n) => new Date(Date.now() + n * 864e5).toISOString().slice(0, 10);
const SIFRE = 'Demo-Sifre-123';
async function api(yol, yontem = 'GET', govde, token) {
  const r = await fetch(B + yol, { method: yontem, headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) }, body: govde ? JSON.stringify(govde) : undefined });
  const t = await r.text();
  if (!r.ok) throw new Error(`${yontem} ${yol} -> ${r.status} ${t.slice(0, 200)}`);
  return t ? JSON.parse(t) : null;
}
const giris = async (email, password) => (await api('/api/auth/login', 'POST', { email, password })).token;
const admin = await giris(YONETICI_EPOSTA, YONETICI_SIFRE);

const birim = {};
for (const ad of ['Bilgi İşlem', 'Mali İşler', 'İnsan Kaynakları', 'Satın Alma']) birim[ad] = (await api('/api/departments', 'POST', { name: ad }, admin)).id;

const kisiler = [
  ['1001', 'Kerem Aydın', 'kerem.aydin@ornek.test', 'MANAGER', 'Bilgi İşlem Müdürü', 'Bilgi İşlem', '2015-03-02'],
  ['1002', 'Selin Yıldız', 'selin.yildiz@ornek.test', 'MANAGER', 'Mali İşler Müdürü', 'Mali İşler', '2012-09-17'],
  ['1010', 'Zeynep Kaya', 'zeynep.kaya@ornek.test', 'EMPLOYEE', 'Yazılım Uzmanı', 'Bilgi İşlem', '2019-06-10'],
  ['1011', 'Mert Demir', 'mert.demir@ornek.test', 'EMPLOYEE', 'Sistem Yöneticisi', 'Bilgi İşlem', '2021-01-18'],
  ['1012', 'Deniz Arslan', 'deniz.arslan@ornek.test', 'EMPLOYEE', 'Veri Analisti', 'Bilgi İşlem', '2023-04-03'],
  ['1020', 'Elif Çelik', 'elif.celik@ornek.test', 'EMPLOYEE', 'Muhasebe Uzmanı', 'Mali İşler', '2017-11-06'],
  ['1030', 'Burak Şahin', 'burak.sahin@ornek.test', 'EMPLOYEE', 'İK Uzmanı', 'İnsan Kaynakları', '2020-02-24'],
];
const id = {};
for (const [no, ad, email, role, unvan, b, ise] of kisiler)
  id[email] = (await api('/api/employees', 'POST', { registrationNo: no, fullName: ad, email, password: SIFRE, role, title: unvan, departmentId: birim[b], hireDate: ise }, admin)).id;

const t = {};
for (const [, , email] of kisiler) t[email] = await giris(email, SIFRE);
const izin = (e, type, startDate, endDate, reason) => api('/api/leaves', 'POST', { type, startDate, endDate, reason }, t[e]);

const l1 = await izin('zeynep.kaya@ornek.test', 'ANNUAL', gun(24), gun(28), 'Yıl sonu tatili');
const l2 = await izin('zeynep.kaya@ornek.test', 'ANNUAL', gun(45), gun(47), 'Aile ziyareti');
const l3 = await izin('mert.demir@ornek.test', 'ANNUAL', gun(10), gun(12), 'Taşınma');
const l4 = await izin('mert.demir@ornek.test', 'EXCUSE', gun(7), gun(7), 'Resmi işlem');
const l5 = await izin('deniz.arslan@ornek.test', 'ANNUAL', gun(31), gun(35), null);
const l6 = await izin('elif.celik@ornek.test', 'ANNUAL', gun(38), gun(41), 'Yılbaşı öncesi dinlenme');
const l7 = await izin('elif.celik@ornek.test', 'SICK', gun(-3), gun(-2), 'Grip');
const l8 = await izin('burak.sahin@ornek.test', 'ANNUAL', gun(17), gun(19), null);
const l9 = await izin('kerem.aydin@ornek.test', 'ANNUAL', gun(18), gun(20), 'Kısa tatil');
const l10 = await izin('deniz.arslan@ornek.test', 'EXCUSE', gun(5), gun(5), 'Okul toplantısı');

const kerem = t['kerem.aydin@ornek.test'], selin = t['selin.yildiz@ornek.test'];
await api(`/api/leaves/${l1.id}/approve`, 'POST', { note: 'İyi tatiller.' }, kerem);
await api(`/api/leaves/${l4.id}/approve`, 'POST', null, kerem);
await api(`/api/leaves/${l10.id}/reject`, 'POST', { note: 'Aynı gün proje teslimi var; farklı güne alalım.' }, kerem);
await api(`/api/leaves/${l6.id}/approve`, 'POST', { note: 'Onaylandı.' }, selin);
await api(`/api/leaves/${l7.id}/approve`, 'POST', null, selin);
await api(`/api/leaves/${l8.id}/approve`, 'POST', null, admin).catch(() => {});

const varlik = [['LT-0101', 'Dizüstü bilgisayar - ThinkPad T14', 'Dizüstü bilgisayar'], ['LT-0102', 'Dizüstü bilgisayar - ThinkPad T14', 'Dizüstü bilgisayar'], ['LT-0103', 'Dizüstü bilgisayar - MacBook Pro 14', 'Dizüstü bilgisayar'],
  ['MN-0201', 'Monitör - Dell 27" 4K', 'Monitör'], ['MN-0202', 'Monitör - Dell 27" 4K', 'Monitör'], ['PH-0301', 'Cep telefonu - iPhone 15', 'Telefon'], ['AC-0401', 'Yerleşik dock istasyonu', 'Aksesuar'], ['LT-0104', 'Dizüstü bilgisayar - eski model', 'Dizüstü bilgisayar']];
const v = [];
for (const [assetTag, name, category] of varlik) v.push(await api('/api/assets', 'POST', { assetTag, name, category }, admin));
const ver = (i, mail, not) => api(`/api/assets/${v[i].id}/assign`, 'POST', { employeeId: id[mail], note: not }, admin);
await ver(0, 'zeynep.kaya@ornek.test', 'İşe başlangıç teslimi');
await ver(1, 'mert.demir@ornek.test', null);
await ver(2, 'kerem.aydin@ornek.test', null);
await ver(3, 'zeynep.kaya@ornek.test', null);
await ver(5, 'selin.yildiz@ornek.test', 'Yönetici hattı');
const g = await ver(6, 'elif.celik@ornek.test', null);
await api(`/api/assets/${v[6].id}/return`, 'POST', { note: 'Arızalı, iade alındı' }, admin);
await api(`/api/assets/${v[7].id}/retire`, 'POST', null, admin);
console.log('tamam', Object.keys(id).length, 'personel');
