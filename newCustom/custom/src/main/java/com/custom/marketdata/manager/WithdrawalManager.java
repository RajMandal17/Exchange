package com.custom.marketdata.manager;

import com.custom.marketdata.entity.WithdrawalEntity;
import com.custom.marketdata.repository.WithdrawalRepository;
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
public class WithdrawalManager {
    private final WithdrawalRepository withdrawalRepository;

    public PagedList<WithdrawalEntity> getWithdrawals(String userId, String currency, String state, int page, int limit) {
        return withdrawalRepository.findByUserId(userId, currency, state, page, limit);
    }

    public WithdrawalEntity createWithdrawal(String userId, String currency, BigDecimal amount, String rid) {
        WithdrawalEntity withdrawal = new WithdrawalEntity();
        withdrawal.setId(UUID.randomUUID().toString());
        withdrawal.setUserId(userId);
        withdrawal.setCurrency(currency);
        withdrawal.setAmount(amount);
        withdrawal.setRid(rid);
        withdrawal.setState("pending");
        withdrawal.setType("crypto"); // Default to crypto, can be extended for fiat
        withdrawal.setCreatedAt(new Date());
        withdrawal.setUpdatedAt(new Date());
        withdrawal.setSettled(false);

        withdrawalRepository.save(withdrawal);
        logger.info("Created withdrawal {} for user {}: {} {}", withdrawal.getId(), userId, amount, currency);

        return withdrawal;
    }

    public void updateWithdrawalState(String withdrawalId, String newState) {
        WithdrawalEntity withdrawal = withdrawalRepository.findById(withdrawalId);
        if (withdrawal != null) {
            withdrawal.setState(newState);
            withdrawal.setUpdatedAt(new Date());
            if ("succeed".equals(newState)) {
                withdrawal.setSettled(true);
            }
            withdrawalRepository.update(withdrawal);
            logger.info("Updated withdrawal {} state to {}", withdrawalId, newState);
        }
    }

    public WithdrawalEntity getWithdrawal(String withdrawalId) {
        return withdrawalRepository.findById(withdrawalId);
    }
}
