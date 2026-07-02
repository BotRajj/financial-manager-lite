package com.financialmanager.lite.service;

import com.financialmanager.lite.model.Account;
import com.financialmanager.lite.model.Bank;
import com.financialmanager.lite.model.CreditCard;
import com.financialmanager.lite.model.Entry;
import com.financialmanager.lite.model.User;
import com.financialmanager.lite.repository.BankRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BankService {

    private final BankRepository bankRepository;

    public List<Bank> findActiveByUser(User user) {
        return bankRepository.findByUserAndActiveTrueOrderByNameAsc(user);
    }

    public Optional<Bank> findByIdAndUser(Long id, User user) {
        return bankRepository.findByIdAndUser(id, user);
    }

    @Transactional
    public void create(User user, String name) {
        bankRepository.save(Bank.builder().user(user).name(name).build());
    }

    @Transactional
    public void deactivate(Long id, User user) {
        bankRepository.findByIdAndUser(id, user).ifPresent(b -> {
            b.setActive(false);
            bankRepository.save(b);
        });
    }

    public record BankSummary(
            Bank bank, List<Account> accounts, List<CreditCard> cards, List<Entry> installments,
            BigDecimal totalBalance, BigDecimal totalCardUsed, BigDecimal totalDebt,
            BigDecimal totalInstallmentMonthly) {}

    public List<BankSummary> buildSummaries(List<Bank> banks, List<Account> accounts,
                                             List<CreditCard> cards, List<Entry> installments) {
        return banks.stream().map(bank -> {
            List<Account> bankAccounts = accounts.stream()
                    .filter(a -> a.getBank() != null && a.getBank().getId().equals(bank.getId()))
                    .toList();
            List<CreditCard> bankCards = cards.stream()
                    .filter(c -> c.getBank() != null && c.getBank().getId().equals(bank.getId()))
                    .toList();
            Set<Long> bankCardIds = bankCards.stream().map(CreditCard::getId).collect(Collectors.toSet());
            List<Entry> bankInstallments = installments.stream()
                    .filter(e ->
                            (e.getCreditCard() != null && bankCardIds.contains(e.getCreditCard().getId()))
                              || (e.getCreditCard() == null && e.getBank() != null && e.getBank().getId().equals(bank.getId()))
                    )
                    .toList();
            BigDecimal totalBalance  = bankAccounts.stream().map(Account::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalCardUsed = bankCards.stream().map(CreditCard::getUsedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalDebt     = bankInstallments.stream().map(Entry::getRemainingAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal totalMonthly  = bankInstallments.stream().map(Entry::getInstallmentValue).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new BankSummary(bank, bankAccounts, bankCards, bankInstallments, totalBalance, totalCardUsed, totalDebt, totalMonthly);
        }).toList();
    }
}
