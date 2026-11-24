
package com.returnmate.purchase.service;

import com.returnmate.purchase.dto.CreatePurchaseRequest;
import com.returnmate.purchase.dto.PurchaseResponse;
import com.returnmate.purchase.entity.Purchase;
import com.returnmate.purchase.entity.User;
import com.returnmate.purchase.exceptions.ResourceNotFoundException;
import com.returnmate.purchase.repository.PurchaseRepository;
import com.returnmate.purchase.repository.UserRepository;
import com.returnmate.purchase.security.AuthenticatedUser;

import com.returnmate.purchase.dto.UpdatePurchasePatchRequest;
import com.returnmate.purchase.entity.DeadlineSource;
import com.returnmate.purchase.entity.ReturnStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

    // Freeze "today" so daysUntilDeadline is predictable on any day the tests run.
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 23);
    private final Clock fixedClock =
            Clock.fixed(TODAY.atStartOfDay(ZoneOffset.UTC).toInstant(), ZoneOffset.UTC);

    private PurchaseService purchaseService;

    @BeforeEach
    void setUp() {
        // Built by hand instead of @InjectMocks: Mockito would pass null for the Clock,
        // because a Clock isn't a @Mock.
        purchaseService = new PurchaseService(purchaseRepository, userRepository, fixedClock);
    }

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

    // ------------------------------------------------------------ return tracking

    private CreatePurchaseRequest requestWith(LocalDate deadline, Integer windowDays) {
        return new CreatePurchaseRequest("Amazon", "Sony WH-1000XM5", LocalDate.of(2026, 11, 20),
                new BigDecimal("348.00"), "USD", "112-4471893-1234567", deadline, windowDays);
    }

    private PurchaseResponse create(CreatePurchaseRequest request) {
        when(userRepository.findById(userId)).thenReturn(Optional.of(createTestUser()));
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(inv -> inv.getArgument(0));
        return purchaseService.createPurchase(request, authenticatedUser);
    }

    @Test
    void createPurchase_withReturnWindow_countsFromPurchaseDate() {
        PurchaseResponse response = create(requestWith(null, 30));

        assertEquals(LocalDate.of(2026, 12, 20), response.returnDeadline());
        assertEquals(DeadlineSource.USER_ENTERED, response.deadlineSource());
        assertEquals(27, response.daysUntilDeadline()); // Nov 23 -> Dec 20
        assertEquals(ReturnStatus.OPEN, response.returnStatus());
        assertEquals("112-4471893-1234567", response.orderNumber());
        assertFalse(response.expired());
    }

    @Test
    void createPurchase_withExplicitDeadline_usesIt() {
        PurchaseResponse response = create(requestWith(LocalDate.of(2026, 11, 25), null));

        assertEquals(LocalDate.of(2026, 11, 25), response.returnDeadline());
        assertEquals(2, response.daysUntilDeadline());
    }

    @Test
    void createPurchase_withoutDeadline_hasNoCountdown() {
        PurchaseResponse response = create(requestWith(null, null));

        assertNull(response.returnDeadline());
        assertNull(response.deadlineSource());
        assertNull(response.daysUntilDeadline());
        assertFalse(response.expired());
    }

    @Test
    void openPurchasePastItsDeadline_isExpired() {
        Purchase purchase = new Purchase("Target", "Air fryer", LocalDate.of(2026, 10, 1),
                new BigDecimal("129.99"), "USD");
        purchase.setReturnDeadline(LocalDate.of(2026, 11, 20), DeadlineSource.MERCHANT_POLICY);
        UUID id = UUID.randomUUID();
        when(purchaseRepository.findByIdAndUser_Id(id, userId)).thenReturn(Optional.of(purchase));

        PurchaseResponse response = purchaseService.getPurchaseById(id, authenticatedUser);

        assertEquals(-3, response.daysUntilDeadline());
        assertTrue(response.expired());
    }

    @Test
    void patchReturnStatus_returnedPurchaseIsNoLongerExpired() {
        Purchase purchase = new Purchase("Target", "Air fryer", LocalDate.of(2026, 10, 1),
                new BigDecimal("129.99"), "USD");
        purchase.setReturnDeadline(LocalDate.of(2026, 11, 20), DeadlineSource.MERCHANT_POLICY);
        UUID id = UUID.randomUUID();
        when(purchaseRepository.findByIdAndUser_Id(id, userId)).thenReturn(Optional.of(purchase));
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdatePurchasePatchRequest markReturned = new UpdatePurchasePatchRequest(
                null, null, null, null, null, null, null, ReturnStatus.RETURNED);
        PurchaseResponse response = purchaseService.patchPurchaseById(id, markReturned, authenticatedUser);

        assertEquals(ReturnStatus.RETURNED, response.returnStatus());
        assertFalse(response.expired());
    }

    @Test
    void patchPurchaseDate_afterExistingDeadline_isRejected() {
        Purchase purchase = new Purchase("Amazon", "Headphones", LocalDate.of(2026, 11, 1),
                new BigDecimal("348.00"), "USD");
        purchase.setReturnDeadline(LocalDate.of(2026, 12, 1), DeadlineSource.RECEIPT);
        UUID id = UUID.randomUUID();
        when(purchaseRepository.findByIdAndUser_Id(id, userId)).thenReturn(Optional.of(purchase));

        UpdatePurchasePatchRequest moveDate = new UpdatePurchasePatchRequest(
                null, null, LocalDate.of(2026, 12, 5), null, null, null, null, null);

        assertThrows(IllegalArgumentException.class,
                () -> purchaseService.patchPurchaseById(id, moveDate, authenticatedUser));
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void putReplacesTheDeadline_evenWhenThePurchaseDateMovesPastTheOldOne() {
        Purchase purchase = new Purchase("Amazon", "Headphones", LocalDate.of(2026, 11, 1),
                new BigDecimal("348.00"), "USD");
        purchase.setReturnDeadline(LocalDate.of(2026, 11, 10), DeadlineSource.RECEIPT);
        UUID id = UUID.randomUUID();
        when(purchaseRepository.findByIdAndUser_Id(id, userId)).thenReturn(Optional.of(purchase));
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(inv -> inv.getArgument(0));

        // New purchase date Nov 20 is after the OLD deadline (Nov 10), but the request
        // brings a new 30-day window, so the update is valid.
        PurchaseResponse response = purchaseService.updatePurchaseById(id, requestWith(null, 30), authenticatedUser);

        assertEquals(LocalDate.of(2026, 12, 20), response.returnDeadline());
        assertEquals(DeadlineSource.USER_ENTERED, response.deadlineSource());
    }
}
