package com.financialmanager.lite.controller;

import com.financialmanager.lite.service.UserService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String error,
                            @RequestParam(required = false) String logout,
                            Model model) {
        if (error != null)  model.addAttribute("error",  "E-mail ou senha incorretos.");
        if (logout != null) model.addAttribute("logout", "Você saiu com sucesso.");
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerPage() {
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@RequestParam @NotBlank String name,
                           @RequestParam @Email @NotBlank String email,
                           @RequestParam @Size(min = 6) String password,
                           RedirectAttributes ra) {
        try {
            userService.register(name.trim(), email.trim().toLowerCase(), password);
            ra.addFlashAttribute("success", "Conta criada! Faça login.");
            return "redirect:/auth/login";
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/auth/register";
        }
    }
}
