
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
import com.returnmate.purchase.entity.ItemCategory;
import com.returnmate.purchase.entity.ReturnStatus;
import com.returnmate.purchase.policy.DeadlineService;
import com.returnmate.purchase.policy.StorePolicyService;
import com.returnmate.purchase.policy.TestPolicies;
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
        // The real store logic with hand-made policies: it's pure, so no need to mock it.
        purchaseService = new PurchaseService(purchaseRepository, userRepository,
                new StorePolicyService(TestPolicies.ALL), new DeadlineService(), fixedClock);
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
                new BigDecimal("348.00"), "USD", "112-4471893-1234567", deadline, windowDays, null, null, null);
    }

    private CreatePurchaseRequest storeRequest(String merchant, String storeCode, ItemCategory category,
                                               LocalDate purchased, LocalDate delivered) {
        return new CreatePurchaseRequest(merchant, "Item", purchased, new BigDecimal("99.00"), "USD",
                null, null, null, delivered, storeCode, category);
    }

    private static UpdatePurchasePatchRequest patch(LocalDate purchaseDate, LocalDate returnDeadline,
                                                    ReturnStatus status, LocalDate deliveryDate,
                                                    String storeCode, ItemCategory category) {
        return new UpdatePurchasePatchRequest(null, null, purchaseDate, null, null, null,
                returnDeadline, status, deliveryDate, storeCode, category);
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
    void createPurchase_unknownStoreWithoutDeadline_hasNoCountdown() {
        // Design doc: "never guess a deadline". The app asks the user instead.
        PurchaseResponse response = create(storeRequest("Corner Shop", null, null, LocalDate.of(2026, 11, 20), null));

        assertNull(response.storeCode());
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

        UpdatePurchasePatchRequest markReturned = patch(null, null, ReturnStatus.RETURNED, null, null, null);
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

        UpdatePurchasePatchRequest moveDate = patch(LocalDate.of(2026, 12, 5), null, null, null, null, null);

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

    // ------------------------------------------------------------ store policies

    @Test
    void knownMerchantName_getsTheStorePolicyDeadline() {
        // "Amazon.com" is recognised; bought Sep 1, delivered Sep 3 -> 30 days from delivery.
        PurchaseResponse response = create(storeRequest("Amazon.com", null, null,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3)));

        assertEquals("amazon", response.storeCode());
        assertEquals(LocalDate.of(2026, 10, 3), response.returnDeadline());
        assertEquals(DeadlineSource.MERCHANT_POLICY, response.deadlineSource());
        assertEquals(LocalDate.of(2026, 9, 3), response.deliveryDate());
    }

    @Test
    void chosenStoreAndCategory_holidayExtensionApplies() {
        PurchaseResponse response = create(storeRequest("Amazon", "amazon", ItemCategory.APPLE,
                LocalDate.of(2026, 11, 20), null));

        assertEquals(ItemCategory.APPLE, response.itemCategory());
        assertEquals(LocalDate.of(2027, 1, 15), response.returnDeadline());
        assertEquals(DeadlineSource.HOLIDAY_EXTENSION, response.deadlineSource());
    }

    @Test
    void userDeadlineWinsOverTheStorePolicy() {
        PurchaseResponse response = create(requestWith(LocalDate.of(2026, 11, 25), null)); // merchant "Amazon"

        assertEquals("amazon", response.storeCode());
        assertEquals(LocalDate.of(2026, 11, 25), response.returnDeadline());
        assertEquals(DeadlineSource.USER_ENTERED, response.deadlineSource());
    }

    @Test
    void returnWindowCountsFromDeliveryWhenKnown() {
        CreatePurchaseRequest request = new CreatePurchaseRequest("Corner Shop", "Lamp", LocalDate.of(2026, 11, 1),
                new BigDecimal("40.00"), "USD", null, null, 10, LocalDate.of(2026, 11, 5), null, null);

        assertEquals(LocalDate.of(2026, 11, 15), create(request).returnDeadline());
    }

    @Test
    void storeWithNoFixedLimit_hasNoDeadline() {
        PurchaseResponse response = create(storeRequest("Costco", null, null, LocalDate.of(2026, 11, 20), null));

        assertEquals("costco", response.storeCode());
        assertNull(response.returnDeadline());
    }

    @Test
    void unknownStoreCode_isRejected() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(createTestUser()));
        CreatePurchaseRequest request = storeRequest("IKEA", "ikea", null, LocalDate.of(2026, 11, 20), null);

        assertThrows(IllegalArgumentException.class, () -> purchaseService.createPurchase(request, authenticatedUser));
        verify(purchaseRepository, never()).save(any());
    }

    @Test
    void newPurchasesAreManual() {
        assertEquals(com.returnmate.purchase.entity.PurchaseSource.MANUAL, create(requestWith(null, null)).source());
    }

    private Purchase existing(String merchant, String storeCode, LocalDate purchased) {
        Purchase purchase = new Purchase(merchant, "Item", purchased, new BigDecimal("99.00"), "USD");
        purchase.setStoreCode(storeCode);
        return purchase;
    }

    private PurchaseResponse patchExisting(Purchase purchase, UpdatePurchasePatchRequest request) {
        UUID id = UUID.randomUUID();
        when(purchaseRepository.findByIdAndUser_Id(id, userId)).thenReturn(Optional.of(purchase));
        when(purchaseRepository.save(any(Purchase.class))).thenAnswer(inv -> inv.getArgument(0));
        return purchaseService.patchPurchaseById(id, request, authenticatedUser);
    }

    @Test
    void patchDeliveryDate_recalculatesAPolicyDeadline() {
        Purchase purchase = existing("Amazon", "amazon", LocalDate.of(2026, 9, 1));
        purchase.setReturnDeadline(LocalDate.of(2026, 10, 1), DeadlineSource.MERCHANT_POLICY);

        PurchaseResponse response = patchExisting(purchase, patch(null, null, null, LocalDate.of(2026, 9, 10), null, null));

        assertEquals(LocalDate.of(2026, 10, 10), response.returnDeadline());
    }

    @Test
    void patchPurchaseDate_pastAPolicyDeadline_recalculatesInsteadOfFailing() {
        Purchase purchase = existing("Amazon", "amazon", LocalDate.of(2026, 9, 1));
        purchase.setReturnDeadline(LocalDate.of(2026, 10, 1), DeadlineSource.MERCHANT_POLICY);

        PurchaseResponse response = patchExisting(purchase, patch(LocalDate.of(2026, 10, 5), null, null, null, null, null));

        assertEquals(LocalDate.of(2026, 11, 4), response.returnDeadline());
    }

    @Test
    void patchDeliveryDate_leavesTheUsersOwnDeadlineAlone() {
        Purchase purchase = existing("Amazon", "amazon", LocalDate.of(2026, 9, 1));
        purchase.setReturnDeadline(LocalDate.of(2026, 9, 20), DeadlineSource.USER_ENTERED);

        PurchaseResponse response = patchExisting(purchase, patch(null, null, null, LocalDate.of(2026, 9, 10), null, null));

        assertEquals(LocalDate.of(2026, 9, 20), response.returnDeadline());
        assertEquals(DeadlineSource.USER_ENTERED, response.deadlineSource());
    }

    @Test
    void patchStoreCode_givesAnUnknownStorePurchaseADeadline() {
        Purchase purchase = existing("BB Store #42", null, LocalDate.of(2026, 9, 1));

        PurchaseResponse response = patchExisting(purchase, patch(null, null, null, null, "bestbuy", null));

        assertEquals("bestbuy", response.storeCode());
        assertEquals(LocalDate.of(2026, 9, 16), response.returnDeadline());
    }

    @Test
    void patchDeliveryBeforePurchase_isRejected() {
        Purchase purchase = existing("Amazon", "amazon", LocalDate.of(2026, 9, 10));
        UUID id = UUID.randomUUID();
        when(purchaseRepository.findByIdAndUser_Id(id, userId)).thenReturn(Optional.of(purchase));

        assertThrows(IllegalArgumentException.class, () -> purchaseService.patchPurchaseById(id,
                patch(null, null, null, LocalDate.of(2026, 9, 1), null, null), authenticatedUser));
    }
}
