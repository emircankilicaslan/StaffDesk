package com.staffdesk.repository;

import com.staffdesk.domain.AssetAssignment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetAssignmentRepository extends JpaRepository<AssetAssignment, Long> {

    Optional<AssetAssignment> findByAssetIdAndReturnedAtIsNull(Long assetId);

    List<AssetAssignment> findByEmployeeIdOrderByAssignedAtDesc(Long employeeId);

    long countByEmployeeIdAndReturnedAtIsNull(Long employeeId);
}
