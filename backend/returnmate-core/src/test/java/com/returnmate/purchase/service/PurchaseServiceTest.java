package com.returnmate.purchase.service;

import com.returnmate.purchase.dto.CreatePurchaseRequest;
import com.returnmate.purchase.dto.PurchaseResponse;
import com.returnmate.purchase.entity.Purchase;
import com.returnmate.purchase.repository.PurchaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PurchaseServiceTest {
    @Mock
    private PurchaseRepository purchaseRepository;

    @InjectMocks
    private PurchaseService purchaseService;

    @Test
    void createPurchase_shouldSaveAndReturnPurchase() {

        // Arrange: Prepare request data
        CreatePurchaseRequest request = new CreatePurchaseRequest(
                "Amazon",
                "MacBook Air M5",
                LocalDate.of(2026, 11, 19),
                new BigDecimal("1049.99"),
                "USD"
        );

        // Mock repository behavior
        when(purchaseRepository.save(any(Purchase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act: Execute service method
        PurchaseResponse response =
                purchaseService.createPurchase(request);

        // Assert: Verify response
        assertNotNull(response);
        assertEquals("Amazon", response.merchantName());
        assertEquals("MacBook Air M5", response.productName());
        assertEquals(
                new BigDecimal("1049.99"),
                response.purchaseAmount()
        );
        assertEquals("USD", response.currency());

        // Verify repository interaction
        ArgumentCaptor<Purchase> captor =
                ArgumentCaptor.forClass(Purchase.class);

        verify(purchaseRepository, times(1))
                .save(captor.capture());

        Purchase savedPurchase = captor.getValue();

        assertEquals("Amazon", savedPurchase.getMerchantName());
        assertEquals("MacBook Air M5", savedPurchase.getProductName());
        assertEquals(
                new BigDecimal("1049.99"),
                savedPurchase.getPurchaseAmount()
        );
    }

    @Test
    void getPurchaseById_shouldReturnPurchase() {

        // Arrange
        UUID purchaseId = UUID.randomUUID();

        Purchase purchase = new Purchase(
                "Amazon",
                "MacBook Air M5",
                LocalDate.of(2026, 11, 19),
                new BigDecimal("1049.99"),
                "USD"
        );

        when(purchaseRepository.findById(purchaseId))
                .thenReturn(Optional.of(purchase));

        // Act
        PurchaseResponse response =
                purchaseService.getPurchaseById(purchaseId);

        // Assert
        assertNotNull(response);
        assertEquals("Amazon", response.merchantName());
        assertEquals("MacBook Air M5", response.productName());
        assertEquals(
                new BigDecimal("1049.99"),
                response.purchaseAmount()
        );

        verify(purchaseRepository, times(1))
                .findById(purchaseId);
    }


}
