package com.custom.marketdata.manager;

import com.custom.marketdata.entity.BeneficiaryEntity;
import com.custom.marketdata.repository.BeneficiaryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
public class BeneficiaryManager {
    private final BeneficiaryRepository beneficiaryRepository;
    private final AtomicLong idGenerator = new AtomicLong(1);

    public List<BeneficiaryEntity> getBeneficiaries(String userId) {
        return beneficiaryRepository.findByUserId(userId);
    }

    public BeneficiaryEntity createBeneficiary(String userId, String currency, String name, String description, String data) {
        BeneficiaryEntity beneficiary = new BeneficiaryEntity();
        beneficiary.setId(idGenerator.getAndIncrement());
        beneficiary.setUserId(userId);
        beneficiary.setCurrency(currency);
        beneficiary.setName(name);
        beneficiary.setDescription(description);
        beneficiary.setData(data);
        beneficiary.setState("pending");
        beneficiary.setCreatedAt(LocalDateTime.now());
        beneficiary.setUpdatedAt(LocalDateTime.now());

        beneficiaryRepository.save(beneficiary);
        logger.info("Created beneficiary {} for user {}", beneficiary.getId(), userId);

        return beneficiary;
    }

    public void activateBeneficiary(Long id) {
        BeneficiaryEntity beneficiary = beneficiaryRepository.findById(id);
        if (beneficiary != null) {
            beneficiary.setState("active");
            beneficiary.setUpdatedAt(LocalDateTime.now());
            beneficiaryRepository.save(beneficiary);
            logger.info("Activated beneficiary {}", id);
        }
    }

    public void deleteBeneficiary(Long id) {
        beneficiaryRepository.delete(id);
        logger.info("Deleted beneficiary {}", id);
    }

    public BeneficiaryEntity getBeneficiary(Long id) {
        return beneficiaryRepository.findById(id);
    }
}