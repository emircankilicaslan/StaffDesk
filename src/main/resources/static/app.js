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
};
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

// ---------------------------------------------------------------------------
// Gezinme
// ---------------------------------------------------------------------------

const SAYFALAR = [
  { id: "ozet", ad: "Genel bakış", ikon: "ozet", gorunur: () => true },
  { id: "izinlerim", ad: "İzinlerim", ikon: "izin", gorunur: () => true },
  { id: "onay", ad: "Onay kutusu", ikon: "onay", gorunur: mudurVeyaYonetici },
  { id: "personel", ad: "Personel", ikon: "personel", gorunur: mudurVeyaYonetici },
  { id: "zimmetim", ad: "Zimmetlerim", ikon: "zimmetim", gorunur: () => true },
  { id: "zimmet", ad: "Demirbaşlar", ikon: "zimmet", gorunur: mudurVeyaYonetici },
  { id: "rapor", ad: "Raporlar", ikon: "rapor", gorunur: yonetici },
];

function menuyuCiz() {
  $("#menu").replaceChildren(...SAYFALAR.filter((s) => s.gorunur()).map((s) =>
    h("button", { class: s.id === durum.sayfa ? "aktif" : "", onclick: () => sayfaAc(s.id) },
      IKONLAR[s.ikon](), s.ad,
      s.id === "onay" && durum.bekleyen > 0 ? h("span", { class: "sayac" }, durum.bekleyen) : null)));
}

const YUKLEYICILER = { ozet: ozetSayfasi, izinlerim: izinlerimSayfasi, onay: onaySayfasi, personel: personelSayfasi, zimmetim: zimmetimSayfasi, zimmet: zimmetSayfasi, rapor: raporSayfasi };

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
    durum.bekleyen = (await api("/api/leaves?status=PENDING&size=1")).totalElements ?? 0;
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
  const [bakiye, izinler, zimmetler] = await Promise.all([
    api(`/api/employees/${durum.ben.id}/leave-balance?year=${yil}`),
    api("/api/leaves/mine?size=5"),
    api(`/api/employees/${durum.ben.id}/assets`),
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
  );
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
    veri.totalPages > 1 ? sayfalama(sayfa, veri.totalPages, veri.totalElements, izinlerimSayfasi) : null));
}

// ---------------------------------------------------------------------------
// Onay kutusu (müdür / yönetici)
// ---------------------------------------------------------------------------

let onayFiltre = "PENDING";
async function onaySayfasi(sayfa = 0) {
  const veri = await api(`/api/leaves?status=${onayFiltre}&page=${sayfa}&size=10`);
  $("#sayfa-alt").textContent = yonetici() ? "Tüm birimlerdeki izin talepleri" : "Birimindeki personelin izin talepleri";
  if (onayFiltre === "PENDING") { durum.bekleyen = veri.totalElements; menuyuCiz(); }
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
  const filtre = h("div", { class: "filtre-cubugu" }, Object.entries(IZIN_DURUM).map(([k, v]) =>
    h("button", { class: k === onayFiltre ? "aktif" : "", onclick: () => { onayFiltre = k; onaySayfasi(); } }, v)));
  $("#icerik").replaceChildren(h("div", {}, filtre),
    tabloKarti(null, null, izinTablosu(veri.content, { kisiGoster: true, eylemler, eylemYok: onayFiltre !== "PENDING" }),
      veri.totalPages > 1 ? sayfalama(sayfa, veri.totalPages, veri.totalElements, onaySayfasi) : null));
}

// ---------------------------------------------------------------------------
// Personel
// ---------------------------------------------------------------------------

