package com.mehmetserin.banking.transfer;

import com.mehmetserin.banking.transfer.dto.TransferRequest;
import com.mehmetserin.banking.transfer.dto.TransferResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferResponse> createTransfer(@AuthenticationPrincipal String username,
                                                             @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                             @Valid @RequestBody TransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(transferService.transfer(username, idempotencyKey, request));
    }

    @PostMapping("/{transferId}/reverse")
    public ResponseEntity<TransferResponse> reverseTransfer(@AuthenticationPrincipal String username,
                                                              @PathVariable UUID transferId,
                                                              @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transferService.reverse(username, transferId, idempotencyKey));
    }

    @GetMapping("/{transferId}")
    public ResponseEntity<TransferResponse> getTransfer(@AuthenticationPrincipal String username,
                                                          @PathVariable UUID transferId) {
        return ResponseEntity.ok(transferService.getMyTransfer(username, transferId));
    }
}
