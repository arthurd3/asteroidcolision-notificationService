[← O front-end em JSP](06-jsp-frontend.md) · [English](../en/07-testing.md) · **Português (Brasil)**

# Testes

Cinco técnicas, cada uma usada onde é a coisa mais barata capaz de provar a afirmação. A
pergunta interessante nunca é "testamos isso?", e sim "o que teria pegado isso?".

```bash
./mvnw test      # testes unitários e de slice, sem Docker
./mvnw verify    # acrescenta os testes de integração, precisa de um daemon Docker
```

## Stubs escritos à mão, quando a interface é pequena

O `AsteroidAlertingServiceTest` não usa framework de mock nenhum. O publisher é uma
subclasse que registra o que recebeu; o relógio é `Clock.fixed(...)`.

Até pouco tempo o `NasaNeoClient` tinha um método e podia ser um lambda:

```java
final NasaNeoClient client = (from, to) -> feed;
```

Adicionar `lookup` e `browse` acabou com isso — três métodos não são uma interface
funcional — então virou uma classe nomeada cujos dois métodos não usados **lançam
exceção**:

```java
@Override
public Asteroid lookup(String neoReferenceId) {
    throw new UnsupportedOperationException("alerting never looks an object up");
}
```

Devolver `null` ou lista vazia ali seria uma mentira: permitiria que uma mudança futura
passasse a chamar `lookup` no caminho de alertas e o teste continuasse passando.

## Mockito, quando o colaborador não é pequeno

`DeliveryDispatchServiceTest` e `NotificationQueryServiceTest` usam Mockito, porque o
`NotificationDeliveryRepository` tem uma dúzia de métodos herdados e escrever um stub à
mão seria ruído.

A regra que este projeto segue: **escreva à mão quando a interface tem um ou dois métodos
que você controla; mocke quando é a interface gorda de outra pessoa.**

## WireMock, quando você precisa de um socket de verdade

Todo teste de cliente da NASA roda contra um servidor HTTP real em uma porta real. Isso
não é cerimônia — um `RestClient` mockado não consegue demonstrar o que de fato dá errado:

- um 429 com corpo de erro em JSON,
- uma resposta truncada ou malformada,
- uma conexão recusada de saída,
- um corpo vazio com status 200.

O `MockRestServiceServer` também não expressa isso; ele verifica requisições, não
comportamento de transporte.

Note a dependência:

```xml
<dependency>
  <groupId>org.wiremock</groupId>
  <artifactId>wiremock-standalone</artifactId>
  <version>3.13.2</version>
  <scope>test</scope>
</dependency>
```

**`wiremock-standalone`, o jar sombreado, de propósito.** O `org.wiremock:wiremock`
comum precisa do Jetty 11, enquanto o Boot 4 gerencia o Jetty 12, e o descompasso falha na
inicialização com "Jetty 11 is not present". O jar sombreado traz seu próprio servidor e
evita o conflito por completo.

### Fixtures, e quais são reais

`RestNasaDonkiClientTest` e `RestNasaEpicClientTest` parseiam respostas gravadas em
`src/test/resources/nasa/` em vez de JSON escrito à mão. Escrever JSON a partir da
documentação é como um record acaba mapeando campos que não existem.

As quatro fixtures são capturas reais. O `README-fixtures.md` naquele diretório registra
isso e explica por que a procedência vale ser escrita: `ignoreUnknown` torna um campo
*extra* inofensivo, mas não faz nada quanto a um *faltando*, então um componente cujo nome
não bate com o que a NASA envia desserializa como `null` silenciosamente — e um teste que
parseia uma fixture escrita à mão passa mesmo assim. Duas destas foram escritas à mão por
um tempo, exatamente por isso, e o arquivo diz. Ele também dá os comandos para
regravá-las.

## `@WebMvcTest`, para o contrato HTTP

Todo controller tem um teste de slice. Eles cobrem o formato da resposta, os códigos de
status e — importante — o corpo `problem+json`:

```java
.andExpect(status().isBadRequest())
.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
.andExpect(jsonPath("$.title").value("Invalid scan window"))
.andExpect(jsonPath("$.type").value("https://asteroid.arthur.com/problems/invalid-scan-window"))
```

Um slice não roda `@ConfigurationPropertiesScan` nem o `ClockConfig`, então os testes
fornecem isso à mão em um `@TestConfiguration` aninhado.

