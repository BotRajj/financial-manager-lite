package com.financialmanager.lite.controller;

import com.financialmanager.lite.dto.*;
import com.financialmanager.lite.service.EntryService;
import com.financialmanager.lite.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

@Controller
@RequestMapping("/entries")
@RequiredArgsConstructor
public class EntryWebController {

    private final EntryService entryService;
    private final UserService userService;

    @PostMapping("/create")
    public String create(@AuthenticationPrincipal UserDetails principal,
                         @ModelAttribute EntryCreateFormDTO form,
                         RedirectAttributes ra) {
        var user = userService.findByEmail(principal.getUsername());
        Long accId     = parseId(form.getAccountId());
        Long cardId    = parseId(form.getCreditCardId());
        Long recvAccId = parseId(form.getReceiveAccountId());

        return switch (form.getMode()) {
            case "RECURRING" -> {
                String freq = form.getFrequency() != null ? form.getFrequency() : "MONTHLY";
                int dom = form.getDayOfMonth() != null ? form.getDayOfMonth() : 1;

                entryService.createRecurring(user, RecurringEntryDTO.builder()
                        .description(form.getDescription()).amount(form.getAmount())
                        .type(form.getType()).category(form.getCategory())
                        .direction("STANDARD")
                        .frequency(freq).dayOfMonth(dom).monthOfYear(form.getMonthOfYear())
                        .accountId(accId).creditCardId(cardId)
                        .autoApply(form.isAutoApply())
                        .build());

                if (form.isMeDevem() && "EXPENSE".equals(form.getType())) {
                    entryService.createRecurring(user, RecurringEntryDTO.builder()
                            .description(form.getDescription()).amount(form.getAmount())
                            .type("INCOME").category(form.getCategory())
                            .direction("RECEIVABLE").personName(form.getPersonName())
                            .frequency(freq).dayOfMonth(dom).monthOfYear(form.getMonthOfYear())
                            .accountId(recvAccId != null ? recvAccId : accId).creditCardId(cardId)
                            .autoApply(form.isAutoApply())
                            .build());
                }
                ra.addFlashAttribute("success", "Recorrência cadastrada!");
                yield "redirect:/movimentacoes?tab=recorrentes";
            }
            case "INSTALLMENT" -> {
                int total = form.getTotalInstallments() != null ? form.getTotalInstallments() : 1;
                LocalDate baseDate = parseDate(form.getInstallmentStartDate());
                BigDecimal intRate = scalePercent(form.getInterestRate());
                BigDecimal feeRate = scalePercent(form.getLateFeeRate());

                entryService.createInstallment(user, InstallmentEntryDTO.builder()
                        .description(form.getDescription()).amount(form.getAmount())
                        .type(form.getType()).category(form.getCategory())
                        .direction("PAYABLE")
                        .personName(form.isMeDevem() ? null : form.getPersonName())
                        .totalInstallments(total)
                        .dayOfMonth(form.getDayOfMonth())
                        .accountId(accId).creditCardId(cardId)
                        .customAmounts(form.getCustomAmounts())
                        .baseDate(baseDate)
                        .interestRate(intRate).lateFeeRate(feeRate)
                        .alreadyPaid(form.getAlreadyPaidInstallments())
                        .build());

                if (form.isMeDevem() && "EXPENSE".equals(form.getType())) {
                    entryService.createInstallment(user, InstallmentEntryDTO.builder()
                            .description(form.getDescription()).amount(form.getAmount())
                            .type("INCOME").category(form.getCategory())
                            .direction("RECEIVABLE").personName(form.getPersonName())
                            .totalInstallments(total)
                            .dayOfMonth(form.getDayOfMonth())
                            .accountId(recvAccId != null ? recvAccId : accId).creditCardId(cardId)
                            .baseDate(baseDate)
                            .build());
                }
                ra.addFlashAttribute("success", "Parcelamento registrado!");
                yield "redirect:/movimentacoes?tab=parcelas";
            }
            default -> {
                String date = (form.getEntryDate() != null && !form.getEntryDate().isBlank())
                        ? form.getEntryDate() : LocalDate.now().toString();

                entryService.createSimple(user, SimpleEntryDTO.builder()
                        .description(form.getDescription()).amount(form.getAmount())
                        .type(form.getType()).category(form.getCategory())
                        .entryDate(LocalDate.parse(date))
                        .accountId(accId).creditCardId(cardId)
                        .notes(form.getNotes())
                        .invoiceMonth(form.getInvoiceMonth())
                        .build());

                if (form.isMeDevem() && "EXPENSE".equals(form.getType())) {
                    LocalDate rd = parseDate(form.getReceiveDate());
                    if (rd == null) rd = LocalDate.parse(date);
                    entryService.createInstallment(user, InstallmentEntryDTO.builder()
                            .description(form.getDescription()).amount(form.getAmount())
                            .type("INCOME").category(form.getCategory())
                            .direction("RECEIVABLE").personName(form.getPersonName())
                            .totalInstallments(1)
                            .dayOfMonth(rd.getDayOfMonth())
                            .accountId(recvAccId != null ? recvAccId : accId).creditCardId(cardId)
                            .baseDate(rd)
                            .build());
                }
                ra.addFlashAttribute("success", "Movimentação registrada!");
                yield "redirect:/movimentacoes?tab=lancamentos";
            }
        };
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id,
                       @AuthenticationPrincipal UserDetails principal,
                       @ModelAttribute EntryEditFormDTO form,
                       RedirectAttributes ra) {
        var user = userService.findByEmail(principal.getUsername());
        entryService.updateSimple(id, user, SimpleEntryDTO.builder()
                .description(form.getDescription()).amount(form.getAmount())
                .type(form.getType()).category(form.getCategory())
                .entryDate(LocalDate.parse(form.getEntryDate()))
                .accountId(parseId(form.getAccountId()))
                .creditCardId(parseId(form.getCreditCardId()))
                .notes(form.getNotes())
                .invoiceMonth(form.getInvoiceMonth())
                .build());
        ra.addFlashAttribute("success", "Movimentação atualizada!");
        return "redirect:/movimentacoes?tab=lancamentos";
    }

