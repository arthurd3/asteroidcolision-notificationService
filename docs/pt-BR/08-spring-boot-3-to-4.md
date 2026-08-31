[← Testes](07-testing.md) · [English](../en/08-spring-boot-3-to-4.md) · **Português (Brasil)**

# Spring Boot 3 para 4

Tudo neste documento custou tempo real a alguém para descobrir. A maioria não produz
mensagem de erro útil; várias não produzem erro nenhum.

## Starters foram renomeados

| Boot 3 | Boot 4 |
|---|---|
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `spring-boot-starter-aop` | `spring-boot-starter-aspectj` |
| `org.springframework.kafka:spring-kafka` | `spring-boot-starter-kafka` |
| `org.flywaydb:flyway-core` sozinho | `spring-boot-starter-flyway` |
| *(parte do `-web`)* | `spring-boot-starter-restclient` |

**O `-restclient` é o que surpreende.** A auto-configuração de `RestClient` e
`RestTemplate` não faz mais parte do starter web. Sem ele você não tem bean
`RestClient.Builder`, e a falha é um erro de bean ausente na inicialização que não
menciona o starter de que você precisa.

**`flyway-core` sozinho não basta mais.** A auto-configuração migrou para
`spring-boot-flyway`, que o starter traz. Com apenas `flyway-core` no classpath, as
migrações simplesmente nunca rodam — sem erro — e, com `ddl-auto: validate`, você recebe
uma falha de validação de esquema que culpa o mapeamento da entidade.

**O Resilience4j é baseado em `@Aspect`**, então precisa do `spring-boot-starter-aspectj`.
Sem ele, `@Retry` e `@CircuitBreaker` compilam, sobem e não fazem absolutamente nada.

## `spring.http.client.*` → `spring.http.clients.*`

Plural. Depreciado desde a 4.0.0. As chaves no singular ainda ligam, então nada quebra de
imediato — mas estão marcadas como depreciadas nos metadados, IDEs sinalizam, e o
`spring-boot-properties-migrator` reclama.

## `server.error.*` → `spring.web.error.*`

**Esta silenciosamente não faz nada.** Toda a família — `server.error.whitelabel.enabled`,
`server.error.include-message`, `server.error.path` e as demais — está depreciada em nível
**`error`** desde a 4.0.0, o que significa que não liga mais. Defina
`server.error.whitelabel.enabled: false` no Boot 4 e o whitelabel continua ligado.

Isso importa aqui porque um `error.jsp` é inalcançável enquanto o whitelabel estiver
ligado — veja o [documento 06](06-jsp-frontend.md).

## `server.servlet.register-default-servlet` agora é `false` por padrão

Nenhum `DefaultServlet` é registrado, então nada serve o document root de um WAR exceto o
Jasper servindo `.jsp`. Assets estáticos em `src/main/webapp/` devolvem 404 sem
explicação. Coloque-os em `src/main/resources/static/`.

## Configurações HTTP por cliente agora são de primeira classe

Não é armadilha — é uma melhoria que vale conhecer. O `HttpClientAutoConfiguration`
publica um bean `HttpClientSettings`, e `withTimeouts(Duration, Duration)` devolve uma
cópia:

```java
settings.getIfAvailable(HttpClientSettings::defaults)
        .withTimeouts(connect, read)
```

Combinado com `ClientHttpRequestFactoryBuilder.build(HttpClientSettings)` e
`RestClient.Builder#requestFactory`, timeouts por cliente são poucas linhas e você mantém
todas as outras configurações que o Boot gerencia. Veja o
[documento 03](03-http-clients-and-resilience.md).

O Boot 4 também traz um HTTP Service Registry (`@ImportHttpServices`,
`spring.http.serviceclient.<group>.*`) que faz isso de forma declarativa.

## Anotações de teste mudaram de pacote

```java
// Boot 3: org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

// e, menos obviamente
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
```

Os starters de teste também foram divididos: `spring-boot-starter-webmvc-test`,
`spring-boot-starter-data-jpa-test`, `spring-boot-starter-restclient-test`,
`spring-boot-starter-kafka-test`.

