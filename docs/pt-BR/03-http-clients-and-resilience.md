[← As APIs da NASA](02-nasa-apis.md) · [English](../en/03-http-clients-and-resilience.md) · **Português (Brasil)**

# Clientes HTTP e resiliência

O raciocínio mais detalhado do projeto, e tudo decorre de uma medição: **uma consulta ao
DONKI leva de 60 a 90 segundos, enquanto o feed NEO responde em menos de dois.**

## Por que `base-url` teve de virar a raiz da API

`asteroid.nasa.base-url` era o endpoint completo do feed:

```yaml
asteroid:
  nasa:
    base-url: https://api.nasa.gov/neo/rest/v1/feed   # antes
```

…e o `RestNasaNeoClient` fazia `restClientBuilder.baseUrl(properties.baseUrl())`. Isso
funciona quando existe exatamente uma API e para de funcionar no momento em que existem
cinco.

A correção não é apenas mecânica. O caminho de um endpoint faz parte do *contrato daquele
cliente com a NASA*, não de uma configuração de implantação: qual host chamar pode
variar entre ambientes, mas `/planetary/apod` é o que o APOD é, em todo lugar, para
sempre. Então a raiz mora na configuração e o caminho mora no cliente:

```yaml
asteroid:
  nasa:
    base-url: https://api.nasa.gov          # depois
```

```java
static final String PATH = "/planetary/apod";   // em RestNasaApodClient
```

Quem lê o cliente agora vê qual endpoint ele chama sem consultar um arquivo YAML.

## Por que um `RestClient` por API

`spring.http.clients.read-timeout` é um único número. Defina 10 segundos e o DONKI não
funciona. Defina 120 e um feed NEO travado prende uma thread de requisição por dois
minutos. Não existe valor certo para os dois.

O Boot 4 torna timeouts por cliente uma questão de cinco linhas. O
`HttpClientAutoConfiguration` publica um bean `HttpClientSettings` ligado a
`spring.http.clients.*`, e `HttpClientSettings#withTimeouts` devolve uma cópia com
outros:

```java
final HttpClientSettings perApi = settings.getIfAvailable(HttpClientSettings::defaults)
        .withTimeouts(timeouts.connect(), timeouts.read());

return builder.clone()
        .baseUrl(properties.baseUrl())
        .requestFactory(factories.getIfAvailable(ClientHttpRequestFactoryBuilder::detect)
                .build(perApi))
        .build();
```

O motivo de passar por `HttpClientSettings` em vez de montar uma request factory à mão é
que tudo o *mais* que o Boot gerencia — tratamento de redirecionamentos, SSL bundles,
qual implementação de cliente HTTP foi detectada — é preservado. Sobrescrever dois campos
não é a mesma coisa que substituir o objeto.

Veja `NasaRestClientsConfig`. Quatro beans, diferindo apenas no orçamento de timeout:

| Cliente | connect | read | por quê |
|---|---|---|---|
| `nasaNeoRestClient` | 3s | 10s | rápido, e no caminho dos alertas |
| `nasaApodRestClient` | 3s | 10s | payload pequeno |
| `nasaDonkiRestClient` | 3s | **120s** | medido: 60–90s é o normal |
| `nasaEpicRestClient` | 3s | 30s | PNGs de 2 MB |

Quatro beans `RestClient` no mesmo contexto tornam ambígua uma injeção de `RestClient`
puro, então todo cliente recebe um `@Qualifier`. O POM pai compila com `-parameters`,
então resolução por nome também funcionaria — mas depender de um nome de parâmetro para
escolher um orçamento de timeout não é óbvio o bastante para valer a pena.

## `NasaEndpoint`: quatro regras escritas uma vez

Todo cliente da NASA precisa dos mesmos quatro comportamentos. Com um cliente isso é
tranquilo; com quatro são quatro cópias e só a primeira tem teste.

```java
<T> T get(String path, UnaryOperator<UriBuilder> parameters, ParameterizedTypeReference<T> type)
```

1. Um status de erro vira `NasaUnavailableException`.
2. Um corpo que não parseia também.
3. Um corpo `null` também, em vez de um `NullPointerException` três frames acima.
4. **A URI da requisição nunca chega a uma linha de log nem a uma mensagem de exceção.**

A regra 4 é o motivo de a classe existir. A NASA recebe a chave como parâmetro de query,
então qualquer código que "prestativamente" inclua a URI em um erro está publicando a
credencial. O `NasaEndpoint` anexa a chave ele mesmo — para que nenhum cliente esqueça —
e não lança nada que carregue a URI.

Ele mantém o `RestClientException` original como *causa*, e essa causa *contém* a URI
completa em sua própria mensagem, mas nunca copia esse texto: o `ApiExceptionHandler`
renderiza apenas `getMessage()`, nunca a causa. O `NasaEndpointTest` fixa as duas metades.

Composição em vez de classe base abstrata, deliberadamente. Os clientes são donos de seus
caminhos, DTOs e nomes de instância de resiliência; compartilham apenas o transporte.

## Resilience4j: instâncias por domínio de falha

A regra de nomenclatura é: **uma instância por domínio de falha do upstream, não por
método.**

- Feed, lookup e browse compartilham `nasaNeo`. São um serviço só atrás do mesmo balde de
  rate limit — um feed falhando genuinamente prevê um lookup falhando, então devem
  compartilhar um circuito.
- O DONKI recebe `nasaDonki` justamente porque **não** compartilha. Seu perfil de
  latência não tem relação, e um timeout do DONKI não pode abrir um circuito que descarte
  chamadas ao APOD.

