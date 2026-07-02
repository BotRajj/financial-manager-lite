package com.financialmanager.lite.service;

import com.financialmanager.lite.dto.InstallmentEntryDTO;
import com.financialmanager.lite.dto.RecurringEntryDTO;
import com.financialmanager.lite.dto.SimpleEntryDTO;
import com.financialmanager.lite.model.*;
import com.financialmanager.lite.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class EntryService {

    private final EntryRepository entryRepository;
    private final EntryInstallmentRepository installmentRepository;
    private final AccountRepository accountRepository;
    private final CreditCardRepository creditCardRepository;
    private final UserCategoryService categoryService;
    private final BudgetService budgetService;

    // ── Query methods (for controllers that must not import repositories) ─────

    public List<Entry> findRecurringActiveByUser(User user) {
        return entryRepository.findRecurringActiveByUser(user);
    }

    public List<Entry> findInstallmentsByUser(User user) {
        return entryRepository.findInstallmentsByUser(user);
    }

    public List<Entry> findActivePayablesByUser(User user) {
        return entryRepository.findActivePayablesByUser(user);
    }

    public List<Entry> findActiveInstallmentsByCreditCard(CreditCard card) {
        return entryRepository.findActiveInstallmentsByCreditCard(card);
    }

    public List<Entry> findSimpleByCreditCardAndInvoice(CreditCard card, String invoiceKey,
                                                         LocalDate from, LocalDate to) {
        return entryRepository.findSimpleByCreditCardAndInvoice(card, invoiceKey, from, to);
    }

    public BigDecimal sumSimpleByCreditCardAndInvoice(CreditCard card, String invoiceKey,
                                                       LocalDate from, LocalDate to) {
        return entryRepository.sumSimpleByCreditCardAndInvoice(card, invoiceKey, from, to);
    }

    public boolean existsLateChargeForCard(CreditCard card, String prefix, LocalDate since) {
        return entryRepository.existsLateChargeForCard(card, prefix, since);
    }

    public Page<Entry> findSimpleByUser(User user, int page) {
        return entryRepository.findSimpleByUser(user, PageRequest.of(page, 30));
    }

    public Page<Entry> findSimpleByUserAndAccount(User user, Long accountId, int page) {
        return entryRepository.findSimpleByUserAndAccount(user, accountId, PageRequest.of(page, 30));
    }

    public Page<Entry> findSimpleByUserAndCreditCard(User user, Long cardId, int page) {
        return entryRepository.findSimpleByUserAndCreditCard(user, cardId, PageRequest.of(page, 30));
    }

    public Optional<Entry> findInstallmentByIdAndUser(Long id, User user) {
        return entryRepository.findInstallmentByIdAndUser(id, user);
    }

    @Transactional
    public void moveInvoice(Long id, User user, String invoiceMonth) {
        entryRepository.findByIdAndUser(id, user).ifPresent(entry -> {
            entry.setInvoiceMonth(invoiceMonth);
            entryRepository.save(entry);
        });
    }

    // ── SIMPLE ────────────────────────────────────────────────────────────
    @SuppressWarnings("UnusedReturnValue")
    @Transactional
    public Entry createSimple(User user, SimpleEntryDTO dto) {
        String cat = categoryService.resolveAndSave(user, dto.getCategory());
        String dir = dto.getDirection() != null ? dto.getDirection() : "STANDARD";
        LocalDate date = dto.getEntryDate() != null ? dto.getEntryDate() : LocalDate.now();

        CreditCard card = null;
        Account account = null;
        String invoiceMonth = null;

        if (dto.getCreditCardId() != null) {
            card = creditCardRepository.findByIdAndUser(dto.getCreditCardId(), user).orElse(null);
            if (card != null) {
                if ("EXPENSE".equals(dto.getType())) {
                    card.setUsedAmount(card.getUsedAmount().add(dto.getAmount()));
                } else if ("INCOME".equals(dto.getType())) {
                    BigDecimal newUsed = card.getUsedAmount().subtract(dto.getAmount());
                    card.setUsedAmount(newUsed.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newUsed);
                }
                creditCardRepository.save(card);
                invoiceMonth = (dto.getInvoiceMonth() != null && !dto.getInvoiceMonth().isBlank())
                        ? dto.getInvoiceMonth()
                        : card.closingMonthFor(date).toString();
            }
        } else if (dto.getAccountId() != null) {
            account = accountRepository.findByIdAndUser(dto.getAccountId(), user).orElse(null);
            if (account != null) {
                applyBalanceChange(account, dto.getType(), dto.getAmount());
                accountRepository.save(account);
            }
        }

        Entry saved = entryRepository.save(Entry.builder()
                .user(user)
                .mode("SIMPLE")
                .description(dto.getDescription())
                .amount(dto.getAmount())
                .type(dto.getType()).category(cat)
                .entryDate(date)
                .direction(dir)
                .personName(dto.getPersonName())
                .account(account)
                .creditCard(card)
                .notes(dto.getNotes())
                .invoiceMonth(invoiceMonth)
                .build());
        if ("EXPENSE".equals(dto.getType())) {
            budgetService.adjustSpent(user, cat, YearMonth.from(date).toString(), dto.getAmount());
        }
        return saved;
    }

    @Transactional
    public void updateSimple(Long id, User user, SimpleEntryDTO dto) {
        entryRepository.findByIdAndUser(id, user).ifPresent(entry -> {
            if (!"SIMPLE".equals(entry.getMode())) return;

            // Reverter efeito antigo
            if (entry.getCreditCard() != null) {
                CreditCard oldCard = entry.getCreditCard();
                if ("EXPENSE".equals(entry.getType())) {
                    BigDecimal newUsed = oldCard.getUsedAmount().subtract(entry.getAmount());
                    oldCard.setUsedAmount(newUsed.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newUsed);
                } else if ("INCOME".equals(entry.getType())) {
                    oldCard.setUsedAmount(oldCard.getUsedAmount().add(entry.getAmount()));
                }
                creditCardRepository.save(oldCard);
            } else if (entry.getAccount() != null) {
                applyBalanceChange(entry.getAccount(), reverseType(entry.getType()), entry.getAmount());
                accountRepository.save(entry.getAccount());
            }

            String oldCategory = entry.getCategory();
            LocalDate oldDate = entry.getEntryDate();
            String oldType = entry.getType();
            BigDecimal oldAmount = entry.getAmount();

            String cat = categoryService.resolveAndSave(user, dto.getCategory());
            LocalDate date = dto.getEntryDate() != null ? dto.getEntryDate() : LocalDate.now();
            CreditCard newCard = null;
            Account newAccount = null;
            String newInvoiceMonth = null;

            if (dto.getCreditCardId() != null) {
                newCard = creditCardRepository.findByIdAndUser(dto.getCreditCardId(), user).orElse(null);
                if (newCard != null) {
                    if ("EXPENSE".equals(dto.getType())) {
                        newCard.setUsedAmount(newCard.getUsedAmount().add(dto.getAmount()));
                    } else if ("INCOME".equals(dto.getType())) {
                        BigDecimal newUsed = newCard.getUsedAmount().subtract(dto.getAmount());
                        newCard.setUsedAmount(newUsed.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newUsed);
                    }
                    creditCardRepository.save(newCard);
                    newInvoiceMonth = (dto.getInvoiceMonth() != null && !dto.getInvoiceMonth().isBlank())
                            ? dto.getInvoiceMonth() : newCard.closingMonthFor(date).toString();
                }
            } else if (dto.getAccountId() != null) {
                newAccount = accountRepository.findByIdAndUser(dto.getAccountId(), user).orElse(null);
                if (newAccount != null) {
                    applyBalanceChange(newAccount, dto.getType(), dto.getAmount());
                    accountRepository.save(newAccount);
                }
            }

            entry.setDescription(dto.getDescription());
            entry.setAmount(dto.getAmount());
            entry.setType(dto.getType());
            entry.setCategory(cat);
            entry.setEntryDate(date);
            entry.setNotes(dto.getNotes());
            entry.setAccount(newAccount);
            entry.setCreditCard(newCard);
            entry.setInvoiceMonth(newInvoiceMonth);
            entryRepository.save(entry);

            if ("EXPENSE".equals(oldType) && oldDate != null) {
                budgetService.adjustSpent(user, oldCategory, YearMonth.from(oldDate).toString(), oldAmount.negate());
            }
            if ("EXPENSE".equals(dto.getType())) {
                budgetService.adjustSpent(user, cat, YearMonth.from(date).toString(), dto.getAmount());
            }
        });
    }

    @Transactional
    public void deleteSimple(Long id, User user) {
        entryRepository.findByIdAndUser(id, user).ifPresent(entry -> {
            if (!"SIMPLE".equals(entry.getMode())) return;
            if (entry.getCreditCard() != null) {
                CreditCard card = entry.getCreditCard();
                if ("EXPENSE".equals(entry.getType())) {
                    BigDecimal newUsed = card.getUsedAmount().subtract(entry.getAmount());
                    card.setUsedAmount(newUsed.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newUsed);
                } else if ("INCOME".equals(entry.getType())) {
                    card.setUsedAmount(card.getUsedAmount().add(entry.getAmount()));
                }
                creditCardRepository.save(card);
            } else if (entry.getAccount() != null) {
                Account account = entry.getAccount();
                applyBalanceChange(account, reverseType(entry.getType()), entry.getAmount());
                accountRepository.save(account);
            }
            if ("EXPENSE".equals(entry.getType()) && entry.getEntryDate() != null) {
                budgetService.adjustSpent(user, entry.getCategory(), YearMonth.from(entry.getEntryDate()).toString(), entry.getAmount().negate());
            }
            entryRepository.delete(entry);
        });
    }

    // ── RECURRING ─────────────────────────────────────────────────────────

    @SuppressWarnings("UnusedReturnValue")
    @Transactional
    public Entry createRecurring(User user, RecurringEntryDTO dto) {
        String cat = categoryService.resolveAndSave(user, dto.getCategory());
        String dir = dto.getDirection() != null ? dto.getDirection() : "STANDARD";
        Account account = dto.getAccountId() != null
                ? accountRepository.findByIdAndUser(dto.getAccountId(), user).orElse(null) : null;
        CreditCard card = dto.getCreditCardId() != null
                ? creditCardRepository.findByIdAndUser(dto.getCreditCardId(), user).orElse(null) : null;

        return entryRepository.save(Entry.builder()
                .user(user)
                .mode("RECURRING")
                .description(dto.getDescription())
                .amount(dto.getAmount())
                .type(dto.getType())
                .category(cat)
                .direction(dir)
                .personName(dto.getPersonName())
                .frequency(dto.getFrequency())
                .dayOfMonth(dto.getDayOfMonth())
                .monthOfYear(dto.getMonthOfYear())
                .account(account)
                .creditCard(card)
                .autoApply(dto.isAutoApply())
                .build());
    }

    @Transactional
    public int applyRecurringMonth(User user, List<Long> ids) {
        YearMonth current = YearMonth.now();
        String monthKey = current.toString();

        List<Entry> toApply = entryRepository.findRecurringActiveByUser(user).stream()
                .filter(e -> appliesThisMonth(e, current))
                .filter(e -> !monthKey.equals(e.getLastAppliedMonth()))
                .filter(e -> ids.contains(e.getId()))
                .toList();

        for (Entry rec : toApply) {
            if (rec.getCreditCard() != null && "EXPENSE".equals(rec.getType())) {
                CreditCard card = rec.getCreditCard();
                card.setUsedAmount(card.getUsedAmount().add(rec.getAmount()));
                creditCardRepository.save(card);
            } else if (rec.getAccount() != null) {
                Account account = rec.getAccount();
                applyBalanceChange(account, rec.getType(), rec.getAmount());
                accountRepository.save(account);
            }

            int day = Math.min(rec.getDayOfMonth() != null ? rec.getDayOfMonth() : 1, current.lengthOfMonth());
            entryRepository.save(Entry.builder()
                    .user(user).mode("SIMPLE")
                    .description(rec.getDescription()).amount(rec.getAmount())
                    .type(rec.getType()).category(rec.getCategory())
                    .direction(rec.getDirection()).personName(rec.getPersonName())
                    .entryDate(LocalDate.of(current.getYear(), current.getMonth(), day))
                    .account(rec.getAccount()).creditCard(rec.getCreditCard())
                    .build());
            if ("EXPENSE".equals(rec.getType())) {
                budgetService.adjustSpent(user, rec.getCategory(), monthKey, rec.getAmount());
            }

            rec.setLastAppliedMonth(monthKey);
            entryRepository.save(rec);
        }
        return toApply.size();
    }

    @Transactional
    public void applySingleRecurring(Long id, User user, String customDate) {
        entryRepository.findRecurringByIdAndUser(id, user).ifPresent(rec -> {
            YearMonth current = YearMonth.now();
            String monthKey = current.toString();
            if (monthKey.equals(rec.getLastAppliedMonth())) return;

            LocalDate date;
            if (customDate != null && !customDate.isBlank()) {
                date = LocalDate.parse(customDate);
            } else {
                int day = Math.min(rec.getDayOfMonth() != null ? rec.getDayOfMonth() : 1, current.lengthOfMonth());
                date = LocalDate.of(current.getYear(), current.getMonth(), day);
            }

            if (rec.getCreditCard() != null && "EXPENSE".equals(rec.getType())) {
                rec.getCreditCard().setUsedAmount(rec.getCreditCard().getUsedAmount().add(rec.getAmount()));
                creditCardRepository.save(rec.getCreditCard());
            } else if (rec.getAccount() != null) {
                applyBalanceChange(rec.getAccount(), rec.getType(), rec.getAmount());
                accountRepository.save(rec.getAccount());
            }

            entryRepository.save(Entry.builder()
                    .user(user).mode("SIMPLE")
                    .description(rec.getDescription()).amount(rec.getAmount())
                    .type(rec.getType()).category(rec.getCategory())
                    .direction(rec.getDirection()).personName(rec.getPersonName())
                    .entryDate(date)
                    .account(rec.getAccount()).creditCard(rec.getCreditCard())
                    .build());
            if ("EXPENSE".equals(rec.getType())) {
                budgetService.adjustSpent(user, rec.getCategory(), YearMonth.from(date).toString(), rec.getAmount());
            }

            rec.setLastAppliedMonth(monthKey);
            entryRepository.save(rec);
        });
    }

    @Transactional
    public void updateRecurring(Long id, User user, RecurringEntryDTO dto) {
        entryRepository.findRecurringByIdAndUser(id, user).ifPresent(entry -> {
            entry.setDescription(dto.getDescription());
            entry.setAmount(dto.getAmount());
            entry.setCategory(categoryService.resolveAndSave(user, dto.getCategory()));
            entry.setFrequency(dto.getFrequency());
            entry.setDayOfMonth(dto.getDayOfMonth());
            entry.setMonthOfYear("ANNUAL".equals(dto.getFrequency()) ? dto.getMonthOfYear() : null);
            entry.setAccount(dto.getAccountId() != null
                    ? accountRepository.findByIdAndUser(dto.getAccountId(), user).orElse(null) : null);
            entry.setCreditCard(dto.getCreditCardId() != null
                    ? creditCardRepository.findByIdAndUser(dto.getCreditCardId(), user).orElse(null) : null);
            entry.setAutoApply(dto.isAutoApply());
            entryRepository.save(entry);
        });
    }

    @Transactional
    public void deactivateRecurring(Long id, User user) {
        entryRepository.findRecurringByIdAndUser(id, user).ifPresent(e -> {
            e.setActive(false);
            entryRepository.save(e);
        });
    }

    @Transactional
    public int applyDueRecurringEntries() {
        YearMonth current = YearMonth.now();
        String monthKey = current.toString();
        int today = LocalDate.now().getDayOfMonth();

        List<Entry> candidates = entryRepository.findAllAutoApplyRecurring();
        int count = 0;
        for (Entry rec : candidates) {
            if (!appliesThisMonth(rec, current)) continue;
            if (monthKey.equals(rec.getLastAppliedMonth())) continue;
            int dom = rec.getDayOfMonth() != null ? rec.getDayOfMonth() : 1;
            if (today < dom) continue;

            if (rec.getCreditCard() != null && "EXPENSE".equals(rec.getType())) {
                rec.getCreditCard().setUsedAmount(rec.getCreditCard().getUsedAmount().add(rec.getAmount()));
                creditCardRepository.save(rec.getCreditCard());
            } else if (rec.getAccount() != null) {
                applyBalanceChange(rec.getAccount(), rec.getType(), rec.getAmount());
                accountRepository.save(rec.getAccount());
            }

            int day = Math.min(dom, current.lengthOfMonth());
            entryRepository.save(Entry.builder()
                    .user(rec.getUser()).mode("SIMPLE")
                    .description(rec.getDescription()).amount(rec.getAmount())
                    .type(rec.getType()).category(rec.getCategory())
                    .direction(rec.getDirection()).personName(rec.getPersonName())
                    .entryDate(LocalDate.of(current.getYear(), current.getMonth(), day))
                    .account(rec.getAccount()).creditCard(rec.getCreditCard())
                    .build());
            if ("EXPENSE".equals(rec.getType())) {
                budgetService.adjustSpent(rec.getUser(), rec.getCategory(), monthKey, rec.getAmount());
            }

            rec.setLastAppliedMonth(monthKey);
            entryRepository.save(rec);
            count++;
        }
        return count;
    }

    // ── INSTALLMENT ───────────────────────────────────────────────────────

    @SuppressWarnings("UnusedReturnValue")
    @Transactional
    public Entry createInstallment(User user, InstallmentEntryDTO dto) {
        String cat = categoryService.resolveAndSave(user, dto.getCategory());
        String dir = dto.getDirection() != null ? dto.getDirection() : "STANDARD";
        Account account = dto.getAccountId() != null
                ? accountRepository.findByIdAndUser(dto.getAccountId(), user).orElse(null) : null;
        CreditCard card = dto.getCreditCardId() != null
                ? creditCardRepository.findByIdAndUser(dto.getCreditCardId(), user).orElse(null) : null;

        // Para despesas no cartão, só aumenta usedAmount quando NÃO é PAYABLE.
        if (card != null && "EXPENSE".equals(dto.getType()) && !"PAYABLE".equals(dir)) {
            card.setUsedAmount(card.getUsedAmount().add(dto.getAmount()));
            creditCardRepository.save(card);
        }

        int paid = Math.min(Math.max(dto.getAlreadyPaid(), 0), dto.getTotalInstallments());
        LocalDate startDate = dto.getBaseDate() != null ? dto.getBaseDate() : LocalDate.now();

        Entry entry = entryRepository.save(Entry.builder()
                .user(user).mode("INSTALLMENT")
                .description(dto.getDescription()).amount(dto.getAmount())
                .type(dto.getType()).category(cat)
                .direction(dir).personName(dto.getPersonName())
                .totalInstallments(dto.getTotalInstallments())
                .paidInstallments(paid)
                .installmentAmount(dto.getInstallmentAmount())
                .dayOfMonth(dto.getDayOfMonth())
                .entryDate(startDate)
                .account(account).creditCard(card)
                .contractRate(dto.getContractRate())
                .interestRate(dto.getInterestRate())
                .lateFeeRate(dto.getLateFeeRate())
                .build());

        // Para cartão de crédito, usa o dia de fechamento como dueDate da parcela.
        LocalDate firstDue;
        int effectiveDay;
        if (card != null) {
            YearMonth closingMonth = card.closingMonthFor(startDate);
            effectiveDay = Math.min(card.getClosingDay(), closingMonth.lengthOfMonth());
            firstDue = closingMonth.atDay(effectiveDay);
        } else {
            effectiveDay = dto.getDayOfMonth() != null ? dto.getDayOfMonth() : startDate.getDayOfMonth();
            firstDue = startDate.withDayOfMonth(Math.min(effectiveDay, startDate.lengthOfMonth()));
        }

        BigDecimal uniform = entry.getInstallmentValue();
        BigDecimal paidAmt = BigDecimal.ZERO;

        for (int i = 0; i < dto.getTotalInstallments(); i++) {
            BigDecimal parcAmt = (dto.getCustomAmounts() != null && i < dto.getCustomAmounts().size()
                    && dto.getCustomAmounts().get(i) != null
                    && dto.getCustomAmounts().get(i).compareTo(BigDecimal.ZERO) > 0)
                    ? dto.getCustomAmounts().get(i) : uniform;
            LocalDate base = firstDue.plusMonths(i);
            LocalDate due = base.withDayOfMonth(Math.min(effectiveDay, base.lengthOfMonth()));
            boolean isConfirmed = i < paid;
            if (isConfirmed) paidAmt = paidAmt.add(parcAmt);
            installmentRepository.save(EntryInstallment.builder()
                    .entry(entry).installmentNumber(i + 1).amount(parcAmt).dueDate(due)
                    .confirmed(isConfirmed)
                    .confirmedAt(isConfirmed ? LocalDateTime.now() : null)
                    .build());
        }

        if (paid > 0) {
            entry.setPaidAmount(paidAmt);
            if (paid >= dto.getTotalInstallments()) entry.setStatus("SETTLED");
            entryRepository.save(entry);
        }

        return entry;
    }

    @SuppressWarnings("UnusedReturnValue")
    @Transactional
    public Entry createInvoiceInstallment(User user, CreditCard card,
            BigDecimal installmentAmount, int totalInstallments,
            int dayOfMonth, BigDecimal interestRate, LocalDate baseDate) {
        String cat = categoryService.resolveAndSave(user, "Fatura Cartão");
        BigDecimal totalAmount = installmentAmount.multiply(BigDecimal.valueOf(totalInstallments));

        Entry entry = entryRepository.save(Entry.builder()
                .user(user).mode("INSTALLMENT")
                .description("Parcelamento fatura: " + card.getName())
                .amount(totalAmount).type("EXPENSE").category(cat)
                .direction("PAYABLE")
                .totalInstallments(totalInstallments)
                .installmentAmount(installmentAmount)
                .dayOfMonth(dayOfMonth)
                .creditCard(card)
                .interestRate(interestRate)
                .build());

        for (int i = 0; i < totalInstallments; i++) {
            LocalDate base = baseDate.plusMonths(i);
            LocalDate due = base.withDayOfMonth(Math.min(dayOfMonth, base.lengthOfMonth()));
            installmentRepository.save(EntryInstallment.builder()
                    .entry(entry).installmentNumber(i + 1)
                    .amount(installmentAmount).dueDate(due)
                    .build());
        }

        return entry;
    }

    @Transactional
    public void payNextInstallment(Long entryId, User user) {
        entryRepository.findInstallmentByIdAndUser(entryId, user).ifPresent(entry -> {
            BigDecimal value = entry.getInstallmentValue();
            entry.setPaidAmount(entry.getPaidAmount().add(value));
            entry.setPaidInstallments(Math.min(entry.getPaidInstallments() + 1, entry.getTotalInstallments()));

            if (entry.getPaidInstallments() >= entry.getTotalInstallments()) {
                entry.setStatus("SETTLED");
            }

            // Parcelamentos PAYABLE vinculados a cartão nunca modificam usedAmount
            if (entry.getCreditCard() != null && !"PAYABLE".equals(entry.getDirection())) {
                CreditCard card = entry.getCreditCard();
                BigDecimal newUsed = card.getUsedAmount().subtract(value);
                card.setUsedAmount(newUsed.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newUsed);
                creditCardRepository.save(card);
            } else if (entry.getAccount() != null && "PAYABLE".equals(entry.getDirection())) {
                Account account = entry.getAccount();
                account.setBalance(account.getBalance().subtract(value));
                accountRepository.save(account);
            }

            int nextNum = entry.getPaidInstallments();
            installmentRepository.findByEntryAndInstallmentNumber(entry, nextNum)
                    .ifPresent(inst -> {
                        inst.setConfirmed(true);
                        inst.setConfirmedAt(java.time.LocalDateTime.now());
                        installmentRepository.save(inst);
                    });

            entryRepository.save(entry);
        });
    }

    @Transactional
    public void markDefault(Long id, User user, BigDecimal interestRate, BigDecimal lateFeeRate,
                            java.time.LocalDate defaultSinceDate) {
        entryRepository.findInstallmentByIdAndUser(id, user).ifPresent(entry -> {
            entry.setStatus("IN_DEFAULT");
            entry.setInterestRate(interestRate);
            entry.setLateFeeRate(lateFeeRate);
            entry.setDefaultSinceDate(defaultSinceDate);
            entryRepository.save(entry);
        });
    }

    @Transactional
    public void updateInstallment(Long id, User user, InstallmentEntryDTO dto) {
        entryRepository.findInstallmentByIdAndUser(id, user).ifPresent(entry -> {
            // Reverter efeito antigo do cartão (apenas o valor restante não pago)
            if (entry.getCreditCard() != null && "EXPENSE".equals(entry.getType())
                    && !"PAYABLE".equals(entry.getDirection())) {
                CreditCard oldCard = entry.getCreditCard();
                BigDecimal newUsed = oldCard.getUsedAmount().subtract(entry.getRemainingAmount());
                oldCard.setUsedAmount(newUsed.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newUsed);
                creditCardRepository.save(oldCard);
            }

            String cat = categoryService.resolveAndSave(user, dto.getCategory());
            CreditCard newCard = dto.getCreditCardId() != null
                    ? creditCardRepository.findByIdAndUser(dto.getCreditCardId(), user).orElse(null) : null;
            boolean allowBoth = "RECEIVABLE".equals(entry.getDirection());
            Account newAccount = (dto.getAccountId() != null && (newCard == null || allowBoth))
                    ? accountRepository.findByIdAndUser(dto.getAccountId(), user).orElse(null) : null;

            BigDecimal newRemaining = dto.getAmount().subtract(entry.getPaidAmount()).max(BigDecimal.ZERO);

            if (newCard != null && "EXPENSE".equals(dto.getType()) && !"PAYABLE".equals(entry.getDirection())) {
                newCard.setUsedAmount(newCard.getUsedAmount().add(newRemaining));
                creditCardRepository.save(newCard);
            }

            List<EntryInstallment> all = installmentRepository
                    .findByEntryOrderByInstallmentNumberAsc(entry);
            List<EntryInstallment> unpaid = all.stream()
                    .filter(i -> !Boolean.TRUE.equals(i.getConfirmed()))
                    .toList();

            boolean amountChanged = entry.getAmount().compareTo(dto.getAmount()) != 0;
            int newDom = dto.getDayOfMonth() != null ? dto.getDayOfMonth() : 0;
            boolean dayChanged = newDom > 0 && newDom != (entry.getDayOfMonth() != null ? entry.getDayOfMonth() : 0);

            BigDecimal each = (amountChanged && !unpaid.isEmpty())
                    ? newRemaining.divide(BigDecimal.valueOf(unpaid.size()), 2, RoundingMode.HALF_EVEN)
                    : null;

            LocalDate installmentStartDate = dto.getBaseDate();
            if (installmentStartDate != null) {
                int dom = newDom > 0 ? newDom : installmentStartDate.getDayOfMonth();
                for (int i = 0; i < all.size(); i++) {
                    EntryInstallment inst = all.get(i);
                    if (Boolean.TRUE.equals(inst.getConfirmed())) continue;
                    LocalDate base = installmentStartDate.plusMonths(i);
                    inst.setDueDate(base.withDayOfMonth(Math.min(dom, base.lengthOfMonth())));
                    if (each != null) inst.setAmount(each);
                    installmentRepository.save(inst);
                }
                if (amountChanged) entry.setInstallmentAmount(null);
            } else if ((amountChanged || dayChanged) && !unpaid.isEmpty()) {
                for (EntryInstallment inst : unpaid) {
                    if (each != null) inst.setAmount(each);
                    if (dayChanged && inst.getDueDate() != null) {
                        LocalDate base = inst.getDueDate();
                        inst.setDueDate(base.withDayOfMonth(Math.min(newDom, base.lengthOfMonth())));
                    }
                    installmentRepository.save(inst);
                }
                if (amountChanged) entry.setInstallmentAmount(null);
            }

            entry.setDescription(dto.getDescription());
            entry.setAmount(dto.getAmount());
            entry.setType(dto.getType());
            entry.setCategory(cat);
            entry.setPersonName(dto.getPersonName() != null && !dto.getPersonName().isBlank()
                    ? dto.getPersonName() : null);
            entry.setAccount(newAccount);
            entry.setCreditCard(newCard);

            if (installmentStartDate != null) {
                entry.setEntryDate(installmentStartDate);
                int dom = newDom > 0 ? newDom : installmentStartDate.getDayOfMonth();
                entry.setDayOfMonth(dom);
            } else if (dto.getDayOfMonth() != null) {
                entry.setDayOfMonth(dto.getDayOfMonth());
            }

            entryRepository.save(entry);
        });
    }

    @Transactional
    public void deleteInstallment(Long id, User user) {
        entryRepository.findInstallmentByIdAndUser(id, user).ifPresent(entry -> {
            // Parcelamentos PAYABLE no cartão não adicionaram ao usedAmount, então não subtraem
            if (entry.getCreditCard() != null && "EXPENSE".equals(entry.getType())
                    && !"PAYABLE".equals(entry.getDirection())) {
                CreditCard card = entry.getCreditCard();
                BigDecimal newUsed = card.getUsedAmount().subtract(entry.getRemainingAmount());
                card.setUsedAmount(newUsed.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newUsed);
                creditCardRepository.save(card);
            }
            entryRepository.delete(entry);
        });
    }

    @Transactional
    public void confirmInstallment(Long installmentId, User user, Long selectedAccountId) {
        installmentRepository.findByIdAndUser(installmentId, user).ifPresent(inst -> {
            if (Boolean.TRUE.equals(inst.getConfirmed())) return;

            inst.setConfirmed(true);
            inst.setConfirmedAt(LocalDateTime.now());

            Entry entry = inst.getEntry();
            entry.setPaidAmount(entry.getPaidAmount().add(inst.getAmount()));
            entry.setPaidInstallments(Math.min(entry.getPaidInstallments() + 1, entry.getTotalInstallments()));
            if (entry.getPaidInstallments() >= entry.getTotalInstallments()) {
                entry.setStatus("SETTLED");
            }

            // Apenas entries STANDARD com cartão reduzem o usedAmount
            if (entry.getCreditCard() != null && "STANDARD".equals(entry.getDirection())) {
                CreditCard card = entry.getCreditCard();
                BigDecimal newUsed = card.getUsedAmount().subtract(inst.getAmount());
                card.setUsedAmount(newUsed.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newUsed);
                creditCardRepository.save(card);
            } else {
                Account account = null;
                if (selectedAccountId != null) {
                    account = accountRepository.findByIdAndUser(selectedAccountId, user).orElse(null);
                } else if (entry.getAccount() != null) {
                    account = entry.getAccount();
                }
                if (account != null) {
                    if ("PAYABLE".equals(entry.getDirection())) {
                        account.setBalance(account.getBalance().subtract(inst.getAmount()));
                    } else if ("RECEIVABLE".equals(entry.getDirection())) {
                        account.setBalance(account.getBalance().add(inst.getAmount()));
                    }
                    accountRepository.save(account);

                    String simpleType = "PAYABLE".equals(entry.getDirection()) ? "EXPENSE" : "INCOME";
                    String notes = "Parcela " + inst.getInstallmentNumber() + "/" + entry.getTotalInstallments();
                    if (entry.getPersonName() != null && !entry.getPersonName().isBlank()) {
                        notes += " — " + entry.getPersonName();
                    }
                    entryRepository.save(Entry.builder()
                            .user(user)
                            .mode("SIMPLE")
                            .description(entry.getDescription())
                            .amount(inst.getAmount())
                            .type(simpleType)
                            .category(entry.getCategory())
                            .entryDate(LocalDate.now())
                            .direction("STANDARD")
                            .account(account)
                            .notes(notes)
                            .build());
                    if ("EXPENSE".equals(simpleType)) {
                        budgetService.adjustSpent(user, entry.getCategory(), YearMonth.now().toString(), inst.getAmount());
                    }
                }
            }

            installmentRepository.save(inst);
            entryRepository.save(entry);
        });
    }

    // ── TRANSFERÊNCIA ENTRE CONTAS ───────────────────────────────────────

    @Transactional
    public void transferBetweenAccounts(User user, Long fromAccountId, Long toAccountId,
                                        BigDecimal amount, String description, LocalDate date) {
        Account from = accountRepository.findByIdAndUser(fromAccountId, user)
                .orElseThrow(() -> new IllegalArgumentException("Conta de origem não encontrada."));
        Account to = accountRepository.findByIdAndUser(toAccountId, user)
                .orElseThrow(() -> new IllegalArgumentException("Conta de destino não encontrada."));

        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));
        accountRepository.save(from);
        accountRepository.save(to);

        String cat = categoryService.resolveAndSave(user, "Transferência");
        boolean hasCustomDesc = description != null && !description.isBlank();

        entryRepository.save(Entry.builder()
                .user(user).mode("SIMPLE")
                .description(hasCustomDesc ? description : "Transferência para " + to.getName())
                .amount(amount).type("TRANSFER_OUT").category(cat)
                .entryDate(date).account(from)
                .build());

        entryRepository.save(Entry.builder()
                .user(user).mode("SIMPLE")
                .description(hasCustomDesc ? description : "Transferência de " + from.getName())
                .amount(amount).type("TRANSFER_IN").category(cat)
                .entryDate(date).account(to)
                .build());
    }

    // ── Utilitários ───────────────────────────────────────────────────────

    public boolean appliesThisMonth(Entry e, YearMonth month) {
        if ("MONTHLY".equals(e.getFrequency())) return true;
        if ("ANNUAL".equals(e.getFrequency()) && e.getMonthOfYear() != null) {
            return e.getMonthOfYear().equals(month.getMonthValue());
        }
        return false;
    }

    private void applyBalanceChange(Account account, String type, BigDecimal amount) {
        if ("INCOME".equals(type) || "TRANSFER_IN".equals(type))  account.setBalance(account.getBalance().add(amount));
        if ("EXPENSE".equals(type) || "TRANSFER_OUT".equals(type)) account.setBalance(account.getBalance().subtract(amount));
    }

    private String reverseType(String type) {
        if ("TRANSFER_OUT".equals(type)) return "TRANSFER_IN";
        if ("TRANSFER_IN".equals(type))  return "TRANSFER_OUT";
        return "INCOME".equals(type) ? "EXPENSE" : "INCOME";
    }
}
