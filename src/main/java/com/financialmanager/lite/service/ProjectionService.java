package com.financialmanager.lite.service;

import com.financialmanager.lite.model.*;
import com.financialmanager.lite.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProjectionService {

    private final AccountRepository accountRepository;
    private final CreditCardRepository creditCardRepository;
    private final EntryRepository entryRepository;
    private final EntryInstallmentRepository installmentRepository;
    private final EntryService entryService;

    // ── Records ──────────────────────────────────────────────────────────────

    public record CashFlowEvent(
            int day, String description, String type, BigDecimal amount,
            String originLabel, String badgeClass, BigDecimal runningBalance) {}

    public record DailyBreakdown(
            int day, BigDecimal openingBalance,
            List<CashFlowEvent> events, BigDecimal closingBalance) {}

    public record DayFlow(
            LocalDate date, boolean isToday, boolean isPast,
            BigDecimal openingBalance, List<CashFlowEvent> events, BigDecimal closingBalance) {}

    public record MonthProjection(
            YearMonth month, BigDecimal openingBalance,
            BigDecimal recurringIncome, BigDecimal receivableIncome, BigDecimal simpleIncome,
            BigDecimal recurringExpenses, BigDecimal debtInstallments, BigDecimal simpleExpenses,
            BigDecimal cardBills, BigDecimal closingBalance,
            List<CashFlowEvent> events, List<DailyBreakdown> dailyBreakdown) {
        public BigDecimal netChange() {
            return recurringIncome.add(receivableIncome).add(simpleIncome)
                    .subtract(recurringExpenses).subtract(debtInstallments)
                    .subtract(simpleExpenses).subtract(cardBills);
        }
        public BigDecimal totalIncome()   { return recurringIncome.add(receivableIncome).add(simpleIncome); }
        public BigDecimal totalExpenses() { return recurringExpenses.add(debtInstallments).add(simpleExpenses).add(cardBills); }
    }

    public record ProjectionData(
            BigDecimal currentBalance,
            List<MonthProjection> projections,
            MonthProjection selected,
            List<DayFlow> currentMonthFlow,
            YearMonth currentMonth,
            int monthsBefore,
            int monthsAfter) {}

    // ── Main entry point ──────────────────────────────────────────────────────

    public ProjectionData compute(User user, String month, int monthsBefore, int monthsAfter) {
        BigDecimal currentBalance    = accountRepository.sumBalanceByUser(user);
        List<Entry> recurring        = entryRepository.findRecurringActiveByUser(user);
        List<Entry> futureSimple     = entryRepository.findFutureSimpleByUser(user, LocalDate.now());

        List<CreditCard> cardsWithBalance = creditCardRepository.findByUserAndActiveTrue(user);
        Map<CreditCard, LocalDate> cardDueDates = new LinkedHashMap<>();
        for (CreditCard card : cardsWithBalance) {
            cardDueDates.put(card, cardNextDueDate(card));
        }

        YearMonth currentMonth = YearMonth.now();
        LocalDate today        = LocalDate.now();
        LocalDate endOfMonth   = currentMonth.atEndOfMonth();

        List<Entry> recurringForNow = recurring.stream()
                .filter(e -> entryService.appliesThisMonth(e, currentMonth))
                .filter(e -> !currentMonth.toString().equals(e.getLastAppliedMonth()))
                .toList();

        LocalDate startOfMonth = currentMonth.atDay(1);
        List<EntryInstallment> installmentsDue =
                installmentRepository.findUnconfirmedDueInRange(user, startOfMonth, endOfMonth);

        List<EntryInstallment> ccCarryOver = installmentsDue.stream()
                .filter(inst -> inst.getDueDate() != null
                        && inst.getEntry().getCreditCard() != null
                        && "PAYABLE".equals(inst.getEntry().getDirection())
                        && !billingMonthFor(inst.getEntry().getCreditCard(), inst.getDueDate()).equals(currentMonth))
                .toList();

        List<EntryInstallment> installmentsForCurrentFlow = installmentsDue.stream()
                .filter(inst -> {
                    if (inst.getDueDate() == null) return false;
                    Entry e = inst.getEntry();
                    if (e.getCreditCard() != null && "PAYABLE".equals(e.getDirection()))
                        return billingMonthFor(e.getCreditCard(), inst.getDueDate()).equals(currentMonth);
                    return true;
                })
                .toList();

        List<Entry> simpleForNow = futureSimple.stream()
                .filter(e -> e.getEntryDate() != null
                        && YearMonth.from(e.getEntryDate()).equals(currentMonth))
                .toList();

        List<DayFlow> currentMonthFlow = buildCurrentMonthDailyFlow(
                currentBalance, today, currentMonth,
                recurringForNow, installmentsForCurrentFlow,
                cardsWithBalance, cardDueDates, simpleForNow);

        // ── Current month projection ─────────────────────────────────────────
        BigDecimal curRecIncome  = BigDecimal.ZERO;
        BigDecimal curRecExpense = BigDecimal.ZERO;
        for (Entry e : recurringForNow) {
            if ("INCOME".equals(e.getType())) curRecIncome  = curRecIncome.add(e.getAmount());
            else                              curRecExpense = curRecExpense.add(e.getAmount());
        }
        BigDecimal curReceivable    = BigDecimal.ZERO;
        BigDecimal curDebtInsts     = BigDecimal.ZERO;
        BigDecimal curCardBills     = BigDecimal.ZERO;
        BigDecimal overdueCardBills = BigDecimal.ZERO;
        for (EntryInstallment inst : installmentsDue) {
            Entry ie = inst.getEntry();
            if ("RECEIVABLE".equals(ie.getDirection()))
                curReceivable = curReceivable.add(inst.getAmount());
            else if (ie.getCreditCard() == null)
                curDebtInsts  = curDebtInsts.add(inst.getAmount());
            else if (inst.getDueDate() != null
                    && billingMonthFor(ie.getCreditCard(), inst.getDueDate()).equals(currentMonth))
                curCardBills  = curCardBills.add(inst.getAmount());
        }
        for (CreditCard card : cardsWithBalance) {
            LocalDate due = cardDueDates.get(card);
            if (due != null && YearMonth.from(due).equals(currentMonth)) {
                curCardBills = curCardBills.add(card.getUsedAmount());
                if (due.isBefore(today)) overdueCardBills = overdueCardBills.add(card.getUsedAmount());
            }
        }
        BigDecimal curSimpleIncome  = BigDecimal.ZERO;
        BigDecimal curSimpleExpense = BigDecimal.ZERO;
        for (Entry e : simpleForNow) {
            if ("INCOME".equals(e.getType())) curSimpleIncome  = curSimpleIncome.add(e.getAmount());
            else                              curSimpleExpense = curSimpleExpense.add(e.getAmount());
        }

        BigDecimal currentProjectedClosing = currentMonthFlow.isEmpty() ? currentBalance :
                currentMonthFlow.get(currentMonthFlow.size() - 1).closingBalance();
        currentProjectedClosing = currentProjectedClosing.subtract(overdueCardBills);

        List<CashFlowEvent> curEvents = currentMonthFlow.stream()
                .flatMap(df -> df.events().stream()).toList();
        List<DailyBreakdown> curDaily = currentMonthFlow.stream()
                .filter(df -> !df.events().isEmpty())
                .map(df -> new DailyBreakdown(df.date().getDayOfMonth(),
                        df.openingBalance(), df.events(), df.closingBalance()))
                .toList();
        MonthProjection currentMonthProjection = new MonthProjection(
                currentMonth, currentBalance,
                curRecIncome, curReceivable, curSimpleIncome,
                curRecExpense, curDebtInsts, curSimpleExpense,
                curCardBills, currentProjectedClosing, curEvents, curDaily);

        // ── Future projections ────────────────────────────────────────────────
        YearMonth now = YearMonth.now();
        List<EntryInstallment> futureInstallments =
                installmentRepository.findUnconfirmedDueAfter(user, today);

        List<EntryInstallment> allFutureInstallments = new ArrayList<>(futureInstallments);
        allFutureInstallments.addAll(ccCarryOver);

        List<MonthProjection> futureProjections = new ArrayList<>();
        BigDecimal balance = currentProjectedClosing;

        for (int i = 1; i <= monthsAfter; i++) {
            YearMonth ym = now.plusMonths(i);

            List<Entry> incomeItems = recurring.stream()
                    .filter(e -> "INCOME".equals(e.getType()) && entryService.appliesThisMonth(e, ym)).toList();
            BigDecimal recurringIncome = sumEntries(incomeItems);

            List<Entry> expenseItems = recurring.stream()
                    .filter(e -> "EXPENSE".equals(e.getType()) && entryService.appliesThisMonth(e, ym)).toList();
            BigDecimal recurringExpenses = sumEntries(expenseItems);

            List<EntryInstallment> dueThisMonth = allFutureInstallments.stream()
                    .filter(inst -> inst.getDueDate() != null)
                    .filter(inst -> {
                        Entry e = inst.getEntry();
                        if (e.getCreditCard() != null && "PAYABLE".equals(e.getDirection()))
                            return billingMonthFor(e.getCreditCard(), inst.getDueDate()).equals(ym);
                        return YearMonth.from(inst.getDueDate()).equals(ym);
                    })
                    .toList();

            List<EntryInstallment> receivableInsts = dueThisMonth.stream()
                    .filter(inst -> "RECEIVABLE".equals(inst.getEntry().getDirection()))
                    .toList();
            BigDecimal receivableIncome = receivableInsts.stream()
                    .map(EntryInstallment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

            List<EntryInstallment> payableInsts = dueThisMonth.stream()
                    .filter(inst -> "PAYABLE".equals(inst.getEntry().getDirection())
                            && inst.getEntry().getCreditCard() == null)
                    .toList();
            BigDecimal debtInstallments = payableInsts.stream()
                    .map(EntryInstallment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

            List<EntryInstallment> cardPayableInsts = dueThisMonth.stream()
                    .filter(inst -> "PAYABLE".equals(inst.getEntry().getDirection())
                            && inst.getEntry().getCreditCard() != null)
                    .toList();

            List<Entry> simpleIncomeItems = futureSimple.stream()
                    .filter(e -> "INCOME".equals(e.getType()) && e.getEntryDate() != null
                            && YearMonth.from(e.getEntryDate()).equals(ym)).toList();
            BigDecimal simpleIncome = sumEntries(simpleIncomeItems);

            List<Entry> simpleExpenseItems = futureSimple.stream()
                    .filter(e -> "EXPENSE".equals(e.getType()) && e.getEntryDate() != null
                            && YearMonth.from(e.getEntryDate()).equals(ym)).toList();
            BigDecimal simpleExpenses = sumEntries(simpleExpenseItems);

            Map<Long, BigDecimal> cardTotalsYm  = new LinkedHashMap<>();
            Map<Long, Integer>    cardDueDaysYm = new LinkedHashMap<>();
            Map<Long, String>     cardNamesYm   = new LinkedHashMap<>();

            for (CreditCard card : cardsWithBalance) {
                LocalDate due = cardDueDates.get(card);
                if (due != null && YearMonth.from(due).equals(ym)) {
                    cardTotalsYm.put(card.getId(), card.getUsedAmount());
                    cardDueDaysYm.put(card.getId(), due.getDayOfMonth());
                    cardNamesYm.put(card.getId(), card.getName());
                }
            }
            for (EntryInstallment inst : cardPayableInsts) {
                CreditCard instCard = inst.getEntry().getCreditCard();
                int dueDay = Math.min(instCard.getDueDay(), ym.lengthOfMonth());
                cardTotalsYm.merge(instCard.getId(), inst.getAmount(), BigDecimal::add);
                cardDueDaysYm.putIfAbsent(instCard.getId(), dueDay);
                cardNamesYm.putIfAbsent(instCard.getId(), instCard.getName());
            }

            List<CashFlowEvent> cardBillEvents = new ArrayList<>();
            BigDecimal cardBills = BigDecimal.ZERO;
            for (Long cardId : cardTotalsYm.keySet()) {
                BigDecimal total = cardTotalsYm.get(cardId);
                cardBills = cardBills.add(total);
                cardBillEvents.add(new CashFlowEvent(cardDueDaysYm.get(cardId),
                        "Fatura: " + cardNamesYm.get(cardId), "EXPENSE", total,
                        "Fatura", "bg-primary", null));
            }

            BigDecimal opening = balance;
            balance = opening.add(recurringIncome).add(receivableIncome).add(simpleIncome)
                             .subtract(recurringExpenses).subtract(debtInstallments)
                             .subtract(simpleExpenses).subtract(cardBills);

            List<CashFlowEvent> events = buildEvents(opening, incomeItems, expenseItems,
                    receivableInsts, payableInsts, simpleIncomeItems, simpleExpenseItems, cardBillEvents);
            List<DailyBreakdown> daily = buildDailyBreakdown(opening, events);

            futureProjections.add(new MonthProjection(ym, opening,
                    recurringIncome, receivableIncome, simpleIncome,
                    recurringExpenses, debtInstallments, simpleExpenses,
                    cardBills, balance, events, daily));
        }

        // ── Past projections ──────────────────────────────────────────────────
        List<MonthProjection> pastProjections = new ArrayList<>();
        BigDecimal pastClosing = currentBalance;

        for (int i = 1; i <= monthsBefore; i++) {
            YearMonth ym = now.minusMonths(i);

            List<Entry> incomeItems = recurring.stream()
                    .filter(e -> "INCOME".equals(e.getType()) && entryService.appliesThisMonth(e, ym)).toList();
            BigDecimal recurringIncome = sumEntries(incomeItems);

            List<Entry> expenseItems = recurring.stream()
                    .filter(e -> "EXPENSE".equals(e.getType()) && entryService.appliesThisMonth(e, ym)).toList();
            BigDecimal recurringExpenses = sumEntries(expenseItems);

            BigDecimal closing = pastClosing;
            BigDecimal opening = closing.subtract(recurringIncome).add(recurringExpenses);
            pastClosing = opening;

            List<CashFlowEvent> events = buildEvents(opening, incomeItems, expenseItems,
                    List.of(), List.of(), List.of(), List.of(), List.of());
            List<DailyBreakdown> daily = buildDailyBreakdown(opening, events);

            pastProjections.add(0, new MonthProjection(ym, opening,
                    recurringIncome, BigDecimal.ZERO, BigDecimal.ZERO,
                    recurringExpenses, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, closing, events, daily));
        }

        List<MonthProjection> projections = new ArrayList<>();
        projections.addAll(pastProjections);
        projections.add(currentMonthProjection);
        projections.addAll(futureProjections);

        YearMonth selectedMonth = (month != null && !month.isBlank()) ? YearMonth.parse(month) : null;
        MonthProjection selected = selectedMonth == null ? null :
                projections.stream().filter(p -> p.month().equals(selectedMonth)).findFirst().orElse(null);

        return new ProjectionData(currentBalance, projections, selected,
                currentMonthFlow, currentMonth, monthsBefore, monthsAfter);
    }

    // ── Daily flow builder ────────────────────────────────────────────────────

    private List<DayFlow> buildCurrentMonthDailyFlow(
            BigDecimal startBalance, LocalDate today, YearMonth ym,
            List<Entry> recurring, List<EntryInstallment> installmentsDue,
            List<CreditCard> cards, Map<CreditCard, LocalDate> cardDueDates,
            List<Entry> simpleEntries) {

        Map<Integer, List<CashFlowEvent>> byDay = new TreeMap<>();

        for (Entry e : recurring) {
            int day = e.getDayOfMonth() != null
                    ? Math.min(e.getDayOfMonth(), ym.lengthOfMonth()) : 1;
            String badge = "INCOME".equals(e.getType()) ? "bg-success" : "bg-secondary";
            byDay.computeIfAbsent(day, k -> new ArrayList<>())
                    .add(new CashFlowEvent(day, e.getDescription(), e.getType(),
                            e.getAmount(), "Recorrente", badge, null));
        }

        Map<Long, BigDecimal> cardInstTotals     = new LinkedHashMap<>();
        Map<String, BigDecimal> payableByAcct    = new LinkedHashMap<>();
        Map<String, String>     payableAcctNames = new LinkedHashMap<>();
        Map<String, Integer>    payableAcctDays  = new LinkedHashMap<>();
        Map<String, BigDecimal> receivableByAcct    = new LinkedHashMap<>();
        Map<String, String>     receivableAcctNames = new LinkedHashMap<>();
        Map<String, Integer>    receivableAcctDays  = new LinkedHashMap<>();

        for (EntryInstallment inst : installmentsDue) {
            if (inst.getDueDate() == null) continue;
            Entry e = inst.getEntry();
            int day = inst.getDueDate().getDayOfMonth();
            if (e.getCreditCard() != null && "PAYABLE".equals(e.getDirection())) {
                cardInstTotals.merge(e.getCreditCard().getId(), inst.getAmount(), BigDecimal::add);
            } else if ("PAYABLE".equals(e.getDirection())) {
                String key = (e.getAccount() != null ? e.getAccount().getId() : "0") + ":" + day;
                payableByAcct.merge(key, inst.getAmount(), BigDecimal::add);
                payableAcctNames.putIfAbsent(key, e.getAccount() != null ? e.getAccount().getName() : null);
                payableAcctDays.putIfAbsent(key, day);
            } else {
                String key = (e.getAccount() != null ? e.getAccount().getId() : "0") + ":" + day;
                receivableByAcct.merge(key, inst.getAmount(), BigDecimal::add);
                receivableAcctNames.putIfAbsent(key, e.getAccount() != null ? e.getAccount().getName() : null);
                receivableAcctDays.putIfAbsent(key, day);
            }
        }

        for (Map.Entry<String, BigDecimal> me : payableByAcct.entrySet()) {
            int day = payableAcctDays.get(me.getKey());
            String acctName = payableAcctNames.get(me.getKey());
            String desc = acctName != null ? "Parcelas — " + acctName : "Parcelas";
            byDay.computeIfAbsent(day, k -> new ArrayList<>())
                    .add(new CashFlowEvent(day, desc, "EXPENSE", me.getValue(), "Parcela", "bg-danger", null));
        }

        for (Map.Entry<String, BigDecimal> me : receivableByAcct.entrySet()) {
            int day = receivableAcctDays.get(me.getKey());
            String acctName = receivableAcctNames.get(me.getKey());
            String desc = acctName != null ? "Recebíveis — " + acctName : "A Receber";
            byDay.computeIfAbsent(day, k -> new ArrayList<>())
                    .add(new CashFlowEvent(day, desc, "INCOME", me.getValue(), "A Receber", "bg-success", null));
        }

        for (CreditCard card : cards) {
            LocalDate due = cardDueDates.get(card);
            if (due == null || !YearMonth.from(due).equals(ym)) continue;
            BigDecimal cardInstExtra = cardInstTotals.getOrDefault(card.getId(), BigDecimal.ZERO);
            int day = due.getDayOfMonth();
            BigDecimal total = card.getUsedAmount().add(cardInstExtra);
            if (total.compareTo(BigDecimal.ZERO) > 0) {
                byDay.computeIfAbsent(day, k -> new ArrayList<>())
                        .add(new CashFlowEvent(day, "Fatura: " + card.getName(), "EXPENSE",
                                total, "Fatura", "bg-primary", null));
            }
        }

        for (Entry e : simpleEntries) {
            if (e.getEntryDate() == null) continue;
            int day = e.getEntryDate().getDayOfMonth();
            String badge = "INCOME".equals(e.getType()) ? "bg-success bg-opacity-75" : "bg-warning text-dark";
            byDay.computeIfAbsent(day, k -> new ArrayList<>())
                    .add(new CashFlowEvent(day, e.getDescription(), e.getType(),
                            e.getAmount(), "Avulso", badge, null));
        }

        List<DayFlow> result = new ArrayList<>();

        BigDecimal netPast = BigDecimal.ZERO;
        for (int d = 1; d < today.getDayOfMonth(); d++) {
            for (CashFlowEvent ev : byDay.getOrDefault(d, List.of())) {
                netPast = "INCOME".equals(ev.type())
                        ? netPast.add(ev.amount()) : netPast.subtract(ev.amount());
            }
        }
        BigDecimal monthOpening = startBalance.subtract(netPast);

        BigDecimal running = monthOpening;
        for (int d = 1; d < today.getDayOfMonth(); d++) {
            List<CashFlowEvent> raw = byDay.getOrDefault(d, List.of());
            if (!raw.isEmpty()) {
                BigDecimal opening = running;
                List<CashFlowEvent> events = new ArrayList<>();
                for (CashFlowEvent ev : raw) {
                    running = "INCOME".equals(ev.type())
                            ? running.add(ev.amount()) : running.subtract(ev.amount());
                    events.add(new CashFlowEvent(ev.day(), ev.description(), ev.type(),
                            ev.amount(), ev.originLabel(), ev.badgeClass(), running));
                }
                result.add(new DayFlow(ym.atDay(d), false, true, opening, events, running));
            }
        }

        running = startBalance;
        for (int d = today.getDayOfMonth(); d <= ym.lengthOfMonth(); d++) {
            LocalDate date  = ym.atDay(d);
            boolean isToday = date.equals(today);
            List<CashFlowEvent> raw = byDay.getOrDefault(d, List.of());
            BigDecimal opening = running;
            List<CashFlowEvent> events = new ArrayList<>();
            for (CashFlowEvent ev : raw) {
                running = "INCOME".equals(ev.type())
                        ? running.add(ev.amount()) : running.subtract(ev.amount());
                events.add(new CashFlowEvent(ev.day(), ev.description(), ev.type(),
                        ev.amount(), ev.originLabel(), ev.badgeClass(), running));
            }
            if (!events.isEmpty() || isToday) {
                result.add(new DayFlow(date, isToday, false, opening, events, running));
            }
        }
        return result;
    }

    // ── Event/breakdown builders ──────────────────────────────────────────────

    private List<CashFlowEvent> buildEvents(BigDecimal opening,
                                             List<Entry> incomeItems, List<Entry> expenseItems,
                                             List<EntryInstallment> receivableInsts,
                                             List<EntryInstallment> payableInsts,
                                             List<Entry> simpleIncomeItems, List<Entry> simpleExpenseItems,
                                             List<CashFlowEvent> extraEvents) {
        List<CashFlowEvent> raw = new ArrayList<>();
        for (Entry e : incomeItems) {
            raw.add(new CashFlowEvent(dayOf(e), e.getDescription(), "INCOME", e.getAmount(), "Fixo", "bg-secondary", null));
        }
        for (Entry e : expenseItems) {
            raw.add(new CashFlowEvent(dayOf(e), e.getDescription(), "EXPENSE", e.getAmount(), "Fixo", "bg-secondary", null));
        }

        Map<String, BigDecimal> recByAcct    = new LinkedHashMap<>();
        Map<String, String>     recAcctNames = new LinkedHashMap<>();
        Map<String, Integer>    recAcctDays  = new LinkedHashMap<>();
        for (EntryInstallment inst : receivableInsts) {
            int day = inst.getDueDate() != null ? inst.getDueDate().getDayOfMonth() : 1;
            String key = (inst.getEntry().getAccount() != null ? inst.getEntry().getAccount().getId() : "0") + ":" + day;
            recByAcct.merge(key, inst.getAmount(), BigDecimal::add);
            recAcctNames.putIfAbsent(key, inst.getEntry().getAccount() != null ? inst.getEntry().getAccount().getName() : null);
            recAcctDays.putIfAbsent(key, day);
        }
        for (Map.Entry<String, BigDecimal> me : recByAcct.entrySet()) {
            int day = recAcctDays.get(me.getKey());
            String acctName = recAcctNames.get(me.getKey());
            raw.add(new CashFlowEvent(day, acctName != null ? "Recebíveis — " + acctName : "A Receber",
                    "INCOME", me.getValue(), "A Receber", "bg-success", null));
        }

        Map<String, BigDecimal> payByAcct    = new LinkedHashMap<>();
        Map<String, String>     payAcctNames = new LinkedHashMap<>();
        Map<String, Integer>    payAcctDays  = new LinkedHashMap<>();
        for (EntryInstallment inst : payableInsts) {
            int day = inst.getDueDate() != null ? inst.getDueDate().getDayOfMonth() : 1;
            String key = (inst.getEntry().getAccount() != null ? inst.getEntry().getAccount().getId() : "0") + ":" + day;
            payByAcct.merge(key, inst.getAmount(), BigDecimal::add);
            payAcctNames.putIfAbsent(key, inst.getEntry().getAccount() != null ? inst.getEntry().getAccount().getName() : null);
            payAcctDays.putIfAbsent(key, day);
        }
        for (Map.Entry<String, BigDecimal> me : payByAcct.entrySet()) {
            int day = payAcctDays.get(me.getKey());
            String acctName = payAcctNames.get(me.getKey());
            raw.add(new CashFlowEvent(day, acctName != null ? "Parcelas — " + acctName : "Parcelas",
                    "EXPENSE", me.getValue(), "Parcela", "bg-danger", null));
        }

        for (Entry e : simpleIncomeItems) {
            int day = e.getEntryDate() != null ? e.getEntryDate().getDayOfMonth() : 1;
            raw.add(new CashFlowEvent(day, e.getDescription(), "INCOME", e.getAmount(), "Avulso", "bg-success bg-opacity-50", null));
        }
        for (Entry e : simpleExpenseItems) {
            int day = e.getEntryDate() != null ? e.getEntryDate().getDayOfMonth() : 1;
            raw.add(new CashFlowEvent(day, e.getDescription(), "EXPENSE", e.getAmount(), "Avulso", "bg-warning text-dark", null));
        }
        raw.addAll(extraEvents);
        raw.sort(Comparator.comparingInt(CashFlowEvent::day));

        BigDecimal running = opening;
        List<CashFlowEvent> result = new ArrayList<>();
        for (CashFlowEvent ev : raw) {
            running = "INCOME".equals(ev.type()) ? running.add(ev.amount()) : running.subtract(ev.amount());
            result.add(new CashFlowEvent(ev.day(), ev.description(), ev.type(),
                    ev.amount(), ev.originLabel(), ev.badgeClass(), running));
        }
        return result;
    }

    private List<DailyBreakdown> buildDailyBreakdown(BigDecimal opening, List<CashFlowEvent> events) {
        Map<Integer, List<CashFlowEvent>> byDay = new LinkedHashMap<>();
        for (CashFlowEvent ev : events) {
            byDay.computeIfAbsent(ev.day(), k -> new ArrayList<>()).add(ev);
        }
        List<DailyBreakdown> result = new ArrayList<>();
        BigDecimal running = opening;
        for (Map.Entry<Integer, List<CashFlowEvent>> e : byDay.entrySet()) {
            BigDecimal dayOpen = running;
            running = e.getValue().get(e.getValue().size() - 1).runningBalance();
            result.add(new DailyBreakdown(e.getKey(), dayOpen, e.getValue(), running));
        }
        return result;
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    private int dayOf(Entry e) {
        return e.getDayOfMonth() != null ? e.getDayOfMonth() : 1;
    }

    private BigDecimal sumEntries(List<Entry> items) {
        return items.stream().map(Entry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private YearMonth billingMonthFor(CreditCard card, LocalDate chargeDate) {
        int closing = card.getClosingDay();
        int due     = card.getDueDay();
        YearMonth closingMonth = chargeDate.getDayOfMonth() <= closing
                ? YearMonth.from(chargeDate)
                : YearMonth.from(chargeDate).plusMonths(1);
        return due > closing ? closingMonth : closingMonth.plusMonths(1);
    }

    private LocalDate cardNextDueDate(CreditCard card) {
        YearMonth current = YearMonth.now();
        int due = card.getDueDay();
        return current.atDay(Math.min(due, current.lengthOfMonth()));
    }
}
