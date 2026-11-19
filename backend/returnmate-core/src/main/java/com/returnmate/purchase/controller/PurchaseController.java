    package com.returnmate.purchase.controller;

    import com.returnmate.purchase.dto.CreatePurchaseRequest;
    import com.returnmate.purchase.dto.PurchaseResponse;
    import com.returnmate.purchase.dto.UpdatePurchasePatchRequest;
    import com.returnmate.purchase.service.PurchaseService;
    import jakarta.validation.Valid;
    import org.springframework.data.domain.Page;
    import org.springframework.data.domain.Pageable;
    import org.springframework.data.domain.Sort;
    import org.springframework.data.web.PageableDefault;
    import org.springframework.http.HttpStatus;
    import org.springframework.web.bind.annotation.*;

    import java.util.List;
    import java.util.UUID;

    @RestController
    @RequestMapping("/api/v1/purchases")
    public class PurchaseController {

        private final PurchaseService purchaseService;


        public PurchaseController(PurchaseService purchaseService) {
            this.purchaseService = purchaseService;
        }

        @PostMapping
        @ResponseStatus(HttpStatus.CREATED)
        public PurchaseResponse createPurchase(
                @Valid @RequestBody CreatePurchaseRequest request) {

            return purchaseService.createPurchase(request);
        }
        @GetMapping
        public Page<PurchaseResponse> getAllPurchases(@PageableDefault (
                page = 0,
                size =10,
                sort = "purchaseDate",
                direction = Sort.Direction.DESC
        )Pageable pageable){
            return purchaseService.getAllPurchases(pageable);
        }


        @GetMapping("/{id}")
        public PurchaseResponse getPurchaseById(@PathVariable UUID id){
            return purchaseService.getPurchaseById(
                    id
            );
        }

        @PutMapping("/{id}")
        public PurchaseResponse patchPurchaseDetails(@PathVariable UUID id,
                                                      @Valid @RequestBody CreatePurchaseRequest request){
                return purchaseService.updatePurchaseById(id,request);
        }

        @PatchMapping("/{id}")
        public PurchaseResponse patchPurchaseDetails(
                @PathVariable UUID id,
                @Valid @RequestBody UpdatePurchasePatchRequest request) {

            return purchaseService.patchPurchaseById(id, request);
        }

        @DeleteMapping("/{id}")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public void deletePurchase(@PathVariable UUID id) {

            purchaseService.deletePurchaseById(id);
        }
    }


