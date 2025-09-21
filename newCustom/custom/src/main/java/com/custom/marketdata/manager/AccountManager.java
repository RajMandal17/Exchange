package com.custom.marketdata.manager;

import com.custom.marketdata.entity.AccountEntity;
import com.custom.marketdata.repository.AccountRepository;
import com.custom.marketdata.repository.BillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountManager {
    private final AccountRepository accountRepository;
    private final BillRepository billRepository;

    public List<AccountEntity> getAccounts(String userId) {
        return accountRepository.findAccountsByUserId(userId);
    }

    public void saveAll(Collection<AccountEntity> accounts) {
        if (accounts.isEmpty()) {
            return;
        }

        long t1 = System.currentTimeMillis();
        accountRepository.saveAll(accounts);
        logger.info("saved {} account(s) ({}ms)", accounts.size(), System.currentTimeMillis() - t1);
    }
}
