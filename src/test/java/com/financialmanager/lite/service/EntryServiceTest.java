package com.financialmanager.lite.service;

import com.financialmanager.lite.dto.SimpleEntryDTO;
import com.financialmanager.lite.model.Account;
import com.financialmanager.lite.model.CreditCard;
import com.financialmanager.lite.model.Entry;
import com.financialmanager.lite.model.User;
import com.financialmanager.lite.repository.AccountRepository;
import com.financialmanager.lite.repository.CreditCardRepository;
import com.financialmanager.lite.repository.EntryInstallmentRepository;
import com.financialmanager.lite.repository.EntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Cobertura representativa do EntryService — o service tem ~20 métodos com regras
 * de negócio complexas (SIMPLE, RECURRING, INSTALLMENT, transferências). Em vez de
 * testar tudo, focamos nos métodos com lógica mais rica e nos padrões mais úteis de
 * aprender: ArgumentCaptor para inspecionar o Entry salvo, múltiplos mocks colaborando,
 * e teste de exceções.
 */
@ExtendWith(MockitoExtension.class)
class EntryServiceTest {

    @Mock private EntryRepository entryRepository;
    @Mock private EntryInstallmentRepository installmentRepository;
    @Mock private AccountRepository accountRepository;
    @Mock private CreditCardRepository creditCardRepository;
    @Mock private UserCategoryService categoryService;
    @Mock private BudgetService budgetService;

