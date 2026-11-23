"use strict";

// ---------------------------------------------------------------------------
// Yardımcılar
// ---------------------------------------------------------------------------

const $ = (s, k = document) => k.querySelector(s);
const $$ = (s, k = document) => [...k.querySelectorAll(s)];
const SVG = "http://www.w3.org/2000/svg";

/** Güvenli DOM üretimi: metinler textContent olarak eklenir, innerHTML kullanılmaz. */
function h(etiket, ozellikler = {}, ...cocuklar) {
  const e = svgEtiketi(etiket) ? document.createElementNS(SVG, etiket) : document.createElement(etiket);
  for (const [k, v] of Object.entries(ozellikler || {})) {
    if (v === false || v == null) continue;
    if (k === "class") e.setAttribute("class", v);
    else if (k.startsWith("on")) e.addEventListener(k.slice(2), v);
    else if (k === "value" && "value" in e) e.value = v;
    else e.setAttribute(k, v === true ? "" : v);
  }
  for (const c of cocuklar.flat(Infinity)) if (c != null && c !== false) e.append(c.nodeType ? c : String(c));
  return e;
}
const svgEtiketi = (t) => ["svg", "path", "circle", "rect", "text", "g"].includes(t);

const ROL_AD = { ADMIN: "Yönetici", MANAGER: "Müdür", EMPLOYEE: "Personel" };
const TUR_AD = { ANNUAL: "Yıllık izin", SICK: "Hastalık izni", EXCUSE: "Mazeret izni" };
const IZIN_DURUM = { PENDING: "Onay bekliyor", APPROVED: "Onaylandı", REJECTED: "Reddedildi", CANCELLED: "İptal edildi" };
const ZIMMET_DURUM = { AVAILABLE: "Müsait", ASSIGNED: "Zimmetli", RETIRED: "Kayıttan düşüldü" };

const AYLAR = ["Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara"];
function tarih(s) {
  if (!s) return "–";
  const [y, a, g] = String(s).slice(0, 10).split("-").map(Number);
  return `${g} ${AYLAR[a - 1]} ${y}`;
}
const tarihSaat = (s) => (s ? `${tarih(s)} ${String(s).slice(11, 16)}` : "–");
const aralik = (b, s) => (b === s ? tarih(b) : `${tarih(b)} – ${tarih(s)}`);
const bugun = () => new Date().toISOString().slice(0, 10);
const bas = (ad) => (ad || "?").trim().split(/\s+/).slice(0, 2).map((p) => p[0]).join("").toLocaleUpperCase("tr-TR");

const ikon = (...yollar) =>
  h("svg", { viewBox: "0 0 24 24", fill: "none", stroke: "currentColor", "stroke-width": "1.8", "stroke-linecap": "round", "stroke-linejoin": "round", "aria-hidden": "true" },
    yollar.map((d) => h("path", { d })));
const IKONLAR = {
  ozet: () => ikon("M3 12l9-8 9 8", "M5 10v10h5v-6h4v6h5V10"),
  izin: () => ikon("M8 3v4M16 3v4M4 9h16", "M5 5h14a1 1 0 011 1v13a1 1 0 01-1 1H5a1 1 0 01-1-1V6a1 1 0 011-1z"),
  onay: () => ikon("M5 13l4 4L19 7"),
  personel: () => ikon("M16 20v-1a4 4 0 00-4-4H8a4 4 0 00-4 4v1", "M10 11a4 4 0 100-8 4 4 0 000 8", "M20 20v-1a4 4 0 00-3-3.9", "M15 3.2a4 4 0 010 7.6"),
  zimmetim: () => ikon("M4 7h16v11a1 1 0 01-1 1H5a1 1 0 01-1-1z", "M9 7V5a1 1 0 011-1h4a1 1 0 011 1v2"),
  zimmet: () => ikon("M3 7l9-4 9 4-9 4z", "M3 12l9 4 9-4", "M3 17l9 4 9-4"),
  rapor: () => ikon("M4 20V10", "M10 20V4", "M16 20v-7", "M22 20H2"),
  denetim: () => ikon("M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2", "M9 3h6v4H9z", "M9 12h6", "M9 16h4"),
  takvim: () => ikon("M8 3v4M16 3v4M4 9h16", "M5 5h14a1 1 0 011 1v13a1 1 0 01-1 1H5a1 1 0 01-1-1V6a1 1 0 011-1z", "M8 13h2M12 13h2M8 16.5h2"),
};

/** Çubuk genişlikleri satır içi stil yerine CSSOM ile verilir (CSP satır içi stile izin vermez). */
function genislikleriUygula(kok = document) {
  $$("[data-genislik]", kok).forEach((s) => { s.style.width = `${s.dataset.genislik}%`; });
}

/** Yatay çubuk listesi: [{etiket, deger, yazi?}]. */
function cubukListesi(satirlar, birim = "") {
  const en = Math.max(1, ...satirlar.map((s) => s.deger));
  return h("div", { class: "grafik" }, satirlar.map((s) => h("div", { class: "grafik-satir" },
    h("span", { class: "grafik-etiket", title: s.etiket }, s.etiket),
    h("div", { class: "grafik-raylar" }, h("div", { class: "grafik-ray", role: "img", "aria-label": `${s.etiket}: ${s.deger}` },
      h("span", { class: "dolgu", "data-genislik": s.deger ? Math.max(3, Math.round((s.deger / en) * 100)) : 0 }))),
    h("span", { class: "sag-yazi" }, s.yazi ?? `${s.deger}${birim}`))));
}

const ayEtiketi = (ym) => { const [y, a] = ym.split("-").map(Number); return `${AYLAR[a - 1]} ${y}`; };

/** Arama çubuğu: Enter ile uygular. */
function aramaKutusu(deger, yer, uygula) {
  const k = h("input", { type: "search", placeholder: yer, "aria-label": yer, value: deger });
  k.addEventListener("keydown", (e) => { if (e.key === "Enter") uygula(k.value.trim()); });
  return k;
}
function secim(etiket, secenekler, deger, degisti) {
  return h("select", { "aria-label": etiket, onchange: (e) => degisti(e.target.value) },
    secenekler.map(([k, v]) => h("option", { value: k, selected: String(k) === String(deger) }, v)));
}
function sorguOlustur(nesne) {
  const p = new URLSearchParams();
  for (const [k, v] of Object.entries(nesne)) if (v !== "" && v != null) p.set(k, v);
  return p;
}
/** Spring Data sayfa bilgisi (totalElements, totalPages) yanıtta "page" altında döner. */
const sayfaBilgisi = (veri) => veri.page ?? veri;
const bosIkon = () => ikon("M4 4h16v16H4z", "M4 9h16", "M9 9v11");

const rozet = (sinif, metin) => h("span", { class: `rozet r-${sinif.toLowerCase()}` }, metin);

// ---------------------------------------------------------------------------
// API
// ---------------------------------------------------------------------------

class ApiHatasi extends Error {
  constructor(durum, mesaj) { super(mesaj); this.durum = durum; }
}
const Oturum = {
  oku: () => { try { return JSON.parse(sessionStorage.getItem("oturum") || "null"); } catch { return null; } },
  yaz: (o) => sessionStorage.setItem("oturum", JSON.stringify(o)),
  sil: () => sessionStorage.removeItem("oturum"),
};

async function api(yol, { yontem = "GET", govde } = {}) {
  const o = Oturum.oku();
  const basliklar = {};
  if (govde !== undefined) basliklar["Content-Type"] = "application/json";
  if (o?.token) basliklar.Authorization = `Bearer ${o.token}`;
  const r = await fetch(yol, { method: yontem, headers: basliklar, body: govde !== undefined ? JSON.stringify(govde) : undefined });
  if (r.status === 401 && o?.token && yol !== "/api/auth/login") {
    cikis("Oturum süresi doldu. Lütfen yeniden giriş yapın.");
    throw new ApiHatasi(401, "Oturum süresi doldu.");
  }
  if (!r.ok) {
    let mesaj = `İstek başarısız (${r.status}).`;
    try {
      const p = await r.json();
      mesaj = p.detail || p.title || mesaj;
      if (p.errors || p.violations) mesaj = Object.values(p.errors || p.violations).flat().join(" ");
    } catch { /* gövde yok */ }
    if (r.status === 429) mesaj = "Çok fazla deneme. Biraz bekleyip tekrar deneyin.";
    throw new ApiHatasi(r.status, mesaj);
  }
  return r.status === 204 ? null : r.json();
}

