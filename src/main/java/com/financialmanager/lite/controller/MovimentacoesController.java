package com.financialmanager.lite.controller;

import com.financialmanager.lite.model.CreditCard;
import com.financialmanager.lite.model.Entry;
import com.financialmanager.lite.model.EntryInstallment;
import com.financialmanager.lite.model.User;
import com.financialmanager.lite.service.AccountService;
import com.financialmanager.lite.service.CreditCardService;
import com.financialmanager.lite.service.EntryService;
import com.financialmanager.lite.service.InterestProjectionService;
import com.financialmanager.lite.service.UserCategoryService;
import com.financialmanager.lite.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.financialmanager.lite.service.InterestProjectionService.InstallmentFeeResult;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/movimentacoes")
@RequiredArgsConstructor
public class MovimentacoesController {

    private final EntryService entryService;
    private final AccountService accountService;
    private final CreditCardService creditCardService;
    private final UserCategoryService categoryService;
    private final UserService userService;
    private final InterestProjectionService interestProjectionService;

    private static final List<String> VALID_TABS = List.of("lancamentos", "recorrentes", "parcelas", "fatura", "medevem");

    @GetMapping
    public String index(@AuthenticationPrincipal UserDetails principal,
                        @RequestParam(defaultValue = "lancamentos") String tab,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(required = false) String filter,
                        @RequestParam(required = false) Long cardId,
                        @RequestParam(defaultValue = "0") int monthOffset,
                        Model model) {
        User user = userService.findByEmail(principal.getUsername());
        if (!VALID_TABS.contains(tab)) tab = "lancamentos";

        model.addAttribute("accounts", accountService.findActiveByUser(user));
        model.addAttribute("cards", creditCardService.findActiveByUser(user));
        model.addAttribute("categories", categoryService.findAll(user));
        model.addAttribute("tab", tab);
        model.addAttribute("filter", filter != null ? filter : "");
        model.addAttribute("monthOffset", monthOffset);

        switch (tab) {
            case "recorrentes" -> loadRecorrentes(user, model);
            case "parcelas"    -> loadParcelas(user, model);
            case "fatura"      -> loadFatura(user, cardId, monthOffset, model);
            case "medevem"     -> loadMeDevem(user, model);
            default            -> loadLancamentos(user, filter, page, model);
        }

        return "movimentacoes";
    }

    private void loadLancamentos(User user, String filter, int page, Model model) {
        var entries = resolveFilter(user, filter, page);
        model.addAttribute("lancamentos", entries);
    }

    private void loadRecorrentes(User user, Model model) {
        var all = entryService.findRecurringActiveByUser(user);
        model.addAttribute("incomeRecurring",  all.stream().filter(e -> "INCOME".equals(e.getType())).toList());
        model.addAttribute("expenseRecurring", all.stream().filter(e -> "EXPENSE".equals(e.getType())).toList());

        YearMonth current = YearMonth.now();
        List<Entry> applyPreview = all.stream()
                .filter(e -> entryService.appliesThisMonth(e, current))
                .filter(e -> !current.toString().equals(e.getLastAppliedMonth()))
                .toList();
        model.addAttribute("applyPreview", applyPreview);
        model.addAttribute("currentMonth", current);
    }

    private void loadParcelas(User user, Model model) {
        var all = entryService.findInstallmentsByUser(user);
        var payable = all.stream()
                .filter(e -> "PAYABLE".equals(e.getDirection())
                        || ("STANDARD".equals(e.getDirection()) && "EXPENSE".equals(e.getType())))
                .toList();

        var byCard = payable.stream()
                .filter(e -> e.getCreditCard() != null)
                .collect(Collectors.groupingBy(Entry::getCreditCard, LinkedHashMap::new, Collectors.toList()));

        var byAccount = payable.stream()
                .filter(e -> e.getCreditCard() == null && e.getAccount() != null)
                .collect(Collectors.groupingBy(Entry::getAccount, LinkedHashMap::new, Collectors.toList()));

        var noLink = payable.stream()
                .filter(e -> e.getCreditCard() == null && e.getAccount() == null)
                .toList();

        Map<Long, InstallmentFeeResult> installmentFees = new HashMap<>();
        for (Entry e : payable) {
            for (var inst : e.getInstallments()) {
                installmentFees.put(inst.getId(), interestProjectionService.calculateInstallmentFee(inst, e));
            }
        }

        model.addAttribute("cardInstallmentGroups", byCard);
        model.addAttribute("accountInstallmentGroups", byAccount);
        model.addAttribute("noLinkInstallments", noLink);
        model.addAttribute("hasPayableInstallments", !payable.isEmpty());
        model.addAttribute("installmentFees", installmentFees);
    }