    @InjectMocks private EntryService entryService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
        // Stubs compartilhados: nem todo teste os exercita, então usamos lenient()
        lenient().when(categoryService.resolveAndSave(eq(user), anyString()))
                .thenAnswer(inv -> inv.getArgument(1));
        lenient().when(entryRepository.save(any(Entry.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private Account accountWithBalance(BigDecimal balance) {
        return Account.builder().user(user).name("Conta").type("CHECKING").balance(balance).build();
    }

    private CreditCard cardWithUsedAmount(BigDecimal usedAmount) {
        return CreditCard.builder()
                .user(user).name("Cartão").creditLimit(new BigDecimal("5000"))
                .usedAmount(usedAmount).closingDay(10).dueDay(20).build();
    }

    // =========================================================================
    // createSimple
    // =========================================================================

    @Nested
    @DisplayName("createSimple")
    class CreateSimple {

        @Test
        @DisplayName("com conta, EXPENSE → diminui saldo e ajusta orçamento")
        void withAccountExpenseDecreasesBalanceAndAdjustsBudget() {
            Account acc = accountWithBalance(new BigDecimal("500"));
            when(accountRepository.findByIdAndUser(1L, user)).thenReturn(Optional.of(acc));

            SimpleEntryDTO dto = SimpleEntryDTO.builder()
                    .description("Mercado").amount(new BigDecimal("100"))
                    .type("EXPENSE").category("Alimentação").accountId(1L).build();

            Entry saved = entryService.createSimple(user, dto);

            assertThat(acc.getBalance()).isEqualByComparingTo("400"); // 500-100
            verify(accountRepository).save(acc);
            verify(budgetService).adjustSpent(user, "Alimentação", YearMonth.now().toString(), new BigDecimal("100"));
            assertThat(saved.getAccount()).isSameAs(acc);
            assertThat(saved.getCreditCard()).isNull();
            assertThat(saved.getType()).isEqualTo("EXPENSE");
        }

        @Test
        @DisplayName("com conta, INCOME → aumenta saldo e não ajusta orçamento")
        void withAccountIncomeIncreasesBalanceAndSkipsBudget() {
            Account acc = accountWithBalance(new BigDecimal("500"));
            when(accountRepository.findByIdAndUser(1L, user)).thenReturn(Optional.of(acc));

            SimpleEntryDTO dto = SimpleEntryDTO.builder()
                    .description("Salário").amount(new BigDecimal("100"))
                    .type("INCOME").category("Trabalho").accountId(1L).build();

            entryService.createSimple(user, dto);

            assertThat(acc.getBalance()).isEqualByComparingTo("600"); // 500+100
            verify(budgetService, never()).adjustSpent(any(), any(), any(), any());
        }

        @Test
        @DisplayName("com cartão, EXPENSE sem invoiceMonth → usa closingMonthFor como padrão")
        void withCreditCardUsesClosingMonthAsDefaultInvoice() {
            CreditCard card = cardWithUsedAmount(new BigDecimal("1000"));
            when(creditCardRepository.findByIdAndUser(2L, user)).thenReturn(Optional.of(card));

            SimpleEntryDTO dto = SimpleEntryDTO.builder()
                    .description("Compra").amount(new BigDecimal("100"))
                    .type("EXPENSE").category("Lazer").creditCardId(2L).build();

            Entry saved = entryService.createSimple(user, dto);

            assertThat(card.getUsedAmount()).isEqualByComparingTo("1100"); // 1000+100
            verify(creditCardRepository).save(card);
            String expectedInvoice = card.closingMonthFor(LocalDate.now()).toString();
            assertThat(saved.getInvoiceMonth()).isEqualTo(expectedInvoice);
        }

        @Test
        @DisplayName("com cartão, EXPENSE com invoiceMonth explícito → usa o valor informado")
        void withCreditCardUsesProvidedInvoiceMonth() {
            CreditCard card = cardWithUsedAmount(new BigDecimal("1000"));
            when(creditCardRepository.findByIdAndUser(2L, user)).thenReturn(Optional.of(card));

            SimpleEntryDTO dto = SimpleEntryDTO.builder()
                    .description("Compra").amount(new BigDecimal("100"))
                    .type("EXPENSE").category("Lazer").creditCardId(2L)
                    .invoiceMonth("2026-05").build();

            Entry saved = entryService.createSimple(user, dto);

            assertThat(saved.getInvoiceMonth()).isEqualTo("2026-05");
        }

        @Test
        @DisplayName("com cartão, INCOME → reduz usedAmount sem ficar negativo")
        void withCreditCardIncomeClampsUsedAmountAtZero() {
            CreditCard card = cardWithUsedAmount(new BigDecimal("50"));
            when(creditCardRepository.findByIdAndUser(2L, user)).thenReturn(Optional.of(card));

            SimpleEntryDTO dto = SimpleEntryDTO.builder()
                    .description("Estorno").amount(new BigDecimal("100"))
                    .type("INCOME").category("Estorno").creditCardId(2L).build();

            entryService.createSimple(user, dto);

            assertThat(card.getUsedAmount()).isEqualByComparingTo("0"); // 50-100 → clamp em 0
        }

        @Test
        @DisplayName("sem conta nem cartão → salva lançamento sem vínculo, sem alterar saldos")
        void withoutAccountOrCardSavesUnlinkedEntry() {
            SimpleEntryDTO dto = SimpleEntryDTO.builder()
                    .description("Dinheiro").amount(new BigDecimal("50"))
                    .type("EXPENSE").category("Outros").build();

            Entry saved = entryService.createSimple(user, dto);

            assertThat(saved.getAccount()).isNull();
            assertThat(saved.getCreditCard()).isNull();
            verify(accountRepository, never()).save(any());
            verify(creditCardRepository, never()).save(any());
        }
    }

    // =========================================================================
    // deleteSimple
    // =========================================================================

    @Nested
    @DisplayName("deleteSimple")
    class DeleteSimple {

        @Test
        @DisplayName("SIMPLE com conta, EXPENSE → reverte saldo, reverte orçamento e deleta")
        void reversesBalanceAndBudgetThenDeletes() {
            Account acc = accountWithBalance(new BigDecimal("400"));
            Entry entry = Entry.builder()
                    .user(user).mode("SIMPLE").type("EXPENSE").category("Alimentação")
                    .description("Mercado").amount(new BigDecimal("100"))
                    .entryDate(LocalDate.now()).account(acc).build();
            when(entryRepository.findByIdAndUser(1L, user)).thenReturn(Optional.of(entry));

            entryService.deleteSimple(1L, user);

            assertThat(acc.getBalance()).isEqualByComparingTo("500"); // reverte: 400+100
            verify(accountRepository).save(acc);
            verify(budgetService).adjustSpent(user, "Alimentação",
                    YearMonth.from(entry.getEntryDate()).toString(), new BigDecimal("-100"));
            verify(entryRepository).delete(entry);
        }

        @Test
        @DisplayName("modo diferente de SIMPLE → não faz nada")
        void ignoresNonSimpleMode() {
            Entry entry = Entry.builder()
                    .user(user).mode("RECURRING").type("EXPENSE")
                    .description("Aluguel").amount(new BigDecimal("100")).build();
            when(entryRepository.findByIdAndUser(2L, user)).thenReturn(Optional.of(entry));

            entryService.deleteSimple(2L, user);

            verify(entryRepository, never()).delete(any());
            verify(accountRepository, never()).save(any());
        }
    }

    // =========================================================================
    // transferBetweenAccounts
    // =========================================================================

    @Nested
    @DisplayName("transferBetweenAccounts")
    class TransferBetweenAccounts {

        @Test
        @DisplayName("transferência bem-sucedida → debita origem, credita destino, cria 2 lançamentos")
        void transfersBetweenAccounts() {
            Account from = accountWithBalance(new BigDecimal("1000"));
            Account to = accountWithBalance(new BigDecimal("200"));
            when(accountRepository.findByIdAndUser(1L, user)).thenReturn(Optional.of(from));
            when(accountRepository.findByIdAndUser(2L, user)).thenReturn(Optional.of(to));

            entryService.transferBetweenAccounts(user, 1L, 2L, new BigDecimal("300"), null, LocalDate.now());

            assertThat(from.getBalance()).isEqualByComparingTo("700");  // 1000-300
            assertThat(to.getBalance()).isEqualByComparingTo("500");    // 200+300

            ArgumentCaptor<Entry> captor = ArgumentCaptor.forClass(Entry.class);
            verify(entryRepository, times(2)).save(captor.capture());
            List<Entry> saved = captor.getAllValues();

            assertThat(saved).extracting(Entry::getType).containsExactlyInAnyOrder("TRANSFER_OUT", "TRANSFER_IN");
            assertThat(saved).allSatisfy(e -> assertThat(e.getAmount()).isEqualByComparingTo("300"));
        }

        @Test
        @DisplayName("conta de origem não encontrada → lança IllegalArgumentException")
        void throwsWhenSourceAccountNotFound() {
            when(accountRepository.findByIdAndUser(99L, user)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    entryService.transferBetweenAccounts(user, 99L, 2L, BigDecimal.TEN, null, LocalDate.now()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("origem");

            verify(accountRepository, never()).save(any());
        }

        @Test
        @DisplayName("conta de destino não encontrada → lança IllegalArgumentException")
        void throwsWhenDestinationAccountNotFound() {
            Account from = accountWithBalance(new BigDecimal("1000"));
            when(accountRepository.findByIdAndUser(1L, user)).thenReturn(Optional.of(from));
            when(accountRepository.findByIdAndUser(99L, user)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    entryService.transferBetweenAccounts(user, 1L, 99L, BigDecimal.TEN, null, LocalDate.now()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("destino");

            verify(accountRepository, never()).save(any());
        }
    }

    // =========================================================================
    // payNextInstallment
    // =========================================================================

    @Nested
    @DisplayName("payNextInstallment")
    class PayNextInstallment {

        @Test
        @DisplayName("com cartão (direção não-PAYABLE) → reduz usedAmount do cartão")
        void withNonPayableCardReducesUsedAmount() {
            CreditCard card = cardWithUsedAmount(new BigDecimal("500"));
            Entry entry = Entry.builder()
                    .user(user).mode("INSTALLMENT").type("EXPENSE").direction("STANDARD")
                    .description("Parcelado").amount(new BigDecimal("1200"))
                    .totalInstallments(12).paidInstallments(5).paidAmount(new BigDecimal("500"))
                    .installmentAmount(new BigDecimal("100")).creditCard(card).build();
            when(entryRepository.findInstallmentByIdAndUser(10L, user)).thenReturn(Optional.of(entry));
            when(installmentRepository.findByEntryAndInstallmentNumber(entry, 6)).thenReturn(Optional.empty());

            entryService.payNextInstallment(10L, user);

            assertThat(entry.getPaidAmount()).isEqualByComparingTo("600");  // 500+100
            assertThat(entry.getPaidInstallments()).isEqualTo(6);
            assertThat(entry.getStatus()).isEqualTo("ACTIVE"); // 6 < 12
            assertThat(card.getUsedAmount()).isEqualByComparingTo("400"); // 500-100
            verify(creditCardRepository).save(card);
        }

        @Test
        @DisplayName("com conta (direção PAYABLE) → reduz saldo da conta")
        void withPayableAccountReducesBalance() {
            Account acc = accountWithBalance(new BigDecimal("1000"));
            Entry entry = Entry.builder()
                    .user(user).mode("INSTALLMENT").type("EXPENSE").direction("PAYABLE")
                    .description("Empréstimo").amount(new BigDecimal("1200"))
                    .totalInstallments(12).paidInstallments(0).paidAmount(BigDecimal.ZERO)
                    .installmentAmount(new BigDecimal("100")).account(acc).build();
            when(entryRepository.findInstallmentByIdAndUser(11L, user)).thenReturn(Optional.of(entry));
            when(installmentRepository.findByEntryAndInstallmentNumber(entry, 1)).thenReturn(Optional.empty());

            entryService.payNextInstallment(11L, user);

            assertThat(acc.getBalance()).isEqualByComparingTo("900"); // 1000-100
            verify(accountRepository).save(acc);
        }

        @Test
        @DisplayName("atinge o total de parcelas → status SETTLED e não ultrapassa o total")
        void reachingTotalInstallmentsSetsSettledStatus() {
            Entry entry = Entry.builder()
                    .user(user).mode("INSTALLMENT").type("EXPENSE").direction("STANDARD")
                    .description("Última parcela").amount(new BigDecimal("1200"))
                    .totalInstallments(12).paidInstallments(11).paidAmount(new BigDecimal("1100"))
                    .installmentAmount(new BigDecimal("100")).build();
            when(entryRepository.findInstallmentByIdAndUser(12L, user)).thenReturn(Optional.of(entry));
            when(installmentRepository.findByEntryAndInstallmentNumber(entry, 12)).thenReturn(Optional.empty());

            entryService.payNextInstallment(12L, user);

            assertThat(entry.getPaidInstallments()).isEqualTo(12); // clamped no total
            assertThat(entry.getStatus()).isEqualTo("SETTLED");
        }
    }

    // =========================================================================
    // appliesThisMonth
    // =========================================================================

    @Nested
    @DisplayName("appliesThisMonth")
    class AppliesThisMonth {

        @Test
        @DisplayName("frequência MONTHLY → sempre true")
        void monthlyAlwaysApplies() {
            Entry entry = Entry.builder().user(user).description("X").amount(BigDecimal.ONE)
                    .type("EXPENSE").frequency("MONTHLY").build();

            assertThat(entryService.appliesThisMonth(entry, YearMonth.of(2026, 3))).isTrue();
            assertThat(entryService.appliesThisMonth(entry, YearMonth.of(2026, 11))).isTrue();
        }

        @Test
        @DisplayName("frequência ANNUAL com mês correspondente → true")
        void annualMatchingMonthApplies() {
            Entry entry = Entry.builder().user(user).description("X").amount(BigDecimal.ONE)
                    .type("EXPENSE").frequency("ANNUAL").monthOfYear(6).build();

            assertThat(entryService.appliesThisMonth(entry, YearMonth.of(2026, 6))).isTrue();
        }

        @Test
        @DisplayName("frequência ANNUAL com mês diferente → false")
        void annualNonMatchingMonthDoesNotApply() {
            Entry entry = Entry.builder().user(user).description("X").amount(BigDecimal.ONE)
                    .type("EXPENSE").frequency("ANNUAL").monthOfYear(6).build();

            assertThat(entryService.appliesThisMonth(entry, YearMonth.of(2026, 7))).isFalse();
        }

        @Test
        @DisplayName("sem frequência reconhecida → false")
        void unknownFrequencyDoesNotApply() {
            Entry entry = Entry.builder().user(user).description("X").amount(BigDecimal.ONE)
                    .type("EXPENSE").frequency(null).build();

            assertThat(entryService.appliesThisMonth(entry, YearMonth.now())).isFalse();
        }
    }
}
