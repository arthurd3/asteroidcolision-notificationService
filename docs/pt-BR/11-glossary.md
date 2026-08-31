[← Executando localmente](10-running-locally.md) · [English](../en/11-glossary.md) · **Português (Brasil)**

# Glossário

## Astronomia

**NEO — Near-Earth Object (objeto próximo da Terra).** Asteroide ou cometa cuja órbita o
traz perto da órbita da Terra. Cerca de 62.000 estão catalogados.

**Potencialmente perigoso.** É a classificação da NASA, não uma previsão. Significa que o
objeto é grande o bastante e sua órbita passa perto o bastante da terrestre para valer
acompanhamento. Este projeto alerta com base na flag; não a calcula.

**Close approach (aproximação).** Uma passagem específica. Um objeto tem várias, para
vários corpos — o lookup do NeoWs devolve aproximações a Mercúrio, Vênus e Marte, além da
Terra.

**Miss distance (distância de passagem).** Quão perto passou, reportada em quatro
unidades. A **distância lunar (LD)** — múltiplos da distância média Terra–Lua — é a que as
pessoas conseguem imaginar.

**MOID — Minimum Orbit Intersection Distance.** Quão perto as duas *órbitas* chegam,
independentemente de onde os corpos estão. É este número que decide "potencialmente
perigoso".

**Magnitude absoluta (H).** Brilho a uma distância padrão, usada como proxy de tamanho.
Menor é maior.

**Classe de órbita.** A família a que a órbita pertence — Apollo, Aten, Amor. A NASA envia
uma descrição junto com o código, e é por isso que o record carrega os dois.

**APOD.** Astronomy Picture of the Day. Publicada diariamente desde 1995-06-16; cerca de
um dia por semana é um vídeo.

**CME — Coronal Mass Ejection (ejeção de massa coronal).** Uma nuvem de plasma lançada
pelo Sol.

**Tempestade geomagnética.** Uma perturbação do campo magnético da Terra, normalmente
causada por uma CME que chegou. Medida pelo **índice Kp**, de 0 a 9; reportada a partir de
5, e 8–9 é a faixa em que redes elétricas e satélites são afetados.

**Explosão solar (solar flare).** Um clarão repentino, classificado em A/B/C/M/X pela
intensidade de raios X, numa escala **logarítmica** — uma classe X é dez vezes uma M.

**DSCOVR / EPIC.** Uma espaçonave no ponto L1 Terra–Sol, a cerca de um milhão e meio de
quilômetros, e a câmera nela que fotografa o disco completo da Terra uma dúzia de vezes
por dia.

**DONKI.** Space Weather Database Of Notifications, Knowledge, Information.

## Sistemas distribuídos

**Orientado a eventos.** Serviços se comunicam publicando fatos que já aconteceram, em vez
de chamarem uns aos outros. O produtor não sabe quem consome.

**Entrega at-least-once.** A garantia do Kafka: uma mensagem será entregue, possivelmente
mais de uma vez. Consumidores precisam ser idempotentes — e é por isso que este projeto
deriva ids de evento de chaves de negócio.

**Idempotente.** Fazer duas vezes tem o mesmo efeito que fazer uma. Aqui: revarrer uma
janela sobreposta não adiciona linhas nem envia e-mails duplicados.

**Tópico / partição / offset.** Um tópico é um log nomeado; é dividido em partições para
paralelismo; um offset é a posição de uma mensagem em uma partição. Mensagens com a mesma
chave caem na mesma partição e mantêm a ordem relativa.

**Consumer group.** Um conjunto de consumidores que dividem as partições de um tópico.
Cada partição vai para exatamente um membro.

**DLT — Dead-Letter Topic.** Para onde vai uma mensagem que não pode ser processada. Sem
um, uma mensagem envenenada é retentada para sempre e **bloqueia toda mensagem atrás dela
na sua partição**.

**Mensagem envenenada.** Aquela que nunca terá sucesso, por mais que seja retentada —
tipicamente malformada e não desserializável.

