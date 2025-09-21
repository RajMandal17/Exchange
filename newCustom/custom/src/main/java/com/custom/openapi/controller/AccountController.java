package com.custom.openapi.controller;

import com.custom.marketdata.entity.AccountEntity;
import com.custom.marketdata.entity.User;
import com.custom.marketdata.manager.AccountManager;
import com.custom.marketdata.manager.BeneficiaryManager;
import com.custom.marketdata.manager.DepositManager;
import com.custom.marketdata.manager.WithdrawalManager;
import com.custom.marketdata.manager.DepositAddressManager;
import com.custom.openapi.model.AccountDto;
import com.custom.openapi.model.peatio.*;
import com.custom.service.UnifiedAuthenticationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/v2/peatio")
@RequiredArgsConstructor
public class AccountController {
    private final AccountManager accountManager;
    private final DepositManager depositManager;
    private final WithdrawalManager withdrawalManager;
    private final DepositAddressManager depositAddressManager;
    private final BeneficiaryManager beneficiaryManager;
    private final UnifiedAuthenticationService unifiedAuthenticationService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/accounts")
    public List<AccountDto> getAccounts(@RequestParam(name = "currency") List<String> currencies,
                                        @RequestAttribute(required = false) User currentUser) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        List<AccountEntity> accounts = accountManager.getAccounts(currentUser.getId());
        Map<String, AccountEntity> accountByCurrency = accounts.stream()
                .collect(Collectors.toMap(AccountEntity::getCurrency, x -> x));

