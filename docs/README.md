# Documentation · Documentação

Teaching material for this repository, in two languages.

| | |
|---|---|
| 🇬🇧 **English** | [Start here](en/00-overview.md) |
| 🇧🇷 **Português (Brasil)** | [Comece aqui](pt-BR/00-overview.md) |

## Contents · Conteúdo

| # | English | Português |
|---|---|---|
| 00 | [Overview](en/00-overview.md) | [Visão geral](pt-BR/00-overview.md) |
| 01 | [Architecture](en/01-architecture.md) | [Arquitetura](pt-BR/01-architecture.md) |
| 02 | [The NASA APIs](en/02-nasa-apis.md) | [As APIs da NASA](pt-BR/02-nasa-apis.md) |
| 03 | [HTTP clients and resilience](en/03-http-clients-and-resilience.md) | [Clientes HTTP e resiliência](pt-BR/03-http-clients-and-resilience.md) |
| 04 | [Event-driven with Kafka](en/04-event-driven-kafka.md) | [Orientado a eventos com Kafka](pt-BR/04-event-driven-kafka.md) |
| 05 | [Persistence and Flyway](en/05-persistence-and-flyway.md) | [Persistência e Flyway](pt-BR/05-persistence-and-flyway.md) |
| 06 | [The JSP front end](en/06-jsp-frontend.md) | [O front-end em JSP](pt-BR/06-jsp-frontend.md) |
| 07 | [Testing](en/07-testing.md) | [Testes](pt-BR/07-testing.md) |
| 08 | [Spring Boot 3 to 4](en/08-spring-boot-3-to-4.md) | [Spring Boot 3 para 4](pt-BR/08-spring-boot-3-to-4.md) |
| 09 | [Security and secrets](en/09-security-and-secrets.md) | [Segurança e segredos](pt-BR/09-security-and-secrets.md) |
| 10 | [Running locally](en/10-running-locally.md) | [Executando localmente](pt-BR/10-running-locally.md) |
| 11 | [Glossary](en/11-glossary.md) | [Glossário](pt-BR/11-glossary.md) |

---

## How these documents are written

Four rules, stated here so both trees stay usable.

**1. Only prose is translated.** Identifiers, YAML keys, commands, file paths, log
lines, exception names and code comments stay in English in *both* trees. A reader of
the Portuguese documents must be able to copy every command and `grep` every
identifier without translating anything back. This is the rule bilingual documentation
most often gets wrong, and getting it wrong makes the translated half useless.

**2. Identical filenames in both trees.** Cross-links are relative
(`../pt-BR/03-http-clients-and-resilience.md`), so a missing translation is a broken
link that someone notices, rather than a gap that nobody does.

**3. Code is referenced by path and symbol, never by line number.** `RestNasaNeoClient`
and `findAsteroids`, not `RestNasaNeoClient.java:47`. Line numbers rot within one
commit; symbol names survive until someone renames them, and a rename is exactly when
the documentation should be updated anyway.

**4. Every document ends with something you can run.** A command and what it should
print. A claim you cannot check is a claim you have to take on faith.

## Where the rest of the material is

Much of this project's reasoning is not in these files:

- **The commit history.** Each commit message explains why a change was made, not what
  changed — `git log` is part of the material. `git log --format='%s%n%n%b'` reads well.
- **The POMs and YAMLs.** They are unusually heavily commented, mostly with Spring Boot
  3 → 4 migration traps that cost real time to find. [Document 08](en/08-spring-boot-3-to-4.md)
  collects them.
- **`asteroid-service/src/test/resources/nasa/README-fixtures.md`**, which records which
  recorded API responses are real captures and which are hand-written — and why that
  distinction matters more than it looks.
