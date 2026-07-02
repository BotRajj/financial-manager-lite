package com.financialmanager.lite.service;

import com.financialmanager.lite.model.*;
import com.financialmanager.lite.repository.AccountRepository;
import com.financialmanager.lite.repository.BankRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private BankRepository bankRepository;
    @Mock private EntryRepository entryRepository;

    @InjectMocks private AccountService accountService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User();
    }

    // BaseEntity.id é privado sem setter — usa ReflectionTestUtils para definir nos testes
    private Account accountWithId(long id) {
        Account acc = Account.builder()
                .user(user).name("Conta Teste").type("CHECKING").balance(BigDecimal.ZERO).build();
        ReflectionTestUtils.setField(acc, "id", id);
        return acc;
    }

    // -------------------------------------------------------------------------
    // findActiveByUser / findInactiveByUser
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("findActiveByUser")
    class FindActiveByUser {

        @Test
        @DisplayName("delega ao repositório e retorna a lista")
        void returnsRepositoryList() {
            Account acc = accountWithId(1L);
            when(accountRepository.findByUserAndActiveTrue(user)).thenReturn(List.of(acc));

            assertThat(accountService.findActiveByUser(user)).containsExactly(acc);
        }

        @Test
        @DisplayName("retorna lista vazia quando não há contas ativas")
        void returnsEmptyList() {
            when(accountRepository.findByUserAndActiveTrue(user)).thenReturn(List.of());

            assertThat(accountService.findActiveByUser(user)).isEmpty();
        }
    }

    @Nested
    @DisplayName("findInactiveByUser")
    class FindInactiveByUser {

        @Test
        @DisplayName("delega ao repositório e retorna a lista")
        void returnsRepositoryList() {
            Account acc = accountWithId(2L);
            when(accountRepository.findByUserAndActiveFalse(user)).thenReturn(List.of(acc));

            assertThat(accountService.findInactiveByUser(user)).containsExactly(acc);
        }
    }

    // -------------------------------------------------------------------------
    // sumBalanceByUser
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("sumBalanceByUser")
    class SumBalanceByUser {

        @Test
        @DisplayName("retorna o total somado pelo repositório")
        void returnsRepositoryValue() {
            when(accountRepository.sumBalanceByUser(user)).thenReturn(new BigDecimal("3500.00"));

            assertThat(accountService.sumBalanceByUser(user)).isEqualByComparingTo("3500.00");
        }
    }

    // -------------------------------------------------------------------------
    // getInstallmentSummary
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getInstallmentSummary")
    class GetInstallmentSummary {

        @Test
        @DisplayName("popula counts, monthly e remaining para conta com parcelas ativas")
        void populatesMapsForAccountWithInstallments() {
            Account acc = accountWithId(10L);

            // e1: installmentValue=100, remaining=1000 (1200-200)
            Entry e1 = Entry.builder().user(user).description("Parcela A").type("EXPENSE")
                    .amount(new BigDecimal("1200")).paidAmount(new BigDecimal("200"))
                    .installmentAmount(new BigDecimal("100")).build();
            // e2: installmentValue=50, remaining=500 (600-100)
            Entry e2 = Entry.builder().user(user).description("Parcela B").type("EXPENSE")
                    .amount(new BigDecimal("600")).paidAmount(new BigDecimal("100"))
                    .installmentAmount(new BigDecimal("50")).build();

            when(entryRepository.findActiveInstallmentsByAccount(acc)).thenReturn(List.of(e1, e2));

            AccountService.InstallmentSummary summary = accountService.getInstallmentSummary(List.of(acc));

            assertThat(summary.counts()).containsEntry(10L, 2);
            assertThat(summary.monthly()).containsEntry(10L, new BigDecimal("150"));    // 100 + 50
            assertThat(summary.remaining()).containsEntry(10L, new BigDecimal("1500")); // 1000 + 500
        }

        @Test
        @DisplayName("não inclui conta sem parcelas ativas nos mapas")
        void skipsAccountWithNoInstallments() {
            Account acc = accountWithId(20L);
            when(entryRepository.findActiveInstallmentsByAccount(acc)).thenReturn(List.of());

            AccountService.InstallmentSummary summary = accountService.getInstallmentSummary(List.of(acc));

            assertThat(summary.counts()).doesNotContainKey(20L);
            assertThat(summary.monthly()).doesNotContainKey(20L);
            assertThat(summary.remaining()).doesNotContainKey(20L);
        }

        @Test
        @DisplayName("retorna mapas vazios para lista vazia de contas")
        void returnsEmptyMapsForEmptyAccountList() {
            AccountService.InstallmentSummary summary = accountService.getInstallmentSummary(List.of());

            assertThat(summary.counts()).isEmpty();
            assertThat(summary.monthly()).isEmpty();
            assertThat(summary.remaining()).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // getEntrySums
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getEntrySums")
    class GetEntrySums {

        @Test
        @DisplayName("totalIn = INCOME + TRANSFER_IN; totalOut = EXPENSE + TRANSFER_OUT + CARD_PAYMENT")
        void combinesAllTypesIntoTotals() {
            when(entryRepository.sumSimpleByAccountAndType(user, 1L, "INCOME"))
                    .thenReturn(new BigDecimal("1000"));
            when(entryRepository.sumSimpleByAccountAndType(user, 1L, "TRANSFER_IN"))
                    .thenReturn(new BigDecimal("200"));
            when(entryRepository.sumSimpleByAccountAndType(user, 1L, "EXPENSE"))
                    .thenReturn(new BigDecimal("400"));
            when(entryRepository.sumSimpleByAccountAndType(user, 1L, "TRANSFER_OUT"))
                    .thenReturn(new BigDecimal("100"));
            when(entryRepository.sumSimpleByAccountAndType(user, 1L, "CARD_PAYMENT"))
                    .thenReturn(new BigDecimal("50"));

            AccountService.EntrySums sums = accountService.getEntrySums(user, 1L);

            assertThat(sums.totalIn()).isEqualByComparingTo("1200");  // 1000 + 200
            assertThat(sums.totalOut()).isEqualByComparingTo("550");  // 400 + 100 + 50
        }
    }

    // -------------------------------------------------------------------------
    // create
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        @DisplayName("vincula banco encontrado e usa bank.getName() como bankName")
        void savesAccountWithBankWhenBankIdFound() {
            Bank bank = Bank.builder().user(user).name("Nubank").build();
            when(bankRepository.findByIdAndUser(5L, user)).thenReturn(Optional.of(bank));

            accountService.create(user, "Carteira", 5L, null, "CHECKING", new BigDecimal("500"));

            ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
            verify(accountRepository).save(captor.capture());
            Account saved = captor.getValue();

            assertThat(saved.getBank()).isSameAs(bank);
            assertThat(saved.getBankName()).isEqualTo("Nubank");
            assertThat(saved.getBalance()).isEqualByComparingTo("500");
        }

        @Test
        @DisplayName("usa bankName fornecido quando bankId não encontrado no repositório")
        void savesAccountWithProvidedBankNameWhenBankIdNotFound() {
            when(bankRepository.findByIdAndUser(99L, user)).thenReturn(Optional.empty());

            accountService.create(user, "Carteira", 99L, "Banco Manual", "SAVINGS", BigDecimal.ZERO);

            ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
            verify(accountRepository).save(captor.capture());
            Account saved = captor.getValue();

            assertThat(saved.getBank()).isNull();
            assertThat(saved.getBankName()).isEqualTo("Banco Manual");
        }

        @Test
        @DisplayName("usa bankName fornecido sem consultar repositório quando bankId é null")
        void savesAccountWithProvidedBankNameWhenBankIdIsNull() {
            accountService.create(user, "Carteira", null, "Banco Manual", "SAVINGS", BigDecimal.ZERO);

            ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
            verify(accountRepository).save(captor.capture());
            Account saved = captor.getValue();

            assertThat(saved.getBank()).isNull();
            assertThat(saved.getBankName()).isEqualTo("Banco Manual");
            verify(bankRepository, never()).findByIdAndUser(any(), any());
        }
    }

    // -------------------------------------------------------------------------
    // update
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("atualiza todos os campos quando conta pertence ao usuário")
        void updatesAllFieldsWhenAccountFound() {
            Account acc = accountWithId(1L);
            Bank bank = Bank.builder().user(user).name("Inter").build();
            when(accountRepository.findByIdAndUser(1L, user)).thenReturn(Optional.of(acc));
            when(bankRepository.findByIdAndUser(7L, user)).thenReturn(Optional.of(bank));

            accountService.update(1L, user, "Nova Conta", 7L, null, "SAVINGS", new BigDecimal("999"));

            assertThat(acc.getName()).isEqualTo("Nova Conta");
            assertThat(acc.getBank()).isSameAs(bank);
            assertThat(acc.getBankName()).isEqualTo("Inter");
            assertThat(acc.getType()).isEqualTo("SAVINGS");
            assertThat(acc.getBalance()).isEqualByComparingTo("999");
            verify(accountRepository).save(acc);
        }

        @Test
        @DisplayName("não salva quando conta não pertence ao usuário")
        void doesNothingWhenAccountNotFound() {
            when(accountRepository.findByIdAndUser(99L, user)).thenReturn(Optional.empty());

            accountService.update(99L, user, "X", null, "X", "CHECKING", BigDecimal.ZERO);

            verify(accountRepository, never()).save(any(Account.class));
        }
    }

    // -------------------------------------------------------------------------
    // reactivate / deactivate
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("reactivate")
    class Reactivate {

        @Test
        @DisplayName("define active=true e salva quando conta encontrada")
        void setsActiveTrueAndSaves() {
            Account acc = accountWithId(1L);
            acc.setActive(false);
            when(accountRepository.findByIdAndUser(1L, user)).thenReturn(Optional.of(acc));

            accountService.reactivate(1L, user);

            assertThat(acc.isActive()).isTrue();
            verify(accountRepository).save(acc);
        }

        @Test
        @DisplayName("não faz nada quando conta não encontrada")
        void doesNothingWhenAccountNotFound() {
            when(accountRepository.findByIdAndUser(99L, user)).thenReturn(Optional.empty());

            accountService.reactivate(99L, user);

            verify(accountRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deactivate")
    class Deactivate {

        @Test
        @DisplayName("define active=false e salva quando conta encontrada")
        void setsActiveFalseAndSaves() {
            Account acc = accountWithId(1L);
            when(accountRepository.findByIdAndUser(1L, user)).thenReturn(Optional.of(acc));

            accountService.deactivate(1L, user);

            assertThat(acc.isActive()).isFalse();
            verify(accountRepository).save(acc);
        }

        @Test
        @DisplayName("não faz nada quando conta não encontrada")
        void doesNothingWhenAccountNotFound() {
            when(accountRepository.findByIdAndUser(99L, user)).thenReturn(Optional.empty());

            accountService.deactivate(99L, user);

            verify(accountRepository, never()).save(any());
        }
    }

    // -------------------------------------------------------------------------
    // linkBank
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("linkBank")
    class LinkBank {

        @Test
        @DisplayName("vincula banco encontrado à conta e salva")
        void linksBankAndSaves() {
            Account acc = accountWithId(1L);
            Bank bank = Bank.builder().user(user).name("Bradesco").build();
            when(accountRepository.findByIdAndUser(1L, user)).thenReturn(Optional.of(acc));
            when(bankRepository.findByIdAndUser(3L, user)).thenReturn(Optional.of(bank));

            accountService.linkBank(1L, 3L, user);

            assertThat(acc.getBank()).isSameAs(bank);
            verify(accountRepository).save(acc);
        }

        @Test
        @DisplayName("define banco como null sem consultar repositório quando bankId é null")
        void setsBankToNullWhenBankIdIsNull() {
            Account acc = accountWithId(1L);
            when(accountRepository.findByIdAndUser(1L, user)).thenReturn(Optional.of(acc));

            accountService.linkBank(1L, null, user);

            assertThat(acc.getBank()).isNull();
            verify(accountRepository).save(acc);
            verify(bankRepository, never()).findByIdAndUser(any(), any());
        }
    }
}
