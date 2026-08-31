[← Segurança e segredos](09-security-and-secrets.md) · [English](../en/10-running-locally.md) · **Português (Brasil)**

# Executando localmente

## O que você precisa

| | |
|---|---|
| **JDK 21** | `java -version` deve dizer 21 |
| **Docker** | para MySQL, Kafka e os testes de integração |
| **Uma chave de API da NASA** | gratuita e instantânea, em <https://api.nasa.gov> |

O Maven não é necessário — o wrapper (`./mvnw`) o baixa.

> **Pegue uma chave de verdade antes de qualquer outra coisa.** A `DEMO_KEY` permite 30
> requisições por hora somando *todos* os endpoints de `api.nasa.gov`, e um carregamento
> da home gasta quatro. Com `DEMO_KEY` o front-end vai renderizar quase só páginas de
> erro, e vai parecer que este projeto está quebrado quando não está.

## 1. Configure

```bash
cp .env.example .env
```

| Variável | Observações |
|---|---|
| `NASA_API_KEY` | de <https://api.nasa.gov>. **Não deixe como `DEMO_KEY`.** |
| `MYSQL_ROOT_PASSWORD`, `MYSQL_USER`, `MYSQL_PASSWORD` | quaisquer valores; o compose e a aplicação leem os mesmos |
| `MAIL_USERNAME`, `MAIL_PASSWORD` | credenciais SMTP — sandbox do [Mailtrap](https://mailtrap.io) por padrão |

O `.env` é ignorado pelo git. Nunca o commite — veja o
[documento 09](09-security-and-secrets.md).

## 2. Suba a infraestrutura

```bash
docker compose up -d
docker compose ps          # espere mysql e kafka reportarem healthy
```

Três containers: MySQL 8.4, um broker Kafka 4 KRaft de nó único, e o Kafka UI.

## 3. Compile

```bash
./mvnw clean install
```

Acrescente `-DskipTests` para pular os testes de integração com Testcontainers, que
precisam do Docker.

## 4. Rode os três serviços

Cada um em seu terminal, com o ambiente carregado:

```bash
set -a; . ./.env; set +a

./mvnw -pl asteroid-service      spring-boot:run    # :8080
./mvnw -pl notification-service  spring-boot:run    # :8081
./mvnw -pl web-ui                spring-boot:run    # :8082
```

A linha `set -a` exporta tudo do `.env` para o shell. O Spring Boot **não** lê arquivos
`.env` sozinho; as variáveis precisam estar no ambiente.

Eles sobem em qualquer ordem. O `web-ui` funciona bem com os outros dois parados — ele
apenas deixa em cinza os painéis que não consegue preencher.

## 5. Abra

<http://localhost:8082>

Depois:

1. Vá em **Run a scan** e clique no botão. Isso lê o feed da NASA e publica um evento
   para cada aproximação perigosa.
2. Vá em **Alert history**. Os alertas estão lá, cada um com o status de entrega por
   destinatário.
3. Observe as mensagens cruas no Kafka UI em <http://localhost:8084>.
4. Confira os e-mails na sua caixa do Mailtrap. O worker de entrega roda a cada 30
   segundos.

## A API, sem o front-end

```bash
# dados da NASA
curl -s localhost:8080/api/v1/nasa/apod | python3 -m json.tool
curl -s "localhost:8080/api/v1/nasa/neo/feed?from=2026-09-01&to=2026-09-05" | head -c 400
curl -s "localhost:8080/api/v1/nasa/neo/browse?page=0&size=5" | python3 -m json.tool | head -20
curl -s localhost:8080/api/v1/nasa/neo/2000433 | python3 -m json.tool | head -30
curl -s localhost:8080/api/v1/nasa/donki/cme | head -c 400        # lento: reserve 90s
curl -s localhost:8080/api/v1/nasa/epic/natural | python3 -m json.tool | head -20

# dispare uma varredura
curl -X POST localhost:8080/api/v1/asteroid-alerting/alert

# o que o pipeline armazenou
curl -s localhost:8081/api/v1/notifications | python3 -m json.tool
curl -s localhost:8081/api/v1/notifications/stats | python3 -m json.tool
```

## Portas

| | |
|---|---|
| 8080 | `asteroid-service` |
| 8081 | `notification-service` |
| 8082 | `web-ui` |
| 8084 | Kafka UI |
| 3306 | MySQL |
| 9092 | Kafka |

## Resolução de problemas

**Tudo que vem da NASA devolve 503, ou a UI é só página de erro.**
Quase sempre é o rate limit. A `DEMO_KEY` são 30 requisições/hora somando todos os
endpoints, e uma vez esgotada o circuit breaker ainda descarta chamadas por cima. Pegue
sua própria chave. Confirme com:

```bash
curl -s "https://api.nasa.gov/planetary/apod?api_key=DEMO_KEY" | head -c 200
```

Se aparecer `OVER_RATE_LIMIT`, essa é a resposta.

**O clima espacial demora uma eternidade.** É para demorar mesmo. O DONKI leva
genuinamente de 60 a 90 segundos; o `asteroid-service` lhe dá um orçamento de 120. Se você
receber um 429 em vez disso, o bulkhead está fazendo seu trabalho — só duas dessas
chamadas rodam ao mesmo tempo.

**O health do `notification-service` está DOWN.** Normalmente é o indicador de e-mail com
credenciais SMTP ausentes ou erradas. O `/actuator/health/readiness` continua UP: o e-mail
é reportado honestamente sem tirar o serviço de rotação.

**Nenhum e-mail chega.** Verifique `notification_delivery` — ou simplesmente abra
<http://localhost:8082/history>, que agora mostra exatamente isso por destinatário. As
colunas `status` e `last_error` dizem o que aconteceu.

**`./mvnw verify` falha com erros de Docker.** Os testes de integração precisam de um
daemon rodando. `docker info` deve funcionar. Use `./mvnw test` para pulá-los.

**O serviço não sobe depois que mudei uma entidade.** Isso é o `ddl-auto: validate`
funcionando. Adicione uma migração Flyway em
`notification-service/src/main/resources/db/migration` — a próxima é `V4__*.sql`.

**Uma mudança em JSP não aparece.** Reinicie o `web-ui`. O Jasper faz cache das páginas
compiladas, e uma alteração em um `.jspf` incluído nem sempre é detectada.

## Experimente

O ciclo inteiro de uma vez, com tudo no ar:

```bash
curl -X POST localhost:8080/api/v1/asteroid-alerting/alert
sleep 5
curl -s localhost:8081/api/v1/notifications/stats | python3 -m json.tool
```

`notifications` deve ser maior que zero, e `pending` deve ser diferente de zero por cerca
de trinta segundos antes de virar `sent`. Esse intervalo é o período do worker de entrega,
e vê-lo mudar é a demonstração mais clara de que o pipeline é assíncrono.
