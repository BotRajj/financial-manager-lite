package com.financialmanager.lite.controller;

import com.financialmanager.lite.model.User;
import com.financialmanager.lite.service.ProjectionService;
import com.financialmanager.lite.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/projection")
@RequiredArgsConstructor
public class ProjectionController {

    private final ProjectionService projectionService;
    private final UserService userService;

    @GetMapping
    public String projection(@AuthenticationPrincipal UserDetails principal,
                             @RequestParam(required = false) String month,
                             @RequestParam(defaultValue = "3")  int monthsBefore,
                             @RequestParam(defaultValue = "9") int monthsAfter,
                             Model model) {
        monthsBefore = Math.min(Math.max(monthsBefore, 0), 36);
        monthsAfter  = Math.min(Math.max(monthsAfter,  1), 36);

        User user = userService.findByEmail(principal.getUsername());
        var data = projectionService.compute(user, month, monthsBefore, monthsAfter);

        model.addAttribute("currentBalance", data.currentBalance());
        model.addAttribute("projections", data.projections());
        model.addAttribute("selected", data.selected());
        model.addAttribute("currentMonthFlow", data.currentMonthFlow());
        model.addAttribute("currentMonth", data.currentMonth());
        model.addAttribute("monthsBefore", data.monthsBefore());
        model.addAttribute("monthsAfter", data.monthsAfter());
        model.addAttribute("monthsBeforeOptions", List.of(0, 3, 6, 12, 18, 24));
        model.addAttribute("monthsAfterOptions", List.of(3, 6, 9, 12, 18, 24));
        return "projection";
    }
}
