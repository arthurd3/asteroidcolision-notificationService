[English](../en/00-overview.md) · **Português (Brasil)**

# Visão geral

## O que este sistema faz

A NASA publica um feed de asteroides que passarão perto da Terra. Este projeto observa
esse feed e, quando algo classificado como *potencialmente perigoso* está a caminho,
registra o fato e envia e-mail para quem pediu para ser avisado.

A ideia é pequena, e está implementada de propósito do jeito que um sistema real seria,
e não do menor jeito que funcionaria:

```
NASA  ──►  asteroid-service  ──►  Kafka  ──►  notification-service  ──►  e-mail
                  ▲                                    │
                  │                                    ▼
              web-ui  ◄──────────────────────────  MySQL
```

Três serviços, um log de eventos entre eles, um banco de dados e um front-end que
enxerga tudo isso.

## Para quem é isto

Para quem lê Java e quer entender *por que* um sistema distribuído é construído de
determinada forma — não apenas o que o código faz. Todo documento aqui responde "por
que é assim" tanto quanto "o que é".

Você não precisa conhecer Kafka, Spring Boot ou JSP de antemão. Precisa, sim, estar à
vontade lendo código e rodando comandos.

## Os três serviços

**`asteroid-service`** (porta 8080) é tudo que conversa com a NASA. Lê cinco APIs —
objetos próximos da Terra, a foto astronômica do dia, clima espacial, imagens da Terra
—, transforma tudo em records Java e publica um evento no Kafka para cada aproximação
perigosa encontrada. É o dono da chave de API, e nenhum outro processo a vê.

**`notification-service`** (porta 8081) consome esses eventos. Armazena cada um,
distribui em uma linha de entrega por assinante, e um worker envia os e-mails. Também
expõe uma API somente leitura para você perguntar o que foi armazenado e se o e-mail
realmente chegou.

**`web-ui`** (porta 8082) renderiza tudo isso como HTML. Não tem cliente da NASA nem
banco de dados; lê os outros dois via HTTP. É um WAR e não um JAR, porque serve JSP.

Há ainda o **`contracts`**, que não é um serviço: um record, compartilhado por produtor
e consumidor, definindo o evento que trafega entre eles.

## Como ler isto

Na ordem, se você tiver tempo. Cada documento pressupõe os anteriores.

Se estiver com pressa, três deles concentram a maior parte do conteúdo:

- [01 — Arquitetura](01-architecture.md), para entender por que são três serviços e não um.
- [03 — Clientes HTTP e resiliência](03-http-clients-and-resilience.md), o raciocínio
  mais detalhado do projeto: timeouts, retentativas, circuit breakers e o que acontece
  quando um upstream está lento em vez de quebrado.
- [04 — Orientado a eventos com Kafka](04-event-driven-kafka.md), para idempotência e
  dead letters, as duas ideias que tornam seguro reexecutar o pipeline.

## Este é um repositório didático

Duas consequências que vale conhecer desde já.

**Os comentários fazem parte do material.** Os arquivos POM e YAML são comentados muito
mais do que código de produção normalmente é, porque a maior parte do que registram são
armadilhas da migração Spring Boot 3 → 4 que custaram tempo real para descobrir. O
[documento 08](08-spring-boot-3-to-4.md) reúne todas.

**O histórico de commits também.** Cada mensagem explica o porquê, não o quê. Vale ler
`git log`.

**E os erros ficam à vista.** Onde algo não foi verificado, está escrito que não foi —
veja os records de tempestade e explosão solar do DONKI, ou
`asteroid-service/src/test/resources/nasa/README-fixtures.md`. Onde existe uma
alternativa mais simples que não foi adotada, o raciocínio está registrado — veja o
proxy de imagens do EPIC no [documento 02](02-nasa-apis.md). Um repositório didático que
esconde seus trade-offs ensina a coisa errada.

## Experimente

Antes de continuar lendo, coloque o sistema no ar — o resto faz mais sentido com o
navegador aberto:

```bash
docker compose up -d
./mvnw clean install -DskipTests

set -a; . ./.env; set +a
./mvnw -pl asteroid-service      spring-boot:run   # terminal 1
./mvnw -pl notification-service  spring-boot:run   # terminal 2
./mvnw -pl web-ui                spring-boot:run   # terminal 3
```

Depois abra <http://localhost:8082>. As instruções completas, inclusive o que fazer
quando um passo falha, estão no [documento 10](10-running-locally.md).
