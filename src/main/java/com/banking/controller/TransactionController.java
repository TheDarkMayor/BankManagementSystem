package com.banking.controller;

import com.banking.dto.TransactionRequest;
import com.banking.dto.TransactionResult;
import com.banking.service.BankingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
@Slf4j
public class TransactionController {

    private final BankingService bankingService;

    @PostMapping
    public ResponseEntity<TransactionResult> process(
            @Valid @RequestBody TransactionRequest request) {
        TransactionResult result = bankingService.processTransaction(request);
        return ResponseEntity.ok(result);
    }
}
