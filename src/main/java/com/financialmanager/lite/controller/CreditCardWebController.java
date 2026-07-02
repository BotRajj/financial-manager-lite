package com.financialmanager.lite.controller;

import com.financialmanager.lite.model.User;
import com.financialmanager.lite.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Controller
@RequestMapping("/credit-cards")
@RequiredArgsConstructor
public class CreditCardWebController {

    private final CreditCardService creditCardService;
    private final AccountService accountService;
    private final BankService bankService;
    private final UserService userService;

    @GetMapping
    public String list(@AuthenticationPrincipal UserDetails principal, Model model) {
        User user = userService.findByEmail(principal.getUsername());
        var cards   = creditCardService.findActiveByUser(user);
        var summary = creditCardService.getInvoicePlanSummary(cards);
        model.addAttribute("cards", cards);
        model.addAttribute("invoicePlanCount", summary.counts());
        model.addAttribute("invoicePlanMonthly", summary.monthly());
        model.addAttribute("invoicePlanRemaining", summary.remaining());
        model.addAttribute("adjustedAvailableByCardId", summary.adjustedAvailable());
        model.addAttribute("inactiveCards", creditCardService.findInactiveByUser(user));
        model.addAttribute("accounts", accountService.findActiveByUser(user));
        model.addAttribute("banks", bankService.findActiveByUser(user));
        return "credit-cards/list";
    }

    @PostMapping("/create")
    public String create(@AuthenticationPrincipal UserDetails principal,
                         @RequestParam String name,
                         @RequestParam BigDecimal creditLimit,
                         @RequestParam int closingDay,
                         @RequestParam int dueDay,
                         @RequestParam(required = false) String bankId,
                         @RequestParam(defaultValue = "0.15") BigDecimal minimumPaymentRate,
                         @RequestParam(defaultValue = "0.15") BigDecimal rotatingCreditRate,
                         @RequestParam(defaultValue = "0.1590") BigDecimal installmentRate,
                         @RequestParam(defaultValue = "0.0200") BigDecimal latePaymentFine,
                         @RequestParam(defaultValue = "0.0100") BigDecimal latePaymentInterest,
                         @RequestParam(defaultValue = "0.000082") BigDecimal iofDailyRate,
                         @RequestParam(defaultValue = "0.0038") BigDecimal iofAdditionalRate,
                         @RequestParam(required = false) List<String> cardLastFour,
                         @RequestParam(required = false) List<String> cardType,
                         RedirectAttributes ra) {
        User user = userService.findByEmail(principal.getUsername());
        creditCardService.create(user, name, creditLimit, closingDay, dueDay, parseId(bankId),
                minimumPaymentRate, rotatingCreditRate, installmentRate,
                latePaymentFine, latePaymentInterest, iofDailyRate, iofAdditionalRate,
                cardLastFour, cardType);
        ra.addFlashAttribute("success", "Cartão adicionado!");
        return "redirect:/credit-cards";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id,
                       @AuthenticationPrincipal UserDetails principal,
                       @RequestParam String name,
                       @RequestParam BigDecimal creditLimit,
                       @RequestParam int closingDay,
                       @RequestParam int dueDay,
                       @RequestParam(required = false) String bankId,
                       @RequestParam(defaultValue = "0.15") BigDecimal minimumPaymentRate,
                       @RequestParam(defaultValue = "0.15") BigDecimal rotatingCreditRate,
                       @RequestParam(defaultValue = "0.1590") BigDecimal installmentRate,
                       @RequestParam(defaultValue = "0.0200") BigDecimal latePaymentFine,
                       @RequestParam(defaultValue = "0.0100") BigDecimal latePaymentInterest,
                       @RequestParam(defaultValue = "0.000082") BigDecimal iofDailyRate,
                       @RequestParam(defaultValue = "0.0038") BigDecimal iofAdditionalRate,
                       @RequestParam(required = false) List<String> cardLastFour,
                       @RequestParam(required = false) List<String> cardType,
                       RedirectAttributes ra) {
        User user = userService.findByEmail(principal.getUsername());
        creditCardService.update(id, user, name, creditLimit, closingDay, dueDay, parseId(bankId),
                minimumPaymentRate, rotatingCreditRate, installmentRate,
                latePaymentFine, latePaymentInterest, iofDailyRate, iofAdditionalRate,
                cardLastFour, cardType);
        ra.addFlashAttribute("success", "Cartão atualizado com sucesso!");
        return "redirect:/credit-cards";
    }

    @PostMapping("/{id}/reactivate")
    public String reactivate(@PathVariable Long id,
                             @AuthenticationPrincipal UserDetails principal,
                             RedirectAttributes ra) {
        User user = userService.findByEmail(principal.getUsername());
        creditCardService.reactivate(id, user);
        ra.addFlashAttribute("success", "Cartão reativado.");
        return "redirect:/credit-cards";
    }

    @GetMapping("/{id}/invoice")
    public String invoice(@PathVariable Long id) {
        return "redirect:/movimentacoes?tab=fatura&cardId=" + id;
    }

