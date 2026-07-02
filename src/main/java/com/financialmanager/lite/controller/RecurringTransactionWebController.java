package com.financialmanager.lite.controller;

import com.financialmanager.lite.dto.RecurringEntryDTO;
import com.financialmanager.lite.service.EntryService;
import com.financialmanager.lite.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/recurring")
@RequiredArgsConstructor
public class RecurringTransactionWebController {

    private final EntryService entryService;
    private final UserService userService;

    @GetMapping
    public String list() { return "redirect:/movimentacoes?tab=recorrentes"; }

    @PostMapping("/create")
    public String create(@AuthenticationPrincipal UserDetails principal,
                         @RequestParam String description,
                         @RequestParam BigDecimal amount,
                         @RequestParam String type,
                         @RequestParam(required = false) String category,
                         @RequestParam String frequency,
                         @RequestParam(defaultValue = "1") int dayOfMonth,
                         @RequestParam(required = false) Integer monthOfYear,
                         @RequestParam(required = false) Long accountId,
                         @RequestParam(required = false) Long creditCardId,
                         @RequestParam(defaultValue = "false") boolean autoApply,
                         RedirectAttributes ra) {
        var user = userService.findByEmail(principal.getUsername());
        entryService.createRecurring(user, RecurringEntryDTO.builder()
                .description(description).amount(amount).type(type).category(category)
                .frequency(frequency).dayOfMonth(dayOfMonth).monthOfYear(monthOfYear)
                .accountId(accountId).creditCardId(creditCardId)
                .autoApply(autoApply)
                .build());
        ra.addFlashAttribute("success", "Recorrência cadastrada!");
        return "redirect:/movimentacoes?tab=recorrentes";
    }

    @PostMapping("/apply-month")
    public String applyMonth(@AuthenticationPrincipal UserDetails principal,
                             @RequestParam(required = false) List<Long> ids,
                             RedirectAttributes ra) {
        var user = userService.findByEmail(principal.getUsername());
        if (ids == null || ids.isEmpty()) {
            ra.addFlashAttribute("error", "Nenhum item selecionado para lançar.");
            return "redirect:/movimentacoes?tab=recorrentes";
        }
        int count = entryService.applyRecurringMonth(user, ids);
        ra.addFlashAttribute("success", count + " transação(ões) lançada(s).");
        return "redirect:/movimentacoes?tab=recorrentes";
    }

    @PostMapping("/{id}/apply-single")
    public String applySingle(@PathVariable Long id,
                              @RequestParam(required = false) String customDate,
                              @AuthenticationPrincipal UserDetails principal,
                              RedirectAttributes ra) {
        var user = userService.findByEmail(principal.getUsername());
        entryService.applySingleRecurring(id, user, customDate);
        ra.addFlashAttribute("success", "Recorrência lançada!");
        return "redirect:/movimentacoes?tab=recorrentes";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id,
                       @AuthenticationPrincipal UserDetails principal,
                       @RequestParam String description,
                       @RequestParam BigDecimal amount,
                       @RequestParam(required = false) String category,
                       @RequestParam String frequency,
                       @RequestParam(defaultValue = "1") int dayOfMonth,
                       @RequestParam(required = false) Integer monthOfYear,
                       @RequestParam(required = false) Long accountId,
                       @RequestParam(required = false) Long creditCardId,
                       @RequestParam(defaultValue = "false") boolean autoApply,
                       RedirectAttributes ra) {
        var user = userService.findByEmail(principal.getUsername());
        entryService.updateRecurring(id, user, RecurringEntryDTO.builder()
                .description(description).amount(amount).category(category)
                .frequency(frequency).dayOfMonth(dayOfMonth).monthOfYear(monthOfYear)
                .accountId(accountId).creditCardId(creditCardId)
                .autoApply(autoApply)
                .build());
        ra.addFlashAttribute("success", "Recorrência atualizada!");
        return "redirect:/movimentacoes?tab=recorrentes";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @AuthenticationPrincipal UserDetails principal,
                         RedirectAttributes ra) {
        var user = userService.findByEmail(principal.getUsername());
        entryService.deactivateRecurring(id, user);
        ra.addFlashAttribute("success", "Recorrência removida.");
        return "redirect:/movimentacoes?tab=recorrentes";
    }
}
