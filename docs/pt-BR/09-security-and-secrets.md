[← Spring Boot 3 para 4](08-spring-boot-3-to-4.md) · [English](../en/09-security-and-secrets.md) · **Português (Brasil)**

# Segurança e segredos

## Um incidente real, primeiro

O commit `8b6a1f4` se chama `security: purge committed .env files and add secret
hygiene`. Arquivos de ambiente contendo credenciais reais tinham sido commitados neste
repositório, e aquele commit os removeu e adicionou as regras de `.gitignore` que impedem
que aconteça de novo.

Essa é a coisa mais útil deste documento, porque não é hipotética. Também vale saber o
que remover **não** faz: `git rm` apaga um arquivo da árvore atual, não do histórico. Uma
credencial que algum dia foi enviada precisa ser tratada como comprometida e
**rotacionada**, não apenas apagada.

```
### Secrets - never commit these ###
.env
.env.*
!.env.example
*.pem
*.key
secrets/
```

As regras não são ancoradas, então `.env` é ignorado em qualquer profundidade — e é por
isso que um `notification-service/.env` obsoleto, anterior àquele commit, está sem
rastreamento hoje. Ele contém valores com cara de reais em `EMAIL.USERNAME` e
`EMAIL.PASSWORD` que **nada lê**: a aplicação usa `${MAIL_USERNAME}`/`${MAIL_PASSWORD}`, e
o Spring Boot não carrega arquivos `.env`. É resíduo morto, vale apagar, e vale rotacionar
aquelas credenciais se um dia foram reais.

O `.env.example` é commitado e guarda o *formato* sem os valores. Esse é o padrão: o
repositório documenta quais variáveis existem; nunca carrega o que elas contêm.

## O problema da chave de API

A NASA autentica com `?api_key=...`. Um parâmetro de query é o pior lugar para uma
credencial, e essa não é uma escolha deste projeto:

- aparece nos logs de acesso de todo servidor no caminho;
- aparece no histórico do navegador e em cabeçalhos `Referer`;
- aparece na mensagem de qualquer exceção que inclua a URI da requisição.

Três defesas, todas no `asteroid-service`.

**A chave nunca sai do serviço.** Nenhum navegador e nenhum outro serviço a recebe. O
`web-ui` não tem cliente da NASA nenhum.

**O `NasaEndpoint` anexa a chave ele mesmo**, para que nenhum cliente esqueça — e não
lança nada que carregue a URI:

```java
throw new NasaUnavailableException(displayName + " returned " + response.getStatusCode());
```

O código de status, o nome da API, nada mais.

**A mensagem da causa nunca é copiada.** O `NasaEndpoint` mantém o `RestClientException`
subjacente como causa, e *essa* contém a URI completa com a chave. Mas o
`ApiExceptionHandler` renderiza apenas `getMessage()`, nunca a causa, então a chave não
consegue chegar a um corpo de resposta. O `NasaEndpointTest` verifica as duas metades:
`hasMessageNotContaining(API_KEY)` em um status de erro, e de novo em uma conexão
recusada.

## O proxy de imagens do EPIC

O único lugar onde o problema da chave vira um problema de design.

O arquivo do EPIC na NASA exige a chave como parâmetro de query, então uma URL de arquivo
não pode ir para um `<img src>` — isso publica a credencial para todo navegador, proxy e
histórico que vir a página. Compare com o APOD, cujas URLs não carregam chave e são
linkadas diretamente. Se uma URL de mídia precisa de credencial é propriedade de cada API
individualmente.

A solução tem três partes:

1. **`EpicImageView` não tem campo capaz de guardar uma URL da NASA.** Seu `imagePath` é
   um caminho no `asteroid-service` — `/api/v1/nasa/epic/image/natural/2026/08/29/epic_1b_…`
   — sem query string e sem chave. Como não há onde colocar uma URL de arquivo real, isso
   não pode regredir por acidente, apenas de propósito.
2. **O `asteroid-service` serve esse caminho** buscando os bytes no servidor.
3. **O `web-ui` faz proxy mais uma vez**, para que o navegador só converse com uma origem.

### O proxy é a parte crítica de segurança

Aqueles segmentos de caminho são concatenados em `https://api.nasa.gov` **com a chave
anexada**. Sem validação, o endpoint vira um proxy aberto para qualquer caminho de
`api.nasa.gov`, assinado com a nossa credencial — o que é estritamente *pior* que o
vazamento que ele existe para evitar.