    @PostMapping("/{id}/pay-invoice")
    public String payInvoice(@PathVariable Long id,
                             @RequestParam BigDecimal amount,
                             @RequestParam(required = false) Long accountId,
                             @AuthenticationPrincipal UserDetails principal,
                             RedirectAttributes ra) {
        User user = userService.findByEmail(principal.getUsername());
        BigDecimal rotatingInterest = creditCardService.payInvoice(id, user, amount, accountId);
        if (rotatingInterest.compareTo(BigDecimal.ZERO) > 0) {
            ra.addFlashAttribute("success",
                    "Pagamento registrado! Juros rotativos de R$ "
                    + rotatingInterest.setScale(2, RoundingMode.HALF_EVEN).toPlainString().replace(".", ",")
                    + " aplicados sobre o saldo restante.");
        } else {
            ra.addFlashAttribute("success", "Fatura paga com sucesso!");
        }
        return "redirect:/movimentacoes?tab=fatura&cardId=" + id;
    }

    @PostMapping("/{id}/link-bank")
    public String linkBank(@PathVariable Long id,
                           @RequestParam(required = false) Long bankId,
                           @AuthenticationPrincipal UserDetails principal,
                           RedirectAttributes ra) {
        User user = userService.findByEmail(principal.getUsername());
        creditCardService.linkBank(id, bankId, user);
        ra.addFlashAttribute("success", bankId != null ? "Cartão vinculado ao banco!" : "Vínculo removido.");
        return "redirect:/banks";
    }

    @PostMapping("/{id}/installment-invoice")
    public String installmentInvoice(@PathVariable Long id,
                                     @RequestParam BigDecimal entrada,
                                     @RequestParam int totalInstallments,
                                     @RequestParam int dayOfMonth,
                                     @RequestParam(required = false) Long accountId,
                                     @AuthenticationPrincipal UserDetails principal,
                                     RedirectAttributes ra) {
        User user = userService.findByEmail(principal.getUsername());
        var cardOpt = creditCardService.findByIdAndUser(id, user);
        if (cardOpt.isEmpty()) {
            ra.addFlashAttribute("error", "Cartão não encontrado.");
            return "redirect:/credit-cards";
        }
        if (cardOpt.get().getUsedAmount().compareTo(BigDecimal.ZERO) <= 0) {
            ra.addFlashAttribute("error", "Fatura zerada, nenhum saldo a parcelar.");
            return "redirect:/credit-cards";
        }
        creditCardService.installmentInvoice(id, user, entrada, totalInstallments, dayOfMonth, accountId);
        ra.addFlashAttribute("success", "Parcelamento criado! " + totalInstallments + " parcelas adicionadas em A Pagar.");
        return "redirect:/credit-cards";
    }

    @PostMapping("/{id}/mark-invoice-default")
    public String markInvoiceDefault(@PathVariable Long id,
                                     @RequestParam BigDecimal invoiceTotal,
                                     @RequestParam BigDecimal latePaymentFineRate,
                                     @RequestParam BigDecimal latePaymentInterestRate,
                                     @RequestParam(required = false) String dueDate,
                                     @RequestParam(defaultValue = "0") int monthOffset,
                                     @RequestParam(defaultValue = "DEFAULT") String paymentMode,
                                     @RequestParam(required = false) String paymentDate,
                                     @RequestParam(required = false) Long accountId,
                                     @AuthenticationPrincipal UserDetails principal,
                                     RedirectAttributes ra) {
        User user = userService.findByEmail(principal.getUsername());

        if ("PAID".equals(paymentMode)) {
            BigDecimal[] totals = creditCardService.markInvoiceDefaultPaid(
                    id, user, invoiceTotal, latePaymentFineRate, latePaymentInterestRate,
                    dueDate, accountId, paymentDate);
            BigDecimal totalCharges = totals[0].add(totals[1]);
            if (totalCharges.compareTo(BigDecimal.ZERO) > 0) {
                ra.addFlashAttribute("success",
                        "Pagamento registrado! Encargos do atraso (R$ "
                        + totalCharges.setScale(2, RoundingMode.HALF_EVEN).toPlainString().replace(".", ",")
                        + ") lançados na fatura.");
            } else {
                ra.addFlashAttribute("success", "Pagamento registrado! Sem encargos — fatura paga no prazo.");
            }
        } else {
            creditCardService.markInvoiceDefaultOverdue(
                    id, user, invoiceTotal, latePaymentFineRate, latePaymentInterestRate, dueDate);
            ra.addFlashAttribute("success", "Encargos de inadimplência registrados na fatura.");
        }
        return "redirect:/movimentacoes?tab=fatura&cardId=" + id + "&monthOffset=" + monthOffset;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @AuthenticationPrincipal UserDetails principal,
                         RedirectAttributes ra) {
        User user = userService.findByEmail(principal.getUsername());
        creditCardService.deactivate(id, user);
        ra.addFlashAttribute("success", "Cartão desativado.");
        return "redirect:/banks";
    }

    private Long parseId(String val) {
        if (val == null || val.isBlank()) return null;
        try { return Long.parseLong(val); } catch (NumberFormatException e) { return null; }
    }
}
