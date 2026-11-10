# RideFleet — serviço de corridas por aplicativo

Back-end em **Java 21 + Spring Boot 4 + PostgreSQL** de um serviço de transporte por aplicativo (passageiros, motoristas e corridas), desenvolvido na disciplina de **Sistemas Distribuídos (SIN 142)** da UFV — Campus Rio Paranaíba.

No projeto da disciplina, vários serviços como este se conectam a um **Core** comum: quando um serviço fica congestionado, ele delega corridas a serviços parceiros por meio de um leilão coordenado pelo Core. Este repositório é o serviço cliente.

> **Status:** em desenvolvimento. A primeira etapa (back-end do serviço: autenticação, motoristas, ciclo de vida da corrida e política de overflow) está concluída e testada. A integração com o Core (delegação de entrada e saída) é a próxima etapa.

## O que já está implementado

| Área | O que faz |
|---|---|
| Autenticação | cadastro e login de passageiros e motoristas, login de administrador; JWT com a identidade do usuário no principal |
| Motoristas | perfil, disponibilidade, localização, exclusão lógica e gestão pelo administrador |
| Corridas | solicitação com estimativa de preço e ETA, atribuição automática de motorista, aceite, recusa, início, conclusão e cancelamento |
| Overflow | decide quando o serviço está congestionado (sem motoristas livres, fila de corridas pendentes ou latência média alta) e marca a corrida para delegação |
| Relógio lógico | relógio de Lamport thread-safe, usado para ordenar os eventos das corridas |
| Erros | tratamento centralizado com exceções tipadas e respostas padronizadas |
| Observabilidade | health check e métricas no formato do Prometheus via Actuator |

## Decisões técnicas

### Um motorista nunca recebe duas corridas

Com várias instâncias do serviço atendendo ao mesmo tempo, duas requisições podem escolher o mesmo motorista livre. A reserva do motorista é um `UPDATE` condicional: a linha só muda de `AVAILABLE` para ocupado se ainda estiver `AVAILABLE`. O banco garante que só uma transação vence; quem recebe "0 linhas alteradas" tenta o próximo candidato. A entidade também tem `@Version` como segunda barreira.

A primeira opção avaliada foi `SELECT ... FOR UPDATE SKIP LOCKED`. Ela foi descartada depois de um experimento: no H2 usado nos testes, com `LIMIT 1`, a consulta pula a linha travada e devolve vazio, em vez de avançar para a próxima livre como o PostgreSQL faz. Com 10 motoristas e 10 threads, só uma corrida era atribuída. O `UPDATE` condicional se comporta igual nos dois bancos.

Testes de integração com threads concorrentes comprovam que:

- um único motorista disputado nunca é atribuído duas vezes;
- com N motoristas e N corridas simultâneas, cada motorista fica com exatamente uma;
- corridas excedentes permanecem no pool, sem se perder.

### Máquina de estados em um único lugar

O fluxo principal é `REQUEST → MATCH → CONFIRM → IN_TRANSIT → COMPLETE`, com `DELEGATED` e `CANCELLED` como saídas. A tabela de transições permitidas fica no próprio enum `RideStatus`, e não espalhada pelos services: é a única fonte da regra e é testável sem Spring e sem banco. Uma transição inválida vira erro de negócio tipado.

### Política de overflow configurável

O serviço se considera congestionado quando qualquer sinal dispara: nenhum motorista disponível, corridas pendentes acima de um limite ou latência média acima de um limite numa janela de tempo. Um interceptor registra a latência de cada requisição numa janela deslizante thread-safe. A janela só opina depois de um número mínimo de amostras, para que uma única requisição lenta de *cold start* não provoque delegação.

### Segredos fora do código

A senha do administrador inicial não é versionada: se não for informada por variável de ambiente, o seed gera uma aleatória e a registra uma vez no log. O segredo do JWT e a chave do Core vêm de variáveis de ambiente.

## Endpoints principais

| Recurso | Rotas |
|---|---|
| Autenticação | `POST /api/v1/auth/{passenger,driver}/register`, `POST /api/v1/auth/{passenger,driver,admin}/login` |
| Motorista (próprio) | `GET/PATCH /api/v1/drivers/me`, `PATCH /me/status`, `PATCH /me/location`, `GET /me/rides` |
| Motorista (admin) | `GET/PUT/DELETE /api/v1/drivers/{id}`, `PUT /{id}/status` |
| Corridas | `POST /api/v1/rides/request`, `GET /me`, `GET /pending`, `GET /{id}`, `POST /{id}/{accept,decline,start,complete,cancel}` |

## Como executar

Pré-requisitos: Java 21 e um PostgreSQL acessível.

```bash
# Banco local (exemplo com Docker)
docker run -d --name ride-fleet-db -p 5432:5432 \
  -e POSTGRES_DB=ride_fleet_db -e POSTGRES_PASSWORD=postgres postgres:16

# Segredos (os valores padrão do application.yml servem só para desenvolvimento)
export JWT_SECRET="um-segredo-longo-e-aleatorio"
export ADMIN_PASSWORD="senha-do-admin"

./mvnw spring-boot:run
```

Variáveis do banco: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` (padrões: `localhost`, `5432`, `ride_fleet_db`, `postgres`, `postgres`).

Testes (usam H2 em memória em modo PostgreSQL, sem precisar de banco):

```bash
./mvnw test
```

## Stack

Java 21 · Spring Boot 4 (Web MVC, Data JPA, Security, Validation, Actuator) · PostgreSQL · JWT · JUnit 5 · H2 (testes)

## Autor

Arthur Roberto de Paula Neto — [GitHub](https://github.com/arthurneto2) · [LinkedIn](https://linkedin.com/in/arthurneto-dev)