/** Yetkili isteği (Bearer) ile dosyayı indirir (Excel); JSON olmayan yanıtlar için api() yerine kullanılır. */
async function dosyaIndir(yol, dosyaAdi) {
  const o = Oturum.oku();
  try {
    const r = await fetch(yol, { headers: o?.token ? { Authorization: `Bearer ${o.token}` } : {} });
    if (!r.ok) throw new ApiHatasi(r.status, r.status === 403 ? "Bu işlem için yetkiniz yok." : `İndirme başarısız (${r.status}).`);
    const url = URL.createObjectURL(await r.blob());
    const a = h("a", { href: url, download: dosyaAdi });
    document.body.append(a); a.click(); a.remove();
    URL.revokeObjectURL(url);
    duyur("basari", "Excel dosyası indirildi.");
  } catch (e) { hataGoster(e); }
}

// ---------------------------------------------------------------------------
// Durum ve bildirimler
// ---------------------------------------------------------------------------

const durum = { ben: null, sayfa: "ozet", bekleyen: 0, birimler: [] };
const yonetici = () => durum.ben?.role === "ADMIN";
const mudurVeyaYonetici = () => ["ADMIN", "MANAGER"].includes(durum.ben?.role);

let bildirimZamanlayici;
function duyur(tur, mesaj) {
  const el = $(tur === "hata" ? "#genel-hata" : "#genel-basari");
  $("#genel-hata").hidden = true;
  $("#genel-basari").hidden = true;
  el.textContent = mesaj;
  el.hidden = false;
  clearTimeout(bildirimZamanlayici);
  if (tur !== "hata") bildirimZamanlayici = setTimeout(() => { el.hidden = true; }, 4000);
}
const hataGoster = (e) => duyur("hata", e instanceof Error ? e.message : String(e));
const temizle = () => { $("#genel-hata").hidden = true; $("#genel-basari").hidden = true; };

// ---------------------------------------------------------------------------
// Giriş / çıkış
// ---------------------------------------------------------------------------

$("#giris-form").addEventListener("submit", async (ev) => {
  ev.preventDefault();
  const f = ev.target, hata = $("#giris-hata"), dugme = $("#giris-gonder");
  hata.hidden = true;
  dugme.disabled = true;
  try {
    const y = await api("/api/auth/login", { yontem: "POST", govde: { email: f.email.value.trim(), password: f.password.value } });
    Oturum.yaz({ token: y.token });
    f.reset();
    await uygulamayiAc();
  } catch (e) {
    hata.textContent = e.status === 401 ? "E-posta veya şifre hatalı." : e.message;
    hata.hidden = false;
  } finally { dugme.disabled = false; }
});

function cikis(mesaj) {
  Oturum.sil();
  durum.ben = null;
  $("#uygulama").hidden = true;
  $("#giris").hidden = false;
  const hata = $("#giris-hata");
  hata.textContent = mesaj || "";
  hata.hidden = !mesaj;
}
$("#cikis").addEventListener("click", () => cikis());

$("#sifre-degistir").addEventListener("click", () => formDialog({
  baslik: "Şifreni değiştir", aciklama: "Yeni şifre en az 8 karakter olmalı ve mevcut şifreden farklı olmalı.", onayMetni: "Şifreyi değiştir",
  alanlar: [
    alan("Mevcut şifre", h("input", { name: "currentPassword", type: "password", required: true, autocomplete: "current-password" }), true),
    alan("Yeni şifre", h("input", { name: "newPassword", type: "password", required: true, minlength: "8", maxlength: "72", autocomplete: "new-password" }), true),
    alan("Yeni şifre (tekrar)", h("input", { name: "tekrar", type: "password", required: true, minlength: "8", maxlength: "72", autocomplete: "new-password" }), true),
  ],
  gonder: async (f) => {
    if (f.get("newPassword") !== f.get("tekrar")) throw new Error("Yeni şifreler eşleşmiyor.");
    await api("/api/auth/change-password", { yontem: "POST", govde: { currentPassword: f.get("currentPassword"), newPassword: f.get("newPassword") } });
    duyur("basari", "Şifren değiştirildi.");
  },
}));

// ---------------------------------------------------------------------------
// Gezinme
// ---------------------------------------------------------------------------

const SAYFALAR = [
  { id: "ozet", ad: "Genel bakış", ikon: "ozet", gorunur: () => true },
  { id: "izinlerim", ad: "İzinlerim", ikon: "izin", gorunur: () => true },
  { id: "takvim", ad: "Ekip takvimi", ikon: "takvim", gorunur: () => true },
  { id: "onay", ad: "Onay kutusu", ikon: "onay", gorunur: mudurVeyaYonetici },
  { id: "personel", ad: "Personel", ikon: "personel", gorunur: mudurVeyaYonetici },
  { id: "zimmetim", ad: "Zimmetlerim", ikon: "zimmetim", gorunur: () => true },
  { id: "zimmet", ad: "Demirbaşlar", ikon: "zimmet", gorunur: mudurVeyaYonetici },
  { id: "rapor", ad: "Raporlar", ikon: "rapor", gorunur: yonetici },
  { id: "denetim", ad: "Denetim kaydı", ikon: "denetim", gorunur: yonetici },
];

function menuyuCiz() {
  $("#menu").replaceChildren(...SAYFALAR.filter((s) => s.gorunur()).map((s) =>
    h("button", { class: s.id === durum.sayfa ? "aktif" : "", onclick: () => sayfaAc(s.id) },
      IKONLAR[s.ikon](), s.ad,
      s.id === "onay" && durum.bekleyen > 0 ? h("span", { class: "sayac" }, durum.bekleyen) : null)));
}

const YUKLEYICILER = { ozet: ozetSayfasi, takvim: takvimSayfasi, izinlerim: izinlerimSayfasi, onay: onaySayfasi, personel: personelSayfasi, zimmetim: zimmetimSayfasi, zimmet: zimmetSayfasi, rapor: raporSayfasi, denetim: denetimSayfasi };

async function sayfaAc(id) {
  durum.sayfa = id;
  $("#yan").classList.remove("acik");
  temizle();
  menuyuCiz();
  const s = SAYFALAR.find((x) => x.id === id);
  $("#sayfa-baslik").textContent = s.ad;
  $("#sayfa-alt").textContent = "";
  $("#ust-eylem").replaceChildren();
  $("#icerik").replaceChildren(h("p", { class: "soluk" }, "Yükleniyor…"));
  try { await YUKLEYICILER[id](); } catch (e) { if (e.durum !== 401) { $("#icerik").replaceChildren(); hataGoster(e); } }
}

$("#menu-ac").addEventListener("click", () => $("#yan").classList.toggle("acik"));

async function bekleyenSayisi() {
  if (!mudurVeyaYonetici()) return;
  try {
    durum.bekleyen = sayfaBilgisi(await api("/api/leaves?status=PENDING&size=1")).totalElements ?? 0;
    menuyuCiz();
  } catch { /* sayaç zorunlu değil */ }
}

async function uygulamayiAc() {
  durum.ben = await api("/api/employees/me");
  $("#kullanici-ad").textContent = durum.ben.fullName;
  $("#kullanici-rol").textContent = ROL_AD[durum.ben.role] ?? durum.ben.role;
  $("#avatar").textContent = bas(durum.ben.fullName);
  $("#giris").hidden = true;
  $("#uygulama").hidden = false;
  await bekleyenSayisi();
  await sayfaAc("ozet");
}

// ---------------------------------------------------------------------------
// Ortak bileşenler
// ---------------------------------------------------------------------------

function bosDurum(baslik, aciklama) {
  return h("div", { class: "bos" }, bosIkon(), h("strong", {}, baslik), aciklama ? h("span", {}, aciklama) : null);
}

function tabloKarti(baslik, sag, tablo, alt) {
  return h("section", { class: "kart" },
    baslik ? h("div", { class: "kart-baslik" }, h("h3", {}, baslik), sag || null) : null,
    h("div", { class: "tablo-kap" }, tablo), alt || null);
}

