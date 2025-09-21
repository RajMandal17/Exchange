package com.custom.marketdata.manager;

import com.custom.marketdata.entity.DepositEntity;
import com.custom.marketdata.repository.DepositRepository;
import com.custom.matchingengine.command.DepositCommand;
import com.custom.matchingengine.command.MatchingEngineCommandProducer;
import com.custom.openapi.model.PagedList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DepositManager {
    private final DepositRepository depositRepository;
    private final MatchingEngineCommandProducer matchingEngineCommandProducer;

    public PagedList<DepositEntity> getDeposits(String userId, String currency, String state, int page, int limit) {
        return depositRepository.findByUserId(userId, currency, state, page, limit);
    }

    public DepositEntity createDeposit(String userId, String currency, BigDecimal amount, String txid, String rid) {
        DepositEntity deposit = new DepositEntity();
        deposit.setId(UUID.randomUUID().toString());
        deposit.setUserId(userId);
        deposit.setCurrency(currency);
        deposit.setAmount(amount);
        deposit.setTxid(txid);
        deposit.setRid(rid);
        deposit.setState("pending");
        deposit.setType("crypto"); // Default to crypto, can be extended for fiat
        deposit.setCreatedAt(new Date());
        deposit.setUpdatedAt(new Date());
        deposit.setSettled(false);

        depositRepository.save(deposit);
        logger.info("Created deposit {} for user {}: {} {}", deposit.getId(), userId, amount, currency);

        // Send command to matching engine
        DepositCommand command = new DepositCommand();
        command.setUserId(userId);
        command.setCurrency(currency);
        command.setAmount(amount);
        command.setTransactionId(deposit.getId());
        matchingEngineCommandProducer.send(command, null);

        return deposit;
    }

    public void updateDepositState(String depositId, String newState) {
        DepositEntity deposit = depositRepository.findById(depositId);
        if (deposit != null) {
            deposit.setState(newState);
            deposit.setUpdatedAt(new Date());
            if ("succeed".equals(newState)) {
                deposit.setSettled(true);
            }
            depositRepository.update(deposit);
            logger.info("Updated deposit {} state to {}", depositId, newState);
        }
    }

    public DepositEntity getDeposit(String depositId) {
        return depositRepository.findById(depositId);
    }
}