```yaml
resilience4j:
  retry:
    configs:
      default:
        max-attempts: 3
        wait-duration: 1s
        enable-exponential-backoff: true
        retry-exceptions:
          - com.arthur.asteroid.alerting.nasa.NasaUnavailableException
    instances:
      nasaNeo:   { base-config: default }
      nasaApod:  { base-config: default }
      nasaDonki: { base-config: default, max-attempts: 2 }
      nasaEpic:  { base-config: default }
```

`configs.default` é tratado de forma especial pelo Resilience4j: qualquer instância que
não declare `base-config` o herda. Declarar a política uma vez significa que ela não pode
ser alterada para três APIs e esquecida na quarta.

**O DONKI tenta uma vez a mais, não duas.** Três tentativas de noventa segundos são uma
requisição de quatro minutos e meio, que ninguém está mais esperando.

### O acoplamento que não tem compilador

`retry-exceptions` cita `NasaUnavailableException` por **string totalmente qualificada**.
Mova ou renomeie essa classe e a retentativa para de funcionar silenciosamente — sem erro
de inicialização, sem aviso, apenas uma política que nunca casa. É também por isso que
existe um único tipo de exceção para as quatro APIs em vez de um por API: cada subclasse
seria mais um FQCN em uma lista que ninguém relê.

O `NasaConfigurationTest` transforma isso em build quebrado. Ele carrega o YAML real,
pega os registries e afirma que o predicado ainda casa:

```java
assertThat(retryRegistry.retry(instance).getRetryConfig()
        .getExceptionPredicate()
        .test(new NasaUnavailableException("boom")))
        .isTrue();
```

Ele pega a metade do acoplamento que é o FQCN da exceção. **Não** pega um nome de
*instância* digitado errado — o Resilience4j silenciosamente cria uma instância a partir
da configuração padrão em vez de falhar. A mitigação é que a configuração padrão é
sensata, então o modo de falha é comportamento degradado e não quebra. Vale saber.

## O bulkhead

```yaml
  bulkhead:
    instances:
      nasaDonki:
        max-concurrent-calls: 2
        max-wait-duration: 0
```

Uma chamada ao DONKI segura um worker do Tomcat por até dois minutos. O `threads.max`
padrão do Tomcat é 200, então um punhado de refreshes é sobrevivível — mas "algumas
atualizações de página impacientes seguram várias threads de requisição por minutos" é
exatamente como um serviço passa fome sem que nada pareça quebrado.
`max-wait-duration: 0` significa que o terceiro chamador é rejeitado imediatamente em vez
de entrar na fila atrás dos dois primeiros.

`BulkheadFullException` mapeia para **429**, não 503. Nada está quebrado; o chamador é um
entre muitos pedindo uma consulta lenta ao mesmo tempo, e tentar de novo em breve vai
funcionar.

## Cache, que aqui não é opcional

`DEMO_KEY` permite 30 requisições por hora somando *todos* os endpoints. Um carregamento
da home gasta quatro. Sem cache, o front-end passa a maior parte da vida renderizando a
página de erro, o que parece um bug deste projeto e não uma cota.

Caffeine, TTL de dez minutos, e duas regras que vale entender:

**`findAsteroids` deliberadamente não é cacheado.** É o que a varredura de alertas chama,
e uma varredura cacheada publicaria eventos de uma janela lida minutos atrás relatando-os
como atuais. Os dois métodos NeoWs somente leitura ao lado dele *são* cacheados, o que
torna a distinção visível em vez de acidental.

**Um nome de cache por método, nunca um por API.** O `SimpleKeyGenerator` do Spring monta
a chave a partir dos *argumentos apenas* — o método não faz parte dela — e os três
endpoints do DONKI recebem `(LocalDate from, LocalDate to)`. Compartilhar um cache entre
eles faz uma requisição de explosões solares devolver as ejeções de massa coronal já
buscadas para aquela janela: silenciosamente, e com aparência totalmente plausível. O
`NasaCacheIT#donkiEndpointsDoNotShareACache` existe para que isso continue verdade.

Aquele teste é de **integração** e não unitário, porque `@Cacheable` funciona por proxy:
chamar o cliente diretamente contorna o advice, e um teste unitário passaria com ou sem
cache funcionando.

## O outro jeito de fazer tudo isto

O Boot 4 traz um HTTP Service Registry — `@ImportHttpServices` com
`spring.http.serviceclient.<group>.*` — que dá base URLs e timeouts por grupo de forma
declarativa, sem classe de configuração nenhuma. É uma opção genuinamente boa.

Não foi usada aqui porque substitui o idioma de `RestClient` escrito à mão por interfaces
`@HttpExchange`, que é uma segunda lição, não relacionada. Se você está construindo algo
novo em vez de lendo algo existente, dê uma olhada nela.

## Experimente

```bash
# os timeouts por API são mesmo diferentes
grep -A2 "donki:" -A6 asteroid-service/src/main/resources/application.yaml | grep timeouts

# o acoplamento por FQCN, protegido
./mvnw -pl asteroid-service test -Dtest=NasaConfigurationTest

# quebre de propósito: mude o nome da classe em retry-exceptions para
# com.arthur.asteroid.alerting.nasa.NoSuchException e rode de novo.
# O build falha. Sem aquele teste, nada teria falhado.

# o cache realmente poupa o rate limit
./mvnw -pl asteroid-service verify -Dtest='!*' -Dit.test=NasaCacheIT
```
