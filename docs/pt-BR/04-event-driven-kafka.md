[← Clientes HTTP e resiliência](03-http-clients-and-resilience.md) · [English](../en/04-event-driven-kafka.md) · **Português (Brasil)**

# Orientado a eventos com Kafka

Duas ideias sustentam este capítulo: **idempotência**, que torna seguro reexecutar, e
**dead-lettering**, que impede uma mensagem ruim de parar tudo que está atrás dela.

## O contrato

Um record, em seu próprio módulo, do qual os dois serviços dependem:

```java
public record AsteroidCollisionEvent(
        @NotBlank String eventId,
        @NotNull Instant occurredAt,
        @NotBlank String asteroidId,
        @NotBlank String asteroidName,
        @NotNull LocalDate closeApproachDate,
        @NotNull @Positive BigDecimal missDistanceKilometers,
        @Positive double estimatedDiameterAvgMeters
) {}
```

Como os dois lados compilam contra ele, o esquema não pode divergir. Mude um componente
e ambos param de compilar — que é o objetivo.

`@JsonIgnoreProperties(ignoreUnknown = true)` é o que permite a um consumidor v1
continuar funcionando com um produtor que começou a enviar um campo a mais.
**Adicionar** um campo é retrocompatível. **Remover ou trocar o tipo** de um não é, e
exige um record `v2` *ao lado* do v1, não uma edição nele.

## O alias no cabeçalho

Os dois serviços configuram o mesmo type mapping:

```yaml
spring.json.type.mapping: >-
  asteroid-collision.v1:com.arthur.asteroid.contracts.v1.AsteroidCollisionEvent
```

Sem isso, o serializador JSON do Spring escreve o **nome totalmente qualificado da
classe** do produtor no cabeçalho `__TypeId__`, e o consumidor procura exatamente essa
classe. Os dois serviços ficam então acoplados pela estrutura de pacotes: renomeie um
pacote no produtor e toda mensagem já presente no tópico se torna ilegível.

Com isso, trafega o alias `asteroid-collision.v1`. Qualquer um dos lados pode mover seus
pacotes livremente. O consumidor ainda restringe `spring.json.trusted.packages` a
`com.arthur.asteroid.contracts.v1` — ampliar isso para aceitar um FQCN do produtor
reintroduziria exatamente o acoplamento que o alias remove.

O `AsteroidAlertConsumeIT` monta seu produtor com essas configurações de propósito, para
que o contrato do alias fique coberto em vez de presumido.

## Idempotência: por que reexecutar é seguro

Esta é a afirmação central do projeto, e precisa de dois mecanismos porque há duas
origens independentes de duplicação.

**Duplicação um: varreduras sobrepostas.** Cada varredura cobre uma janela deslizante —
hoje até sete dias à frente. Rode duas vezes no mesmo dia e a mesma aproximação é
legitimamente relatada duas vezes. Isso é comportamento correto do produtor, não um bug.

**Duplicação dois: o Kafka é at-least-once.** Um consumidor que processa uma mensagem e
morre antes de confirmar seu offset verá a mensagem de novo.

A resposta para as duas é a mesma: fazer a identidade do evento ser função *daquilo que
ele descreve*, e não *de quando foi produzido*.

```java
private static String eventId(final String asteroidId, final LocalDate closeApproachDate) {
    final String businessKey = asteroidId + ':' + closeApproachDate;
    return UUID.nameUUIDFromBytes(businessKey.getBytes(StandardCharsets.UTF_8)).toString();
}
```

Um UUID aleatório faria toda nova varredura parecer um evento novo, e o consumidor
armazenaria e enviaria e-mail de novo. Derivar o id de asteroide + data de aproximação
faz a segunda varredura produzir um evento byte a byte idêntico.

O consumidor então tem duas camadas:

- `NotificationIngestService.ingest()` encerra cedo com `existsByEventId(...)`;
- o banco tem `uk_notification_event_id`, de modo que até uma corrida entre duas
  instâncias termina em violação de constraint em vez de linha duplicada.

A checagem na aplicação é o caminho rápido; a constraint é a que de fato vale. Uma
segunda constraint, `uk_delivery_notification_subscriber`, impede fan-out duplicado para
o mesmo destinatário.

A chave da mensagem é o id do asteroide, então todas as aproximações de um objeto caem na
mesma partição e mantêm sua ordem relativa.

