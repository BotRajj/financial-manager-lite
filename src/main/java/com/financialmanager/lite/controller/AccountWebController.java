package com.financialmanager.lite.controller;

import com.financialmanager.lite.model.User;
import com.financialmanager.lite.service.AccountService;
import com.financialmanager.lite.service.BankService;
import com.financialmanager.lite.service.EntryService;
import com.financialmanager.lite.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountWebController {

    private final AccountService accountService;
    private final BankService bankService;
    private final EntryService entryService;
    private final UserService userService;

    @GetMapping
    public String list(@AuthenticationPrincipal UserDetails principal, Model model) {
        User user = userService.findByEmail(principal.getUsername());
        var accounts = accountService.findActiveByUser(user);
        var summary  = accountService.getInstallmentSummary(accounts);
        model.addAttribute("accounts", accounts);
        model.addAttribute("inactiveAccounts", accountService.findInactiveByUser(user));
        model.addAttribute("banks", bankService.findActiveByUser(user));
        model.addAttribute("installmentCount", summary.counts());
        model.addAttribute("installmentMonthly", summary.monthly());
        model.addAttribute("installmentRemaining", summary.remaining());
        return "accounts/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @AuthenticationPrincipal UserDetails principal,
                         @RequestParam(defaultValue = "0") int page,
                         Model model) {
        User user = userService.findByEmail(principal.getUsername());
        var account = accountService.findByIdWithBank(id, user).orElse(null);
        if (account == null) return "redirect:/accounts";
        var sums = accountService.getEntrySums(user, id);
        model.addAttribute("account", account);
        model.addAttribute("entries", accountService.getEntries(user, id, page));
        model.addAttribute("totalIn", sums.totalIn());
        model.addAttribute("totalOut", sums.totalOut());
        return "accounts/detail";
    }

    @PostMapping("/create")
    public String create(@AuthenticationPrincipal UserDetails principal,
                         @RequestParam String name,
                         @RequestParam(required = false) String bankId,
                         @RequestParam(required = false) String bankName,
                         @RequestParam String type,
                         @RequestParam(defaultValue = "0") BigDecimal balance,
                         RedirectAttributes ra) {
        User user = userService.findByEmail(principal.getUsername());
        Long bankIdLong = parseId(bankId);
        accountService.create(user, name, bankIdLong, bankName, type, balance);
        ra.addFlashAttribute("success", "Conta criada com sucesso!");
        return "redirect:/accounts";
    }

    @PostMapping("/{id}/edit")
    public String edit(@PathVariable Long id,
                       @AuthenticationPrincipal UserDetails principal,
                       @RequestParam String name,
                       @RequestParam(required = false) String bankId,
                       @RequestParam(required = false) String bankName,
                       @RequestParam String type,
                       @RequestParam(defaultValue = "0") BigDecimal balance,
                       RedirectAttributes ra) {
        User user = userService.findByEmail(principal.getUsername());
        accountService.update(id, user, name, parseId(bankId), bankName, type, balance);
        ra.addFlashAttribute("success", "Conta atualizada com sucesso!");
        return "redirect:/accounts";
    }

    @PostMapping("/{id}/reactivate")
    public String reactivate(@PathVariable Long id,
                             @AuthenticationPrincipal UserDetails principal,
                             RedirectAttributes ra) {
        User user = userService.findByEmail(principal.getUsername());
        accountService.reactivate(id, user);
        ra.addFlashAttribute("success", "Conta reativada.");
        return "redirect:/accounts";
    }

    @PostMapping("/{id}/link-bank")
    public String linkBank(@PathVariable Long id,
                           @RequestParam(required = false) Long bankId,
                           @AuthenticationPrincipal UserDetails principal,
                           RedirectAttributes ra) {
        User user = userService.findByEmail(principal.getUsername());
        accountService.linkBank(id, bankId, user);
        ra.addFlashAttribute("success", bankId != null ? "Conta vinculada ao banco!" : "Vínculo removido.");
        return "redirect:/banks";
    }

    @PostMapping("/transfer")
    public String transfer(@AuthenticationPrincipal UserDetails principal,
                           @RequestParam Long fromAccountId,
                           @RequestParam Long toAccountId,
                           @RequestParam BigDecimal amount,
                           @RequestParam(required = false) String description,
                           RedirectAttributes ra) {
        User user = userService.findByEmail(principal.getUsername());
        if (fromAccountId.equals(toAccountId)) {
            ra.addFlashAttribute("error", "Selecione contas diferentes para transferir.");
            return "redirect:/accounts";
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            ra.addFlashAttribute("error", "Informe um valor de transferência maior que zero.");
            return "redirect:/accounts";
        }
        try {
            entryService.transferBetweenAccounts(user, fromAccountId, toAccountId, amount, description, LocalDate.now());
            ra.addFlashAttribute("success", "Transferência realizada com sucesso!");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/accounts";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @AuthenticationPrincipal UserDetails principal,
                         RedirectAttributes ra) {
        User user = userService.findByEmail(principal.getUsername());
        accountService.deactivate(id, user);
        ra.addFlashAttribute("success", "Conta desativada.");
        return "redirect:/banks";
    }

    private Long parseId(String val) {
        if (val == null || val.isBlank()) return null;
        try { return Long.parseLong(val); } catch (NumberFormatException e) { return null; }
    }
}
