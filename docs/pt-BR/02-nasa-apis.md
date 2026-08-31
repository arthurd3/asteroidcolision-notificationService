[← Arquitetura](01-architecture.md) · [English](../en/02-nasa-apis.md) · **Português (Brasil)**

# As APIs da NASA

Cinco APIs, todas sob `https://api.nasa.gov`, todas autenticadas do mesmo jeito: um
parâmetro de query `api_key`. Pegue uma gratuita em <https://api.nasa.gov>.

> **`DEMO_KEY` são 30 requisições por hora somando *todos* os endpoints**, não 30 por
> endpoint. Um carregamento da home deste projeto gasta quatro. Pegue uma chave de
> verdade antes de qualquer outra coisa — este é de longe o motivo mais comum de o
> projeto parecer quebrado.

## Near-Earth Object Web Service (NeoWs)

Três endpoints sobre o mesmo recurso. Só o primeiro alimenta o pipeline de alertas; os
outros dois existem para serem navegados.

| Endpoint | Para quê |
|---|---|
| `GET /neo/rest/v1/feed?start_date=&end_date=` | objetos que se aproximam entre duas datas |
| `GET /neo/rest/v1/neo/{id}` | um objeto, com sua órbita |
| `GET /neo/rest/v1/neo/browse?page=&size=` | o catálogo inteiro, paginado |

**A armadilha: a janela do feed é limitada a sete dias.** Esse limite é da NASA, não uma
escolha daqui, e é por isso que `ScanWindow.MAX_FEED_DAYS` vale 7 e que o formulário de
datas do front-end recusa um intervalo maior.

**A segunda armadilha: `orbital_data` só vem em lookup e browse.** O feed omite isso
para manter o payload pequeno. Um único record `Asteroid` cobre os três endpoints —
separá-lo em `Asteroid` e `NeoDetail` duplicaria `estimated_diameter`,
`close_approach_data` e os dois métodos auxiliares só para que um campo anulável fosse
não-nulo em um deles. O custo é que `orbitalData()` é null em respostas do feed, o que
está declarado no record em vez de deixado para alguém descobrir.

**O browse é realmente grande**: cerca de 62.000 objetos, ou seja, mais de 3.000 páginas
no tamanho máximo de 20 que a NASA permite. Não existe versão "busca tudo e filtra".

Todo elemento orbital chega como *string* decimal, e continua sendo. Converter vinte
campos para `BigDecimal` só para renderizá-los de volta como texto não ganha nada e
transforma um campo malformado em falha da resposta inteira. Apenas
`miss_distance.kilometers` é convertido, porque atravessa a fronteira do Kafka como
`BigDecimal` no contrato do evento — converter na borda faz com que um valor ruim seja
um objeto ignorado em vez de uma falha dentro do listener do consumidor.

**Um campo é obrigatório.** `is_potentially_hazardous_asteroid` mapeia para um
`boolean` primitivo, então uma resposta sem ele falha ao desserializar em vez de assumir
`false`. Isso é deliberado: essa flag decide se um alerta é publicado, e tratar "a NASA
não disse" como "não é perigoso" é a única resposta errada possível.

## APOD — Astronomy Picture of the Day

```
GET /planetary/apod?date=YYYY-MM-DD
```

Pequena, rápida e a mais simpática das cinco. O arquivo começa em **1995-06-16**; o
`ApodController` recusa qualquer data anterior sem chamar a NASA, porque uma requisição
sabidamente inválida não pode gastar um dos trinta slots por hora.

**A armadilha: `media_type`.** Cerca de uma entrada por semana é um vídeo, e nesses dias
`url` é um embed do YouTube ou Vimeo em vez de uma imagem, e `hdurl` não existe. Uma
página que renderiza toda entrada em um `<img>` quebra uma vez por semana. A ramificação
mora no record `ApodEntry` — `image()`, `video()`, `displayUrl()` — em vez de ser
redescoberta por cada view.

**As URLs do APOD não carregam chave de API**, então um navegador pode carregá-las
diretamente. Guarde isso para quando chegarmos ao EPIC.

## DONKI — clima espacial

```
GET /DONKI/CME?startDate=&endDate=      ejeções de massa coronal
GET /DONKI/GST?startDate=&endDate=      tempestades geomagnéticas
GET /DONKI/FLR?startDate=&endDate=      explosões solares
```

Tematicamente o vizinho mais próximo do feed de asteroides — ambos respondem "o que está
acontecendo lá fora que pode afetar a Terra". Operacionalmente é o oposto de todas as
outras APIs aqui.

**A armadilha: o DONKI é genuinamente lento.** Uma consulta de trinta dias leva
rotineiramente de 60 a 90 segundos. Medido, não suposto: a primeira tentativa de capturar
uma resposta para este projeto estourou o timeout em 30 segundos e só teve sucesso com
um orçamento de 90. Esse número é o motivo de toda a configuração de clientes ter a
forma que tem — o [documento 03](03-http-clients-and-resilience.md) é quase todo sobre
ele.

