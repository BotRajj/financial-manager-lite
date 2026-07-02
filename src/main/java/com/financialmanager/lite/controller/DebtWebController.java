package com.financialmanager.lite.controller;

import com.financialmanager.lite.dto.InstallmentEntryDTO;
import com.financialmanager.lite.service.EntryService;
import com.financialmanager.lite.service.InterestProjectionService;
import com.financialmanager.lite.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/debts")
@RequiredArgsConstructor
public class DebtWebController {

    private final EntryService entryService;
    private final InterestProjectionService projectionService;
    private final UserService userService;

    @GetMapping
    public String list() { return "redirect:/movimentacoes?tab=parcelas"; }

    @PostMapping("/create")
    public String create(@AuthenticationPrincipal UserDetails principal,
                         @RequestParam String description,
                         @RequestParam BigDecimal amount,
                         @RequestParam String type,
                         @RequestParam(required = false) String category,
                         @RequestParam(defaultValue = "PAYABLE") String direction,
                         @RequestParam(required = false) String personName,
                         @RequestParam int totalInstallments,
                         @RequestParam(required = false) BigDecimal installmentAmount,
                         @RequestParam(required = false) Integer dayOfMonth,
                         @RequestParam(required = false) Long accountId,
                         @RequestParam(required = false) Long creditCardId,
                         @RequestParam(required = false) List<BigDecimal> customAmounts,
                         @RequestParam(required = false) BigDecimal contractRate,
                         @RequestParam(required = false) BigDecimal interestRate,
                         @RequestParam(required = false) BigDecimal lateFeeRate,
                         @RequestParam(required = false) String startDate,
                         @RequestParam(defaultValue = "0") int paidInstallments,
                         RedirectAttributes ra) {
        var user = userService.findByEmail(principal.getUsername());
        LocalDate baseDate = (startDate != null && !startDate.isBlank()) ? LocalDate.parse(startDate) : null;
        BigDecimal intRate = interestRate != null
                ? interestRate.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_EVEN) : null;
        BigDecimal feeRate = lateFeeRate != null
                ? lateFeeRate.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_EVEN) : null;
        entryService.createInstallment(user, InstallmentEntryDTO.builder()
                .description(description).amount(amount).type(type).category(category)
                .direction(direction).personName(personName)
                .totalInstallments(totalInstallments).installmentAmount(installmentAmount)
                .dayOfMonth(dayOfMonth).accountId(accountId).creditCardId(creditCardId)
                .customAmounts(customAmounts).baseDate(baseDate)
                .contractRate(contractRate).interestRate(intRate).lateFeeRate(feeRate)
                .alreadyPaid(paidInstallments)
                .build());
        String msg = "RECEIVABLE".equals(direction) ? "Receita parcelada registrada!" : "Parcelamento registrado!";
        ra.addFlashAttribute("success", msg);
        return "redirect:/movimentacoes?tab=parcelas";
    }

    @PostMapping("/{id}/pay")
    public String pay(@PathVariable Long id,
                      @AuthenticationPrincipal UserDetails principal,
                      RedirectAttributes ra) {
        var user = userService.findByEmail(principal.getUsername());
        entryService.payNextInstallment(id, user);
        ra.addFlashAttribute("success", "Parcela registrada!");
        return "redirect:/movimentacoes?tab=parcelas";
    }

    @PostMapping("/installments/{id}/confirm")
    public String confirmInstallment(@PathVariable Long id,
                                     @RequestParam(defaultValue = "parcelas") String redirectTab,
                                     @RequestParam(required = false) Long accountId,
                                     @AuthenticationPrincipal UserDetails principal,
                                     RedirectAttributes ra) {
        var user = userService.findByEmail(principal.getUsername());
        entryService.confirmInstallment(id, user, accountId);
        ra.addFlashAttribute("success", "Parcela confirmada!");
        return "redirect:/movimentacoes?tab=" + redirectTab;
    }

    @PostMapping("/{id}/mark-default")
    public String markDefault(@PathVariable Long id,
                              @AuthenticationPrincipal UserDetails principal,
                              @RequestParam BigDecimal interestRate,
                              @RequestParam BigDecimal lateFeeRate,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate defaultSinceDate,
                              RedirectAttributes ra) {
        var user = userService.findByEmail(principal.getUsername());
        BigDecimal rate = interestRate.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_EVEN);
        BigDecimal fee  = lateFeeRate.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_EVEN);
        entryService.markDefault(id, user, rate, fee, defaultSinceDate);
        ra.addFlashAttribute("success", "Inadimplência confirmada. Projeção de juros ativada.");
        return "redirect:/movimentacoes?tab=parcelas";
    }

    @GetMapping("/{id}/interest-projection")
    @ResponseBody
    public Map<String, Object> interestProjection(@PathVariable Long id,
                                                   @AuthenticationPrincipal UserDetails principal,
                                                   @RequestParam(defaultValue = "6") int months) {
        var user = userService.findByEmail(principal.getUsername());
        return entryService.findInstallmentByIdAndUser(id, user)
                .filter(e -> "IN_DEFAULT".equals(e.getStatus()))
                .map(entry -> {
                    var result = projectionService.project(entry, months);
                    Map<String, Object> response = new LinkedHashMap<>();
                    response.put("description",     entry.getDescription());
                    response.put("principal",        result.principal());
                    response.put("lateFeeAmount",    result.lateFeeAmount());
                    response.put("baseAmount",       result.baseAmount());
                    response.put("defaultSinceDate", result.defaultSinceDate().toString());
                    response.put("monthlyRate",      result.monthlyRate());
                    Map<String, BigDecimal> proj = new LinkedHashMap<>();
                    result.projection().forEach((k, v) -> proj.put(k.toString(), v));
                    response.put("projection", proj);
                    return response;
                })
                .orElse(Map.of("error", "Entrada não encontrada ou não está em inadimplência."));
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @AuthenticationPrincipal UserDetails principal,
                         RedirectAttributes ra) {
        var user = userService.findByEmail(principal.getUsername());
        entryService.deleteInstallment(id, user);
        ra.addFlashAttribute("success", "Registro removido.");
        return "redirect:/movimentacoes?tab=parcelas";
    }
}
