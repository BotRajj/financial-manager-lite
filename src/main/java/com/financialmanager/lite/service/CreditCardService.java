package com.financialmanager.lite.service;

import com.financialmanager.lite.dto.SimpleEntryDTO;
import com.financialmanager.lite.model.*;
import com.financialmanager.lite.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class CreditCardService {

    private final CreditCardRepository cardRepository;
    private final BankRepository bankRepository;
    private final AccountRepository accountRepository;
    private final EntryRepository entryRepository;
    private final EntryService entryService;

    public List<CreditCard> findActiveByUser(User user) {
        return cardRepository.findByUserAndActiveTrue(user);
    }

    public List<CreditCard> findInactiveByUser(User user) {
        return cardRepository.findByUserAndActiveFalse(user);
    }

    public Optional<CreditCard> findByIdAndUser(Long id, User user) {
        return cardRepository.findByIdAndUser(id, user);
    }

    public Optional<CreditCard> findByIdWithBank(Long id, User user) {
        return cardRepository.findByIdAndUserWithBank(id, user);
    }
    public record InvoicePlanSummary(
            Map<Long, Integer>    counts,
            Map<Long, BigDecimal> monthly,
            Map<Long, BigDecimal> remaining,
            Map<Long, BigDecimal> adjustedAvailable) {}

    public InvoicePlanSummary getInvoicePlanSummary(List<CreditCard> cards) {
        Map<Long, Integer>    counts    = new LinkedHashMap<>();
        Map<Long, BigDecimal> monthly   = new LinkedHashMap<>();
        Map<Long, BigDecimal> remaining = new LinkedHashMap<>();
        Map<Long, BigDecimal> adjusted  = new LinkedHashMap<>();
        for (CreditCard card : cards) {
            List<Entry> plans = entryRepository.findActiveInstallmentsByCreditCard(card);
            BigDecimal committed = plans.stream().map(Entry::getRemainingAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (!plans.isEmpty()) {
                counts.put(card.getId(), plans.size());
                monthly.put(card.getId(),
                        plans.stream().map(Entry::getInstallmentValue).reduce(BigDecimal.ZERO, BigDecimal::add));
                remaining.put(card.getId(), committed);
            }
            BigDecimal adj = card.getAvailableLimit().subtract(committed);
            adjusted.put(card.getId(), adj.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : adj);
        }
        return new InvoicePlanSummary(counts, monthly, remaining, adjusted);

    }

    @Transactional
    public void create(User user, String name, BigDecimal creditLimit, int closingDay, int dueDay,
                       Long bankId, BigDecimal minimumPaymentRate, BigDecimal rotatingCreditRate,
                       BigDecimal installmentRate, BigDecimal latePaymentFine, BigDecimal latePaymentInterest,
                       BigDecimal iofDailyRate, BigDecimal iofAdditionalRate,
                       List<String> cardLastFour, List<String> cardType) {
        Bank bank = bankId != null ? bankRepository.findByIdAndUser(bankId, user).orElse(null) : null;
        CreditCard card = CreditCard.builder()
                .user(user).name(name).bank(bank)
                .creditLimit(creditLimit).closingDay(closingDay).dueDay(dueDay)
                .minimumPaymentRate(minimumPaymentRate).rotatingCreditRate(rotatingCreditRate)
                .installmentRate(installmentRate).latePaymentFine(latePaymentFine)
                .latePaymentInterest(latePaymentInterest)
                .iofDailyRate(iofDailyRate).iofAdditionalRate(iofAdditionalRate)
                .build();
        card.getCardNumbers().addAll(buildCardNumbers(card, cardLastFour, cardType));
        cardRepository.save(card);
    }

    @Transactional
    public void update(Long id, User user, String name, BigDecimal creditLimit, int closingDay, int dueDay,
                       Long bankId, BigDecimal minimumPaymentRate, BigDecimal rotatingCreditRate,
                       BigDecimal installmentRate, BigDecimal latePaymentFine, BigDecimal latePaymentInterest,
                       BigDecimal iofDailyRate, BigDecimal iofAdditionalRate,
                       List<String> cardLastFour, List<String> cardType) {
        cardRepository.findByIdAndUserWithCardNumbers(id, user).ifPresent(card -> {
            Bank bank = bankId != null ? bankRepository.findByIdAndUser(bankId, user).orElse(null) : null;
            card.setName(name);
            card.setBank(bank);
            card.setCreditLimit(creditLimit);
            card.setClosingDay(closingDay);
            card.setDueDay(dueDay);
            card.setMinimumPaymentRate(minimumPaymentRate);
            card.setRotatingCreditRate(rotatingCreditRate);
            card.setInstallmentRate(installmentRate);
            card.setLatePaymentFine(latePaymentFine);
            card.setLatePaymentInterest(latePaymentInterest);
            card.setIofDailyRate(iofDailyRate);
            card.setIofAdditionalRate(iofAdditionalRate);
            card.getCardNumbers().clear();
            card.getCardNumbers().addAll(buildCardNumbers(card, cardLastFour, cardType));
            cardRepository.save(card);
        });
    }

    @Transactional
    public void reactivate(Long id, User user) {
        cardRepository.findByIdAndUser(id, user).ifPresent(c -> {
            c.setActive(true);
            cardRepository.save(c);
        });
    }

    @Transactional
    public void linkBank(Long id, Long bankId, User user) {
        cardRepository.findByIdAndUser(id, user).ifPresent(card -> {
            Bank bank = bankId != null ? bankRepository.findByIdAndUser(bankId, user).orElse(null) : null;
            card.setBank(bank);
            cardRepository.save(card);
        });
    }

    @Transactional
    public void deactivate(Long id, User user) {
        cardRepository.findByIdAndUser(id, user).ifPresent(c -> {
            c.setActive(false);
            cardRepository.save(c);
        });
    }

    // Returns rotating interest applied (ZERO if none)
    @Transactional
    public BigDecimal payInvoice(Long cardId, User user, BigDecimal amount, Long accountId) {
        CreditCard card = cardRepository.findByIdAndUser(cardId, user)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Cartão não encontrado"));

        BigDecimal newUsed = card.getUsedAmount().subtract(amount);
        card.setUsedAmount(newUsed.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newUsed);
        cardRepository.save(card);

        if (accountId != null) {
            accountRepository.findByIdAndUser(accountId, user).ifPresent(account -> {
                account.setBalance(account.getBalance().subtract(amount));
                accountRepository.save(account);
                entryRepository.save(Entry.builder()
                        .user(user).account(account)
                        .description("Pagamento fatura: " + card.getName())
                        .amount(amount).type("CARD_PAYMENT").mode("SIMPLE")
                        .entryDate(LocalDate.now())
                        .build());
            });
        }

        BigDecimal remaining = newUsed.compareTo(BigDecimal.ZERO) > 0 ? newUsed : BigDecimal.ZERO;
        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal interest = remaining.multiply(card.getRotatingCreditRate())
                    .setScale(2, RoundingMode.HALF_EVEN);
            if (interest.compareTo(BigDecimal.ZERO) > 0) {
                entryService.createSimple(user, SimpleEntryDTO.builder()
                        .description("Juros rotativos: " + card.getName())
                        .amount(interest).type("EXPENSE").category("Fatura Cartão")
                        .entryDate(LocalDate.now()).creditCardId(card.getId())
                        .build());
                card.setUsedAmount(remaining.add(interest));
                cardRepository.save(card);
                return interest;
            }
        }
        return BigDecimal.ZERO;
    }

    @Transactional
    public void installmentInvoice(Long cardId, User user, BigDecimal entrada,
                                   int totalInstallments, int dayOfMonth, Long accountId) {
        CreditCard card = cardRepository.findByIdAndUser(cardId, user).orElse(null);
        if (card == null || card.getUsedAmount().compareTo(BigDecimal.ZERO) <= 0) return;

        if (entrada == null || entrada.compareTo(BigDecimal.ZERO) < 0) entrada = BigDecimal.ZERO;
        if (entrada.compareTo(card.getUsedAmount()) > 0) entrada = card.getUsedAmount();

        final BigDecimal entradaFinal = entrada;
        BigDecimal financedAmount = card.getUsedAmount().subtract(entradaFinal);
        int safeDay = Math.min(dayOfMonth, 28);

        if (financedAmount.compareTo(BigDecimal.ZERO) > 0 && totalInstallments >= 1) {
            BigDecimal rate = card.getInstallmentRate();
            BigDecimal pmt = calculatePmt(financedAmount, rate, totalInstallments);
            LocalDate base = LocalDate.now().plusMonths(1);
            LocalDate baseDate = base.withDayOfMonth(Math.min(safeDay, base.lengthOfMonth()));
            entryService.createInvoiceInstallment(user, card, pmt, totalInstallments, safeDay, rate, baseDate);
        }

        if (entradaFinal.compareTo(BigDecimal.ZERO) > 0 && accountId != null) {
            accountRepository.findByIdAndUser(accountId, user).ifPresent(account -> {
                account.setBalance(account.getBalance().subtract(entradaFinal));
                accountRepository.save(account);
                entryRepository.save(Entry.builder()
                        .user(user).account(account)
                        .description("Entrada parcelamento: " + card.getName())
                        .amount(entradaFinal).type("CARD_PAYMENT").mode("SIMPLE")
                        .entryDate(LocalDate.now())
                        .build());
            });
        }

        card.setUsedAmount(BigDecimal.ZERO);
        cardRepository.save(card);
    }

    // Returns [fine, interest] applied
    @Transactional
    public BigDecimal[] markInvoiceDefaultPaid(Long cardId, User user, BigDecimal invoiceTotal,
                                               BigDecimal latePaymentFineRate, BigDecimal latePaymentInterestRate,
                                               String dueDate, Long accountId, String paymentDate) {
        CreditCard card = cardRepository.findByIdAndUser(cardId, user)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Cartão não encontrado"));

        LocalDate today = LocalDate.now();
        LocalDate refDate = (paymentDate != null && !paymentDate.isBlank())
                ? LocalDate.parse(paymentDate) : today;
        LocalDate dueDateParsed = (dueDate != null && !dueDate.isBlank())
                ? LocalDate.parse(dueDate) : null;

        BigDecimal fineRate     = latePaymentFineRate.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_EVEN);
        BigDecimal interestRate = latePaymentInterestRate.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_EVEN);
        BigDecimal fine     = BigDecimal.ZERO;
        BigDecimal interest = BigDecimal.ZERO;

        if (dueDateParsed != null && refDate.isAfter(dueDateParsed)) {
            fine = invoiceTotal.multiply(fineRate).setScale(2, RoundingMode.HALF_EVEN);
            long daysLate = java.time.temporal.ChronoUnit.DAYS.between(dueDateParsed, refDate);
            if (daysLate > 0 && interestRate.compareTo(BigDecimal.ZERO) > 0) {
                double factor = Math.pow(1.0 + interestRate.doubleValue(), daysLate / 30.0) - 1.0;
                interest = invoiceTotal.multiply(BigDecimal.valueOf(factor)).setScale(2, RoundingMode.HALF_EVEN);
            }
        }

        if (fine.compareTo(BigDecimal.ZERO) > 0) {
            entryService.createSimple(user, SimpleEntryDTO.builder()
                    .description("Multa atraso: " + card.getName())
                    .amount(fine).type("EXPENSE").category("Fatura Cartão")
                    .entryDate(today).creditCardId(card.getId())
                    .build());
        } else {
            entryRepository.save(Entry.builder()
                    .user(user).mode("SIMPLE")
                    .description("Multa atraso: " + card.getName())
                    .amount(BigDecimal.ZERO).type("EXPENSE").category("Fatura Cartão")
                    .entryDate(today).direction("STANDARD").creditCard(card)
                    .build());
        }
        if (interest.compareTo(BigDecimal.ZERO) > 0) {
            entryService.createSimple(user, SimpleEntryDTO.builder()
                    .description("Juros mora: " + card.getName())
                    .amount(interest).type("EXPENSE").category("Fatura Cartão")
                    .entryDate(today).creditCardId(card.getId())
                    .build());
        }

        BigDecimal newUsed = card.getUsedAmount().subtract(invoiceTotal);
        card.setUsedAmount(newUsed.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newUsed);
        cardRepository.save(card);

        if (accountId != null) {
            accountRepository.findByIdAndUser(accountId, user).ifPresent(account -> {
                account.setBalance(account.getBalance().subtract(invoiceTotal));
                accountRepository.save(account);
                entryRepository.save(Entry.builder()
                        .user(user).account(account)
                        .description("Pagamento fatura: " + card.getName())
                        .amount(invoiceTotal).type("CARD_PAYMENT").mode("SIMPLE")
                        .entryDate(refDate)
                        .build());
            });
        }

        return new BigDecimal[]{fine, interest};
    }

    @Transactional
    public void markInvoiceDefaultOverdue(Long cardId, User user, BigDecimal invoiceTotal,
                                          BigDecimal latePaymentFineRate, BigDecimal latePaymentInterestRate,
                                          String dueDate) {
        cardRepository.findByIdAndUser(cardId, user).ifPresent(card -> {
            BigDecimal fineRate     = latePaymentFineRate.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_EVEN);
            BigDecimal interestRate = latePaymentInterestRate.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_EVEN);
            BigDecimal fine         = invoiceTotal.multiply(fineRate).setScale(2, RoundingMode.HALF_EVEN);

            BigDecimal interest;
            LocalDate today = LocalDate.now();
            if (dueDate != null && !dueDate.isBlank() && interestRate.compareTo(BigDecimal.ZERO) > 0) {
                long daysOverdue = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(dueDate), today);
                if (daysOverdue > 0) {
                    double factor = Math.pow(1.0 + interestRate.doubleValue(), daysOverdue / 30.0) - 1.0;
                    interest = invoiceTotal.multiply(BigDecimal.valueOf(factor)).setScale(2, RoundingMode.HALF_EVEN);
                } else {
                    interest = BigDecimal.ZERO;
                }
            } else {
                interest = invoiceTotal.multiply(interestRate).setScale(2, RoundingMode.HALF_EVEN);
            }

            if (invoiceTotal.compareTo(BigDecimal.ZERO) > 0) {
                entryRepository.save(Entry.builder()
                        .user(user).mode("SIMPLE")
                        .description("Saldo anterior: " + card.getName())
                        .amount(invoiceTotal).type("EXPENSE").category("Fatura Cartão")
                        .entryDate(today).direction("STANDARD").creditCard(card)
                        .build());
            }
            if (fine.compareTo(BigDecimal.ZERO) > 0) {
                entryService.createSimple(user, SimpleEntryDTO.builder()
                        .description("Multa atraso: " + card.getName())
                        .amount(fine).type("EXPENSE").category("Fatura Cartão")
                        .entryDate(today).creditCardId(card.getId())
                        .build());
            }
            if (interest.compareTo(BigDecimal.ZERO) > 0) {
                entryService.createSimple(user, SimpleEntryDTO.builder()
                        .description("Juros mora: " + card.getName())
                        .amount(interest).type("EXPENSE").category("Fatura Cartão")
                        .entryDate(today).creditCardId(card.getId())
                        .build());
            }
        });
    }

    private BigDecimal calculatePmt(BigDecimal financedAmount, BigDecimal rate, int totalInstallments) {
        if (rate.compareTo(BigDecimal.ZERO) == 0) {
            return financedAmount.divide(BigDecimal.valueOf(totalInstallments), 2, RoundingMode.HALF_EVEN);
        }
        BigDecimal onePlusRN = BigDecimal.ONE.add(rate).pow(totalInstallments);
        BigDecimal factor = BigDecimal.ONE.divide(onePlusRN, 10, RoundingMode.HALF_EVEN);
        return financedAmount.multiply(rate)
                .divide(BigDecimal.ONE.subtract(factor), 2, RoundingMode.HALF_EVEN);
    }

    private List<CardNumber> buildCardNumbers(CreditCard card, List<String> lastFourList, List<String> typeList) {
        List<CardNumber> result = new ArrayList<>();
        if (lastFourList == null || typeList == null) return result;
        for (int i = 0; i < lastFourList.size() && i < typeList.size(); i++) {
            String lastFour = lastFourList.get(i);
            String type = typeList.get(i);
            if (lastFour == null || lastFour.isBlank() || type == null || type.isBlank()) continue;
            result.add(CardNumber.builder()
                    .creditCard(card)
                    .lastFourDigits(lastFour.trim())
                    .type(CardNumber.CardKind.valueOf(type.trim()))
                    .build());
        }
        return result;
    }
}
