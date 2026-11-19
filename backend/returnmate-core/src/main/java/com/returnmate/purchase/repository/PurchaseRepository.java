package com.returnmate.purchase.repository;

import com.returnmate.purchase.entity.Purchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {


    
}