    @PostMapping("/{id}/edit-installment")
    public String editInstallment(@PathVariable Long id,
                                  @AuthenticationPrincipal UserDetails principal,
                                  @ModelAttribute InstallmentEditFormDTO form,
                                  RedirectAttributes ra) {
        var user = userService.findByEmail(principal.getUsername());
        entryService.updateInstallment(id, user, InstallmentEntryDTO.builder()
                .description(form.getDescription()).amount(form.getAmount())
                .type(form.getType()).category(form.getCategory())
                .personName(form.getPersonName())
                .accountId(parseId(form.getAccountId()))
                .creditCardId(parseId(form.getCreditCardId()))
                .dayOfMonth(form.getDayOfMonth())
                .baseDate(parseDate(form.getInstallmentStartDate()))
                .build());
        ra.addFlashAttribute("success", "Parcelamento atualizado!");
        return "redirect:/movimentacoes?tab=" + form.getRedirectTab();
    }

    @PostMapping("/{id}/move-invoice")
    public String moveInvoice(@PathVariable Long id,
                              @RequestParam String invoiceMonth,
                              @RequestParam(required = false) Long cardId,
                              @RequestParam(defaultValue = "0") int monthOffset,
                              @AuthenticationPrincipal UserDetails principal,
                              RedirectAttributes ra) {
        var user = userService.findByEmail(principal.getUsername());
        entryService.moveInvoice(id, user, invoiceMonth);
        ra.addFlashAttribute("success", "Lançamento movido de fatura!");
        return "redirect:/movimentacoes?tab=fatura&cardId=" + cardId + "&monthOffset=" + monthOffset;
    }

    private Long parseId(String val) {
        if (val == null || val.isBlank()) return null;
        try { return Long.parseLong(val); } catch (NumberFormatException e) { return null; }
    }

    private LocalDate parseDate(String val) {
        if (val == null || val.isBlank()) return null;
        return LocalDate.parse(val);
    }

    private BigDecimal scalePercent(BigDecimal val) {
        if (val == null) return null;
        return val.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_EVEN);
    }
}
