package com.financialmanager.lite.config;

import com.financialmanager.lite.model.*;
import com.financialmanager.lite.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DemoDataInitializer implements ApplicationRunner {

    private static final String DEMO_EMAIL = "demo@demo.com";

    private final UserRepository             userRepository;
    private final BankRepository             bankRepository;
    private final AccountRepository          accountRepository;
    private final CreditCardRepository       creditCardRepository;
    private final EntryRepository            entryRepository;
    private final EntryInstallmentRepository installmentRepository;
    private final BudgetRepository           budgetRepository;
    private final UserCategoryRepository     userCategoryRepository;
    private final PasswordEncoder            passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(DEMO_EMAIL)) return;

        User user = userRepository.save(User.builder()
                .name("Demo")
                .email(DEMO_EMAIL)
                .password(passwordEncoder.encode("demo123"))
                .build());

        // ── Banks ──────────────────────────────────────────────────────────
        Bank nubank = bankRepository.save(Bank.builder().user(user).name("Nubank").build());
        Bank itau   = bankRepository.save(Bank.builder().user(user).name("Itaú").build());

        // ── Accounts ───────────────────────────────────────────────────────
        Account contaNubank = accountRepository.save(Account.builder()
                .user(user).bank(nubank).name("Nubank").bankName("Nubank")
                .type("CHECKING").balance(new BigDecimal("3450.00")).build());
        Account contaItau = accountRepository.save(Account.builder()
                .user(user).bank(itau).name("Itaú Corrente").bankName("Itaú")
                .type("CHECKING").balance(new BigDecimal("12800.00")).build());
        accountRepository.save(Account.builder()
                .user(user).name("Carteira").type("SAVINGS")
                .balance(new BigDecimal("150.00")).build());

        // ── Credit cards ───────────────────────────────────────────────────
        // Nubank: closing day 20 — purchases on days 1-20 go to the same month's invoice,
        // so June entries (days 6-16) stay on the June invoice (already settled).
        // usedAmount reflects only current July purchases.
        CreditCard cardNubank = CreditCard.builder()
                .user(user).bank(nubank).name("Nubank Roxinho")
                .creditLimit(new BigDecimal("5000.00"))
                .usedAmount(new BigDecimal("488.00"))
                .closingDay(20).dueDay(10)
                .build();
        cardNubank.getCardNumbers().add(CardNumber.builder()
                .creditCard(cardNubank).lastFourDigits("4523")
                .type(CardNumber.CardKind.FISICO).build());
        cardNubank = creditCardRepository.save(cardNubank);

        // Itaú: closing day 28 — June entries (days 12-25) stay on June invoice.
        CreditCard cardItau = CreditCard.builder()
                .user(user).bank(itau).name("Itaú Platinum")
                .creditLimit(new BigDecimal("8000.00"))
                .usedAmount(new BigDecimal("325.50"))
                .closingDay(28).dueDay(6)
                .build();
        cardItau.getCardNumbers().add(CardNumber.builder()
                .creditCard(cardItau).lastFourDigits("7891")
                .type(CardNumber.CardKind.FISICO).build());
        cardItau = creditCardRepository.save(cardItau);

        // ── User categories ────────────────────────────────────────────────
        for (String cat : List.of("Alimentação", "Transporte", "Moradia", "Saúde",
                                  "Lazer", "Vestuário", "Educação", "Assinaturas")) {
            userCategoryRepository.save(UserCategory.builder().user(user).name(cat).build());
        }

        // ── Simple entries ─────────────────────────────────────────────────
        YearMonth now      = YearMonth.now();
        YearMonth prevMonth = now.minusMonths(1);
        String prevInvoice = prevMonth.toString(); // June entries forced onto last month's closed invoice

        // Last month: income + fixed
        entry(user, contaNubank, null, "Salário",           "5500.00", "INCOME",  "Outros",      prevMonth.atDay(5),  null);
        entry(user, contaNubank, null, "Aluguel",           "1200.00", "EXPENSE", "Moradia",     prevMonth.atDay(5),  null);
        entry(user, contaItau,   null, "Médico particular",  "250.00", "EXPENSE", "Saúde",       prevMonth.atDay(22), null);

        // Last month card purchases — explicitly set to last month's invoice (already settled)
        entry(user, null, cardNubank, "Netflix",                        "45.90", "EXPENSE", "Assinaturas", prevMonth.atDay(6),  prevInvoice);
        entry(user, null, cardNubank, "Spotify",                        "21.90", "EXPENSE", "Assinaturas", prevMonth.atDay(6),  prevInvoice);
        entry(user, null, cardNubank, "iFood",                          "89.50", "EXPENSE", "Alimentação", prevMonth.atDay(10), prevInvoice);
        entry(user, null, cardNubank, "Supermercado Pão de Açúcar",    "487.30", "EXPENSE", "Alimentação", prevMonth.atDay(14), prevInvoice);
        entry(user, null, cardNubank, "Uber",                           "38.50", "EXPENSE", "Transporte",  prevMonth.atDay(16), prevInvoice);
        entry(user, null, cardItau,  "Restaurante Outback",            "180.00", "EXPENSE", "Alimentação", prevMonth.atDay(12), prevInvoice);
        entry(user, null, cardItau,  "Cinema",                          "87.00", "EXPENSE", "Lazer",       prevMonth.atDay(20), prevInvoice);
        entry(user, null, cardItau,  "Farmácia",                       "127.60", "EXPENSE", "Saúde",       prevMonth.atDay(22), prevInvoice);
        entry(user, null, cardItau,  "Posto Ipiranga",                 "198.00", "EXPENSE", "Transporte",  prevMonth.atDay(25), prevInvoice);

        // Current month: income + fixed
        entry(user, contaNubank, null, "Salário",           "5500.00", "INCOME",  "Outros",  now.atDay(5),  null);
        entry(user, contaNubank, null, "Aluguel",           "1200.00", "EXPENSE", "Moradia", now.atDay(1),  null);

        // Current month card purchases — go to current invoice (usedAmount: Nubank 488, Itaú 325.50)
        entry(user, null, cardNubank, "Supermercado",   "312.80", "EXPENSE", "Alimentação", now.atDay(1), null);
        entry(user, null, cardNubank, "iFood",           "65.40", "EXPENSE", "Alimentação", now.atDay(2), null);
        entry(user, null, cardNubank, "Uber",            "42.00", "EXPENSE", "Transporte",  now.atDay(2), null);
        entry(user, null, cardNubank, "Netflix",         "45.90", "EXPENSE", "Assinaturas", now.atDay(2), null);
        entry(user, null, cardNubank, "Spotify",         "21.90", "EXPENSE", "Assinaturas", now.atDay(2), null);
        entry(user, null, cardItau,  "Posto Ipiranga", "198.00", "EXPENSE", "Transporte",  now.atDay(1), null);
        entry(user, null, cardItau,  "Restaurante",    "127.50", "EXPENSE", "Alimentação", now.atDay(2), null);

        // ── Recurring entries ──────────────────────────────────────────────
        entryRepository.save(Entry.builder()
                .user(user).account(contaNubank).mode("RECURRING").type("INCOME")
                .description("Salário").amount(new BigDecimal("5500.00")).category("Outros")
                .frequency("MONTHLY").dayOfMonth(5).lastAppliedMonth(prevMonth.toString())
                .autoApply(false).build());
        entryRepository.save(Entry.builder()
                .user(user).account(contaNubank).mode("RECURRING").type("EXPENSE")
                .description("Aluguel").amount(new BigDecimal("1200.00")).category("Moradia")
                .frequency("MONTHLY").dayOfMonth(1).lastAppliedMonth(now.toString())
                .autoApply(false).build());
        entryRepository.save(Entry.builder()
                .user(user).account(contaNubank).mode("RECURRING").type("EXPENSE")
                .description("Academia").amount(new BigDecimal("89.90")).category("Saúde")
                .frequency("MONTHLY").dayOfMonth(10).lastAppliedMonth(prevMonth.toString())
                .autoApply(false).build());

        // ── Installment plans linked to credit cards ────────────────────────
        // Nubank: 12x R$245, started 6 months ago, 6 paid, next due on day 10 this month
        YearMonth nubankPlanStart = now.minusMonths(6);
        Entry planoNubank = entryRepository.save(Entry.builder()
                .user(user).creditCard(cardNubank)
                .mode("INSTALLMENT").type("EXPENSE").direction("PAYABLE").status("ACTIVE")
                .description("Parcelamento fatura " + nubankPlanStart.minusMonths(1))
                .amount(new BigDecimal("2940.00")).installmentAmount(new BigDecimal("245.00"))
                .totalInstallments(12).paidInstallments(6).paidAmount(new BigDecimal("1470.00"))
                .dayOfMonth(10).build());

        for (int i = 1; i <= 12; i++) {
            LocalDate due = nubankPlanStart.atDay(10).plusMonths(i - 1);
            installmentRepository.save(EntryInstallment.builder()
                    .entry(planoNubank).installmentNumber(i)
                    .amount(new BigDecimal("245.00")).dueDate(due)
                    .confirmed(i <= 6).build());
        }

        // Itaú: 10x R$185, started 4 months ago, 4 paid, next due on day 6 (coming up soon)
        YearMonth itauPlanStart = now.minusMonths(4);
        Entry planoItau = entryRepository.save(Entry.builder()
                .user(user).creditCard(cardItau)
                .mode("INSTALLMENT").type("EXPENSE").direction("PAYABLE").status("ACTIVE")
                .description("Parcelamento fatura " + itauPlanStart.minusMonths(1))
                .amount(new BigDecimal("1850.00")).installmentAmount(new BigDecimal("185.00"))
                .totalInstallments(10).paidInstallments(4).paidAmount(new BigDecimal("740.00"))
                .dayOfMonth(6).build());

        for (int i = 1; i <= 10; i++) {
            LocalDate due = itauPlanStart.atDay(6).plusMonths(i - 1);
            installmentRepository.save(EntryInstallment.builder()
                    .entry(planoItau).installmentNumber(i)
                    .amount(new BigDecimal("185.00")).dueDate(due)
                    .confirmed(i <= 4).build());
        }

        // ── Budgets (current month) ────────────────────────────────────────
        // spent amounts match current-month entries above
        String period = now.toString();
        budget(user, "Alimentação",  "1200.00", "505.70",  period); // 312.80+65.40+127.50
        budget(user, "Transporte",    "350.00", "240.00",  period); // 198.00+42.00
        budget(user, "Moradia",      "1500.00", "1200.00", period);
        budget(user, "Assinaturas",   "200.00",  "67.80",  period); // 45.90+21.90
        budget(user, "Saúde",         "300.00",   "0.00",  period);
        budget(user, "Lazer",         "400.00",   "0.00",  period);
    }

    private void entry(User user, Account account, CreditCard card,
                       String description, String amount, String type,
                       String category, LocalDate date, String invoiceMonthOverride) {
        String invoiceMonth = null;
        if (card != null) {
            invoiceMonth = invoiceMonthOverride != null
                    ? invoiceMonthOverride
                    : card.closingMonthFor(date).toString();
        }
        entryRepository.save(Entry.builder()
                .user(user).account(account).creditCard(card)
                .mode("SIMPLE").type(type).direction("STANDARD")
                .description(description).amount(new BigDecimal(amount))
                .category(category).entryDate(date).invoiceMonth(invoiceMonth)
                .build());
    }

    private void budget(User user, String category, String limit, String spent, String period) {
        budgetRepository.save(Budget.builder()
                .user(user).category(category).period(period)
                .limitAmount(new BigDecimal(limit)).spentAmount(new BigDecimal(spent))
                .build());
    }
}