**No `web-ui`, um teste de slice não pode verificar HTML** — não há contêiner de servlet,
então o MockMvc só relata o forward. Verifique o nome da view e o modelo; deixe a
renderização para o `WebUiPagesIT`.

## Testcontainers, para tudo que o banco ou o broker decide

Três testes de integração, executados pelo Failsafe em `./mvnw verify`.

**`NotificationPersistenceIT`** — MySQL 8.4 real. Cobre o que um banco em memória não
reproduziria fielmente: as constraints únicas que tornam a ingestão idempotente, o
`DECIMAL(20,4)` preservando os quilômetros fracionários, o `SKIP LOCKED` respeitando um
limite de lote, e a query de projeção sobrevivendo a `entityManager.clear()` — que é o
que prova que `open-in-view: false` está tratado e não funcionando por acaso.

**`AsteroidAlertConsumeIT`** — Kafka *e* MySQL reais. Os comportamentos mais importantes
do pipeline não tinham teste nenhum antes disso: a afirmação central do README sobre
idempotência, e a rota retentativa-depois-dead-letter. Escrevê-lo revelou um bug real de
produção no `DeadLetterProducerConfig`, descrito no [documento 04](04-event-driven-kafka.md).

**`NasaCacheIT`** — teste de integração e não unitário porque `@Cacheable` funciona por
proxy: chamar um cliente diretamente contorna o advice, e um teste unitário passaria com
ou sem cache funcionando. Seu caso mais valioso é o `donkiEndpointsDoNotShareACache`, que
protege contra um bug silencioso — o `SimpleKeyGenerator` chaveia só por argumentos, e os
três endpoints do DONKI compartilham a assinatura.

**`WebUiPagesIT`** — o único teste que compila os JSPs. Parametrizado sobre todas as
páginas; verifica 200, `text/html` e um marcador por página. Mais três afirmações
específicas: que `<c:out>` realmente escapa, que a página de erro é alcançada em vez do
whitelabel do Boot, e que nenhuma página contém `api_key`.

Uma nota sobre o Testcontainers 2.0, que costuma pegar as pessoas:

```java
// o generic autotipado foi removido, então MySQLContainer<?> não compila mais
static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");
```

…e os artifact ids dos módulos agora têm prefixo: `testcontainers-mysql`,
`testcontainers-kafka`. Existem também três classes de container Kafka; a nativa de KRaft,
`org.testcontainers.kafka.KafkaContainer`, é a que corresponde ao broker do
`docker-compose.yml`.

## O teste que protege uma string

O `NasaConfigurationTest` é incomum e vale copiar. O `retry-exceptions` do Resilience4j
cita uma classe por **string totalmente qualificada** no YAML. Mova ou renomeie essa
classe e a retentativa para de funcionar silenciosamente — sem erro, sem aviso.

Então o teste carrega o YAML real, pega os registries e verifica que o predicado ainda
casa. Transforma um acoplamento invisível em build quebrado.

Todo projeto tem alguns acoplamentos que o compilador não enxerga. Encontrá-los e fixar
cada um com um teste é desproporcionalmente valioso.

## O que o Failsafe precisou

O POM pai o ativa à mão:

```xml
<plugin>
  <artifactId>maven-failsafe-plugin</artifactId>
  <executions><execution><goals>
    <goal>integration-test</goal><goal>verify</goal>
  </goals></execution></executions>
</plugin>
```

O `spring-boot-starter-parent` apenas *gerencia* a versão do Failsafe; não vincula seus
goals. Sem isso, toda classe `*IT` é silenciosamente ignorada e o build reporta sucesso —
o que é pior do que não ter testes de integração, porque parece que você tem.

## Experimente

```bash
./mvnw test                                   # rápido, sem Docker
./mvnw verify                                 # tudo, precisa de Docker

# um único teste de integração
./mvnw -pl notification-service verify -Dtest='!*' -Dit.test=AsteroidAlertConsumeIT

# prove que a proteção do FQCN funciona: mude retry-exceptions em
# asteroid-service/src/main/resources/application.yaml para uma classe inexistente
# e rode
./mvnw -pl asteroid-service test -Dtest=NasaConfigurationTest
```

O último falha, que é exatamente o motivo de ele existir.
