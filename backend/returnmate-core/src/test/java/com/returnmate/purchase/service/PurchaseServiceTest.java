
package com.returnmate.purchase.service;

import com.returnmate.purchase.dto.CreatePurchaseRequest;
import com.returnmate.purchase.dto.PurchaseResponse;
import com.returnmate.purchase.entity.Purchase;
import com.returnmate.purchase.entity.User;
import com.returnmate.purchase.exceptions.ResourceNotFoundException;
import com.returnmate.purchase.repository.PurchaseRepository;
import com.returnmate.purchase.repository.UserRepository;
import com.returnmate.purchase.security.AuthenticatedUser;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseServiceTest {

    @Mock
    private PurchaseRepository purchaseRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PurchaseService purchaseService;

    private final UUID userId =
            UUID.fromString("9ded9065-a70a-4291-bfe6-e1e929bacd3a");

    private final AuthenticatedUser authenticatedUser =
            new AuthenticatedUser(
                    userId,
                    "testuser1@returnmate.com"
            );

    private User createTestUser() {
        return new User(
                "testuser1@returnmate.com",
                "encodedPassword"
        );
    }

    @Test
    void createPurchase_shouldSaveAndReturnPurchase() {

        // Arrange
        CreatePurchaseRequest request = new CreatePurchaseRequest(
                "Amazon",
                "MacBook Air M5",
                LocalDate.of(2026, 11, 19),
                new BigDecimal("1049.99"),
                "USD"
        );

        User user = createTestUser();

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        when(purchaseRepository.save(any(Purchase.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        PurchaseResponse response =
                purchaseService.createPurchase(
                        request,
                        authenticatedUser
                );

        // Assert
        assertNotNull(response);
        assertEquals("Amazon", response.merchantName());
        assertEquals("MacBook Air M5", response.productName());
        assertEquals(
                new BigDecimal("1049.99"),
                response.purchaseAmount()
        );
        assertEquals("USD", response.currency());

        // Verify repository interactions
        ArgumentCaptor<Purchase> captor =
                ArgumentCaptor.forClass(Purchase.class);

        verify(purchaseRepository, times(1))
                .save(captor.capture());

        Purchase savedPurchase = captor.getValue();

        assertEquals("Amazon", savedPurchase.getMerchantName());
        assertEquals("MacBook Air M5", savedPurchase.getProductName());

        verify(userRepository, times(1))
                .findById(userId);
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

        when(purchaseRepository.findByIdAndUser_Id(
                purchaseId,
                userId
        )).thenReturn(Optional.of(purchase));

        // Act
        PurchaseResponse response =
                purchaseService.getPurchaseById(
                        purchaseId,
                        authenticatedUser
                );

        // Assert
        assertNotNull(response);
        assertEquals("Amazon", response.merchantName());
        assertEquals("MacBook Air M5", response.productName());
        assertEquals(
                new BigDecimal("1049.99"),
                response.purchaseAmount()
        );

        verify(purchaseRepository, times(1))
                .findByIdAndUser_Id(purchaseId, userId);
    }

    @Test
    void getPurchaseById_shouldThrowExceptionWhenPurchaseDoesNotBelongToUser() {

        // Arrange
        UUID purchaseId = UUID.randomUUID();

        when(purchaseRepository.findByIdAndUser_Id(
                purchaseId,
                userId
        )).thenReturn(Optional.empty());

        // Act and Assert
        assertThrows(
                ResourceNotFoundException.class,
                () -> purchaseService.getPurchaseById(
                        purchaseId,
                        authenticatedUser
                )
        );

        verify(purchaseRepository, times(1))
                .findByIdAndUser_Id(purchaseId, userId);
    }
}