        List<AccountDto> accountDtoList = new ArrayList<>();
        for (String currency : currencies) {
            AccountEntity account = accountByCurrency.get(currency);
            if (account != null) {
                accountDtoList.add(accountDto(account));
            } else {
                AccountDto accountDto = new AccountDto();
                accountDto.setCurrency(currency);
                accountDto.setAvailable("0");
                accountDto.setHold("0");
                accountDtoList.add(accountDto);
            }
        }
        return accountDtoList;
    }

    // ========== PEATIO ENDPOINTS ==========

    @GetMapping("/account/balances")
    public List<PeatioBalanceDto> getPeatioBalances(@RequestAttribute(required = false) User currentUser,
                                                    HttpServletRequest httpRequest) {
        // Use unified authentication - try existing session first, then JWT
        if (currentUser == null) {
            currentUser = unifiedAuthenticationService.authenticate(httpRequest);
        }
        
        if (currentUser == null) {
            logger.warn("Authentication failed - no current user found");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        logger.info("Getting balances for user ID: {} ({})", currentUser.getId(), currentUser.getEmail());
        List<AccountEntity> accounts = accountManager.getAccounts(currentUser.getId());
        logger.info("Found {} accounts for user {}", accounts.size(), currentUser.getId());
        
        for (AccountEntity account : accounts) {
            logger.info("Account: userId={}, currency={}, available={}, hold={}", 
                account.getUserId(), account.getCurrency(), account.getAvailable(), account.getHold());
        }
        return accounts.stream().map(this::peatioBalanceDto).collect(Collectors.toList());
    }

    @GetMapping("/account/deposits")
    public List<PeatioDepositDto> getPeatioDeposits(@RequestParam(required = false) String currency,
                                                   @RequestParam(required = false) String state,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "25") int limit,
                                                   @RequestAttribute(required = false) User currentUser) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        var deposits = depositManager.getDeposits(currentUser.getId(), currency, state, page, limit);
        return deposits.getItems().stream().map(this::peatioDepositDto).collect(Collectors.toList());
    }

    @PostMapping("/account/deposits")
    public ResponseEntity<PeatioDepositDto> createPeatioDeposit(@RequestBody Map<String, Object> request,
                                                               @RequestAttribute(required = false) User currentUser) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        String currency = (String) request.get("currency");
        BigDecimal amount = new BigDecimal(request.get("amount").toString());
        String txid = (String) request.get("txid");
        String rid = (String) request.get("rid");

        var deposit = depositManager.createDeposit(currentUser.getId(), currency, amount, txid, rid);
        return ResponseEntity.ok(peatioDepositDto(deposit));
    }

    @GetMapping("/account/withdraws")
    public List<PeatioWithdrawalDto> getPeatioWithdrawals(@RequestParam(required = false) String currency,
                                                         @RequestParam(required = false) String state,
                                                         @RequestParam(defaultValue = "1") int page,
                                                         @RequestParam(defaultValue = "25") int limit,
                                                         @RequestAttribute(required = false) User currentUser) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        var withdrawals = withdrawalManager.getWithdrawals(currentUser.getId(), currency, state, page, limit);
        return withdrawals.getItems().stream().map(this::peatioWithdrawalDto).collect(Collectors.toList());
    }

    @PostMapping("/account/withdraws")
    public ResponseEntity<PeatioWithdrawalDto> createPeatioWithdrawal(@Valid @RequestBody CreateWithdrawalRequest request,
                                                                     @RequestAttribute(required = false) User currentUser) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        var withdrawal = withdrawalManager.createWithdrawal(currentUser.getId(), request.getCurrency(),
                                                          request.getAmount(), request.getRid());

        return ResponseEntity.ok(peatioWithdrawalDto(withdrawal));
    }

    @GetMapping("/account/deposit_address/{currency}")
    public DepositAddressDto getPeatioDepositAddress(@PathVariable String currency,
                                                    @RequestAttribute(required = false) User currentUser) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        var address = depositAddressManager.getDepositAddress(currentUser.getId(), currency);
        if (address == null) {
            // Generate new address if none exists
            address = depositAddressManager.generateNewAddress(currentUser.getId(), currency);
        }

        return peatioDepositAddressDto(address);
    }

    @PostMapping("/account/deposit_address/{currency}")
    public DepositAddressDto generatePeatioDepositAddress(@PathVariable String currency,
                                                        @RequestAttribute(required = false) User currentUser) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        var address = depositAddressManager.generateNewAddress(currentUser.getId(), currency);
        return peatioDepositAddressDto(address);
    }

    // ========== BENEFICIARIES ENDPOINTS ==========

    @GetMapping("/account/beneficiaries")
    public List<PeatioBeneficiaryDto> getPeatioBeneficiaries(@RequestAttribute(required = false) User currentUser) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        var beneficiaries = beneficiaryManager.getBeneficiaries(currentUser.getId());
        return beneficiaries.stream().map(this::peatioBeneficiaryDto).collect(Collectors.toList());
    }

    @PostMapping("/account/beneficiaries")
    public ResponseEntity<PeatioBeneficiaryDto> createPeatioBeneficiary(@RequestBody Map<String, Object> request,
                                                                       @RequestAttribute(required = false) User currentUser) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        String currency = (String) request.get("currency");
        String name = (String) request.get("name");
        String description = (String) request.get("description");
        Object dataObj = request.get("data");
        
        // Convert data object to JSON string for storage
        String data;
        try {
            if (dataObj instanceof String) {
                data = (String) dataObj;
            } else {
                data = objectMapper.writeValueAsString(dataObj);
            }
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid data format");
        }

        var beneficiary = beneficiaryManager.createBeneficiary(currentUser.getId(), currency, name, description, data);
        return ResponseEntity.ok(peatioBeneficiaryDto(beneficiary));
    }

    @DeleteMapping("/account/beneficiaries/{id}")
    public ResponseEntity<Void> deletePeatioBeneficiary(@PathVariable Long id,
                                                       @RequestAttribute(required = false) User currentUser) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        var beneficiary = beneficiaryManager.getBeneficiary(id);
        if (beneficiary == null || !beneficiary.getUserId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        beneficiaryManager.deleteBeneficiary(id);
        return ResponseEntity.ok().build();
    }

    @PatchMapping("/account/beneficiaries/{id}/activate")
    public ResponseEntity<PeatioBeneficiaryDto> activatePeatioBeneficiary(@PathVariable Long id,
                                                                         @RequestBody Map<String, Object> request,
                                                                         @RequestAttribute(required = false) User currentUser) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        var beneficiary = beneficiaryManager.getBeneficiary(id);
        if (beneficiary == null || !beneficiary.getUserId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        // PIN validation could be added here if needed
        // String pin = (String) request.get("pin");
        
        beneficiaryManager.activateBeneficiary(id);
        beneficiary = beneficiaryManager.getBeneficiary(id);
        return ResponseEntity.ok(peatioBeneficiaryDto(beneficiary));
    }

    private PeatioBalanceDto peatioBalanceDto(AccountEntity account) {
        PeatioBalanceDto dto = new PeatioBalanceDto();
        dto.setCurrency(account.getCurrency());
        dto.setBalance(account.getAvailable() != null ? account.getAvailable() : java.math.BigDecimal.ZERO);
        dto.setLocked(account.getHold() != null ? account.getHold() : java.math.BigDecimal.ZERO);
        return dto;
    }

    private PeatioDepositDto peatioDepositDto(com.custom.marketdata.entity.DepositEntity deposit) {
        PeatioDepositDto dto = new PeatioDepositDto();
        dto.setId(deposit.getId());
        dto.setCurrency(deposit.getCurrency());
        dto.setAmount(deposit.getAmount());
        dto.setFee(deposit.getFee() != null ? deposit.getFee() : java.math.BigDecimal.ZERO);
        dto.setTxid(deposit.getTxid());
        dto.setState(deposit.getState());
        dto.setCreated_at(deposit.getCreatedAt().toInstant().toString());
        dto.setUpdated_at(deposit.getUpdatedAt().toInstant().toString());
        return dto;
    }

    private PeatioWithdrawalDto peatioWithdrawalDto(com.custom.marketdata.entity.WithdrawalEntity withdrawal) {
        PeatioWithdrawalDto dto = new PeatioWithdrawalDto();
        dto.setId(withdrawal.getId());
        dto.setCurrency(withdrawal.getCurrency());
        dto.setAmount(withdrawal.getAmount());
        dto.setFee(withdrawal.getFee() != null ? withdrawal.getFee() : java.math.BigDecimal.ZERO);
        dto.setTxid(withdrawal.getTxid());
        dto.setRid(withdrawal.getRid());
        dto.setState(withdrawal.getState());
        dto.setCreated_at(withdrawal.getCreatedAt().toInstant().toString());
        dto.setUpdated_at(withdrawal.getUpdatedAt().toInstant().toString());
        return dto;
    }

    private DepositAddressDto peatioDepositAddressDto(com.custom.marketdata.entity.DepositAddressEntity address) {
        DepositAddressDto dto = new DepositAddressDto();
        dto.setCurrency(address.getCurrency());
        dto.setAddress(address.getAddress());
        dto.setState(address.getState());
        return dto;
    }

    private AccountDto accountDto(AccountEntity account) {
        AccountDto accountDto = new AccountDto();
        accountDto.setId(account.getId());
        accountDto.setCurrency(account.getCurrency());
        accountDto.setAvailable(account.getAvailable() != null ? account.getAvailable().toPlainString() : "0");
        accountDto.setHold(account.getHold() != null ? account.getHold().toPlainString() : "0");
        return accountDto;
    }

    private PeatioBeneficiaryDto peatioBeneficiaryDto(com.custom.marketdata.entity.BeneficiaryEntity beneficiary) {
        PeatioBeneficiaryDto dto = new PeatioBeneficiaryDto();
        dto.setId(beneficiary.getId());
        dto.setCurrency(beneficiary.getCurrency());
        dto.setName(beneficiary.getName());
        dto.setState(beneficiary.getState());
        dto.setDescription(beneficiary.getDescription());
        dto.setData(beneficiary.getData());
        return dto;
    }
}
