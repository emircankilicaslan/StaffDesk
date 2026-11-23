package com.staffdesk.repository;

import com.staffdesk.domain.Asset;
import com.staffdesk.domain.AssetStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetRepository extends JpaRepository<Asset, Long>, JpaSpecificationExecutor<Asset> {

    @Query("select distinct a.category from Asset a order by a.category")
    List<String> findDistinctCategories();

    boolean existsByAssetTag(String assetTag);

    Page<Asset> findByStatus(AssetStatus status, Pageable pageable);

    /** Eşzamanlı zimmetlemede aynı demirbaşın iki kişiye verilmesini önlemek için satır kilidi alır. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Asset a where a.id = :id")
    Optional<Asset> findByIdForUpdate(@Param("id") Long id);
}