## Jackson 3

O pacote de runtime é `tools.jackson`; **as anotações continuam em
`com.fasterxml.jackson.annotation`**. Essa separação é deliberada, mas desorienta: uma
classe pode importar dos dois.

```java
import com.fasterxml.jackson.annotation.JsonProperty;   // anotação
import tools.jackson.databind.ObjectMapper;             // runtime
```

O suporte a `java.time` está incorporado ao `jackson-databind` — não há módulo `jsr310`
separado.

## Hibernate 7

`MySQL8Dialect` foi removido. Não substitua por outra classe de dialect: o dialect é
detectado automaticamente pelos metadados JDBC, então a correção certa é apagar a
propriedade.

## Testcontainers 2.0

Duas mudanças, ambas erros de compilação em vez de falhas silenciosas.

**Os artifact ids dos módulos ganharam prefixo:** `org.testcontainers:mysql` →
`org.testcontainers:testcontainers-mysql`, e o mesmo para `-kafka` e `-junit-jupiter`.

**O generic autotipado foi removido:**

```java
static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.4");   // sem <?>
```

Além disso: agora há três classes de container Kafka. A
`org.testcontainers.kafka.KafkaContainer` é a nativa de KRaft e a escolha certa contra um
broker moderno.

## O Failsafe é gerenciado mas não ativado

O `spring-boot-starter-parent` define a versão do Failsafe mas não vincula seus goals. Sem
um bloco `<executions>` explícito no seu próprio POM, **toda classe `*IT` é silenciosamente
ignorada e o build reporta sucesso**. Isso é pior do que não ter testes de integração,
porque parece que você tem.

## A lacuna no BOM do Resilience4j

O `resilience4j-bom` 2.4.0 lista `resilience4j-spring-boot3` mas **não** o starter do Boot
4, então importar o BOM não gerencia o `resilience4j-spring-boot4`. É preciso fixar à mão:

```xml
<dependency>
  <groupId>io.github.resilience4j</groupId>
  <artifactId>resilience4j-spring-boot4</artifactId>
  <version>${resilience4j.version}</version>
</dependency>
```

Acompanhado em <https://github.com/resilience4j/resilience4j/issues/2427>.

## WireMock contra Jetty

O `org.wiremock:wiremock` comum precisa do Jetty 11; o Boot 4 gerencia o Jetty 12. O
descompasso falha na inicialização com "Jetty 11 is not present". Use o
`org.wiremock:wiremock-standalone` sombreado, que traz o próprio servidor.

## O Kafka 4 removeu o ZooKeeper

O `docker-compose.yml` roda um broker KRaft de nó único. O broker de teste do Spring Kafka
agora é só KRaft, então manter o ZooKeeper localmente só faria desenvolvimento e teste
discordarem sobre a topologia.

## Onde olhar em seguida

Quase todos os itens acima também estão registrados como comentário no lugar em que se
aplicam — no `pom.xml`, no
`asteroid-service/src/main/resources/application.yaml` ou nos POMs dos módulos. Este
documento existe porque um comentário tem espaço para a correção, mas não para o
raciocínio.

## Experimente

```bash
# todas as notas de migração que vivem nos arquivos de build
grep -rn "Boot 4\|was renamed\|no longer\|deprecated" --include=pom.xml --include=*.yaml . | grep -v target

# confirme uma depreciação você mesmo, direto dos metadados do Boot
python3 - <<'EOF'
import zipfile, json, glob, getpass
for j in glob.glob(f'/home/{getpass.getuser()}/.m2/repository/org/springframework/boot/**/*4.1.1.jar', recursive=True):
    try: z = zipfile.ZipFile(j)
    except Exception: continue
    for n in z.namelist():
        if n.endswith('spring-configuration-metadata.json'):
            for p in json.loads(z.read(n)).get('properties', []):
                if p['name'].startswith('server.error') and p.get('deprecation'):
                    print(p['name'], '->', p['deprecation'].get('replacement'))
EOF
```

O segundo comando imprime a substituição de cada chave `server.error.*`, a partir do jar
e não da memória de alguém.
