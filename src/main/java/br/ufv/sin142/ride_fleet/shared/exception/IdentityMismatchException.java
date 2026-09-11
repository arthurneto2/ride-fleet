package br.ufv.sin142.ride_fleet.shared.exception;

import java.util.UUID;

/**
 * Identidade enviada no corpo da requisicao divergindo da identidade do token.
 *
 * Falha em voz alta em vez de ignorar o campo silenciosamente: honrar o id do
 * corpo seria permitir que um usuario agisse em nome de outro (IDOR), e ignorar
 * sem avisar deixaria um front-end bugado acreditando ter criado a corrida para
 * outra pessoa.
 */
public class IdentityMismatchException extends ForbiddenOperationException {

    public IdentityMismatchException(UUID fromToken, UUID fromBody) {
        super("Identidade do corpo (" + fromBody + ") nao corresponde a do token (" + fromToken + ").");
    }
}