O `EpicImageAssembler` portanto:

- restringe `{collection}` a `natural` ou `enhanced` por lista de permissão;
- ancora `{image}` em `^epic_[A-Za-z0-9]{1,8}_\d{14,20}$`, de forma que um valor com
  barra, ponto ou travessia codificada em percent não case;
- constrói a data via `LocalDate.of(...)`, então `2026-13-40` é rejeitada antes de virar
  parte de uma URL;
- acrescenta `.png` **no servidor**, nunca pegando a extensão do chamador.

O `EpicImageAssemblerTest` dispara `../../../../planetary/apod`, `../DONKI/CME` e
`epic_1b_…?api_key=stolen` contra ele. O `EpicControllerTest` verifica que nenhum corpo de
resposta contém `api_key`, o valor da chave configurada, ou a string `api.nasa.gov`. O
`WebUiPagesIT` verifica o mesmo sobre o HTML renderizado.

**Com honestidade**: o `epic.gsfc.nasa.gov` serve os mesmos PNGs sem chave nenhuma, o que
tornaria os dois saltos de proxy desnecessários. O que o proxy compra é uma origem única e
um cache diante de um upstream com rate limit; o que custa é mover alguns megabytes por
duas JVMs para exibir uma fotografia. A página do EPIC diz isso em voz alta.

## Cross-site scripting no front-end

O `${...}` do JSP **não** escapa, diferente do `th:text` do Thymeleaf. Texto não confiável
chega a estas páginas pelas explicações do APOD, notas do DONKI, legendas do EPIC e pelo
`notification_delivery.last_error`.

Este último merece ênfase: ele guarda **o que quer que um servidor SMTP tenha dito**. É
uma string de terceiros que este sistema armazena no próprio banco e depois renderiza em
HTML — o formato clássico de um XSS armazenado. Todo valor dinâmico passa por `<c:out>`, e
o `WebUiPagesIT#escapesUntrustedText` prova isso com `<script>alert(1)</script>`.

## Injeção de SQL

Não é um risco aqui, e vale entender por quê em vez de supor. Toda query é ou um método
derivado do Spring Data ou uma `@Query` com **parâmetros nomeados**:

```java
where d.notification.eventId = :eventId
```

Valores são vinculados pelo driver, nunca concatenados. O único lugar em que um valor de
requisição chega a algo parecido com query é o id do lookup NeoWs, e ele é restringido no
próprio mapeamento:

```java
@GetMapping("/{id:\\d{4,10}}")
```

Um id não numérico não casa com rota nenhuma, então nunca chega a código algum. Isso é
mais forte que validar dentro do método, e o `NeoCatalogControllerTest` inclui
`../planetary/apod` entre seus casos.

## O que está deliberadamente ausente

**Não há autenticação.** Todo endpoint é aberto. Para um projeto didático local isso é uma
escolha razoável, e deve ser declarada em vez de presumida.

**O formulário de varredura não tem token CSRF.** `POST /scan` dispara uma leitura real da
NASA e publica eventos reais. Sem Spring Security e sem sessão, hoje não há nada em que
forjar — CSRF exige credenciais ambientes que o navegador anexa automaticamente, e não
existem.

Isso deixa de ser verdade no momento em que alguém adicionar autenticação. Se você
adicionar login a este projeto, adicione `spring-boot-starter-security` e um token CSRF
naquele formulário no mesmo commit. Uma frase aqui agora é mais barata que o bug depois.

**O Actuator está parcialmente exposto.** `health,info,metrics,prometheus`, com
`show-details: when-authorized` — então detalhes de health ficam ocultos sem autenticação,
mas o endpoint de métricas é aberto. Tudo bem localmente, nada bem em rede pública.

## Experimente

```bash
# a chave não está em lugar nenhum do HTML renderizado
curl -s localhost:8082/epic | grep -c "api_key"     # 0

# nem em uma resposta de erro
curl -s "localhost:8080/api/v1/nasa/apod?date=1990-01-01" | grep -c "api_key"   # 0

# a defesa contra travessia
curl -s -o /dev/null -w "%{http_code}\n" \
  "localhost:8080/api/v1/nasa/epic/image/natural/2026/08/29/arbitrary"          # 400

# e as proteções que mantêm isso assim
./mvnw -pl asteroid-service test -Dtest='EpicImageAssemblerTest+EpicControllerTest+NasaEndpointTest'
```
