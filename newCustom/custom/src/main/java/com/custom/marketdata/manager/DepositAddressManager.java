package com.custom.marketdata.manager;

import com.custom.marketdata.entity.DepositAddressEntity;
import com.custom.marketdata.repository.DepositAddressRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class DepositAddressManager {
    private final DepositAddressRepository depositAddressRepository;

    public DepositAddressEntity getDepositAddress(String userId, String currency) {
        return depositAddressRepository.findByUserIdAndCurrency(userId, currency);
    }

    public DepositAddressEntity createDepositAddress(String userId, String currency, String address) {
        DepositAddressEntity depositAddress = new DepositAddressEntity();
        depositAddress.setId(UUID.randomUUID().toString());
        depositAddress.setUserId(userId);
        depositAddress.setCurrency(currency);
        depositAddress.setAddress(address);
        depositAddress.setState("active");
        depositAddress.setCreatedAt(new Date());
        depositAddress.setUpdatedAt(new Date());

        depositAddressRepository.save(depositAddress);
        logger.info("Created deposit address for user {} currency {}: {}", userId, currency, address);

        return depositAddress;
    }

    public DepositAddressEntity generateNewAddress(String userId, String currency) {
        // In a real implementation, this would call a wallet service or crypto API
        // For now, we'll generate a mock address
        String mockAddress = generateMockAddress(currency);

        DepositAddressEntity existing = getDepositAddress(userId, currency);
        if (existing != null) {
            existing.setAddress(mockAddress);
            existing.setUpdatedAt(new Date());
            depositAddressRepository.update(existing);
            return existing;
        } else {
            return createDepositAddress(userId, currency, mockAddress);
        }
    }

    private String generateMockAddress(String currency) {
        // Mock address generation - in production this would come from wallet service
        String baseAddress = "bc1q" + UUID.randomUUID().toString().replace("-", "").substring(0, 32);
        return baseAddress;
    }
}
