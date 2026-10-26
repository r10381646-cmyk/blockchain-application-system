package com.wetech.demo.web3j.service;

import com.wetech.demo.web3j.contracts.escrow.Escrow;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.tx.gas.ContractGasProvider;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class EscrowService {

    private static final BigInteger MAX_EVENT_QUERY_BLOCKS = BigInteger.TEN;

    private static final String[] STATUS = {
            "Created", "Paid", "Completed", "Refunded", "Disputed"
    };

    private final Web3j web3j;
    private final Credentials credentials;
    private final ContractGasProvider gasProvider;

    private Escrow contract;

    @Getter
    private String contractAddress;

    @Getter
    private BigInteger deployBlock;

    /**
     * Deploy an Escrow contract whose arbiter is the backend account.
     */
    public CompletableFuture<Escrow> deploy() {
        log.info("Deploying Escrow contract with backend arbiter: {}", credentials.getAddress());
        return Escrow.deploy(web3j, credentials, gasProvider, credentials.getAddress())
                .sendAsync()
                .thenApply(deployedContract -> {
                    this.contract = deployedContract;
                    this.contractAddress = deployedContract.getContractAddress();
                    this.deployBlock = deployedContract.getTransactionReceipt()
                            .map(TransactionReceipt::getBlockNumber)
                            .orElse(BigInteger.ZERO);
                    log.info("Escrow contract deployed to: {} at block {}",
                            contractAddress, deployBlock);
                    return deployedContract;
                });
    }

    /**
     * Load an existing Escrow contract and remember the block from which events should be queried.
     */
    public void load(String address, BigInteger fromBlock) {
        this.contract = Escrow.load(address, web3j, credentials, gasProvider);
        this.contractAddress = address;
        this.deployBlock = fromBlock;
        log.info("Loaded Escrow contract from address: {}, event start block: {}",
                address, fromBlock);
    }

    /**
     * Return the arbiter recorded by the currently loaded contract.
     */
    public CompletableFuture<String> getArbiter() {
        requireContract();
        return contract.arbiter().sendAsync();
    }

    /**
     * Query one order and convert its Solidity fields to REST-friendly strings.
     */
    public CompletableFuture<Map<String, String>> getOrder(BigInteger id) {
        requireContract();
        return contract.getOrder(id).sendAsync().thenApply(order -> Map.of(
                "buyer", order.buyer,
                "seller", order.seller,
                "token", order.token,
                "amount", order.amount.toString(),
                "deadline", order.deadline.toString(),
                "status", statusName(order.status),
                "description", order.description
        ));
    }

    /**
     * Rebuild the order list from OrderCreated events, querying no more than ten blocks at a time.
     */
    public CompletableFuture<List<Map<String, String>>> listOrders() {
        requireContract();
        if (deployBlock == null) {
            throw new IllegalStateException("Contract event start block is not configured");
        }

        return web3j.ethBlockNumber().sendAsync()
                .thenCompose(latestBlock -> collectOrderCreatedEvents(
                        deployBlock, latestBlock.getBlockNumber()))
                .thenApply(events -> events.stream().map(event -> {
                    Map<String, String> order = new HashMap<>();
                    order.put("id", event.id.toString());
                    order.put("buyer", event.buyer);
                    order.put("seller", event.seller);
                    order.put("amount", event.amount.toString());
                    order.put("blockNumber", event.log.getBlockNumber().toString());
                    order.put("transactionHash", event.log.getTransactionHash());
                    return order;
                }).toList());
    }

    private CompletableFuture<List<Escrow.OrderCreatedEventResponse>> collectOrderCreatedEvents(
            BigInteger fromBlock, BigInteger latestBlock) {
        if (fromBlock.compareTo(latestBlock) > 0) {
            return CompletableFuture.completedFuture(List.of());
        }

        BigInteger toBlock = fromBlock.add(MAX_EVENT_QUERY_BLOCKS).subtract(BigInteger.ONE)
                .min(latestBlock);
        CompletableFuture<List<Escrow.OrderCreatedEventResponse>> batchFuture = new CompletableFuture<>();
        contract.orderCreatedEventFlowable(
                        DefaultBlockParameter.valueOf(fromBlock),
                        DefaultBlockParameter.valueOf(toBlock))
                .toList()
                .subscribe(batchFuture::complete, batchFuture::completeExceptionally);

        BigInteger nextFromBlock = toBlock.add(BigInteger.ONE);
        return batchFuture.thenCompose(batch -> collectOrderCreatedEvents(nextFromBlock, latestBlock)
                .thenApply(remaining -> {
                    List<Escrow.OrderCreatedEventResponse> events = new ArrayList<>(batch.size() + remaining.size());
                    events.addAll(batch);
                    events.addAll(remaining);
                    return events;
                }));
    }

    /**
     * Resolve a disputed order. The transaction is signed by the backend arbiter account.
     */
    public CompletableFuture<TransactionReceipt> resolve(BigInteger id, boolean sellerWins) {
        requireContract();
        log.info("Resolving Escrow order {}, sellerWins={}", id, sellerWins);
        return contract.resolve(id, sellerWins).sendAsync();
    }

    private String statusName(BigInteger status) {
        int index = status.intValue();
        if (index < 0 || index >= STATUS.length) {
            return "Unknown(" + status + ")";
        }
        return STATUS[index];
    }

    private void requireContract() {
        if (contract == null) {
            throw new IllegalStateException(
                    "Contract not deployed or loaded, call /deploy or /load first"
            );
        }
    }
}