function sayfalama(sayfa, toplamSayfa, toplam, git) {
  return h("div", { class: "sayfalama" },
    h("span", { class: "soluk kucuk-yazi" }, `${toplam} kayıt · sayfa ${sayfa + 1} / ${Math.max(1, toplamSayfa)}`),
    h("button", { class: "dugme ikincil kucuk", disabled: sayfa <= 0, onclick: () => git(sayfa - 1) }, "‹ Önceki"),
    h("button", { class: "dugme ikincil kucuk", disabled: sayfa + 1 >= toplamSayfa, onclick: () => git(sayfa + 1) }, "Sonraki ›"));
}

const dialog = $("#dialog");
function dialogAc(...icerik) { $("#dialog-icerik").replaceChildren(...icerik); dialog.showModal(); }
const dialogKapat = () => dialog.close();
dialog.addEventListener("click", (e) => { if (e.target === dialog) dialog.close(); });

/** Basit form penceresi: gönder düğmesi sırasında tekrar basılmayı engeller, hatayı pencerede gösterir. */
function formDialog({ baslik, alanlar, onayMetni = "Kaydet", tehlike = false, aciklama, gonder }) {
  const hata = h("p", { class: "hata", role: "alert", hidden: true });
  const form = h("form", { class: "dialog-icerik", style: null },
    h("h2", {}, baslik),
    aciklama ? h("p", { class: "ipucu-kutu" }, aciklama) : null,
    alanlar ? h("div", { class: "form-izgara" }, alanlar) : null,
    hata,
    h("div", { class: "dialog-alt" },
      h("button", { type: "button", class: "dugme ikincil", onclick: dialogKapat }, "Vazgeç"),
      h("button", { type: "submit", class: `dugme ${tehlike ? "tehlike" : "birincil"}` }, onayMetni)));
  form.addEventListener("submit", async (ev) => {
    ev.preventDefault();
    const dugme = $("[type=submit]", form);
    hata.hidden = true;
    dugme.disabled = true;
    try { await gonder(new FormData(form)); dialogKapat(); }
    catch (e) { hata.textContent = e.message; hata.hidden = false; }
    finally { dugme.disabled = false; }
  });
  $("#dialog-icerik").replaceChildren(form);
  dialog.showModal();
}
const alan = (etiket, girdi, tam = false) => h("label", { class: tam ? "tam" : "" }, etiket, girdi);

// ---------------------------------------------------------------------------
// Genel bakış
// ---------------------------------------------------------------------------

function halka(bakiye) {
  const r = 52, c = 2 * Math.PI * r, hak = Math.max(bakiye.entitlement, 1);
  const parca = (oran, renk, ofset) => h("circle", {
    cx: 65, cy: 65, r, fill: "none", stroke: renk, "stroke-width": 12, "stroke-linecap": "butt",
    "stroke-dasharray": `${Math.max(0, oran) * c} ${c}`, "stroke-dashoffset": -ofset * c, transform: "rotate(-90 65 65)",
  });
  const kullanilan = Math.min(bakiye.used / hak, 1), bekleyen = Math.min(bakiye.pending / hak, 1 - kullanilan);
  return h("svg", { class: "halka", viewBox: "0 0 130 130", role: "img", "aria-label": `${bakiye.remaining} gün izin hakkı kaldı` },
    h("circle", { cx: 65, cy: 65, r, fill: "none", stroke: "var(--cizgi)", "stroke-width": 12 }),
    parca(kullanilan, "var(--birincil)", 0),
    parca(bekleyen, "var(--sari)", kullanilan),
    h("text", { x: 65, y: 68, "text-anchor": "middle", class: "halka-metin", fill: "currentColor" }, String(bakiye.remaining)),
    h("text", { x: 65, y: 84, "text-anchor": "middle", class: "halka-alt" }, "gün kaldı"));
}

async function ozetSayfasi() {
  const yil = new Date().getFullYear();
  const [bakiye, izinler, zimmetler, pano] = await Promise.all([
    api(`/api/employees/${durum.ben.id}/leave-balance?year=${yil}`),
    api("/api/leaves/mine?size=5"),
    api(`/api/employees/${durum.ben.id}/assets`),
    api("/api/dashboard"),
  ]);
  const uzerimde = zimmetler.filter((z) => !z.returnedAt);
  const saat = new Date().getHours();
  $("#sayfa-baslik").textContent = `${saat < 12 ? "Günaydın" : saat < 18 ? "İyi günler" : "İyi akşamlar"}, ${durum.ben.fullName.split(" ")[0]}`;
  $("#sayfa-alt").textContent = [durum.ben.title, durum.ben.department].filter(Boolean).join(" · ") || ROL_AD[durum.ben.role];
  $("#ust-eylem").replaceChildren(h("button", { class: "dugme birincil", onclick: () => izinTalebiDialogu(bakiye) }, "+ İzin talep et"));

  const stat = (etiket, deger, sinif, not) => h("div", { class: "kart istatistik" },
    h("span", { class: "etiket" }, etiket), h("span", { class: `deger ${sinif}` }, String(deger)), h("span", { class: "not" }, not));

  $("#icerik").replaceChildren(
    h("div", { class: "izgara" },
      stat(`${yil} yıllık izin hakkı`, bakiye.entitlement, "indigo", "gün"),
      stat("Kullanılan", bakiye.used, "mavi", "onaylı izin günü"),
      stat("Onay bekleyen", bakiye.pending, "sari", "gün"),
      stat("Üzerimdeki zimmet", uzerimde.length, "yesil", uzerimde.length ? uzerimde.map((z) => z.assetName).slice(0, 2).join(", ") : "demirbaş yok"),
      mudurVeyaYonetici() ? stat("Onayımı bekleyen", durum.bekleyen, durum.bekleyen ? "sari" : "yesil", "izin talebi") : null),
    h("div", { class: "iki-sutun" },
      h("section", { class: "kart" },
        h("div", { class: "kart-baslik" }, h("h3", {}, "Yıllık izin bakiyesi")),
        h("div", { class: "halka-kap" }, halka(bakiye),
          h("dl", { class: "bilgi-listesi" },
            h("div", {}, h("dt", {}, "Hak ediş"), h("dd", {}, `${bakiye.entitlement} gün`)),
            h("div", {}, h("dt", {}, "Kullanılan"), h("dd", {}, `${bakiye.used} gün`)),
            h("div", {}, h("dt", {}, "Bekleyen"), h("dd", {}, `${bakiye.pending} gün`)),
            h("div", {}, h("dt", {}, "Kalan"), h("dd", {}, `${bakiye.remaining} gün`))))),
      tabloKarti("Son izin taleplerim",
        h("button", { class: "dugme ikincil kucuk", onclick: () => sayfaAc("izinlerim") }, "Tümü"),
        izinTablosu(izinler.content, { eylemYok: true, kompakt: true }))),
    panoBolumu(pano),
  );
  genislikleriUygula();
}

/** Rol kapsamlı özet: yönetici kurumu, müdür birimini, personel yalnızca kendini görür. */
function panoBolumu(p) {
  const baslik = { ALL: "Kurum özeti", DEPARTMENT: "Birim özeti", SELF: "Aylık izin kullanımım" }[p.scope] ?? "Özet";
  const stat = (etiket, deger, sinif, not) => h("div", { class: "kart istatistik" },
    h("span", { class: "etiket" }, etiket), h("span", { class: `deger ${sinif}` }, String(deger)), h("span", { class: "not" }, not));
  const tumu = p.scope !== "SELF";
  const ust = !tumu ? null : h("div", { class: "izgara" },
    stat("Aktif personel", p.activeEmployees, "indigo", p.scope === "ALL" ? "kurum genelinde" : "biriminde"),
    stat("Bugün izinde", p.onLeaveToday, p.onLeaveToday ? "mavi" : "yesil", "onaylı izinli personel"),
    stat("Onay bekleyen", p.pendingLeaves, p.pendingLeaves ? "sari" : "yesil", "izin talebi"),
    p.scope === "ALL" ? stat("Demirbaş (boşta / zimmetli)", `${p.assetsAvailable} / ${p.assetsAssigned}`, "mavi", `${p.assetsRetired} kayıttan düşüldü`) : null,
    p.scope === "ALL" ? stat("Garantisi bitiyor", p.warrantyExpiring, p.warrantyExpiring ? "sari" : "yesil", "60 gün içinde veya dolmuş") : null);
  const aylik = h("section", { class: "kart" },
    h("div", { class: "kart-baslik" }, h("h3", {}, "Son 6 ay · onaylı izin (iş günü)")),
    cubukListesi(p.monthly.map((m) => ({ etiket: ayEtiketi(m.month), deger: Number(m.businessDays), yazi: `${m.businessDays} gün · ${m.requests} talep` }))));
  const tur = p.leaveByType.length
    ? h("section", { class: "kart" }, h("div", { class: "kart-baslik" }, h("h3", {}, "İzin türü dağılımı (bu yıl)")),
        cubukListesi(p.leaveByType.map((t) => ({ etiket: TUR_AD[t.label] ?? t.label, deger: Number(t.count), yazi: `${t.count} izin` }))))
    : null;
  const kategori = p.scope === "ALL" && p.assetsByCategory.length
    ? h("section", { class: "kart" }, h("div", { class: "kart-baslik" }, h("h3", {}, "Demirbaş kategorileri")),
        cubukListesi(p.assetsByCategory.slice(0, 8).map((k) => ({ etiket: k.label, deger: Number(k.count), yazi: `${k.count} adet` }))))
    : null;
  return h("div", { class: "pano" }, h("h2", { class: "bolum-baslik" }, baslik), ust,
    h("div", { class: "iki-sutun esit" }, aylik, tur ?? kategori), tur && kategori ? kategori : null);
}

