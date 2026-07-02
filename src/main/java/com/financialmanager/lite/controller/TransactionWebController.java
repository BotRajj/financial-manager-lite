package com.financialmanager.lite.controller;

import com.financialmanager.lite.dto.SimpleEntryDTO;
import com.financialmanager.lite.service.EntryService;
import com.financialmanager.lite.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
@RequestMapping("/transactions")
@RequiredArgsConstructor
public class TransactionWebController {

    private final EntryService entryService;
    private final UserService userService;

    @GetMapping
    public String list() { return "redirect:/movimentacoes"; }

    @PostMapping("/create")
    public String create(@AuthenticationPrincipal UserDetails principal,
                         @RequestParam String description,
                         @RequestParam BigDecimal amount,
                         @RequestParam String type,
                         @RequestParam(required = false) String category,
                         @RequestParam String transactionDate,
                         @RequestParam(required = false) Long accountId,
                         @RequestParam(required = false) Long creditCardId,
                         @RequestParam(required = false) String notes,
                         @RequestParam(required = false) String invoiceMonth,
                         RedirectAttributes ra) {
        var user = userService.findByEmail(principal.getUsername());
        entryService.createSimple(user, SimpleEntryDTO.builder()
                .description(description).amount(amount).type(type).category(category)
                .entryDate(LocalDate.parse(transactionDate))
                .accountId(accountId).creditCardId(creditCardId)
                .notes(notes).invoiceMonth(invoiceMonth)
                .build());
        ra.addFlashAttribute("success", "Movimentação registrada!");
        return "redirect:/movimentacoes";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @AuthenticationPrincipal UserDetails principal,
                         RedirectAttributes ra) {
        var user = userService.findByEmail(principal.getUsername());
        entryService.deleteSimple(id, user);
        ra.addFlashAttribute("success", "Movimentação removida.");
        return "redirect:/movimentacoes";
    }
}
