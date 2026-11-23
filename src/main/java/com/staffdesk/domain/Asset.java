package com.staffdesk.domain;

import com.staffdesk.service.BusinessRuleException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "asset")
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "asset_tag", nullable = false, unique = true, length = 50)
    private String assetTag;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 50)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AssetStatus status = AssetStatus.AVAILABLE;

    @Column(name = "serial_no", length = 100)
    private String serialNo;

    @Column(name = "purchase_date")
    private LocalDate purchaseDate;

    @Column(name = "purchase_price", precision = 12, scale = 2)
    private BigDecimal purchasePrice;

    @Column(name = "warranty_end")
    private LocalDate warrantyEnd;

    protected Asset() {}

    public Asset(String assetTag, String name, String category) {
        this.assetTag = assetTag;
        this.name = name;
        this.category = category;
    }

    /** Ad, kategori ve isteğe bağlı satın alma/garanti bilgilerini günceller (durum değişmez). */
    public void updateDetails(String name, String category, String serialNo, LocalDate purchaseDate,
            BigDecimal purchasePrice, LocalDate warrantyEnd) {
        this.name = name;
        this.category = category;
        this.serialNo = serialNo;
        this.purchaseDate = purchaseDate;
        this.purchasePrice = purchasePrice;
        this.warrantyEnd = warrantyEnd;
    }

    // Durum geçişleri: AVAILABLE -> ASSIGNED -> AVAILABLE ; AVAILABLE -> RETIRED (geri dönüş yok)

    public void markAssigned() {
        if (status != AssetStatus.AVAILABLE) {
            throw new BusinessRuleException("Zimmetlenebilmesi için demirbaş boşta olmalı (durum: " + status + ")");
        }
        status = AssetStatus.ASSIGNED;
    }

    public void markReturned() {
        if (status != AssetStatus.ASSIGNED) {
            throw new BusinessRuleException("Yalnızca zimmetli demirbaş iade alınabilir (durum: " + status + ")");
        }
        status = AssetStatus.AVAILABLE;
    }

    public void retire() {
        if (status != AssetStatus.AVAILABLE) {
            throw new BusinessRuleException("Yalnızca boştaki demirbaş kayıttan düşülebilir (durum: " + status + ")");
        }
        status = AssetStatus.RETIRED;
    }

    public Long getId() {
        return id;
    }

    public String getAssetTag() {
        return assetTag;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public AssetStatus getStatus() {
        return status;
    }

    public String getSerialNo() {
        return serialNo;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public BigDecimal getPurchasePrice() {
        return purchasePrice;
    }

    public LocalDate getWarrantyEnd() {
        return warrantyEnd;
    }
}
