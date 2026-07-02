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


@Controller
@RequestMapping("/banks")
@RequiredArgsConstructor
public class BankWebController {

    private final BankService bankService;
    private final AccountService accountService;
    private final CreditCardService creditCardService;
    private final EntryService entryService;
    private final UserService userService;

    @GetMapping
    public String list(@AuthenticationPrincipal UserDetails principal, Model model) {
        User user = userService.findByEmail(principal.getUsername());
        var banks        = bankService.findActiveByUser(user);
        var accounts     = accountService.findActiveByUser(user);
        var cards        = creditCardService.findActiveByUser(user);
        var installments = entryService.findActivePayablesByUser(user);
        var summaries    = bankService.buildSummaries(banks, accounts, cards, installments);

        var unboundAccounts     = accounts.stream().filter(a -> a.getBank() == null).toList();
        var unboundCards        = cards.stream().filter(c -> c.getBank() == null).toList();
        var unboundInstallments = installments.stream()
                .filter(e -> e.getBank() == null
                          && (e.getCreditCard() == null || e.getCreditCard().getBank() == null))
                .toList();

        model.addAttribute("summaries", summaries);
        model.addAttribute("banks", banks);
        model.addAttribute("unboundAccounts", unboundAccounts);
        model.addAttribute("unboundCards", unboundCards);
        model.addAttribute("unboundInstallments", unboundInstallments);
        return "banks/list";
    }

    @PostMapping("/create")
    public String create(@AuthenticationPrincipal UserDetails principal,
                         @RequestParam String name,
                         RedirectAttributes ra) {
        User user = userService.findByEmail(principal.getUsername());
        bankService.create(user, name);
        ra.addFlashAttribute("success", "Banco adicionado!");
        return "redirect:/banks";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @AuthenticationPrincipal UserDetails principal,
                         RedirectAttributes ra) {
        User user = userService.findByEmail(principal.getUsername());
        bankService.deactivate(id, user);
        ra.addFlashAttribute("success", "Banco removido.");
        return "redirect:/banks";
    }
}
