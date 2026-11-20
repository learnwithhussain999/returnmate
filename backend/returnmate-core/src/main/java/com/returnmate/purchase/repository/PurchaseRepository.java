package com.returnmate.purchase.repository;

import com.returnmate.purchase.entity.Purchase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {


    Page<Purchase> findAllByUser_Id(
            UUID userId,
            Pageable pageable
    );

    Optional<Purchase> findByIdAndUser_Id(
            UUID purchaseId,
            UUID userId
    );
}
