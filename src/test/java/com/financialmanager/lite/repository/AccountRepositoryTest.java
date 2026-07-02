package com.financialmanager.lite.repository;

import com.financialmanager.lite.model.Account;
import com.financialmanager.lite.model.Bank;
import com.financialmanager.lite.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @DataJpaTest sobe um H2 embutido e roda as migrations Flyway reais
 * (classpath:db/migration) antes de cada teste — valida queries E mapeamento JPA
 * contra o schema real, não um schema gerado pelo Hibernate.
 */
@DataJpaTest
class AccountRepositoryTest {

    @Autowired private TestEntityManager entityManager;
    @Autowired private AccountRepository accountRepository;

    private User user;
    private User otherUser;

    @BeforeEach
    void setUp() {
        user = entityManager.persistAndFlush(
                User.builder().name("Ana").email("ana@example.com").password("hash").build());
        otherUser = entityManager.persistAndFlush(
                User.builder().name("Bruno").email("bruno@example.com").password("hash").build());
    }

    private Account persistAccount(User owner, String name, BigDecimal balance, boolean active) {
        return entityManager.persistAndFlush(Account.builder()
                .user(owner).name(name).type("CHECKING").balance(balance).active(active).build());
    }

    // -------------------------------------------------------------------------
    // findByUserAndActiveTrue / findByUserAndActiveFalse
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("findByUserAndActiveTrue")
    class FindByUserAndActiveTrue {

        @Test
        @DisplayName("retorna só contas ativas do usuário, ordenadas por nome")
        void returnsOnlyActiveAccountsOrderedByName() {
            persistAccount(user, "Poupança", BigDecimal.TEN, true);
            persistAccount(user, "Carteira", BigDecimal.ONE, true);
            persistAccount(user, "Conta Antiga", BigDecimal.ZERO, false);
            persistAccount(otherUser, "Conta do Bruno", BigDecimal.TEN, true);
            entityManager.clear();

            List<Account> result = accountRepository.findByUserAndActiveTrue(user);

            assertThat(result).extracting(Account::getName).containsExactly("Carteira", "Poupança");
        }

        @Test
        @DisplayName("faz fetch do bank associado sem lançar LazyInitializationException")
        void fetchesAssociatedBankEagerly() {
            Bank bank = entityManager.persistAndFlush(Bank.builder().user(user).name("Nubank").build());
            Account acc = Account.builder()
                    .user(user).name("Conta Nu").type("CHECKING").bank(bank).balance(BigDecimal.ZERO).build();
            entityManager.persistAndFlush(acc);
            entityManager.clear();

            List<Account> result = accountRepository.findByUserAndActiveTrue(user);

            assertThat(result.get(0).getBank().getName()).isEqualTo("Nubank");
        }
    }

    @Nested
    @DisplayName("findByUserAndActiveFalse")
    class FindByUserAndActiveFalse {

        @Test
        @DisplayName("retorna só contas inativas do usuário")
        void returnsOnlyInactiveAccounts() {
            persistAccount(user, "Ativa", BigDecimal.TEN, true);
            persistAccount(user, "Inativa", BigDecimal.ZERO, false);
            entityManager.clear();

            List<Account> result = accountRepository.findByUserAndActiveFalse(user);

            assertThat(result).extracting(Account::getName).containsExactly("Inativa");
        }
    }

    // -------------------------------------------------------------------------
    // findByIdAndUser / findByIdAndUserWithBank
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("findByIdAndUser")
    class FindByIdAndUser {

        @Test
        @DisplayName("encontra a conta quando pertence ao usuário")
        void returnsAccountWhenOwnedByUser() {
            Account acc = persistAccount(user, "Conta", BigDecimal.TEN, true);
            entityManager.clear();

            Optional<Account> result = accountRepository.findByIdAndUser(acc.getId(), user);

            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("Conta");
        }

        @Test
        @DisplayName("retorna vazio quando a conta pertence a outro usuário")
        void returnsEmptyWhenOwnedByAnotherUser() {
            Account acc = persistAccount(otherUser, "Conta do Bruno", BigDecimal.TEN, true);
            entityManager.clear();

            Optional<Account> result = accountRepository.findByIdAndUser(acc.getId(), user);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("retorna vazio para id inexistente")
        void returnsEmptyForUnknownId() {
            assertThat(accountRepository.findByIdAndUser(999_999L, user)).isEmpty();
        }
    }

    @Nested
    @DisplayName("findByIdAndUserWithBank")
    class FindByIdAndUserWithBank {

        @Test
        @DisplayName("faz fetch do bank junto com a conta")
        void fetchesBankWithAccount() {
            Bank bank = entityManager.persistAndFlush(Bank.builder().user(user).name("Itaú").build());
            Account acc = entityManager.persistAndFlush(Account.builder()
                    .user(user).name("Conta").type("CHECKING").bank(bank).balance(BigDecimal.ZERO).build());
            entityManager.clear();

            Optional<Account> result = accountRepository.findByIdAndUserWithBank(acc.getId(), user);

            assertThat(result).isPresent();
            assertThat(result.get().getBank().getName()).isEqualTo("Itaú");
        }
    }

    // -------------------------------------------------------------------------
    // sumBalanceByUser
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("sumBalanceByUser")
    class SumBalanceByUser {

        @Test
        @DisplayName("soma o saldo apenas das contas ativas do usuário")
        void sumsOnlyActiveAccountBalances() {
            persistAccount(user, "Ativa 1", new BigDecimal("100.00"), true);
            persistAccount(user, "Ativa 2", new BigDecimal("250.50"), true);
            persistAccount(user, "Inativa", new BigDecimal("999.00"), false);
            persistAccount(otherUser, "Do Bruno", new BigDecimal("500.00"), true);
            entityManager.clear();

            BigDecimal total = accountRepository.sumBalanceByUser(user);

            assertThat(total).isEqualByComparingTo("350.50");
        }

        @Test
        @DisplayName("retorna zero quando o usuário não tem contas")
        void returnsZeroWhenNoAccounts() {
            assertThat(accountRepository.sumBalanceByUser(user)).isEqualByComparingTo("0");
        }
    }
}
