[← Orientado a eventos com Kafka](04-event-driven-kafka.md) · [English](../en/05-persistence-and-flyway.md) · **Português (Brasil)**

# Persistência e Flyway

Três tabelas no MySQL, de propriedade do `notification-service`. O `asteroid-service` e o
`web-ui` não têm banco de dados algum.

## O Flyway é dono do esquema; o Hibernate apenas confere

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
```

Este projeto usava `ddl-auto: update`. É conveniente e errado para qualquer coisa que
sobreviva a uma demonstração:

- não pode ser revisado — a mudança de esquema é um diff de Java, não de SQL;
- não pode sofrer rollback;
- nunca remove nem altera nada com segurança, acumulando colunas mortas silenciosamente;
- faz coisas diferentes dependendo de qual versão das suas entidades subiu primeiro.

`validate` faz o Hibernate comparar o mapeamento com o esquema real na inicialização e
**falhar o contexto** se discordarem. Um mapeamento que divergiu da migração vira erro de
inicialização, não uma surpresa em tempo de execução na única query que toca a coluna
alterada.

As migrações ficam em `notification-service/src/main/resources/db/migration`. A próxima é
`V4__*.sql`.

## As tabelas

**`notification`** — uma linha por alerta recebido.

A parte estrutural é uma constraint:

```sql
CONSTRAINT uk_notification_event_id UNIQUE (event_id)
```

É isso que faz a reentrega do Kafka e as varreduras sobrepostas do produtor virarem
no-op em vez de linha duplicada e e-mail duplicado. Veja o
[documento 04](04-event-driven-kafka.md).

Note também `miss_distance_kilometers DECIMAL(20,4)`. O padrão do Hibernate para
`BigDecimal` é `decimal(38,2)`, que trunca silenciosamente as distâncias de quatro casas
da NASA. A precisão é explícita na coluna *e* na anotação `@Column`, e o
`NotificationPersistenceIT` verifica que `50661467.0317` sobrevive à ida e volta.

**`subscriber`** — uma linha por destinatário de e-mail.

Chamada `subscriber` e não `user`, porque `user` é reservada no MySQL 8 e toda query que
a tocasse precisaria de aspas.

**`notification_delivery`** — a junção: uma linha por notificação por assinante.

Esta tabela é o motivo de o modelo ter mudado. O estado de entrega era um único booleano
`emailSent` na própria notificação, sem ligação com um destinatário — então não havia
como saber *quem* recebeu *o quê*, e um lote parcialmente bem-sucedido era
indistinguível de um completo. Agora cada linha carrega seu próprio `status`,
`attempts`, `sent_at` e `last_error`.

## Reivindicando trabalho: `SKIP LOCKED`

O worker de despacho reivindica um lote:

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
@Query("select d from NotificationDelivery d where d.status = 'PENDING' order by d.id")
List<NotificationDelivery> claimPending(Limit limit);
```

`PESSIMISTIC_WRITE` emite `SELECT ... FOR UPDATE`. O `-2` é a codificação do Hibernate
para **`SKIP LOCKED`** — linhas que outra instância já está trabalhando são puladas em
vez de esperadas.

Sem isso, duas instâncias do serviço serializariam: a segunda bloqueia nos locks da
primeira e não faz trabalho útil. Com isso, elas compartilham a fila e nenhum assinante
recebe o mesmo alerta duas vezes.

Uma linha só é marcada `SENT` **depois** que o servidor de e-mail aceitou a mensagem. Uma
falha registra a tentativa e deixa a linha reivindicável até `max-attempts`, quando ela
estaciona como `FAILED` com o motivo em `last_error`. Nada se perde e nada é retentado
para sempre.

## `open-in-view: false`, e o que isso obriga

```yaml
spring:
  jpa:
    open-in-view: false
```

Open Session In View mantém a sessão do Hibernate aberta durante toda a requisição HTTP,
de forma que associações lazy ainda carregam durante a serialização da resposta. Vem
ligado por padrão no Spring Boot e esconde um problema real: o contexto de persistência
sobrevive à transação, queries disparam da camada de view, e você tem N+1 selects sem
fronteira transacional ao redor.

Desligar é o certo, e tem uma consequência que precisa ser projetada. Quando um
controller serializa seu resultado, a sessão já fechou, então devolver uma entidade
entrega ao serializador um objeto desanexado e a primeira leitura lazy falha — **dentro
do message converter**, onde o stack trace não explica nada sobre a causa.