// ---------------------------------------------------------------------------
// Ekip takvimi
// ---------------------------------------------------------------------------

let takvimAy = null;
const GUN_AD = ["Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz"];

async function takvimSayfasi() {
  if (!takvimAy) takvimAy = bugun().slice(0, 7);
  const [y, a] = takvimAy.split("-").map(Number);
  const girdiler = await api(`/api/leaves/calendar?month=${takvimAy}`);
  $("#sayfa-baslik").textContent = "Ekip takvimi";
  $("#sayfa-alt").textContent = yonetici() ? "Tüm birimlerdeki onaylı izinler"
    : durum.ben.department ? `${durum.ben.department} birimindeki onaylı izinler` : "Onaylı izinlerin";
  const git = (fark) => { const d = new Date(y, a - 1 + fark, 1); takvimAy = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`; sayfaAc("takvim"); };
  $("#ust-eylem").replaceChildren(
    h("button", { class: "dugme ikincil", "aria-label": "Önceki ay", onclick: () => git(-1) }, "‹"),
    h("strong", { class: "ay-baslik" }, ayEtiketi(takvimAy)),
    h("button", { class: "dugme ikincil", "aria-label": "Sonraki ay", onclick: () => git(1) }, "›"),
    h("button", { class: "dugme ikincil", onclick: () => { takvimAy = bugun().slice(0, 7); sayfaAc("takvim"); } }, "Bugün"));

  const gunSayisi = new Date(y, a, 0).getDate();
  const ilkGun = (new Date(y, a - 1, 1).getDay() + 6) % 7;
  const hucreler = GUN_AD.map((g) => h("div", { class: "takvim-gun-baslik" }, g));
  for (let i = 0; i < ilkGun; i++) hucreler.push(h("div", { class: "takvim-hucre bos-hucre" }));
  for (let g = 1; g <= gunSayisi; g++) {
    const iso = `${takvimAy}-${String(g).padStart(2, "0")}`;
    const haftaSonu = ((ilkGun + g - 1) % 7) >= 5;
    const o = girdiler.filter((e) => e.startDate <= iso && iso <= e.endDate);
    hucreler.push(h("div", { class: `takvim-hucre${haftaSonu ? " haftasonu" : ""}${iso === bugun() ? " bugun" : ""}` },
      h("span", { class: "takvim-no" }, String(g)),
      o.slice(0, 3).map((e) => h("span", { class: `takvim-etiket t-${e.type.toLowerCase()}`, title: `${e.employeeName} · ${TUR_AD[e.type] ?? e.type}` }, e.employeeName.split(" ")[0])),
      o.length > 3 ? h("span", { class: "takvim-diger" }, `+${o.length - 3} kişi`) : null));
  }
  const kisiler = [...new Map(girdiler.map((e) => [e.employeeId + e.startDate, e])).values()]
    .sort((x, z) => x.startDate.localeCompare(z.startDate));
  const liste = kisiler.length
    ? h("table", {}, h("thead", {}, h("tr", {}, ["Personel", "Birim", "Tür", "Tarih"].map((b) => h("th", {}, b)))),
        h("tbody", {}, kisiler.map((e) => h("tr", {},
          h("td", {}, h("div", { class: "kisi" }, h("span", { class: "avatar" }, bas(e.employeeName)), e.employeeName)),
          h("td", {}, e.department || "–"),
          h("td", {}, h("span", { class: `takvim-etiket t-${e.type.toLowerCase()}` }, TUR_AD[e.type] ?? e.type)),
          h("td", {}, aralik(e.startDate, e.endDate))))))
    : bosDurum("Bu ay izinli personel yok");
  $("#icerik").replaceChildren(
    h("section", { class: "kart" }, h("div", { class: "takvim" }, hucreler)),
    tabloKarti("Bu ayki izinler", null, liste));
}

// ---------------------------------------------------------------------------
// İzinler
// ---------------------------------------------------------------------------

function izinTablosu(liste, { eylemYok = false, kisiGoster = false, kompakt = false, eylemler } = {}) {
  if (!liste.length) return bosDurum("Kayıt yok", "Henüz bir izin talebi bulunmuyor.");
  return h("table", {},
    h("thead", {}, h("tr", {}, kisiGoster ? h("th", {}, "Personel") : null, h("th", {}, "Tür"), h("th", {}, "Tarih"), h("th", {}, "İş günü"), h("th", {}, "Durum"), kompakt ? null : h("th", {}, "Not"), eylemYok ? null : h("th", {}))),
    h("tbody", {}, liste.map((l) => h("tr", {},
      kisiGoster ? h("td", {}, h("div", { class: "kisi" }, h("span", { class: "avatar" }, bas(l.employeeName)), l.employeeName)) : null,
      h("td", {}, TUR_AD[l.type] ?? l.type),
      h("td", {}, aralik(l.startDate, l.endDate)),
      h("td", {}, String(l.businessDays)),
      h("td", {}, rozet(l.status, IZIN_DURUM[l.status] ?? l.status)),
      kompakt ? null : h("td", { class: "sarar soluk" }, l.decisionNote || l.reason || "–"),
      eylemYok ? null : h("td", {}, h("div", { class: "eylemler" }, eylemler ? eylemler(l) : null))))));
}

function izinTalebiDialogu(bakiye, sonra = () => sayfaAc(durum.sayfa)) {
  const baslangic = h("input", { name: "startDate", type: "date", required: true, min: bugun() });
  const bitis = h("input", { name: "endDate", type: "date", required: true, min: bugun() });
  baslangic.addEventListener("change", () => { if (!bitis.value || bitis.value < baslangic.value) bitis.value = baslangic.value; bitis.min = baslangic.value; });
  formDialog({
    baslik: "Yeni izin talebi",
    aciklama: bakiye ? `Kalan yıllık izin hakkın: ${bakiye.remaining} gün. Hafta sonu ve resmi tatiller iş günü sayılmaz.` : null,
    onayMetni: "Talebi gönder",
    alanlar: [
      alan("İzin türü", h("select", { name: "type" }, Object.entries(TUR_AD).map(([k, v]) => h("option", { value: k }, v))), true),
      alan("Başlangıç", baslangic), alan("Bitiş", bitis),
      alan("Gerekçe (isteğe bağlı)", h("textarea", { name: "reason", rows: "3", maxlength: "500" }), true),
    ],
    gonder: async (f) => {
      await api("/api/leaves", { yontem: "POST", govde: { type: f.get("type"), startDate: f.get("startDate"), endDate: f.get("endDate"), reason: f.get("reason") || null } });
      duyur("basari", "İzin talebin gönderildi.");
      await sonra();
    },
  });
}

let izinSayfasi = 0;
async function izinlerimSayfasi(sayfa = izinSayfasi) {
  izinSayfasi = sayfa;
  const yil = new Date().getFullYear();
  const [veri, bakiye] = await Promise.all([api(`/api/leaves/mine?page=${sayfa}&size=10`), api(`/api/employees/${durum.ben.id}/leave-balance?year=${yil}`)]);
  $("#sayfa-alt").textContent = `${yil} yılı kalan yıllık izin hakkın: ${bakiye.remaining} gün`;
  $("#ust-eylem").replaceChildren(h("button", { class: "dugme birincil", onclick: () => izinTalebiDialogu(bakiye) }, "+ Yeni talep"));
  const iptal = (l) => l.status === "PENDING"
    ? h("button", { class: "dugme tehlike kucuk", onclick: () => formDialog({
        baslik: "Talebi iptal et", aciklama: `${aralik(l.startDate, l.endDate)} tarihli talebin iptal edilecek.`, tehlike: true, onayMetni: "Talebi iptal et",
        gonder: async () => { await api(`/api/leaves/${l.id}/cancel`, { yontem: "POST" }); duyur("basari", "Talep iptal edildi."); await sayfaAc("izinlerim"); },
      }) }, "İptal et")
    : null;
  $("#icerik").replaceChildren(tabloKarti(null, null, izinTablosu(veri.content, { eylemler: iptal }),
    sayfaBilgisi(veri).totalPages > 1 ? sayfalama(sayfa, sayfaBilgisi(veri).totalPages, sayfaBilgisi(veri).totalElements, izinlerimSayfasi) : null));
}

// ---------------------------------------------------------------------------
// Onay kutusu (müdür / yönetici)
// ---------------------------------------------------------------------------

let onayFiltre = "PENDING";
const onayF = { type: "", q: "" };
async function onaySayfasi(sayfa = 0) {
  const sorgu = sorguOlustur({ status: onayFiltre, type: onayF.type, q: onayF.q });
  const veri = await api(`/api/leaves?${sorgu}&page=${sayfa}&size=10`);
  $("#sayfa-alt").textContent = yonetici() ? "Tüm birimlerdeki izin talepleri" : "Birimindeki personelin izin talepleri";
  if (onayFiltre === "PENDING" && !onayF.q && !onayF.type) { durum.bekleyen = sayfaBilgisi(veri).totalElements; menuyuCiz(); }
  const karar = (l, onay) => formDialog({
    baslik: onay ? "Talebi onayla" : "Talebi reddet",
    aciklama: `${l.employeeName} · ${TUR_AD[l.type]} · ${aralik(l.startDate, l.endDate)} (${l.businessDays} iş günü)`,
    onayMetni: onay ? "Onayla" : "Reddet", tehlike: !onay,
    alanlar: [alan("Not (isteğe bağlı)", h("textarea", { name: "note", rows: "3", maxlength: "500" }), true)],
    gonder: async (f) => {
      await api(`/api/leaves/${l.id}/${onay ? "approve" : "reject"}`, { yontem: "POST", govde: { note: f.get("note") || null } });
      duyur("basari", onay ? "Talep onaylandı." : "Talep reddedildi.");
      await bekleyenSayisi();
      await onaySayfasi();
    },
  });
  const eylemler = (l) => l.status === "PENDING" && l.employeeId !== durum.ben.id
    ? [h("button", { class: "dugme tehlike kucuk", onclick: () => karar(l, false) }, "Reddet"), h("button", { class: "dugme basari-dugme kucuk", onclick: () => karar(l, true) }, "Onayla")]
    : l.status === "PENDING" ? h("span", { class: "soluk kucuk-yazi" }, "Kendi talebini onaylayamazsın") : null;
  if (yonetici()) {
    $("#ust-eylem").replaceChildren(h("button", { class: "dugme ikincil", onclick: () => dosyaIndir(`/api/exports/leaves.xlsx?${sorgu}`, "izinler.xlsx") }, "Excel indir"));
  }
  const filtre = h("div", { class: "filtre-cubugu" }, [["ALL", "Tümü"], ...Object.entries(IZIN_DURUM)].map(([k, v]) =>
    h("button", { class: k === onayFiltre ? "aktif" : "", onclick: () => { onayFiltre = k; onaySayfasi(); } }, v)));
  const araclar = h("div", { class: "arac-cubugu" },
    aramaKutusu(onayF.q, "Personel adı ara…", (q) => { onayF.q = q; onaySayfasi(); }),
    secim("İzin türü", [["", "Tüm türler"], ...Object.entries(TUR_AD)], onayF.type, (v) => { onayF.type = v; onaySayfasi(); }));
  $("#icerik").replaceChildren(h("div", {}, filtre), araclar,
    tabloKarti(null, null, izinTablosu(veri.content, { kisiGoster: true, eylemler, eylemYok: onayFiltre !== "PENDING" && onayFiltre !== "ALL" }),
      sayfaBilgisi(veri).totalPages > 1 ? sayfalama(sayfa, sayfaBilgisi(veri).totalPages, sayfaBilgisi(veri).totalElements, onaySayfasi) : null));
}

// ---------------------------------------------------------------------------
// Personel
// ---------------------------------------------------------------------------

const SIRA_PERSONEL = [["name|", "Ad (A → Z)"], ["name|desc", "Ad (Z → A)"], ["hireDate|desc", "İşe giriş (yeni → eski)"], ["hireDate|", "İşe giriş (eski → yeni)"]];
const personelF = { q: "", active: "", role: "", departmentId: "", sira: "name|" };

function personelSorgusu() {
  const [sort, yon] = personelF.sira.split("|");
  return sorguOlustur({ q: personelF.q, active: personelF.active, role: personelF.role, departmentId: personelF.departmentId, sort, desc: yon ? "true" : "" });
}

async function personelSayfasi(sayfa = 0) {
  const sorgu = personelSorgusu();
  if (yonetici() && !durum.birimler.length) durum.birimler = await api("/api/departments");
  const veri = await api(`/api/employees?${sorgu}&page=${sayfa}&size=15`);
  $("#sayfa-alt").textContent = yonetici() ? "Tüm personel" : "Birimindeki personel";
  if (yonetici()) {
    durum.birimler = await api("/api/departments");
    $("#ust-eylem").replaceChildren(
      h("button", { class: "dugme ikincil", onclick: () => dosyaIndir(`/api/exports/employees.xlsx?${sorgu}`, "personel.xlsx") }, "Excel indir"),
      h("button", { class: "dugme ikincil", onclick: birimDialogu }, "+ Birim"),
      h("button", { class: "dugme birincil", onclick: personelDialogu }, "+ Yeni personel"));
  }
  const yenile = () => personelSayfasi(0);
  const araclar = h("div", { class: "arac-cubugu" },
    aramaKutusu(personelF.q, "Ad, e-posta veya sicil ara…", (q) => { personelF.q = q; yenile(); }),
    secim("Durum", [["", "Tüm durumlar"], ["true", "Aktif"], ["false", "Pasif"]], personelF.active, (v) => { personelF.active = v; yenile(); }),
    secim("Rol", [["", "Tüm roller"], ...Object.entries(ROL_AD)], personelF.role, (v) => { personelF.role = v; yenile(); }),
    yonetici() ? secim("Birim", [["", "Tüm birimler"], ...durum.birimler.map((b) => [b.id, b.name])], personelF.departmentId, (v) => { personelF.departmentId = v; yenile(); }) : null,
    secim("Sıralama", SIRA_PERSONEL, personelF.sira, (v) => { personelF.sira = v; yenile(); }));
  const tablo = h("table", {},
    h("thead", {}, h("tr", {}, ["Personel", "Sicil", "Birim", "Unvan", "Rol", "İşe giriş", "Durum", ""].map((b) => h("th", {}, b)))),
    h("tbody", {}, veri.content.map((p) => h("tr", {},
      h("td", {}, h("div", { class: "kisi" }, h("span", { class: "avatar" }, bas(p.fullName)), h("div", {}, h("strong", {}, p.fullName), h("div", { class: "soluk kucuk-yazi" }, p.email)))),
      h("td", {}, p.registrationNo), h("td", {}, p.department || "–"), h("td", {}, p.title || "–"),
      h("td", {}, rozet(p.role, ROL_AD[p.role])), h("td", {}, tarih(p.hireDate)),
      h("td", {}, rozet(p.active ? "aktif" : "pasif", p.active ? "Aktif" : "Pasif")),
      h("td", {}, h("div", { class: "eylemler" },
        h("button", { class: "dugme ikincil kucuk", onclick: () => personelDetayi(p) }, "Detay"),
        yonetici() ? h("button", { class: "dugme ikincil kucuk", onclick: () => personelDuzenleDialogu(p, sayfa) }, "Düzenle") : null,
        yonetici() && !p.active ? h("button", { class: "dugme birincil kucuk", onclick: async () => {
          try { await api(`/api/employees/${p.id}/activate`, { yontem: "PATCH" }); duyur("basari", "Personel yeniden etkinleştirildi."); await personelSayfasi(sayfa); } catch (e) { hataGoster(e); }
        } }, "Etkinleştir") : null,
        yonetici() && p.active && p.id !== durum.ben.id ? h("button", { class: "dugme tehlike kucuk", onclick: () => formDialog({
          baslik: "Personeli pasife al", aciklama: `${p.fullName} sisteme giriş yapamaz hale gelecek. Geçmiş kayıtları korunur.`, tehlike: true, onayMetni: "Pasife al",
          gonder: async () => { await api(`/api/employees/${p.id}/deactivate`, { yontem: "PATCH" }); duyur("basari", "Personel pasife alındı."); await personelSayfasi(sayfa); },
        }) }, "Pasife al") : null))))));
  const bilgi = sayfaBilgisi(veri);
  $("#icerik").replaceChildren(araclar, veri.content.length
    ? tabloKarti(null, null, tablo, bilgi.totalPages > 1
        ? sayfalama(sayfa, bilgi.totalPages, bilgi.totalElements, personelSayfasi)
        : h("div", { class: "sayfalama" }, h("span", { class: "soluk kucuk-yazi" }, `${bilgi.totalElements} kayıt`)))
    : h("section", { class: "kart" }, bosDurum("Personel bulunamadı", "Arama veya süzgeçleri değiştirmeyi deneyin.")));
}

function personelDuzenleDialogu(p, sayfa) {
  const birimSecimi = h("select", { name: "departmentId" }, [h("option", { value: "" }, "Değiştirme"), ...durum.birimler.map((b) => h("option", { value: b.id, selected: b.id === p.departmentId }, b.name))]);
  formDialog({
    baslik: "Personeli düzenle", aciklama: `${p.email} · Sicil ${p.registrationNo}`, onayMetni: "Kaydet",
    alanlar: [
      alan("Ad soyad", h("input", { name: "fullName", required: true, maxlength: "150", value: p.fullName }), true),
      alan("Unvan", h("input", { name: "title", maxlength: "100", value: p.title ?? "" })),
      alan("Rol", h("select", { name: "role" }, Object.entries(ROL_AD).reverse().map(([k, v]) => h("option", { value: k, selected: k === p.role }, v)))),
      alan("Birim", birimSecimi, true),
    ],
    gonder: async (f) => {
      await api(`/api/employees/${p.id}`, { yontem: "PATCH", govde: {
        fullName: f.get("fullName").trim(), title: f.get("title") ?? "", role: f.get("role"),
        departmentId: f.get("departmentId") ? Number(f.get("departmentId")) : null,
      } });
      duyur("basari", "Personel bilgileri güncellendi.");
      await personelSayfasi(sayfa);
    },
  });
}

async function personelDetayi(p) {
  const yil = new Date().getFullYear();
  dialogAc(h("p", { class: "soluk" }, "Yükleniyor…"));
  try {
    const [bakiye, zimmetler] = await Promise.all([api(`/api/employees/${p.id}/leave-balance?year=${yil}`), api(`/api/employees/${p.id}/assets`)]);
    dialogAc(
      h("div", { class: "kisi" }, h("span", { class: "avatar" }, bas(p.fullName)), h("div", {}, h("h2", {}, p.fullName), h("div", { class: "soluk" }, [p.title, p.department].filter(Boolean).join(" · ") || p.email))),
      h("dl", { class: "bilgi-listesi" },
        h("div", {}, h("dt", {}, `${yil} hak ediş`), h("dd", {}, `${bakiye.entitlement} gün`)),
        h("div", {}, h("dt", {}, "Kullanılan"), h("dd", {}, `${bakiye.used} gün`)),
        h("div", {}, h("dt", {}, "Bekleyen"), h("dd", {}, `${bakiye.pending} gün`)),
        h("div", {}, h("dt", {}, "Kalan"), h("dd", {}, `${bakiye.remaining} gün`))),
      h("h3", {}, "Zimmetler"),
      zimmetler.length ? h("div", { class: "tablo-kap" }, h("table", {}, h("tbody", {}, zimmetler.map((z) => h("tr", {},
        h("td", {}, z.assetName), h("td", { class: "soluk" }, z.assetTag), h("td", {}, z.returnedAt ? `İade: ${tarih(z.returnedAt)}` : rozet("assigned", "Üzerinde"))))))) : h("p", { class: "soluk" }, "Zimmet kaydı yok."),
      h("div", { class: "dialog-alt" }, h("button", { class: "dugme ikincil", onclick: dialogKapat }, "Kapat")));
  } catch (e) { dialogKapat(); hataGoster(e); }
}

function birimDialogu() {
  formDialog({
    baslik: "Yeni birim", onayMetni: "Ekle",
    alanlar: [alan("Birim adı", h("input", { name: "name", required: true, maxlength: "100" }), true)],
    gonder: async (f) => { await api("/api/departments", { yontem: "POST", govde: { name: f.get("name").trim() } }); duyur("basari", "Birim eklendi."); },
  });
}

function personelDialogu() {
  formDialog({
    baslik: "Yeni personel", onayMetni: "Personel ekle",
    alanlar: [
      alan("Ad soyad", h("input", { name: "fullName", required: true, maxlength: "150" }), true),
      alan("Sicil no", h("input", { name: "registrationNo", required: true, maxlength: "20" })),
      alan("İşe giriş tarihi", h("input", { name: "hireDate", type: "date", required: true, max: bugun() })),
      alan("E-posta", h("input", { name: "email", type: "email", required: true, maxlength: "254" }), true),
      alan("Geçici şifre", h("input", { name: "password", type: "password", required: true, minlength: "8", maxlength: "72", autocomplete: "new-password" })),
      alan("Unvan", h("input", { name: "title", maxlength: "100" })),
      alan("Rol", h("select", { name: "role" }, Object.entries(ROL_AD).reverse().map(([k, v]) => h("option", { value: k }, v)))),
      alan("Birim", h("select", { name: "departmentId" }, [h("option", { value: "" }, "Birim yok"), ...durum.birimler.map((b) => h("option", { value: b.id }, b.name))])),
    ],
    gonder: async (f) => {
      await api("/api/employees", { yontem: "POST", govde: {
        fullName: f.get("fullName").trim(), registrationNo: f.get("registrationNo").trim(), email: f.get("email").trim(), password: f.get("password"),
        role: f.get("role"), title: f.get("title") || null, departmentId: f.get("departmentId") ? Number(f.get("departmentId")) : null, hireDate: f.get("hireDate"),
      } });
      duyur("basari", "Personel eklendi.");
      await personelSayfasi();
    },
  });
}

// ---------------------------------------------------------------------------
// Zimmet
// ---------------------------------------------------------------------------

async function zimmetimSayfasi() {
  const liste = await api(`/api/employees/${durum.ben.id}/assets`);
  $("#sayfa-alt").textContent = "Üzerine kayıtlı demirbaşlar ve geçmiş";
  const tablo = liste.length
    ? h("table", {}, h("thead", {}, h("tr", {}, ["Demirbaş", "Etiket", "Teslim alındı", "İade", "Durum"].map((b) => h("th", {}, b)))),
        h("tbody", {}, liste.map((z) => h("tr", {}, h("td", {}, h("strong", {}, z.assetName)), h("td", { class: "soluk" }, z.assetTag),
          h("td", {}, tarihSaat(z.assignedAt)), h("td", {}, z.returnedAt ? tarihSaat(z.returnedAt) : "–"),
          h("td", {}, z.returnedAt ? rozet("available", "İade edildi") : rozet("assigned", "Üzerinde"))))))
    : bosDurum("Zimmet kaydın yok", "Sana zimmetlenen demirbaşlar burada listelenir.");
  $("#icerik").replaceChildren(tabloKarti(null, null, tablo));
}

const SIRA_ZIMMET = [["id|", "Eklenme (eski → yeni)"], ["id|desc", "Eklenme (yeni → eski)"], ["name|", "Ad (A → Z)"], ["warrantyEnd|", "Garanti bitişi (yakın → uzak)"], ["purchaseDate|desc", "Satın alma (yeni → eski)"]];
const GARANTI_SUZGEC = [["", "Tüm garanti durumları"], ["0", "Garantisi dolmuş"], ["30", "30 gün içinde bitecek"], ["60", "60 gün içinde bitecek"], ["90", "90 gün içinde bitecek"]];
const zimmetF = { q: "", status: "", category: "", warranty: "", sira: "id|" };
const para = new Intl.NumberFormat("tr-TR", { style: "currency", currency: "TRY" });

function zimmetSorgusu() {
  const [sort, yon] = zimmetF.sira.split("|");
  return sorguOlustur({ q: zimmetF.q, status: zimmetF.status, category: zimmetF.category, warrantyWithinDays: zimmetF.warranty, sort, desc: yon ? "true" : "" });
}

/** Garanti hücresi: tarih ve kalan gün; 60 gün ve altı uyarı, geçmişse "doldu" rozeti. */
function garantiHucresi(a) {
  if (!a.warrantyEnd) return h("span", { class: "soluk" }, "–");
  const kalan = a.warrantyDaysLeft;
  if (a.status === "RETIRED" || kalan == null) return h("span", {}, tarih(a.warrantyEnd));
  if (kalan < 0) return h("div", {}, tarih(a.warrantyEnd), " ", rozet("rejected", "Doldu"));
  if (kalan <= 60) return h("div", {}, tarih(a.warrantyEnd), " ", rozet("pending", `${kalan} gün`));
  return h("span", {}, tarih(a.warrantyEnd));
}

async function zimmetSayfasi(sayfa = 0) {
  const sorgu = zimmetSorgusu();
  const [veri, kategoriler] = await Promise.all([api(`/api/assets?${sorgu}&page=${sayfa}&size=15`), api("/api/assets/categories")]);
  $("#sayfa-alt").textContent = "Kurumun demirbaş envanteri";
  if (yonetici()) {
    $("#ust-eylem").replaceChildren(
      h("button", { class: "dugme ikincil", onclick: () => dosyaIndir(`/api/exports/assets.xlsx?${sorgu}`, "demirbaslar.xlsx") }, "Excel indir"),
      h("button", { class: "dugme birincil", onclick: () => demirbasDialogu() }, "+ Demirbaş ekle"));
  }
  const yenile = () => zimmetSayfasi(0);
  const eylemler = (a) => !yonetici() ? null : [
    a.status !== "RETIRED" ? h("button", { class: "dugme ikincil kucuk", onclick: () => demirbasDialogu(a, sayfa) }, "Düzenle") : null,
    a.status === "AVAILABLE" ? h("button", { class: "dugme ikincil kucuk", onclick: () => zimmetleDialogu(a) }, "Zimmetle") : null,
    a.status === "ASSIGNED" ? h("button", { class: "dugme ikincil kucuk", onclick: () => iadeDialogu(a) }, "İade al") : null,
    a.status === "AVAILABLE" ? h("button", { class: "dugme tehlike kucuk", onclick: () => formDialog({
      baslik: "Kayıttan düş", aciklama: `${a.name} (${a.assetTag}) envanterden düşülecek. Bu işlem geri alınamaz.`, tehlike: true, onayMetni: "Kayıttan düş",
      gonder: async () => { await api(`/api/assets/${a.id}/retire`, { yontem: "POST" }); duyur("basari", "Demirbaş kayıttan düşüldü."); await zimmetSayfasi(sayfa); },
    }) }, "Kayıttan düş") : null];
  const filtre = h("div", { class: "filtre-cubugu" }, [["", "Tümü"], ...Object.entries(ZIMMET_DURUM)].map(([k, v]) =>
    h("button", { class: k === zimmetF.status ? "aktif" : "", onclick: () => { zimmetF.status = k; yenile(); } }, v)));
  const araclar = h("div", { class: "arac-cubugu" },
    aramaKutusu(zimmetF.q, "Etiket, ad veya seri no ara…", (q) => { zimmetF.q = q; yenile(); }),
    secim("Kategori", [["", "Tüm kategoriler"], ...kategoriler.map((k) => [k, k])], zimmetF.category, (v) => { zimmetF.category = v; yenile(); }),
    secim("Garanti", GARANTI_SUZGEC, zimmetF.warranty, (v) => { zimmetF.warranty = v; yenile(); }),
    secim("Sıralama", SIRA_ZIMMET, zimmetF.sira, (v) => { zimmetF.sira = v; yenile(); }));
  const basliklar = ["Etiket", "Demirbaş", "Kategori", "Seri no", "Satın alma", "Garanti", "Zimmetli", "Durum", yonetici() ? "" : null].filter((x) => x !== null);
  const tablo = h("table", {}, h("thead", {}, h("tr", {}, basliklar.map((b) => h("th", {}, b)))),
    h("tbody", {}, veri.content.map((a) => h("tr", {},
      h("td", {}, h("code", {}, a.assetTag)),
      h("td", {}, h("strong", {}, a.name)),
      h("td", {}, a.category),
      h("td", { class: "soluk" }, a.serialNo || "–"),
      h("td", {}, a.purchaseDate ? h("div", {}, tarih(a.purchaseDate), a.purchasePrice != null ? h("div", { class: "soluk kucuk-yazi" }, para.format(a.purchasePrice)) : null) : h("span", { class: "soluk" }, "–")),
      h("td", {}, garantiHucresi(a)),
      h("td", {}, a.assignedTo || h("span", { class: "soluk" }, "–")),
      h("td", {}, rozet(a.status, ZIMMET_DURUM[a.status])),
      yonetici() ? h("td", {}, h("div", { class: "eylemler" }, eylemler(a))) : null))));
  const bilgi = sayfaBilgisi(veri);
  $("#icerik").replaceChildren(h("div", {}, filtre), araclar, veri.content.length
    ? tabloKarti(null, null, tablo, bilgi.totalPages > 1 ? sayfalama(sayfa, bilgi.totalPages, bilgi.totalElements, zimmetSayfasi)
        : h("div", { class: "sayfalama" }, h("span", { class: "soluk kucuk-yazi" }, `${bilgi.totalElements} kayıt`)))
    : h("section", { class: "kart" }, bosDurum("Demirbaş bulunamadı", "Arama veya süzgeçleri değiştirmeyi deneyin.")));
}

/** Yeni demirbaş ya da (a verilirse) düzenleme penceresi. */
function demirbasDialogu(a = null, sayfa = 0) {
  const duzenle = a != null;
  formDialog({
    baslik: duzenle ? "Demirbaşı düzenle" : "Yeni demirbaş", onayMetni: duzenle ? "Kaydet" : "Ekle",
    aciklama: duzenle ? `${a.assetTag} · demirbaş etiketi değiştirilemez` : null,
    alanlar: [
      duzenle ? null : alan("Demirbaş etiketi", h("input", { name: "assetTag", required: true, maxlength: "50", placeholder: "LT-0042" })),
      alan("Kategori", h("input", { name: "category", required: true, maxlength: "50", placeholder: "Dizüstü bilgisayar", value: a?.category ?? "" })),
      alan("Ad", h("input", { name: "name", required: true, maxlength: "150", value: a?.name ?? "" }), true),
      alan("Seri no", h("input", { name: "serialNo", maxlength: "100", value: a?.serialNo ?? "" })),
      alan("Satın alma tarihi", h("input", { name: "purchaseDate", type: "date", max: bugun(), value: a?.purchaseDate ?? "" })),
      alan("Fiyat (TL)", h("input", { name: "purchasePrice", type: "number", min: "0", step: "0.01", value: a?.purchasePrice ?? "" })),
      alan("Garanti bitişi", h("input", { name: "warrantyEnd", type: "date", value: a?.warrantyEnd ?? "" })),
    ],
    gonder: async (f) => {
      const ortak = {
        name: f.get("name").trim(), category: f.get("category").trim(), serialNo: f.get("serialNo")?.trim() || null,
        purchaseDate: f.get("purchaseDate") || null, purchasePrice: f.get("purchasePrice") ? Number(f.get("purchasePrice")) : null,
        warrantyEnd: f.get("warrantyEnd") || null,
      };
      if (duzenle) await api(`/api/assets/${a.id}`, { yontem: "PATCH", govde: ortak });
      else await api("/api/assets", { yontem: "POST", govde: { assetTag: f.get("assetTag").trim(), ...ortak } });
      duyur("basari", duzenle ? "Demirbaş güncellendi." : "Demirbaş eklendi.");
      await zimmetSayfasi(duzenle ? sayfa : 0);
    },
  });
}

async function zimmetleDialogu(a) {
  const kisiler = (await api("/api/employees?size=100")).content.filter((p) => p.active);
  formDialog({
    baslik: "Zimmetle", aciklama: `${a.name} (${a.assetTag})`, onayMetni: "Zimmetle",
    alanlar: [
      alan("Personel", h("select", { name: "employeeId", required: true }, kisiler.map((p) => h("option", { value: p.id }, `${p.fullName} · ${p.registrationNo}`))), true),
      alan("Not (isteğe bağlı)", h("input", { name: "note", maxlength: "300" }), true),
    ],
    gonder: async (f) => {
      await api(`/api/assets/${a.id}/assign`, { yontem: "POST", govde: { employeeId: Number(f.get("employeeId")), note: f.get("note") || null } });
      duyur("basari", "Demirbaş zimmetlendi.");
      await zimmetSayfasi();
    },
  });
}

function iadeDialogu(a) {
  formDialog({
    baslik: "İade al", aciklama: `${a.name} (${a.assetTag})`, onayMetni: "İade al",
    alanlar: [alan("Not (isteğe bağlı)", h("input", { name: "note", maxlength: "300" }), true)],
    gonder: async (f) => {
      await api(`/api/assets/${a.id}/return`, { yontem: "POST", govde: { note: f.get("note") || null } });
      duyur("basari", "Demirbaş iade alındı.");
      await zimmetSayfasi();
    },
  });
}

// ---------------------------------------------------------------------------
// Rapor
// ---------------------------------------------------------------------------

async function raporSayfasi() {
  const rapor = await api("/api/reports/leave-usage");
  $("#sayfa-alt").textContent = "Birim bazında yıllık izin kullanımı";
  $("#ust-eylem").replaceChildren(h("button", { class: "dugme ikincil", onclick: () => dosyaIndir("/api/reports/leave-usage.xlsx", "izin-kullanimi.xlsx") }, "Excel indir"));
  const enCok = Math.max(1, ...rapor.map((r) => r.annualDaysUsed));
  const toplam = (k) => rapor.reduce((t, r) => t + r[k], 0);
  const stat = (etiket, deger, sinif) => h("div", { class: "kart istatistik" }, h("span", { class: "etiket" }, etiket), h("span", { class: `deger ${sinif}` }, String(deger)));
  $("#icerik").replaceChildren(
    h("div", { class: "izgara" },
      stat("Birim", rapor.length, "indigo"), stat("Personel", toplam("employees"), "mavi"),
      stat("Kullanılan yıllık izin (gün)", toplam("annualDaysUsed"), "yesil"), stat("Bekleyen talep", toplam("pendingRequests"), "sari")),
    h("section", { class: "kart" },
      h("div", { class: "kart-baslik" }, h("h3", {}, "Yıllık izin kullanımı (gün)"),
        h("div", { class: "gosterge" }, h("span", {}, h("i", { style: null, class: "g-birincil" }), "Onaylı izin günü"))),
      rapor.length ? h("div", { class: "grafik" }, rapor.map((r) => h("div", { class: "grafik-satir" },
        h("span", { class: "grafik-etiket", title: r.department }, r.department),
        h("div", { class: "grafik-raylar" }, h("div", { class: "grafik-ray", role: "img", "aria-label": `${r.annualDaysUsed} gün` },
          h("span", { "data-genislik": Math.round((r.annualDaysUsed / enCok) * 100), style: null }))),
        h("span", { class: "sag-yazi" }, `${r.annualDaysUsed} gün · ${r.employees} kişi · ${r.pendingRequests} bekleyen`)))) : bosDurum("Rapor verisi yok")));
  // Genişlikler satır içi stil yerine CSSOM ile verilir (CSP satır içi stile izin vermez).
  $$(".grafik-ray > span").forEach((s) => { s.style.width = `${s.dataset.genislik}%`; s.style.background = "linear-gradient(90deg, var(--birincil), var(--mavi))"; });
}

// ---------------------------------------------------------------------------
// Denetim kaydı (yönetici)
// ---------------------------------------------------------------------------

const DENETIM_ISLEM_AD = {
  LOGIN: "Giriş", LOGIN_FAILED: "Başarısız giriş", EMPLOYEE_CREATED: "Personel eklendi", EMPLOYEE_DEACTIVATED: "Personel pasife alındı",
  DEPARTMENT_CREATED: "Birim eklendi", LEAVE_REQUESTED: "İzin talep edildi", LEAVE_APPROVED: "İzin onaylandı",
  LEAVE_REJECTED: "İzin reddedildi", LEAVE_CANCELLED: "İzin iptal edildi", ASSET_CREATED: "Demirbaş eklendi",
  ASSET_ASSIGNED: "Zimmetlendi", ASSET_RETURNED: "İade alındı", ASSET_RETIRED: "Kayıttan düşüldü",
  EMPLOYEE_UPDATED: "Personel güncellendi", EMPLOYEE_ACTIVATED: "Personel etkinleştirildi", PASSWORD_CHANGED: "Şifre değiştirildi",
  ASSET_UPDATED: "Demirbaş güncellendi", EXPORT: "Excel dışa aktarma",
};
durum.denetim = { actor: "", action: "" };

function denetimSorgu() {
  const p = new URLSearchParams();
  if (durum.denetim.actor) p.set("actor", durum.denetim.actor);
  if (durum.denetim.action) p.set("action", durum.denetim.action);
  return p;
}

async function denetimSayfasi(sayfa = 0) {
  const F = durum.denetim;
  $("#sayfa-alt").textContent = "Kim, ne zaman, ne yaptı";
  $("#ust-eylem").replaceChildren(h("button", { class: "dugme ikincil", onclick: () => dosyaIndir(`/api/audit/export.xlsx?${denetimSorgu()}`, "denetim-kaydi.xlsx") }, "Excel indir"));

  const p = denetimSorgu();
  p.set("page", sayfa);
  p.set("size", 15);
  const veri = await api(`/api/audit?${p}`);
  const bilgi = veri.page ?? veri; // Spring Data sayfa bilgisi "page" altında döner

  const actor = h("input", { type: "search", placeholder: "Kullanıcı e-postası…", "aria-label": "Kullanıcı", value: F.actor });
  actor.addEventListener("keydown", (e) => { if (e.key === "Enter") { F.actor = actor.value.trim(); denetimSayfasi(0); } });
  const action = h("select", { "aria-label": "İşlem", onchange: (e) => { F.action = e.target.value; denetimSayfasi(0); } },
    h("option", { value: "" }, "Tüm işlemler"),
    ...Object.entries(DENETIM_ISLEM_AD).map(([k, v]) => h("option", { value: k, selected: k === F.action }, v)));

  const tablo = veri.content.length
    ? h("table", {},
        h("thead", {}, h("tr", {}, ["Zaman", "Kullanıcı", "İşlem", "Kayıt", "Ayrıntı"].map((b) => h("th", {}, b)))),
        h("tbody", {}, veri.content.map((d) => h("tr", {},
          h("td", {}, new Date(d.occurredAt).toLocaleString("tr-TR", { dateStyle: "medium", timeStyle: "medium" })),
          h("td", {}, h("div", { class: "kisi" }, h("span", { class: "avatar" }, bas(d.actor)), d.actor, d.actorRole ? rozet(d.actorRole, ROL_AD[d.actorRole] ?? d.actorRole) : null)),
          h("td", {}, DENETIM_ISLEM_AD[d.action] ?? d.action),
          h("td", {}, d.entityType ? h("code", {}, `${d.entityType} ${d.entityId ?? ""}`.trim()) : h("span", { class: "soluk" }, "–")),
          h("td", { class: "soluk kucuk-yazi" }, d.details ?? "–")))))
    : null;

  $("#icerik").replaceChildren(
    h("div", { class: "arac-cubugu" }, actor, action),
    tablo ? tabloKarti(null, null, tablo,
      bilgi.totalPages > 1 ? sayfalama(sayfa, bilgi.totalPages, bilgi.totalElements, denetimSayfasi) : h("div", { class: "sayfalama" }, h("span", { class: "soluk kucuk-yazi" }, `${bilgi.totalElements} kayıt`)))
      : h("section", { class: "kart" }, bosDurum("Kayıt bulunamadı", "Filtreleri değiştirmeyi deneyin.")));
}

// ---------------------------------------------------------------------------
// Başlangıç
// ---------------------------------------------------------------------------

if (Oturum.oku()) uygulamayiAc().catch(() => cikis());
else $("#giris").hidden = false;