    private void loadMeDevem(User user, Model model) {
        var allInst = entryService.findInstallmentsByUser(user);
        var meDevemInstallments = allInst.stream()
                .filter(e -> "RECEIVABLE".equals(e.getDirection())).toList();
        model.addAttribute("meDevemInstallments", meDevemInstallments);

        var allRec = entryService.findRecurringActiveByUser(user);
        var meDevemRecurring = allRec.stream()
                .filter(e -> "RECEIVABLE".equals(e.getDirection())).toList();
        model.addAttribute("meDevemRecurring", meDevemRecurring);

        BigDecimal totalPending = meDevemInstallments.stream()
                .map(e -> e.getStatus() != null && "SETTLED".equals(e.getStatus())
                        ? BigDecimal.ZERO : e.getRemainingAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("meDevemTotal", totalPending);
    }

    private void loadFatura(User user, Long cardId, int monthOffset, Model model) {
        if (cardId == null) return;
        creditCardService.findByIdWithBank(cardId, user).ifPresent(card -> {
            LocalDate[] period = card.billingPeriod(monthOffset);
            String invoiceKey = YearMonth.from(period[1]).toString();
            var txList  = entryService.findSimpleByCreditCardAndInvoice(card, invoiceKey, period[0], period[1]);
            BigDecimal txTotal = entryService.sumSimpleByCreditCardAndInvoice(card, invoiceKey, period[0], period[1]);
            Map<Long, EntryInstallment> periodInstallment = new LinkedHashMap<>();
            List<Entry> parcelas = entryService.findActiveInstallmentsByCreditCard(card)
                    .stream()
                    .filter(e -> {
                        Optional<EntryInstallment> match = e.getInstallments().stream()
                                .filter(inst -> inst.getDueDate() != null
                                        && !inst.getDueDate().isBefore(period[0])
                                        && !inst.getDueDate().isAfter(period[1]))
                                .findFirst();
                        if (match.isPresent()) {
                            periodInstallment.put(e.getId(), match.get());
                            return true;
                        }
                        return false;
                    })
                    .sorted((a, b) -> {
                        int da = a.getDayOfMonth() != null ? a.getDayOfMonth() : Integer.MAX_VALUE;
                        int db = b.getDayOfMonth() != null ? b.getDayOfMonth() : Integer.MAX_VALUE;
                        return Integer.compare(da, db);
                    })
                    .toList();
            BigDecimal parcelasTotal = parcelas.stream()
                    .filter(e -> {
                        EntryInstallment inst = periodInstallment.get(e.getId());
                        return inst == null || !Boolean.TRUE.equals(inst.getConfirmed());
                    })
                    .map(e -> {
                        EntryInstallment inst = periodInstallment.get(e.getId());
                        return inst != null ? inst.getAmount() : e.getInstallmentValue();
                    })
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal installmentsCommitted = parcelas.stream()
                    .map(Entry::getRemainingAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal adjustedAvailableLimit = card.getAvailableLimit().subtract(installmentsCommitted)
                    .max(BigDecimal.ZERO);
            BigDecimal total = txTotal.add(parcelasTotal);
            BigDecimal minPayment = total.multiply(card.getMinimumPaymentRate())
                    .setScale(2, RoundingMode.HALF_EVEN);

            Long suggestedAccountId = null;
            if (card.getBank() != null) {
                @SuppressWarnings("unchecked")
                var accounts = (List<com.financialmanager.lite.model.Account>) model.getAttribute("accounts");
                if (accounts != null) {
                    suggestedAccountId = accounts.stream()
                            .filter(a -> a.getBank() != null
                                    && a.getBank().getId().equals(card.getBank().getId()))
                            .map(com.financialmanager.lite.model.Account::getId)
                            .findFirst().orElse(null);
                }
            }

            model.addAttribute("invoiceCard", card);
            model.addAttribute("invoiceAdjustedAvailableLimit", adjustedAvailableLimit);
            model.addAttribute("invoiceTransactions", txList);
            model.addAttribute("invoiceParcelas", parcelas);
            model.addAttribute("periodInstallment", periodInstallment);
            model.addAttribute("invoiceTotal", total);
            model.addAttribute("invoiceMinPayment", minPayment);
            model.addAttribute("invoicePeriodStart", period[0]);
            model.addAttribute("invoicePeriodEnd", period[1]);
            model.addAttribute("invoiceDueDate", period[2]);
            boolean isOverdue = LocalDate.now().isAfter(period[2]);
            boolean defaultConfirmed = isOverdue &&
                    entryService.existsLateChargeForCard(card, "Multa atraso:%", period[2]);
            model.addAttribute("invoiceIsOverdue", isOverdue);
            model.addAttribute("invoiceDefaultConfirmed", defaultConfirmed);
            model.addAttribute("suggestedAccountId", suggestedAccountId);
        });
        model.addAttribute("selectedCardId", cardId);
    }

    private org.springframework.data.domain.Page<Entry> resolveFilter(User user, String filter, int page) {
        if (filter != null && filter.startsWith("acc_")) {
            try { return entryService.findSimpleByUserAndAccount(user, Long.parseLong(filter.substring(4)), page); }
            catch (NumberFormatException ignored) {}
        } else if (filter != null && filter.startsWith("card_")) {
            try { return entryService.findSimpleByUserAndCreditCard(user, Long.parseLong(filter.substring(5)), page); }
            catch (NumberFormatException ignored) {}
        }
        return entryService.findSimpleByUser(user, page);
    }
}
