package com.wetech.demo.web3j.controller;

import com.wetech.demo.web3j.contracts.escrow.Escrow;
import com.wetech.demo.web3j.service.EscrowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/escrow")
@RequiredArgsConstructor
public class EscrowController {

    private final EscrowService escrowService;

    @PostMapping("/deploy")
    public CompletableFuture<ResponseEntity<Map<String, String>>> deploy() {
        return escrowService.deploy().thenApply(contract -> {
            TransactionReceipt receipt = contract.getTransactionReceipt()
                    .orElseThrow(() -> new IllegalStateException("Deployment receipt is unavailable"));
            Map<String, String> response = new HashMap<>();
            response.put("contractAddress", contract.getContractAddress());
            response.put("transactionHash", receipt.getTransactionHash());
            response.put("deployBlock", escrowService.getDeployBlock().toString());
            return ResponseEntity.ok(response);
        });
    }

    @PostMapping("/load")
    public ResponseEntity<Map<String, String>> load(
            @RequestParam String address,
            @RequestParam BigInteger fromBlock) {
        escrowService.load(address, fromBlock);
        Map<String, String> response = new HashMap<>();
        response.put("contractAddress", address);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/address")
    public CompletableFuture<ResponseEntity<Map<String, String>>> getAddress() {
        return escrowService.getArbiter().thenApply(arbiter -> {
            Map<String, String> response = new HashMap<>();
            response.put("contractAddress", escrowService.getContractAddress());
            response.put("arbiter", arbiter);
            return ResponseEntity.ok(response);
        });
    }

    @GetMapping("/orders/{id}")
    public CompletableFuture<ResponseEntity<Map<String, String>>> getOrder(@PathVariable BigInteger id) {
        return escrowService.getOrder(id).thenApply(ResponseEntity::ok);
    }

    @GetMapping("/orders")
    public CompletableFuture<ResponseEntity<List<Map<String, String>>>> getOrders() {
        return escrowService.listOrders().thenApply(ResponseEntity::ok);
    }

    @PostMapping("/orders/{id}/resolve")
    public CompletableFuture<ResponseEntity<Map<String, String>>> resolve(
            @PathVariable BigInteger id,
            @RequestParam boolean sellerWins) {
        log.info("Received resolve request for order {}, sellerWins={}", id, sellerWins);
        return escrowService.resolve(id, sellerWins).thenApply(this::transactionResponse);
    }

    private ResponseEntity<Map<String, String>> transactionResponse(TransactionReceipt receipt) {
        Map<String, String> response = new HashMap<>();
        response.put("transactionHash", receipt.getTransactionHash());
        response.put("blockNumber", receipt.getBlockNumber().toString());
        response.put("status", receipt.getStatus());
        return ResponseEntity.ok(response);
    }
}
