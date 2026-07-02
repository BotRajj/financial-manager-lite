package com.financialmanager.lite.controller;

import com.financialmanager.lite.model.User;
import com.financialmanager.lite.service.SpendingProjectionService;
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
import java.util.Set;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/spending-projections")
@RequiredArgsConstructor
public class SpendingProjectionController {

    private final SpendingProjectionService projectionService;
    private final UserService userService;

    @GetMapping
    public String list(@AuthenticationPrincipal UserDetails principal, Model model) {
        User user = userService.findByEmail(principal.getUsername());
        model.addAttribute("projections", projectionService.findByUser(user));
        return "spending-projections/list";
    }

    @PostMapping("/create")
    public String create(@AuthenticationPrincipal UserDetails principal,
                         @RequestParam String name,
                         @RequestParam(defaultValue = "INCLUDE") String mode,
                         RedirectAttributes ra) {
        User user = userService.findByEmail(principal.getUsername());
        var proj = projectionService.create(user, name, mode);
        return "redirect:/spending-projections/" + proj.getId();
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @AuthenticationPrincipal UserDetails principal,
                         RedirectAttributes ra) {
        User user = userService.findByEmail(principal.getUsername());
        projectionService.delete(id, user);
        ra.addFlashAttribute("success", "Projeção removida.");
        return "redirect:/spending-projections";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @AuthenticationPrincipal UserDetails principal,
                         @RequestParam(required = false) String month,
                         Model model) {
        User user = userService.findByEmail(principal.getUsername());
        var proj = projectionService.findByIdAndUser(id, user)
                .orElseThrow(() -> new IllegalArgumentException("Projeção não encontrada"));
        var items = projectionService.getItems(id);

        Set<Long> addedEntryIds = items.stream()
                .filter(i -> "ENTRY".equals(i.getItemType()) && i.getEntry() != null)
                .map(i -> i.getEntry().getId()).collect(Collectors.toSet());
        Set<Long> addedCardIds = items.stream()
                .filter(i -> "CARD".equals(i.getItemType()) && i.getCard() != null)
                .map(i -> i.getCard().getId()).collect(Collectors.toSet());

        var monthRows = projectionService.calculate(proj, items, user);
        YearMonth selectedMonth = (month != null && !month.isBlank()) ? YearMonth.parse(month) : null;
        var selected = selectedMonth == null ? null :
                monthRows.stream().filter(r -> r.month().equals(selectedMonth)).findFirst().orElse(null);

        model.addAttribute("proj", proj);
        model.addAttribute("items", items);
        model.addAttribute("availableInstallments", projectionService.findAvailableInstallments(user));
        model.addAttribute("availableRecurring", projectionService.findAvailableRecurring(user));
        model.addAttribute("availableCards", projectionService.findAvailableCards(user));
        model.addAttribute("accounts", projectionService.findAccounts(user));
        model.addAttribute("addedEntryIds", addedEntryIds);
        model.addAttribute("addedCardIds", addedCardIds);
        model.addAttribute("monthRows", monthRows);
        model.addAttribute("selected", selected);
        return "spending-projections/detail";
    }

    @PostMapping("/{id}/items/add-entry")
    public String addEntry(@PathVariable Long id,
                           @RequestParam Long entryId,
                           @RequestParam(defaultValue = "false") boolean excluded,
                           @AuthenticationPrincipal UserDetails principal) {
        User user = userService.findByEmail(principal.getUsername());
        projectionService.addEntry(id, entryId, excluded, user);
        return "redirect:/spending-projections/" + id;
    }

    @PostMapping("/{id}/items/add-card")
    public String addCard(@PathVariable Long id,
                          @RequestParam Long cardId,
                          @RequestParam(defaultValue = "MIN_PAYMENT") String cardMode,
                          @RequestParam(required = false) Integer cardInstallments,
                          @RequestParam(required = false) BigDecimal cardEntrada,
                          @RequestParam(required = false) Integer cardDayOfMonth,
                          @AuthenticationPrincipal UserDetails principal) {
        User user = userService.findByEmail(principal.getUsername());
        projectionService.addCard(id, cardId, cardMode, cardInstallments, cardEntrada, cardDayOfMonth, user);
        return "redirect:/spending-projections/" + id;
    }

    @PostMapping("/{id}/items/add-custom")
    public String addCustom(@PathVariable Long id,
                            @RequestParam String customDescription,
                            @RequestParam BigDecimal customAmount,
                            @RequestParam(defaultValue = "1") int customInstallments,
                            @RequestParam(defaultValue = "1") int customDayOfMonth,
                            @RequestParam(defaultValue = "EXPENSE") String customType,
                            @AuthenticationPrincipal UserDetails principal) {
        User user = userService.findByEmail(principal.getUsername());
        projectionService.addCustom(id, customDescription, customAmount, customInstallments, customDayOfMonth, customType, user);
        return "redirect:/spending-projections/" + id;
    }

    @PostMapping("/items/{itemId}/remove")
    public String removeItem(@PathVariable Long itemId,
                             @AuthenticationPrincipal UserDetails principal) {
        User user = userService.findByEmail(principal.getUsername());
        Long projId = projectionService.removeItem(itemId, user);
        return projId != null ? "redirect:/spending-projections/" + projId : "redirect:/spending-projections";
    }

    @PostMapping("/items/{itemId}/confirm")
    public String confirmItem(@PathVariable Long itemId,
                              @AuthenticationPrincipal UserDetails principal,
                              RedirectAttributes ra) {
        User user = userService.findByEmail(principal.getUsername());
        Long projId = projectionService.confirmItem(itemId, user);
        return projId != null ? "redirect:/spending-projections/" + projId : "redirect:/spending-projections";
    }
}