**A segunda armadilha: parâmetros em `camelCase`.** `startDate` e `endDate`, não os
`start_date` e `end_date` do NeoWs. As duas APIs simplesmente discordam, e o DONKI
*ignora silenciosamente* um parâmetro que não reconhece e responde com sua própria
janela padrão. Errar isso parece código funcionando que devolve os eventos errados.

**A terceira armadilha: os timestamps não têm segundos.** `2026-08-02T10:45Z`. Isso só é
parseado porque `ISO_OFFSET_DATE_TIME` trata segundos como opcionais; um padrão escrito à
mão como `yyyy-MM-dd'T'HH:mm:ss'Z'` falha em todos os registros.

**Os três formatos de record estão verificados contra capturas reais**, e as fixtures em
`asteroid-service/src/test/resources/nasa/` são respostas de verdade, reduzidas a alguns
registros representativos.

Vale dizer isso porque por um tempo dois deles não estavam. `GeomagneticStorm` e
`SolarFlare` foram escritos a partir da documentação da NASA enquanto um limite de taxa
da `DEMO_KEY` impedia a captura, e o código e a documentação diziam isso com todas as
letras. Quando uma chave real ficou disponível, foram conferidos campo a campo: nenhuma
divergência, mas valeu confirmar em vez de supor. O
`@JsonIgnoreProperties(ignoreUnknown = true)` torna um campo *extra* inofensivo e não faz
nada quanto a um *faltando* — um componente cujo nome não bate desserializa como `null`,
silenciosamente, e um teste que parseia uma fixture escrita à mão passa mesmo assim.

**Uma quarta armadilha, descoberta durante essa captura: o DONKI devolve 503
transitórios.** Não é limite de taxa — é uma falha do upstream que funciona na
retentativa. O que é uma boa propaganda para a política de retry do
[documento 03](03-http-clients-and-resilience.md).

## EPIC — Earth Polychromatic Imaging Camera

```
GET /EPIC/api/natural                    metadados do conjunto mais recente
GET /EPIC/api/natural/date/YYYY-MM-DD    um dia
GET /EPIC/api/natural/available          todas as datas com quadros
```

Fotografias do disco completo da Terra a partir da espaçonave DSCOVR, a cerca de um
milhão e meio de quilômetros, aproximadamente uma dúzia de quadros por dia.

**A armadilha: `date` não é ISO-8601.** É `"2026-08-29 00:41:06"` — um espaço onde o ISO
exige um `T`. O Jackson não consegue parsear sem ser instruído, então o padrão
`@JsonFormat` em `EpicImage` é estrutural e não decorativo: sem ele, todos os quadros
falham.

**A segunda armadilha: não existe URL de imagem na resposta.** Ela precisa ser montada:

```
/EPIC/archive/natural/{yyyy}/{MM}/{dd}/png/{image}.png
```

…e as partes da data vêm do campo `date`, **não** do `identifier`. O identifier também
parece um timestamp e dá a impressão de servir, mas os dois divergem em minutos, então
usá-lo resulta em 404 em qualquer quadro que cruze a meia-noite UTC.

**A terceira armadilha, e a interessante: essa URL de arquivo exige a chave de API.**
Então, diferente do APOD, uma URL de imagem do EPIC nunca pode ser entregue a um
navegador — fazer isso publica a credencial para todo navegador, proxy e histórico que a
vir. A solução está no [documento 09](09-security-and-secrets.md), e é o código mais
relevante para segurança do projeto.

## Mars Rover Photos — desativada

`https://api.nasa.gov/mars-photos/*` está documentada e morta. Devolve uma página 404 do
Heroku dizendo "No such app": o backend que ela intermediava foi desligado.

Isso está na documentação de propósito. "A API do tutorial não existe mais" é algo com
que quem estuda esbarra o tempo todo, e *como* se constata isso é mais útil do que a API
teria sido. A verificação levou um comando:

```bash
curl -s -w "\nHTTP=%{http_code}\n" \
  "https://api.nasa.gov/mars-photos/api/v1/rovers/curiosity/photos?sol=1000&api_key=DEMO_KEY" \
  | tail -3
```

Uma página de erro HTML onde JSON foi prometido, e um 404 vindo do Heroku em vez da
NASA, diz que o alvo do proxy desapareceu — e não que sua requisição estava errada.

## Experimente

```bash
set -a; . ./.env; set +a

# APOD - rápida
curl -s "https://api.nasa.gov/planetary/apod?api_key=$NASA_API_KEY" | head -c 300

# o timestamp sem segundos do DONKI, e a lentidão. Reserve 90 segundos.
time curl -s "https://api.nasa.gov/DONKI/CME?startDate=2026-08-01&endDate=2026-08-31&api_key=$NASA_API_KEY" \
  | head -c 200

# a data não-ISO do EPIC
curl -s "https://api.nasa.gov/EPIC/api/natural?api_key=$NASA_API_KEY" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)[0]['date'])"
```

O terceiro comando imprime algo como `2026-08-29 00:41:06`. Aquele espaço é a lição
inteira.
