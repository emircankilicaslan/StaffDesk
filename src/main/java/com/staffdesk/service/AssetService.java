package com.staffdesk.service;

import com.staffdesk.api.CurrentUser;
import com.staffdesk.api.Dtos.AssetView;
import com.staffdesk.api.Dtos.AssignAssetRequest;
import com.staffdesk.api.Dtos.AssignmentView;
import com.staffdesk.api.Dtos.CreateAssetRequest;
import com.staffdesk.api.Dtos.ReturnAssetRequest;
import com.staffdesk.api.Dtos.UpdateAssetRequest;
import com.staffdesk.domain.Asset;
import com.staffdesk.domain.AssetAssignment;
import com.staffdesk.domain.AssetStatus;
import com.staffdesk.domain.Employee;
import com.staffdesk.repository.AssetAssignmentRepository;
import com.staffdesk.repository.AssetRepository;
import com.staffdesk.repository.EmployeeRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssetService {

    private final AssetRepository assets;
    private final AssetAssignmentRepository assignments;
    private final EmployeeRepository employees;
    private final AccessPolicy access;
    private final Clock clock;
    private final AuditService audit;

    public AssetService(AssetRepository assets, AssetAssignmentRepository assignments, EmployeeRepository employees,
            AccessPolicy access, Clock clock, AuditService audit) {
        this.audit = audit;
        this.assets = assets;
        this.assignments = assignments;
        this.employees = employees;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public AssetView create(CreateAssetRequest r) {
        String tag = r.assetTag().trim();
        if (assets.existsByAssetTag(tag)) {
            throw new BusinessRuleException("Bu demirbaş numarası zaten kayıtlı");
        }
        validateWarranty(r.purchaseDate(), r.warrantyEnd());
        Asset asset = new Asset(tag, r.name().trim(), r.category().trim());
        asset.updateDetails(r.name().trim(), r.category().trim(), blankToNull(r.serialNo()), r.purchaseDate(),
                r.purchasePrice(), r.warrantyEnd());
        Asset saved = assets.save(asset);
        audit.record("ASSET_CREATED", "Asset", saved.getId(), saved.getAssetTag() + " · " + saved.getName());
        return toView(saved);
    }

    /** Ad, kategori, seri no, satın alma ve garanti bilgilerini günceller; durum ve demirbaş no değişmez. */
    @Transactional
    public AssetView update(Long id, UpdateAssetRequest r) {
        Asset asset = assets.findById(id).orElseThrow(() -> new NotFoundException("Demirbaş bulunamadı"));
        validateWarranty(r.purchaseDate(), r.warrantyEnd());
        asset.updateDetails(r.name().trim(), r.category().trim(), blankToNull(r.serialNo()), r.purchaseDate(),
                r.purchasePrice(), r.warrantyEnd());
        audit.record("ASSET_UPDATED", "Asset", asset.getId(), asset.getAssetTag());
        return toView(asset);
    }

    private static void validateWarranty(LocalDate purchaseDate, LocalDate warrantyEnd) {
        if (purchaseDate != null && warrantyEnd != null && warrantyEnd.isBefore(purchaseDate)) {
            throw new BusinessRuleException("Garanti bitişi satın alma tarihinden önce olamaz");
        }
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    @Transactional(readOnly = true)
    public Page<AssetView> list(AssetStatus status, Pageable pageable) {
        return search(new AssetFilter(null, null, status, null), pageable);
    }

    @Transactional(readOnly = true)
    public Page<AssetView> search(AssetFilter f, Pageable pageable) {
        String pattern = SearchSupport.pattern(f.q());
        LocalDate limit = f.warrantyWithinDays() == null ? null : LocalDate.now(clock).plusDays(f.warrantyWithinDays());
        Specification<Asset> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (pattern != null) {
                p.add(cb.or(
                        cb.like(cb.lower(root.<String>get("name")), pattern, SearchSupport.ESCAPE),
                        cb.like(cb.lower(root.<String>get("assetTag")), pattern, SearchSupport.ESCAPE),
                        cb.like(cb.lower(root.<String>get("serialNo")), pattern, SearchSupport.ESCAPE)));
            }
            if (f.category() != null && !f.category().isBlank()) {
                p.add(cb.equal(root.get("category"), f.category().trim()));
            }
            if (f.status() != null) {
                p.add(cb.equal(root.get("status"), f.status()));
            }
            if (limit != null) {
                p.add(cb.isNotNull(root.get("warrantyEnd")));
                p.add(cb.lessThanOrEqualTo(root.<LocalDate>get("warrantyEnd"), limit));
                p.add(cb.notEqual(root.get("status"), AssetStatus.RETIRED));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return assets.findAll(spec, pageable).map(this::toView);
    }

    @Transactional(readOnly = true)
    public List<String> categories() {
        return assets.findDistinctCategories();
    }

    /** Satır kilidi sayesinde aynı demirbaşa eşzamanlı iki zimmet isteğinden yalnızca biri başarılı olur. */
    @Transactional
    public AssignmentView assign(Long assetId, AssignAssetRequest r) {
        Asset asset = assets.findByIdForUpdate(assetId).orElseThrow(() -> new NotFoundException("Demirbaş bulunamadı"));
        Employee employee = employees.findById(r.employeeId())
                .orElseThrow(() -> new NotFoundException("Personel bulunamadı"));
        if (!employee.isActive()) {
            throw new BusinessRuleException("Pasif personele zimmet verilemez");
        }
        asset.markAssigned();
        AssetAssignment a = assignments.save(new AssetAssignment(asset, employee, LocalDateTime.now(clock), r.note()));
        audit.record("ASSET_ASSIGNED", "Asset", asset.getId(), asset.getAssetTag() + " → " + employee.getEmail());
        return toView(a);
    }

    @Transactional
    public AssignmentView giveBack(Long assetId, ReturnAssetRequest r) {
        Asset asset = assets.findByIdForUpdate(assetId).orElseThrow(() -> new NotFoundException("Demirbaş bulunamadı"));
        asset.markReturned();
        AssetAssignment open = assignments.findByAssetIdAndReturnedAtIsNull(assetId)
                .orElseThrow(() -> new BusinessRuleException("Açık zimmet kaydı bulunamadı"));
        open.close(LocalDateTime.now(clock), r == null ? null : r.note());
        audit.record("ASSET_RETURNED", "Asset", asset.getId(), asset.getAssetTag() + " ← " + open.getEmployee().getEmail());
        return toView(open);
    }

    @Transactional
    public AssetView retire(Long assetId) {
        Asset asset = assets.findByIdForUpdate(assetId).orElseThrow(() -> new NotFoundException("Demirbaş bulunamadı"));
        asset.retire();
        audit.record("ASSET_RETIRED", "Asset", asset.getId(), asset.getAssetTag());
        return toView(asset);
    }

    /** Bir personelin zimmet geçmişi (iade edilenler dahil). */
    @Transactional(readOnly = true)
    public List<AssignmentView> historyOf(Long employeeId, CurrentUser user) {
        Employee employee = employees.findById(employeeId).orElseThrow(() -> new NotFoundException("Personel bulunamadı"));
        access.requireCanView(user, employee);
        return assignments.findByEmployeeIdOrderByAssignedAtDesc(employeeId).stream().map(this::toView).toList();
    }

    private AssetView toView(Asset a) {
        Integer daysLeft = a.getWarrantyEnd() == null ? null
                : (int) ChronoUnit.DAYS.between(LocalDate.now(clock), a.getWarrantyEnd());
        Long holderId = null;
        String holder = null;
        if (a.getStatus() == AssetStatus.ASSIGNED) {
            AssetAssignment open = assignments.findByAssetIdAndReturnedAtIsNull(a.getId()).orElse(null);
            if (open != null) {
                holderId = open.getEmployee().getId();
                holder = open.getEmployee().getFullName();
            }
        }
        return new AssetView(a.getId(), a.getAssetTag(), a.getName(), a.getCategory(), a.getStatus(), a.getSerialNo(),
                a.getPurchaseDate(), a.getPurchasePrice(), a.getWarrantyEnd(), daysLeft, holderId, holder);
    }

    private AssignmentView toView(AssetAssignment a) {
        return new AssignmentView(a.getId(), a.getAsset().getId(), a.getAsset().getAssetTag(), a.getAsset().getName(),
                a.getEmployee().getId(), a.getEmployee().getFullName(), a.getAssignedAt(), a.getReturnedAt(),
                a.getNote());
    }
}
