package br.ufv.sin142.ride_fleet.security;

import java.util.UUID;

/**
 * Identidade do usuario autenticado, extraida do JWT.
 *
 * O UUID ja vem assinado no subject do token. Antes ele era lido e descartado,
 * e o principal era o e-mail - o que obrigaria cada endpoint a fazer um
 * findByEmail para recuperar informacao que a requisicao ja trazia. Em duas
 * instancias sob carga isso e latencia autoinfligida, e pior: ela entra na
 * janela que decide o overflow, fazendo o servico delegar corridas por causa de
 * um custo que ele mesmo criou.
 *
 * Usar em controller com {@code @AuthenticationPrincipal AuthenticatedUser user}.
 */
public record AuthenticatedUser(UUID id, String email, String role) {

    public boolean hasRole(String expected) {
        return expected.equals(role);
    }
}
