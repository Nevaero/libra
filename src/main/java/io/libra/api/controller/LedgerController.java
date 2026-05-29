package io.libra.api.controller;

import io.libra.api.dto.AccountResponse;
import io.libra.api.dto.BalanceResponse;
import io.libra.ledger.port.LedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;

    @GetMapping("/{id}")
    public ResponseEntity<AccountResponse> account(@PathVariable UUID id) {
        return ledgerService.findAccountById(id)
                .map(AccountResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    // getBalance throws if the account is unknown; the exception handler maps that to 404.
    @GetMapping("/{id}/balance")
    public BalanceResponse balance(@PathVariable UUID id) {
        return BalanceResponse.from(ledgerService.getBalance(id));
    }
}