**Circuit breaker.** Depois de falhas suficientes, ele para de chamar o upstream por um
tempo, falhando rápido em vez de acumular requisições condenadas. O Resilience4j chama os
estados de CLOSED, OPEN e HALF_OPEN.

**Bulkhead.** Um limite de chamadas concorrentes, para que uma dependência lenta não ocupe
todas as threads de requisição. O nome vem dos compartimentos estanques de navios.

**Backpressure.** Recusar trabalho que você não consegue fazer, em vez de enfileirar
indefinidamente. O bulkhead deste projeto usa `max-wait-duration: 0` exatamente por isso.

## Spring e Java

**Bean.** Um objeto que o contêiner do Spring cria e conecta. Tudo anotado com
`@Component`, `@Service`, `@Controller`, `@Configuration` ou devolvido de um método
`@Bean`.

**Auto-configuração.** O Spring Boot configurando coisas com base no que está no
classpath. `@ConditionalOnMissingBean` é o que permite sobrescrever simplesmente
declarando o seu próprio.

**`@ConfigurationProperties`.** Liga YAML a um objeto tipado, validado na inicialização —
então uma configuração ausente falha o contexto com mensagem legível em vez de um
`NullPointerException` na primeira requisição.

**Constructor binding.** Como records ligam configuração. `@DefaultValue` fornece padrões;
records aninhados dão estrutura às propriedades.

**Teste de slice.** `@WebMvcTest`, `@DataJpaTest` e afins: um contexto parcial com apenas
a camada em teste. Rápido, mas note que **`@WebMvcTest` não renderiza JSPs**.

**Testcontainers.** Dependências reais em containers Docker, iniciados pelo teste. Usado
aqui para MySQL e Kafka, porque constraints e `SKIP LOCKED` não se comportam da mesma
forma em um substituto em memória.

**`@ServiceConnection`.** Aponta o Spring para um container do Testcontainers
automaticamente — e é o mecanismo por trás do bug de `KafkaConnectionDetails` descrito no
[documento 04](04-event-driven-kafka.md).

**Open Session In View.** Manter a sessão do Hibernate aberta durante toda a requisição.
Desligado aqui, e é por isso que a API de leitura devolve records em vez de entidades.

**Projeção.** Selecionar direto para um DTO em vez de carregar uma entidade. Uma
**expressão de construtor** (`select new com.example.Dto(...)`) o constrói dentro da
query.

**RFC 9457 / `ProblemDetail`.** O formato padrão para respostas de erro HTTP — `type`,
`title`, `status`, `detail`. Os dois backends o emitem e o `web-ui` o decodifica para
montar sua página de erro.

**Anti-corruption layer.** Uma fronteira que traduz um modelo externo para o seu, de modo
que uma mudança no upstream não se propague pelo seu código. O pacote NASA do
`asteroid-service` é uma.

## JSP

**JSP / Jasper.** JavaServer Pages, e o componente do Tomcat que compila um `.jsp` em
servlet na primeira requisição.

**JSTL.** A biblioteca de tags padrão — `<c:out>`, `<c:forEach>`. No Jakarta EE as URIs
são `jakarta.tags.*`; as antigas de `java.sun.com` não resolvem mais.

**EL — Expression Language.** `${...}`. **Não escapa HTML**, e é por isso que todo valor
dinâmico aqui passa por `<c:out>`.

**Include estático vs dinâmico.** `<%@ include %>` funde em tempo de tradução — um servlet
compilado. `<jsp:include>` faz dispatch em tempo de requisição, toda requisição.

**`.jspf`.** Convenção para fragmento JSP: um arquivo incluído em uma página, não
requisitado sozinho.

**WAR.** Web Application Archive. Necessário para JSP; o layout de JAR executável do Boot
não consegue servir páginas de um document root que um JAR não tem.

## Experimente

Todo termo acima aparece no código. Encontre-os:

```bash
grep -rn "SKIP LOCKED\|idempoten\|bulkhead\|circuit" --include=*.java --include=*.yaml . | grep -v target | head -20
```
