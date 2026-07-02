package com.financialmanager.lite.service;

import com.financialmanager.lite.model.Account;
import com.financialmanager.lite.model.Bank;
import com.financialmanager.lite.model.Entry;
import com.financialmanager.lite.model.User;
import com.financialmanager.lite.repository.AccountRepository;
import com.financialmanager.lite.repository.BankRepository;
import com.financialmanager.lite.repository.EntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final BankRepository bankRepository;
    private final EntryRepository entryRepository;

    public List<Account> findActiveByUser(User user) {
        return accountRepository.findByUserAndActiveTrue(user);
    }

    public List<Account> findInactiveByUser(User user) {
        return accountRepository.findByUserAndActiveFalse(user);
    }

    public Optional<Account> findByIdAndUser(Long id, User user) {
        return accountRepository.findByIdAndUser(id, user);
    }

    public Optional<Account> findByIdWithBank(Long id, User user) { return accountRepository.findByIdAndUserWithBank(id, user); }

    public BigDecimal sumBalanceByUser(User user) { return accountRepository.sumBalanceByUser(user); }

    public record InstallmentSummary(
            Map<Long, Integer> counts,
            Map<Long, BigDecimal> monthly,
            Map<Long, BigDecimal> remaining) {}

    public InstallmentSummary getInstallmentSummary(List<Account> accounts) {
        Map<Long, Integer>    counts    = new LinkedHashMap<>();
        Map<Long, BigDecimal> monthly   = new LinkedHashMap<>();
        Map<Long, BigDecimal> remaining = new LinkedHashMap<>();
        for (Account acc : accounts) {
            List<Entry> plans = entryRepository.findActiveInstallmentsByAccount(acc);
            if (!plans.isEmpty()) {
                counts.put(acc.getId(), plans.size());
                monthly.put(acc.getId(),
                        plans.stream().map(Entry::getInstallmentValue).reduce(BigDecimal.ZERO, BigDecimal::add));
                remaining.put(acc.getId(),
                        plans.stream().map(Entry::getRemainingAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
            }
        }
        return new InstallmentSummary(counts, monthly, remaining);
    }

    public record EntrySums(BigDecimal totalIn, BigDecimal totalOut) {}

    public Page<Entry> getEntries(User user, Long accountId, int page) {
        return entryRepository.findSimpleByUserAndAccount(user, accountId, PageRequest.of(page, 25));
    }

    public EntrySums getEntrySums(User user, Long accountId) {
        BigDecimal totalIn = entryRepository.sumSimpleByAccountAndType(user, accountId, "INCOME")
                .add(entryRepository.sumSimpleByAccountAndType(user, accountId, "TRANSFER_IN"));
        BigDecimal totalOut = entryRepository.sumSimpleByAccountAndType(user, accountId, "EXPENSE")
                .add(entryRepository.sumSimpleByAccountAndType(user, accountId, "TRANSFER_OUT"))
                .add(entryRepository.sumSimpleByAccountAndType(user, accountId, "CARD_PAYMENT"));
        return new EntrySums(totalIn, totalOut);
    }

    @Transactional
    public void create(User user, String name, Long bankId, String bankName, String type, BigDecimal balance) {
        Bank bank = bankId != null ? bankRepository.findByIdAndUser(bankId, user).orElse(null) : null;
        accountRepository.save(Account.builder()
                .user(user).name(name).bank(bank)
                .bankName(bank != null ? bank.getName() : bankName)
                .type(type).balance(balance).build());
    }

    @Transactional
    public void update(Long id, User user, String name, Long bankId, String bankName, String type, BigDecimal balance) {
        accountRepository.findByIdAndUser(id, user).ifPresent(account -> {
            Bank bank = bankId != null ? bankRepository.findByIdAndUser(bankId, user).orElse(null) : null;
            account.setName(name);
            account.setBank(bank);
            account.setBankName(bank != null ? bank.getName() : bankName);
            account.setType(type);
            account.setBalance(balance);
            accountRepository.save(account);
        });
    }

    @Transactional
    public void reactivate(Long id, User user) {
        accountRepository.findByIdAndUser(id, user).ifPresent(a -> {
            a.setActive(true);
            accountRepository.save(a);
        });
    }

    @Transactional
    public void linkBank(Long id, Long bankId, User user) {
        accountRepository.findByIdAndUser(id, user).ifPresent(account -> {
            Bank bank = bankId != null ? bankRepository.findByIdAndUser(bankId, user).orElse(null) : null;
            account.setBank(bank);
            accountRepository.save(account);
        });
    }

    @Transactional
    public void deactivate(Long id, User user) {
        accountRepository.findByIdAndUser(id, user).ifPresent(a -> {
            a.setActive(false);
            accountRepository.save(a);
        });
    }
}
