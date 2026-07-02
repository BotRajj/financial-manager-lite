package com.financialmanager.lite.controller;

import com.financialmanager.lite.model.User;
import com.financialmanager.lite.service.BudgetService;
import com.financialmanager.lite.service.UserCategoryService;
import com.financialmanager.lite.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.YearMonth;

@Controller
@RequestMapping("/budgets")
@RequiredArgsConstructor
public class BudgetWebController {

    private final BudgetService budgetService;
    private final UserCategoryService categoryService;
    private final UserService userService;

    @GetMapping
    public String list(@AuthenticationPrincipal UserDetails principal,
                       @RequestParam(required = false) String period,
                       Model model) {
        User user = userService.findByEmail(principal.getUsername());
        String p = (period != null && !period.isBlank())
                ? period.substring(0, 7)
                : YearMonth.now().toString();
        model.addAttribute("budgets", budgetService.findByUserAndPeriod(user, p));
        model.addAttribute("period", p);
        model.addAttribute("categories", categoryService.findAll(user));
        return "budgets/list";
    }

    @PostMapping("/create")
    public String create(@AuthenticationPrincipal UserDetails principal,
                         @RequestParam String category,
                         @RequestParam String period,
                         @RequestParam BigDecimal limitAmount,
                         RedirectAttributes ra) {
        User user = userService.findByEmail(principal.getUsername());
        String p = period.substring(0, 7);
        if (budgetService.existsByUserAndCategoryAndPeriod(user, category, p)) {
            ra.addFlashAttribute("error", "Orçamento já existe para " + category + " em " + p);
            return "redirect:/budgets?period=" + p;
        }
        budgetService.create(user, category, p, limitAmount);
        ra.addFlashAttribute("success", "Orçamento criado!");
        return "redirect:/budgets?period=" + p;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @AuthenticationPrincipal UserDetails principal,
                         @RequestParam(required = false) String period,
                         RedirectAttributes ra) {
        User user = userService.findByEmail(principal.getUsername());
        budgetService.delete(id, user);
        ra.addFlashAttribute("success", "Orçamento removido.");
        return "redirect:/budgets" + (period != null ? "?period=" + period : "");
    }
}
