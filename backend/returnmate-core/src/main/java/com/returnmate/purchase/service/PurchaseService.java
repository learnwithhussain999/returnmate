package com.returnmate.purchase.service;

import com.returnmate.purchase.dto.CreatePurchaseRequest;
import com.returnmate.purchase.dto.PurchaseResponse;
import com.returnmate.purchase.dto.UpdatePurchasePatchRequest;
import com.returnmate.purchase.entity.Purchase;
import com.returnmate.purchase.exceptions.ResourceNotFoundException;
import com.returnmate.purchase.mapper.PurchaseMapper;
import com.returnmate.purchase.repository.PurchaseRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.springframework.data.jpa.domain.AbstractPersistable_.id;

@Service
public class PurchaseService {

    private final PurchaseRepository purchaseRepository;


    public PurchaseService(PurchaseRepository purchaseRepository) {
        this.purchaseRepository = purchaseRepository;
    }

    @Transactional
    public PurchaseResponse createPurchase(CreatePurchaseRequest request){
        Purchase purchase= PurchaseMapper.toEntity(request);
        Purchase savedPurchase=purchaseRepository.save(purchase);
        return PurchaseMapper.toResponse(savedPurchase);
    }


    public List<PurchaseResponse> getAllPurchases(){
        return purchaseRepository.findAll().stream().map(PurchaseMapper::toResponse).toList();
    }

    public PurchaseResponse getPurchaseById(UUID uuid){
        Purchase purchase = purchaseRepository.findById(uuid)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Purchase not found with id: " + uuid));
        return PurchaseMapper.toResponse(purchase);
    }

    @Transactional
    public PurchaseResponse updatePurchaseById(UUID uuid,CreatePurchaseRequest request){
        Purchase purchase = purchaseRepository.findById(uuid)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Purchase not found with id: " + uuid));

        purchase.updateDetails( request.merchantName().trim(),
                request.productName().trim(),
                request.purchaseDate(),
                request.purchaseAmount(),
                request.currency());

       Purchase updatedPurchase= purchaseRepository.save(purchase);
       return PurchaseMapper.toResponse(purchase);

    }

    @Transactional
    public void deletePurchaseById(UUID uuid){
        Purchase purchase = purchaseRepository.findById(uuid)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Purchase not found with id: " + uuid));
        purchaseRepository.delete(purchase);
    }

    @Transactional
    public PurchaseResponse patchPurchaseById(
            UUID uuid,
            UpdatePurchasePatchRequest request) {

        Purchase purchase = purchaseRepository.findById(uuid)
                .orElseThrow(() ->
                        new ResourceNotFoundException(
                                "Purchase not found with id: " + uuid));

        purchase.patchDetails(request);

        Purchase updatedPurchase = purchaseRepository.save(purchase);

        return PurchaseMapper.toResponse(updatedPurchase);
    }
}
