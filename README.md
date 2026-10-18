# QueueFlow

API genérica de gerenciamento de filas de atendimento (farmácias, clínicas, laboratórios, órgãos públicos…).

## Módulos

| Módulo           | Conteúdo                                                                  | Dependências           |
|------------------|---------------------------------------------------------------------------|------------------------|
| `domain`         | Agregados, objetos de valor, eventos e portas de repositório              | nenhuma                |
| `application`    | Casos de uso e portas técnicas (`UnitOfWork`, `TicketSequence`…)          | `domain`               |
| `infrastructure` | PostgreSQL (Spring JDBC + Flyway), transações, publicação de eventos      | `application`, Spring  |
| `api`            | REST `/api/v1`, Problem Details, agendador de expiração de chamadas       | `infrastructure`       |

## Executando

Pré-requisitos: JDK 25, Maven 3.9+, Docker.

```bash
docker compose up -d --build        # PostgreSQL + API em http://localhost:8080
# ou: só o banco e a API pela IDE / Maven
docker compose up -d postgres
mvn -pl api -am spring-boot:run
```

### Console web

Com a API no ar, abra **http://localhost:8080/console**. O botão *Criar ambiente de demonstração* cadastra
organização, fila com prioridades e dois guichês; a partir daí dá para retirar senhas no totem, chamar,
atender, registrar não comparecimento, alterar prioridades e acompanhar o painel e os indicadores. Os IDs do
ambiente ficam no `localStorage` do navegador (*Trocar ambiente* limpa).

Testes (os de integração usam Testcontainers e precisam do Docker):

```bash
mvn verify
```

Variáveis: `DB_URL`, `DB_USER`, `DB_PASSWORD`, `DB_POOL_SIZE`; no compose, `POSTGRES_PORT` e `API_PORT`.

## Identificação das requisições

Ainda não há autenticação. A identidade vem de cabeçalhos, lidos apenas por `RequestContextResolver`
(ponto único a substituir por JWT/OAuth2):

- `X-Organization-Id` — obrigatório (tenant);
- `X-Operator-Id` — operador; as permissões são carregadas do cadastro, nunca da requisição;
- `X-Client-Channel` — totem/integração de autoatendimento (ingresso e desistência).

## Fluxo básico

```bash
B=http://localhost:8080/api/v1
curl -X POST $B/organizations -H 'Content-Type: application/json' \
     -d '{"name":"Clínica","adminName":"Ana"}'          # -> organizationId, adminOperatorId (todas as permissões)
# com -H "X-Organization-Id: $ORG" -H "X-Operator-Id: $ADMIN":
#   POST $B/locations            {"name":"Matriz","timeZone":"America/Sao_Paulo"}
#   POST $B/services             {"code":"CONSULTA","name":"Consulta"}
#   POST $B/service-points       {"locationId":"…","name":"Consultório 1"}
#   POST $B/queues               {"locationId":"…","name":"Triagem","ticketPrefix":"T","policy":{…},"acceptedServices":[…]}
#   POST $B/operators            {"name":"Bruno","permissions":["CALL_ENTRIES"]}
#   POST $B/operators/{id}/assignments {"queueId":"…"}
```

| Operação                         | Endpoint                                   |
|----------------------------------|--------------------------------------------|
| Ingressar (aceita `Idempotency-Key`) | `POST /queues/{id}/entries`            |
| Espera, na ordem de chamada      | `GET /queues/{id}/entries?offset=&limit=`  |
| Chamar próxima (204 se vazia)    | `POST /queues/{id}/calls/next`             |
| Iniciar / concluir atendimento   | `POST /entries/{id}/start` · `/complete`   |
| Não comparecimento               | `POST /entries/{id}/no-show`               |
| Devolver à fila / cancelar       | `POST /entries/{id}/return` · `/cancel`    |
| Reavaliar prioridade             | `POST /entries/{id}/priority`              |
| Status da fila / indicadores     | `PUT /queues/{id}/status` · `GET /queues/{id}/metrics` |

Erros seguem RFC 9457: 400 validação, 401 sem identificação, 403 sem permissão, 404 inexistente ou de outra
organização, 409 transição inválida/idempotência/concorrência, 422 regra de negócio.
