package com.financialmanager.lite.controller;

import com.financialmanager.lite.config.SecurityConfig;
import com.financialmanager.lite.model.Account;
import com.financialmanager.lite.model.User;
import com.financialmanager.lite.service.AccountService;
import com.financialmanager.lite.service.BankService;
import com.financialmanager.lite.service.EntryService;
import com.financialmanager.lite.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @WebMvcTest sobe apenas a camada web (DispatcherServlet, Thymeleaf, Security)
 * sem JPA nem service beans reais. Todos os services são @MockBean.
 *
 * Padrões ensinados aqui:
 *  - @WithMockUser para simular usuário autenticado
 *  - csrf() em POST para não receber 403
 *  - model(), view(), flash() para assertir o conteúdo da resposta
 *  - status().isOk() / is3xxRedirection() / redirectedUrl()
 */
@WebMvcTest(AccountWebController.class)
@Import(SecurityConfig.class) // carrega o SecurityConfig real (formLogin /auth/login) em vez da segurança auto-configurada
class AccountWebControllerTest {

    @Autowired MockMvc mockMvc;

    @MockBean AccountService accountService;
    @MockBean BankService     bankService;
    @MockBean EntryService    entryService;
    @MockBean UserService     userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().name("Ana").email("user@example.com").password("hash").build();
        // @WithMockUser usa username="user" por padrão — mapeamos para nosso User de domínio
        when(userService.findByEmail("user")).thenReturn(user);
        // Stubs genéricos para listas e agregados usados em quase todos os handlers
        lenient().when(accountService.findActiveByUser(user)).thenReturn(List.of());
        lenient().when(accountService.findInactiveByUser(user)).thenReturn(List.of());
        lenient().when(bankService.findActiveByUser(user)).thenReturn(List.of());
        lenient().when(accountService.getInstallmentSummary(any()))
                .thenReturn(new AccountService.InstallmentSummary(Map.of(), Map.of(), Map.of()));
    }

    // =========================================================================
    // Segurança — acesso sem autenticação
    // =========================================================================

    @Nested
    @DisplayName("Segurança")
    class Security {

        @Test
        @DisplayName("GET /accounts sem autenticação → redireciona para a página de login")
        void unauthenticatedRedirectsToLogin() throws Exception {
            mockMvc.perform(get("/accounts"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/auth/login"));
        }

        @Test
        @DisplayName("POST /accounts/create sem CSRF → 403 Forbidden")
        @WithMockUser
        void postWithoutCsrfIsForbidden() throws Exception {
            mockMvc.perform(post("/accounts/create")
                            .param("name", "Test")
                            .param("type", "CHECKING"))
                    .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // GET /accounts
    // =========================================================================

    @Nested
    @DisplayName("GET /accounts")
    class GetList {

        @Test
        @DisplayName("lista contas → view 'accounts/list', atributos no model")
        @WithMockUser
        void rendersListView() throws Exception {
            mockMvc.perform(get("/accounts"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/list"))
                    .andExpect(model().attributeExists("accounts"))
                    .andExpect(model().attributeExists("inactiveAccounts"))
                    .andExpect(model().attributeExists("banks"))
                    .andExpect(model().attributeExists("installmentCount"))
                    .andExpect(model().attributeExists("installmentMonthly"))
                    .andExpect(model().attributeExists("installmentRemaining"));
        }
    }

    // =========================================================================
    // GET /accounts/{id}
    // =========================================================================

    @Nested
    @DisplayName("GET /accounts/{id}")
    class GetDetail {

        @Test
        @DisplayName("conta encontrada → view 'accounts/detail', model com account e totais")
        @WithMockUser
        void rendersDetailViewWhenFound() throws Exception {
            Account acc = Account.builder().user(user).name("Conta").type("CHECKING")
                    .balance(BigDecimal.TEN).build();
            when(accountService.findByIdWithBank(1L, user)).thenReturn(Optional.of(acc));
            when(accountService.getEntrySums(user, 1L))
                    .thenReturn(new AccountService.EntrySums(BigDecimal.ZERO, BigDecimal.ZERO));
            when(accountService.getEntries(user, 1L, 0)).thenReturn(new PageImpl<>(List.of()));

            mockMvc.perform(get("/accounts/1"))
                    .andExpect(status().isOk())
                    .andExpect(view().name("accounts/detail"))
                    .andExpect(model().attributeExists("account"))
                    .andExpect(model().attributeExists("totalIn"))
                    .andExpect(model().attributeExists("totalOut"));
        }

        @Test
        @DisplayName("conta não encontrada → redireciona para /accounts")
        @WithMockUser
        void redirectsWhenAccountNotFound() throws Exception {
            when(accountService.findByIdWithBank(99L, user)).thenReturn(Optional.empty());

            mockMvc.perform(get("/accounts/99"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/accounts"));
        }
    }

    // =========================================================================
    // POST /accounts/create
    // =========================================================================

    @Nested
    @DisplayName("POST /accounts/create")
    class PostCreate {

        @Test
        @DisplayName("cria conta → chama service, redireciona com flash de sucesso")
        @WithMockUser
        void createsAccountAndRedirects() throws Exception {
            mockMvc.perform(post("/accounts/create").with(csrf())
                            .param("name", "Carteira").param("type", "CHECKING"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/accounts"))
                    .andExpect(flash().attribute("success", "Conta criada com sucesso!"));

            verify(accountService).create(eq(user), eq("Carteira"), isNull(), isNull(), eq("CHECKING"), any());
        }
    }

    // =========================================================================
    // POST /accounts/{id}/reactivate
    // =========================================================================

    @Nested
    @DisplayName("POST /accounts/{id}/reactivate")
    class PostReactivate {

        @Test
        @DisplayName("reativa conta → chama service, redireciona com flash de sucesso")
        @WithMockUser
        void reactivatesAndRedirects() throws Exception {
            mockMvc.perform(post("/accounts/5/reactivate").with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/accounts"))
                    .andExpect(flash().attribute("success", "Conta reativada."));

            verify(accountService).reactivate(5L, user);
        }
    }

    // =========================================================================
    // POST /accounts/transfer
    // =========================================================================

    @Nested
    @DisplayName("POST /accounts/transfer")
    class PostTransfer {

        @Test
        @DisplayName("contas de origem e destino iguais → não transfere, redireciona com erro")
        @WithMockUser
        void sameAccountsReturnsError() throws Exception {
            mockMvc.perform(post("/accounts/transfer").with(csrf())
                            .param("fromAccountId", "1")
                            .param("toAccountId", "1")
                            .param("amount", "100.00"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/accounts"))
                    .andExpect(flash().attribute("error", "Selecione contas diferentes para transferir."));

            verify(entryService, never()).transferBetweenAccounts(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("valor zero → não transfere, redireciona com erro")
        @WithMockUser
        void zeroAmountReturnsError() throws Exception {
            mockMvc.perform(post("/accounts/transfer").with(csrf())
                            .param("fromAccountId", "1")
                            .param("toAccountId", "2")
                            .param("amount", "0"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/accounts"))
                    .andExpect(flash().attribute("error",
                            "Informe um valor de transferência maior que zero."));

            verify(entryService, never()).transferBetweenAccounts(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("transferência válida → chama service, redireciona com sucesso")
        @WithMockUser
        void validTransferCallsServiceAndRedirects() throws Exception {
            mockMvc.perform(post("/accounts/transfer").with(csrf())
                            .param("fromAccountId", "1")
                            .param("toAccountId", "2")
                            .param("amount", "200.00")
                            .param("description", "Reserva"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/accounts"))
                    .andExpect(flash().attribute("success", "Transferência realizada com sucesso!"));

            verify(entryService).transferBetweenAccounts(eq(user), eq(1L), eq(2L),
                    eq(new BigDecimal("200.00")), eq("Reserva"), any());
        }

        @Test
        @DisplayName("service lança IllegalArgumentException → redireciona com a mensagem de erro")
        @WithMockUser
        void serviceExceptionRedirectsWithError() throws Exception {
            doThrow(new IllegalArgumentException("Conta de origem não encontrada."))
                    .when(entryService).transferBetweenAccounts(any(), any(), any(), any(), any(), any());

            mockMvc.perform(post("/accounts/transfer").with(csrf())
                            .param("fromAccountId", "99")
                            .param("toAccountId", "2")
                            .param("amount", "100.00"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/accounts"))
                    .andExpect(flash().attribute("error", "Conta de origem não encontrada."));
        }
    }

    // =========================================================================
    // POST /accounts/{id}/delete
    // =========================================================================

    @Nested
    @DisplayName("POST /accounts/{id}/delete")
    class PostDelete {

        @Test
        @DisplayName("desativa conta → chama service, redireciona para /banks com flash de sucesso")
        @WithMockUser
        void deactivatesAndRedirectsToBanks() throws Exception {
            mockMvc.perform(post("/accounts/3/delete").with(csrf()))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/banks"))
                    .andExpect(flash().attribute("success", "Conta desativada."));

            verify(accountService).deactivate(3L, user);
        }
    }
}