## Tratamento de falhas: o dead-letter topic

Uma mensagem que não pode ser desserializada nunca vai ter sucesso, por mais vezes que
seja retentada. Sem um lugar para colocá-la, o consumidor tenta para sempre e **toda
mensagem atrás dela naquela partição para de ser processada** — uma parada silenciosa e
total de um terço do pipeline.

O `KafkaErrorHandlingConfig` combina:

- `ExponentialBackOffWithMaxRetries(4)`, 500 ms iniciais, ×2, com teto de 10 s — para
  falhas transitórias, como uma oscilação do banco;
- um `DeadLetterPublishingRecoverer` roteando para `record.topic() + ".DLT"`,
  preservando a partição original;
- `DeserializationException` e `MessageConversionException` registradas como **não
  retentáveis**, porque retentar um payload malformado é desperdício puro.

### Duas sutilezas que custaram tempo real

**O produtor do DLT precisa de `DelegatingByTypeSerializer`.** Um registro enviado ao DLT
carrega um de dois payloads bem diferentes: uma mensagem que falhou na *desserialização*
é encaminhada como o `byte[]` bruto original, enquanto uma que desserializou bem mas
explodiu no listener é encaminhada como objeto. Com um `StringSerializer` comum, o
primeiro caso falha com `Can't convert value of class [B`, o recoverer lança exceção, o
offset nunca é confirmado, e o container relê a mesma mensagem envenenada para sempre —
precisamente a falha que o DLT existe para evitar.

**O produtor do DLT precisa usar `KafkaConnectionDetails`, não só `KafkaProperties`.**
Este foi um bug real neste repositório, descoberto ao escrever o
`AsteroidAlertConsumeIT`. A auto-configuração de Kafka do próprio Boot chama
`buildProducerProperties()` **e depois** aplica `KafkaConnectionDetails` por cima.
Connection details são o mecanismo pelo qual um endereço de broker chega de outro lugar
que não o YAML — um `@ServiceConnection` do Testcontainers, suporte a Docker Compose, um
binding de nuvem. Construir uma factory só a partir das properties mantém o que quer que
`spring.kafka.bootstrap-servers` diga.

O sintoma é desagradável: o consumidor conecta e lê perfeitamente, o error handler
identifica corretamente a mensagem envenenada, e então o dead-lettering falha com
`Topic asteroid-alert.DLT not present in metadata after 60000 ms` — depois do que o
registro não recuperado bloqueia sua partição e tudo atrás dele para silenciosamente.

**O tópico `.DLT` precisa ser declarado.** O broker roda com
`KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"`, então sem o bean `NewTopic` em
`KafkaTopicConfig` o recoverer falharia com `UNKNOWN_TOPIC_OR_PARTITION`. O consumidor
declara seu próprio DLT — o produtor não tem motivo para saber que um consumidor falhou.

## Configurações do produtor

```yaml
acks: all
retries: 10
properties:
  enable.idempotence: true
  max.in.flight.requests.per.connection: 5
  delivery.timeout.ms: 120000
```

`acks: all` espera por todas as réplicas em sincronia. `enable.idempotence: true` é o que
torna `retries: 10` seguro — sem isso, um envio retentado pode produzir uma duplicata no
broker, e retentar troca um problema por outro.

## Experimente

```bash
docker compose up -d

# publique a mesma varredura duas vezes e veja nada mudar na segunda
curl -X POST localhost:8080/api/v1/asteroid-alerting/alert
curl -s localhost:8081/api/v1/notifications | python3 -c "import json,sys; print('alertas:', json.load(sys.stdin)['totalElements'])"

curl -X POST localhost:8080/api/v1/asteroid-alerting/alert
curl -s localhost:8081/api/v1/notifications | python3 -c "import json,sys; print('alertas:', json.load(sys.stdin)['totalElements'])"
```

As duas contagens são idênticas. Depois observe as mensagens no Kafka UI em
<http://localhost:8084> — o tópico tem o dobro de registros que o banco tem de linhas,
que é exatamente o ponto.

Para ver o dead-lettering, publique algo malformado:

```bash
docker exec -it asteroid-kafka /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 --topic asteroid-alert
> not json at all
```

Ele aparece em `asteroid-alert.DLT` em poucos segundos, e a próxima mensagem válida
continua sendo processada. O `AsteroidAlertConsumeIT` verifica os três comportamentos sem
que você precise digitar nada disso.