Por isso a API de leitura nunca devolve entidades. O `NotificationQueryService` é
`@Transactional(readOnly = true)` e devolve records, e as linhas por destinatário vêm de
uma **expressão de construtor JPQL**:

```java
@Query("""
        select new com.arthur.asteroid.notification.web.DeliveryView(
            s.email, s.fullName, d.status, d.attempts, d.sentAt, d.lastError, d.createdAt)
        from NotificationDelivery d
        join d.subscriber s
        where d.notification.eventId = :eventId
        order by s.email
        """)
List<DeliveryView> findViewsByEventId(String eventId);
```

Construir o DTO *dentro da query* torna o erro estruturalmente impossível, em vez de algo
que um revisor precisa notar. O `NotificationPersistenceIT` chama `entityManager.clear()`
antes de ler a projeção, o que reproduz a sessão fechada — então se isso um dia regredir
para busca de entidade, o teste falha em vez da produção.

### A query de contagem, e a versão esperta que não está aqui

As contagens de entrega de uma página vêm de uma query agrupada, não de uma por linha:

```java
@Query("""
        select d.notification.id, d.status, count(d)
        from NotificationDelivery d
        where d.notification.id in :notificationIds
        group by d.notification.id, d.status
        """)
List<Object[]> countByNotificationAndStatus(Collection<Long> notificationIds);
```

`Object[]`, não um record, e isso é deliberado. Uma expressão de construtor sobre
`count(d)` recebe um `Long`, que não casa com um componente `long` de record — e essa
falha aparece quando a query é compilada na *inicialização*, não em tempo de compilação. A
montagem é Java comum no `NotificationQueryService`, onde é legível e não pode falhar
tarde.

A versão de query única — um `select new NotificationSummary(... sum(case when ...)) ...
group by` — ainda precisaria de um `countQuery` escrito à mão para paginar. Duas queries
simples evitam as duas armadilhas.

## Paginação

`GET /api/v1/notifications` devolve um record `PageResponse` explícito, nunca o `Page` do
Spring Data. Serializar `PageImpl` diretamente não é suportado: emite aviso, e sua
estrutura JSON não faz parte do contrato público do Spring Data, então uma atualização
pode renomear campos sob um cliente que dependia deles. O `web-ui` é exatamente esse
cliente.

## Índices, incluindo um deliberadamente ausente

`V3__notification_history_read_index.sql` adiciona um índice e explica por que não
adiciona um segundo:

```sql
CREATE INDEX ix_notification_created_at ON notification (created_at);
```

A página de histórico ordena por `created_at desc` e pagina; sem isso, aquela ordenação é
um filesort sobre a tabela inteira a cada visualização, e a tabela só cresce.

Nenhum índice é adicionado em `notification_delivery(notification_id)` para a query
agrupada, porque `uk_delivery_notification_subscriber` já é
`(notification_id, subscriber_id)` e o MySQL usa seu **prefixo mais à esquerda** para uma
busca `where notification_id in (...)`. Um segundo índice seria outra cópia da mesma
árvore B para manter a cada insert.

## Por que as entidades não são `@Data`

O `@Data` do Lombok gera `equals`/`hashCode` sobre todos os campos, inclusive o id
gerado. O hash de uma entidade portanto *muda* no momento em que `IDENTITY` atribui esse
id após o persist, o que quebra a pertinência em `HashSet` e viola o contrato de
identidade da JPA.

`Notification` usa sua chave natural, `eventId`, estável desde a construção.
`NotificationDelivery` — que não tem chave natural — usa o id quando presente e um
`hashCode` constante, que é o padrão seguro conhecido.

## Experimente

```bash
docker compose up -d
./mvnw -pl notification-service verify   # roda os ITs com Testcontainers

# veja o esquema real que o Flyway produziu
docker exec -it asteroid-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" asteroidalerting \
  -e "show create table notification_delivery\G"

# confira o estado das entregas
docker exec -it asteroid-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" asteroidalerting \
  -e "select id, status, attempts, last_error from notification_delivery limit 5;"
```

Depois quebre de propósito: adicione um campo a `Notification` sem migração e suba o
serviço. Ele falha na inicialização com erro de validação de esquema, que é exatamente
para isso que serve `ddl-auto: validate`.
