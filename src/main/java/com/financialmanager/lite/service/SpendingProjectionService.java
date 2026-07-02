package com.financialmanager.lite.service;

import com.financialmanager.lite.dto.InstallmentEntryDTO;
import com.financialmanager.lite.model.*;
import com.financialmanager.lite.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SpendingProjectionService {

    private final SpendingProjectionRepository projectionRepo;
    private final SpendingProjectionItemRepository itemRepo;
    private final EntryRepository entryRepository;
    private final CreditCardRepository cardRepository;
    private final AccountRepository accountRepository;
    private final EntryService entryService;

    public record MonthRow(
            YearMonth month, BigDecimal income, BigDecimal expense,
            BigDecimal netChange, BigDecimal closingBalance,
            List<EventRow> events, List<OriginSummary> originSummaries) {}

    public record EventRow(
            int day, String description, String type,
            BigDecimal amount, String badge, String originLabel, BigDecimal running) {}

    public record OriginSummary(String label, String badge, BigDecimal income, BigDecimal expense) {
        public BigDecimal net() { return income.subtract(expense); }
    }

    public List<SpendingProjection> findByUser(User user) {
        return projectionRepo.findByUserOrderByCreatedAtDesc(user);
    }

    public Optional<SpendingProjection> findByIdAndUser(Long id, User user) {
        return projectionRepo.findByIdAndUser(id, user);
    }

    @Transactional
    public SpendingProjection create(User user, String name, String mode) {
        return projectionRepo.save(SpendingProjection.builder().user(user).name(name).mode(mode).build());
    }

    @Transactional
    public void delete(Long id, User user) {
        projectionRepo.findByIdAndUser(id, user).ifPresent(projectionRepo::delete);
    }

    public List<SpendingProjectionItem> getItems(Long projectionId) {
        return itemRepo.findByProjectionIdWithDetails(projectionId);
    }

    public List<Entry> findAvailableInstallments(User user) {
        return entryRepository.findActivePayablesByUser(user);
    }

    public List<Entry> findAvailableRecurring(User user) {
        return entryRepository.findRecurringActiveByUser(user);
    }

    public List<CreditCard> findAvailableCards(User user) {
        return cardRepository.findByUserAndActiveTrue(user);
    }

    public List<Account> findAccounts(User user) {
        return accountRepository.findByUserAndActiveTrue(user);
    }

    @Transactional
    public void addEntry(Long projId, Long entryId, boolean excluded, User user) {
        projectionRepo.findByIdAndUser(projId, user).ifPresent(proj ->
            entryRepository.findByIdAndUser(entryId, user).ifPresent(entry ->
                itemRepo.save(SpendingProjectionItem.builder()
                        .projection(proj).itemType("ENTRY").entry(entry).excluded(excluded).build())
            )
        );
    }

    @Transactional
    public void addCard(Long projId, Long cardId, String cardMode, Integer cardInstallments,
                        BigDecimal cardEntrada, Integer cardDayOfMonth, User user) {
        projectionRepo.findByIdAndUser(projId, user).ifPresent(proj ->
            cardRepository.findByIdAndUser(cardId, user).ifPresent(card ->
                itemRepo.save(SpendingProjectionItem.builder()
                        .projection(proj).itemType("CARD").card(card)
                        .cardMode(cardMode)
                        .cardInstallments(cardInstallments != null ? cardInstallments : 6)
                        .cardEntrada(cardEntrada != null ? cardEntrada : BigDecimal.ZERO)
                        .cardDayOfMonth(cardDayOfMonth != null ? cardDayOfMonth : card.getDueDay())
                        .build())
            )
        );
    }

    @Transactional
    public void addCustom(Long projId, String description, BigDecimal amount, int installments,
                          int dayOfMonth, String type, User user) {
        projectionRepo.findByIdAndUser(projId, user).ifPresent(proj ->
            itemRepo.save(SpendingProjectionItem.builder()
                    .projection(proj).itemType("CUSTOM")
                    .customDescription(description).customAmount(amount)
                    .customInstallments(installments).customDayOfMonth(dayOfMonth)
                    .customType(type).build())
        );
    }

    // Returns the projection id to redirect to, or null if not found
    @Transactional
    public Long removeItem(Long itemId, User user) {
        return itemRepo.findByIdAndUserId(itemId, user.getId())
                .map(item -> {
                    Long projId = item.getProjection().getId();
                    itemRepo.delete(item);
                    return projId;
                })
                .orElse(null);
    }

    @Transactional
    public Long confirmItem(Long itemId, User user) {
        return itemRepo.findByIdAndUserId(itemId, user.getId())
                .map(item -> {
                    Long projId = item.getProjection().getId();
                    if (!item.isConfirmed()) {
                        executeConfirm(item, user);
                        item.setConfirmed(true);
                        item.setConfirmedAt(LocalDateTime.now());
                        itemRepo.save(item);
                    }
                    return projId;
                })
                .orElse(null);
    }

    public List<MonthRow> calculate(SpendingProjection proj, List<SpendingProjectionItem> items, User user) {
        Set<Long> excludedIds = items.stream()
                .filter(i -> "ENTRY".equals(i.getItemType()) && i.isExcluded() && i.getEntry() != null)
                .map(i -> i.getEntry().getId()).collect(Collectors.toSet());

        List<Entry> recurringItems;
        List<Entry> payableItems;

        if ("EXCLUDE_FROM_ALL".equals(proj.getMode())) {
            recurringItems = entryRepository.findRecurringActiveByUser(user).stream()
                    .filter(e -> !excludedIds.contains(e.getId())).toList();
            payableItems = entryRepository.findActivePayablesByUser(user).stream()
                    .filter(e -> !excludedIds.contains(e.getId())).toList();
        } else {
            recurringItems = items.stream()
                    .filter(i -> "ENTRY".equals(i.getItemType()) && !i.isExcluded()
                            && i.getEntry() != null && "RECURRING".equals(i.getEntry().getMode()))
                    .map(SpendingProjectionItem::getEntry).toList();
            payableItems = items.stream()
                    .filter(i -> "ENTRY".equals(i.getItemType()) && !i.isExcluded()
                            && i.getEntry() != null && "INSTALLMENT".equals(i.getEntry().getMode()))
                    .map(SpendingProjectionItem::getEntry).toList();
        }

        List<SpendingProjectionItem> cardItems   = items.stream()
                .filter(i -> "CARD".equals(i.getItemType())).toList();
        List<SpendingProjectionItem> customItems = items.stream()
                .filter(i -> "CUSTOM".equals(i.getItemType())).toList();

        Map<Long, BigDecimal> rotatingBal = new LinkedHashMap<>();
        for (SpendingProjectionItem ci : cardItems) {
            if ("MIN_PAYMENT".equals(ci.getCardMode()) && ci.getCard() != null) {
                rotatingBal.put(ci.getCard().getId(), ci.getCard().getUsedAmount());
            }
        }

        BigDecimal balance = accountRepository.sumBalanceByUser(user);
        List<MonthRow> rows = new ArrayList<>();
        YearMonth now = YearMonth.now();

        for (int idx = 1; idx <= 12; idx++) {
            YearMonth ym = now.plusMonths(idx);
            final int fi = idx;
            List<EventRow> events = new ArrayList<>();
            BigDecimal income = BigDecimal.ZERO;
            BigDecimal expense = BigDecimal.ZERO;

            for (Entry e : recurringItems) {
                if (!entryService.appliesThisMonth(e, ym)) continue;
                boolean isIn = "INCOME".equals(e.getType());
                events.add(new EventRow(dayOf(e), e.getDescription(), e.getType(),
                        e.getAmount(), isIn ? "bg-success" : "bg-secondary", "Recorrente", null));
                if (isIn) income = income.add(e.getAmount());
                else expense = expense.add(e.getAmount());
            }

            for (Entry e : payableItems) {
                if (e.getPaidInstallments() + fi > e.getTotalInstallments()) continue;
                BigDecimal amt = e.getInstallmentValue();
                events.add(new EventRow(dayOf(e),
                        e.getDescription() + " (" + (e.getPaidInstallments() + fi) + "/" + e.getTotalInstallments() + "x)",
                        "EXPENSE", amt, "bg-danger", "Parcela", null));
                expense = expense.add(amt);
            }

            for (SpendingProjectionItem ci : cardItems) {
                if (ci.getCard() == null) continue;
                if ("MIN_PAYMENT".equals(ci.getCardMode())) {
                    BigDecimal bal = rotatingBal.getOrDefault(ci.getCard().getId(), BigDecimal.ZERO);
                    if (bal.compareTo(BigDecimal.ZERO) <= 0) continue;
                    BigDecimal minPay = bal.multiply(ci.getCard().getMinimumPaymentRate())
                            .setScale(2, RoundingMode.HALF_EVEN);
                    BigDecimal rem = bal.subtract(minPay);
                    BigDecimal interest = rem.multiply(ci.getCard().getRotatingCreditRate())
                            .setScale(2, RoundingMode.HALF_EVEN);
                    rotatingBal.put(ci.getCard().getId(), rem.add(interest));
                    events.add(new EventRow(ci.getCard().getDueDay(),
                            "Mín. " + ci.getCard().getName(), "EXPENSE", minPay, "bg-warning", "Rotativo", null));
                    expense = expense.add(minPay);
                } else if ("INSTALLMENT".equals(ci.getCardMode())
                        && fi <= (ci.getCardInstallments() != null ? ci.getCardInstallments() : 0)) {
                    BigDecimal pmt = ci.calcCardPmt();
                    if (pmt.compareTo(BigDecimal.ZERO) <= 0) continue;
                    int day = ci.getCardDayOfMonth() != null ? ci.getCardDayOfMonth() : ci.getCard().getDueDay();
                    events.add(new EventRow(day,
                            ci.getCard().getName() + " (" + fi + "/" + ci.getCardInstallments() + "x)",
                            "EXPENSE", pmt, "bg-primary", "Fatura", null));
                    expense = expense.add(pmt);
                }
            }

            for (SpendingProjectionItem ci : customItems) {
                if (ci.getCustomAmount() == null || fi > ci.getCustomInstallments()) continue;
                boolean isIn = "INCOME".equals(ci.getCustomType());
                BigDecimal amt = ci.getCustomAmount();
                String label = ci.getCustomInstallments() > 1
                        ? ci.getCustomDescription() + " (" + fi + "/" + ci.getCustomInstallments() + "x)"
                        : ci.getCustomDescription();
                events.add(new EventRow(ci.getCustomDayOfMonth(), label,
                        ci.getCustomType(), amt, isIn ? "bg-success" : "bg-info", "Personalizado", null));
                if (isIn) income = income.add(amt);
                else expense = expense.add(amt);
            }

            events.sort(Comparator.comparingInt(EventRow::day));
            BigDecimal running = balance;
            List<EventRow> withRunning = new ArrayList<>();
            for (EventRow ev : events) {
                running = "INCOME".equals(ev.type()) ? running.add(ev.amount()) : running.subtract(ev.amount());
                withRunning.add(new EventRow(ev.day(), ev.description(), ev.type(),
                        ev.amount(), ev.badge(), ev.originLabel(), running));
            }

            BigDecimal net = income.subtract(expense);
            balance = balance.add(net);
            rows.add(new MonthRow(ym, income, expense, net, balance, withRunning, buildOriginSummaries(withRunning)));
        }
        return rows;
    }

    private void executeConfirm(SpendingProjectionItem item, User user) {
        if ("CARD".equals(item.getItemType()) && "INSTALLMENT".equals(item.getCardMode())
                && item.getCard() != null) {
            CreditCard card = item.getCard();
            int n   = item.getCardInstallments() != null ? item.getCardInstallments() : 6;
            int day = item.getCardDayOfMonth() != null ? item.getCardDayOfMonth() : card.getDueDay();
            BigDecimal pmt  = item.calcCardPmt();
            BigDecimal rate = card.getInstallmentRate();
            if (pmt.compareTo(BigDecimal.ZERO) > 0) {
                LocalDate base = LocalDate.now().plusMonths(1);
                LocalDate baseDate = base.withDayOfMonth(Math.min(day, base.lengthOfMonth()));
                entryService.createInvoiceInstallment(user, card, pmt, n, day, rate, baseDate);
            }
            card.setUsedAmount(BigDecimal.ZERO);
            cardRepository.save(card);

        } else if ("CUSTOM".equals(item.getItemType()) && item.getCustomAmount() != null) {
            entryService.createInstallment(user, InstallmentEntryDTO.builder()
                    .description(item.getCustomDescription())
                    .amount(item.getCustomAmount())
                    .type(item.getCustomType())
                    .category("Projeção").direction("PAYABLE")
                    .totalInstallments(item.getCustomInstallments())
                    .dayOfMonth(item.getCustomDayOfMonth())
                    .build());
        }
    }

    private int dayOf(Entry e) {
        return e.getDayOfMonth() != null ? e.getDayOfMonth() : 1;
    }

    private List<OriginSummary> buildOriginSummaries(List<EventRow> events) {
        Map<String, BigDecimal[]> totals = new LinkedHashMap<>();
        Map<String, String> badges = new LinkedHashMap<>();
        for (EventRow ev : events) {
            String key = ev.originLabel() != null ? ev.originLabel() : "Outro";
            totals.computeIfAbsent(key, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            badges.putIfAbsent(key, ev.badge());
            if ("INCOME".equals(ev.type())) totals.get(key)[0] = totals.get(key)[0].add(ev.amount());
            else                             totals.get(key)[1] = totals.get(key)[1].add(ev.amount());
        }
        List<OriginSummary> result = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> e : totals.entrySet()) {
            result.add(new OriginSummary(e.getKey(), badges.get(e.getKey()), e.getValue()[0], e.getValue()[1]));
        }
        return result;
    }
}
