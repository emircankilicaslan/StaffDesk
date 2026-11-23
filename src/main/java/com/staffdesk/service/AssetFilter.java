package com.staffdesk.service;

import com.staffdesk.domain.AssetStatus;

/**
 * Demirbaş listesi süzgeci; boş alanlar süzgece katılmaz.
 *
 * @param q arama metni (ad, demirbaş no, seri no)
 * @param warrantyWithinDays verilirse garantisi bu kadar gün içinde bitecek (veya bitmiş) kayıtlar; kayıttan düşülenler hariç
 */
public record AssetFilter(String q, String category, AssetStatus status, Integer warrantyWithinDays) {

    public static final AssetFilter ALL = new AssetFilter(null, null, null, null);
}