async function personelSayfasi(sayfa = 0) {
  const veri = await api(`/api/employees?page=${sayfa}&size=15`);
  $("#sayfa-alt").textContent = yonetici() ? "Tüm personel" : "Birimindeki personel";
  if (yonetici()) {
    durum.birimler = await api("/api/departments");
    $("#ust-eylem").replaceChildren(
      h("button", { class: "dugme ikincil", onclick: birimDialogu }, "+ Birim"),
      h("button", { class: "dugme birincil", onclick: personelDialogu }, "+ Yeni personel"));
  }
  const tablo = h("table", {},
    h("thead", {}, h("tr", {}, ["Personel", "Sicil", "Birim", "Unvan", "Rol", "İşe giriş", "Durum", ""].map((b) => h("th", {}, b)))),
    h("tbody", {}, veri.content.map((p) => h("tr", {},
      h("td", {}, h("div", { class: "kisi" }, h("span", { class: "avatar" }, bas(p.fullName)), h("div", {}, h("strong", {}, p.fullName), h("div", { class: "soluk kucuk-yazi" }, p.email)))),
      h("td", {}, p.registrationNo), h("td", {}, p.department || "–"), h("td", {}, p.title || "–"),
      h("td", {}, rozet(p.role, ROL_AD[p.role])), h("td", {}, tarih(p.hireDate)),
      h("td", {}, rozet(p.active ? "aktif" : "pasif", p.active ? "Aktif" : "Pasif")),
      h("td", {}, h("div", { class: "eylemler" },
        h("button", { class: "dugme ikincil kucuk", onclick: () => personelDetayi(p) }, "Detay"),
        yonetici() && p.active && p.id !== durum.ben.id ? h("button", { class: "dugme tehlike kucuk", onclick: () => formDialog({
          baslik: "Personeli pasife al", aciklama: `${p.fullName} sisteme giriş yapamaz hale gelecek. Geçmiş kayıtları korunur.`, tehlike: true, onayMetni: "Pasife al",
          gonder: async () => { await api(`/api/employees/${p.id}/deactivate`, { yontem: "PATCH" }); duyur("basari", "Personel pasife alındı."); await personelSayfasi(sayfa); },
        }) }, "Pasife al") : null))))));
  $("#icerik").replaceChildren(tabloKarti(null, null, tablo,
    veri.totalPages > 1 ? sayfalama(sayfa, veri.totalPages, veri.totalElements, personelSayfasi) : null));
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

let zimmetFiltre = "";
async function zimmetSayfasi(sayfa = 0) {
  const veri = await api(`/api/assets?page=${sayfa}&size=15${zimmetFiltre ? `&status=${zimmetFiltre}` : ""}`);
  $("#sayfa-alt").textContent = "Kurumun demirbaş envanteri";
  if (yonetici()) $("#ust-eylem").replaceChildren(h("button", { class: "dugme birincil", onclick: demirbasDialogu }, "+ Demirbaş ekle"));
  const eylemler = (a) => !yonetici() ? null : [
    a.status === "AVAILABLE" ? h("button", { class: "dugme ikincil kucuk", onclick: () => zimmetleDialogu(a) }, "Zimmetle") : null,
    a.status === "ASSIGNED" ? h("button", { class: "dugme ikincil kucuk", onclick: () => iadeDialogu(a) }, "İade al") : null,
    a.status === "AVAILABLE" ? h("button", { class: "dugme tehlike kucuk", onclick: () => formDialog({
      baslik: "Kayıttan düş", aciklama: `${a.name} (${a.assetTag}) envanterden düşülecek. Bu işlem geri alınamaz.`, tehlike: true, onayMetni: "Kayıttan düş",
      gonder: async () => { await api(`/api/assets/${a.id}/retire`, { yontem: "POST" }); duyur("basari", "Demirbaş kayıttan düşüldü."); await zimmetSayfasi(sayfa); },
    }) }, "Kayıttan düş") : null];
  const filtre = h("div", { class: "filtre-cubugu" }, [["", "Tümü"], ...Object.entries(ZIMMET_DURUM)].map(([k, v]) =>
    h("button", { class: k === zimmetFiltre ? "aktif" : "", onclick: () => { zimmetFiltre = k; zimmetSayfasi(); } }, v)));
  const tablo = veri.content.length
    ? h("table", {}, h("thead", {}, h("tr", {}, ["Etiket", "Demirbaş", "Kategori", "Durum", yonetici() ? "" : null].filter((x) => x !== null).map((b) => h("th", {}, b)))),
        h("tbody", {}, veri.content.map((a) => h("tr", {}, h("td", {}, h("code", {}, a.assetTag)), h("td", {}, h("strong", {}, a.name)), h("td", {}, a.category),
          h("td", {}, rozet(a.status, ZIMMET_DURUM[a.status])), yonetici() ? h("td", {}, h("div", { class: "eylemler" }, eylemler(a))) : null))))
    : bosDurum("Demirbaş bulunamadı");
  $("#icerik").replaceChildren(h("div", {}, filtre), tabloKarti(null, null, tablo, veri.totalPages > 1 ? sayfalama(sayfa, veri.totalPages, veri.totalElements, zimmetSayfasi) : null));
}

function demirbasDialogu() {
  formDialog({
    baslik: "Yeni demirbaş", onayMetni: "Ekle",
    alanlar: [
      alan("Demirbaş etiketi", h("input", { name: "assetTag", required: true, maxlength: "50", placeholder: "LT-0042" })),
      alan("Kategori", h("input", { name: "category", required: true, maxlength: "50", placeholder: "Dizüstü bilgisayar" })),
      alan("Ad", h("input", { name: "name", required: true, maxlength: "150" }), true),
    ],
    gonder: async (f) => {
      await api("/api/assets", { yontem: "POST", govde: { assetTag: f.get("assetTag").trim(), name: f.get("name").trim(), category: f.get("category").trim() } });
      duyur("basari", "Demirbaş eklendi.");
      await zimmetSayfasi();
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
// Başlangıç
// ---------------------------------------------------------------------------

if (Oturum.oku()) uygulamayiAc().catch(() => cikis());
else $("#giris").hidden = false;
