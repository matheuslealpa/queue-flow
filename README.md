# QueueFlow

API genérica de gerenciamento de filas de atendimento (farmácias, clínicas, laboratórios, órgãos públicos…).

## Módulos

| Módulo           | Conteúdo                                                                  | Dependências           |
|------------------|---------------------------------------------------------------------------|------------------------|
| `domain`         | Agregados, objetos de valor, eventos e portas de repositório              | nenhuma                |
| `application`    | Casos de uso e portas técnicas (`UnitOfWork`, `TicketSequence`…)          | `domain`               |
| `infrastructure` | PostgreSQL (Spring JDBC + Flyway), transações, eventos via Redis Pub/Sub  | `application`, Spring  |
| `api`            | REST `/api/v1`, SSE, Problem Details, agendador de expiração de chamadas  | `infrastructure`       |

## Executando

Pré-requisitos: JDK 25, Maven 3.9+, Docker.

```bash
docker compose up -d --build        # PostgreSQL + Redis + API em http://localhost:8080 + web em :4200
# ou: banco e Redis no Docker, API pela IDE / Maven
docker compose up -d postgres redis
mvn -pl api -am spring-boot:run
# sem Redis (instância única): EVENTS_BROKER=local mvn -pl api -am spring-boot:run
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

### Frontend (`web/`)

Aplicação Angular 22 (standalone, zoneless, signals) com [spartan/ui](https://spartan.ng) e Tailwind v4. Telas:
fila (`/admin`), guichê (`/operador/:id`), totem (`/totem`), painel para TV (`/painel`), configurações e setup.
Requer Node 24 LTS (ver `web/.nvmrc`) e pnpm (`corepack enable pnpm`).

```bash
cd web
pnpm install
pnpm start        # http://localhost:4200, com /api repassado para localhost:8080
pnpm test         # Vitest
```

Pelo compose, `docker compose up -d --build` também sobe o front em **http://localhost:4200** (nginx).

As telas acompanham a fila em tempo real (SSE, ver abaixo) e só voltam a consultar por polling enquanto a conexão
estiver caída; o indicador no rodapé do menu mostra o estado. Como a API ainda não lista organizações, filas e
guichês, esses IDs ficam no `localStorage`; o JSON em *Configurações* leva o ambiente para outro navegador.

## Eventos em tempo real (SSE)

`GET /api/v1/queues/{id}/events` (`Accept: text/event-stream`, mesmo `X-Organization-Id` das demais rotas) mantém
uma conexão Server-Sent Events com as mudanças da fila. O primeiro evento é `ready`; depois chegam
`entry.created`, `entry.called`, `entry.call-missed`, `entry.no-show`, `entry.returned`, `entry.cancelled`,
`entry.priority-changed`, `entry.service-started`, `entry.service-completed`, `queue.status-changed` e
`queue.policy-changed`, cada um com um JSON curto (`queueId`, `entryId`, `ticket`, `status`, `servicePointName`…).
A ideia é avisar, não replicar estado: o cliente recarrega o que precisa pela API REST. Um comentário `:ping` a
cada 20s mantém proxies abertos.

Os eventos de domínio são repassados após o commit para o Redis Pub/Sub (canal
`queueflow:queue-events:{organização}:{fila}`), e cada instância da API entrega às suas conexões; assim funciona com
várias instâncias atrás de um balanceador. Pub/Sub não guarda mensagens: quem reconecta recarrega o estado. Com o
Redis fora do ar, a API continua respondendo (a publicação desiste em 2s) e entrega só aos clientes da própria
instância. `EVENTS_BROKER=local` dispensa o Redis em ambientes de instância única.

```bash
curl -N http://localhost:8080/api/v1/queues/$QUEUE/events -H "X-Organization-Id: $ORG"
```

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
