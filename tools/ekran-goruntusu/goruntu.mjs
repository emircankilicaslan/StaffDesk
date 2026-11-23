// Çalışan uygulamadan README ekran görüntülerini alır (scripts/demo-verisi.mjs'den sonra çalıştırılır).
import { chromium } from 'playwright';
import { mkdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const BASE = process.env.BASE || 'http://localhost:8080';
const YONETICI = process.env.ADMIN_EMAIL, YONETICI_SIFRE = process.env.ADMIN_PASSWORD;
const DEMO_SIFRE = 'Demo-Sifre-123';
if (!YONETICI || !YONETICI_SIFRE) throw new Error('ADMIN_EMAIL ve ADMIN_PASSWORD tanımlı olmalı');

const klasor = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../docs/screenshots');
mkdirSync(klasor, { recursive: true });
const tarayici = await chromium.launch();

async function sayfaAc(secenek = {}) {
  const b = await tarayici.newContext({ viewport: { width: 1280, height: 800 }, locale: 'tr-TR', timezoneId: 'Europe/Istanbul', colorScheme: 'light', ...secenek });
  const s = await b.newPage();
  s.on('pageerror', (e) => console.log('SAYFA HATASI:', e.message));
  s.on('console', (m) => { if (m.type() === 'error') console.log('KONSOL:', m.text()); });
  s.on('response', (r) => { if (r.url().includes('/api/') && r.status() >= 400) console.log('API', r.status(), r.url()); });
  return s;
}

async function girisYap(s, eposta, sifre) {
  await s.goto(BASE + '/');
  await s.fill('input[name=email]', eposta);
  await s.fill('input[name=password]', sifre);
  await s.click('#giris-gonder');
  await s.locator('#kullanici-ad').waitFor();
  await s.waitForLoadState('networkidle');
  await s.locator('#icerik > *').first().waitFor();
}

async function cek(s, ad, secenek = {}) {
  await s.screenshot({ path: path.join(klasor, ad + '.jpg'), type: 'jpeg', quality: 88, ...secenek });
  console.log('Kaydedildi:', ad);
}

async function sayfa(s, menuAdi, bekle) {
  await s.locator('#menu button', { hasText: menuAdi }).click();
  await s.locator(bekle).first().waitFor();
  await s.waitForLoadState('networkidle');
}

// 1) Giriş
{
  const s = await sayfaAc({ viewport: { width: 1280, height: 640 } });
  await s.goto(BASE + '/');
  await s.waitForSelector('input[name=email]');
  await cek(s, 'arayuz-giris');
  await s.context().close();
}

// 2) Yönetici
{
  const s = await sayfaAc();
  await girisYap(s, YONETICI, YONETICI_SIFRE);
  await s.locator('.pano .grafik').first().waitFor();
  await cek(s, 'arayuz-genel-bakis', { fullPage: true });

  await sayfa(s, 'Ekip takvimi', '.takvim-etiket');
  await cek(s, 'arayuz-takvim', { fullPage: true });

  await sayfa(s, 'Onay kutusu', '#icerik table');
  await cek(s, 'arayuz-onay-kutusu', { fullPage: true });

  await sayfa(s, 'Personel', '#icerik table');
  await s.fill('input[type=search]', 'ornek.test');
  await s.selectOption('select[aria-label=Rol]', 'EMPLOYEE');
  await s.keyboard.press('Enter');
  await s.waitForLoadState('networkidle');
  await s.locator('#icerik table').first().waitFor();
  await cek(s, 'arayuz-personel', { fullPage: true });

  await s.locator('button', { hasText: 'Düzenle' }).first().click();
  await s.locator('dialog[open] input[name=fullName]').waitFor();
  await cek(s, 'arayuz-personel-duzenle');
  await s.keyboard.press('Escape');

  await sayfa(s, 'Demirbaşlar', '#icerik table');
  await cek(s, 'arayuz-demirbas', { fullPage: true });
  await s.selectOption('select[aria-label=Garanti]', '60');
  await s.waitForLoadState('networkidle');
  await s.locator('#icerik table').first().waitFor();
  await cek(s, 'arayuz-demirbas-garanti', { fullPage: true });
  await s.locator('button', { hasText: 'Düzenle' }).first().click();
  await s.locator('dialog[open] input[name=serialNo]').waitFor();
  await cek(s, 'arayuz-demirbas-duzenle');
  await s.keyboard.press('Escape');

  await sayfa(s, 'Raporlar', '.grafik');
  await cek(s, 'arayuz-rapor', { fullPage: true });

  await sayfa(s, 'Denetim kaydı', '#icerik table');
  await cek(s, 'arayuz-denetim-kaydi', { fullPage: true });
  await s.context().close();
}

// 3) Müdür: onay kutusu
{
  const s = await sayfaAc();
  await girisYap(s, 'kerem.aydin@ornek.test', DEMO_SIFRE);
  await sayfa(s, 'Onay kutusu', '#icerik table');
  await cek(s, 'arayuz-onay-kutusu-mudur', { fullPage: true });
  await s.context().close();
}

// 4) Personel: izin talebi penceresi ve mobil görünüm
{
  const s = await sayfaAc();
  await girisYap(s, 'zeynep.kaya@ornek.test', DEMO_SIFRE);
  await s.locator('button', { hasText: 'İzin talep et' }).click();
  await s.locator('dialog[open] select[name=type]').waitFor();
  await cek(s, 'arayuz-izin-talebi');
  await s.context().close();
}
{
  const s = await sayfaAc({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
  await girisYap(s, 'zeynep.kaya@ornek.test', DEMO_SIFRE);
  await cek(s, 'arayuz-mobil');
  await s.context().close();
}

// 5) Koyu tema
{
  const s = await sayfaAc({ colorScheme: 'dark' });
  await girisYap(s, YONETICI, YONETICI_SIFRE);
  await s.locator('.pano .grafik').first().waitFor();
  await cek(s, 'arayuz-koyu-tema', { fullPage: true });
  await s.context().close();
}

await tarayici.close();
