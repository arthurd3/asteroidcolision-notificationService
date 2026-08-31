[← Persistência e Flyway](05-persistence-and-flyway.md) · [English](../en/06-jsp-frontend.md) · **Português (Brasil)**

# O front-end em JSP

## Comece pela parte honesta

A própria documentação de referência do Spring Boot diz:

> If possible, JSPs should be avoided. There are several known limitations when using
> them with embedded servlet containers.

("Se possível, JSPs devem ser evitados. Há várias limitações conhecidas ao usá-los com
contêineres de servlet embarcados.")

Essa é a posição do Boot, é razoável, e um repositório didático que a omitisse estaria
ensinando algo falso.

Então por que este projeto usa JSP mesmo assim?

Porque uma quantidade enorme de Java existente roda sobre isso. Se você trabalha com
aplicações web Java, você **vai** encontrar JSP, normalmente em algo velho o bastante
para ninguém querer reescrever, e conseguir ler e alterar com segurança é uma habilidade
real. Aprender isso em uma base de código pequena e limpa é muito melhor do que encontrar
pela primeira vez em uma de quinze anos.

Se você está começando algo novo, use Thymeleaf. Este projeto também usa, para os corpos
de e-mail no `notification-service`.

## WAR, não JAR

A única diferença estrutural entre o `web-ui` e os outros dois módulos:

```xml
<packaging>war</packaging>
```

O layout de JAR executável do Boot **não consegue servir JSPs** — as páginas ficam no
document root do arquivo, que um JAR não tem. Isso é um requisito, não uma preferência.

Um WAR executável continua funcionando com `java -jar`, e também pode ser colocado em um
Tomcat standalone. `WebUiApplication extends SpringBootServletInitializer` é o que torna
essa segunda opção real: um contêiner standalone não tem `main` e conduz a inicialização
pelo `ServletContainerInitializer`.

`./mvnw -pl web-ui spring-boot:run` funciona igual aos outros módulos, porque o mojo de
run filtra do classpath apenas artefatos de escopo *test* e portanto mantém o Jasper
`provided`.

## As dependências, e por que são `provided`

```xml
<dependency>
  <groupId>org.apache.tomcat.embed</groupId>
  <artifactId>tomcat-embed-jasper</artifactId>
  <scope>provided</scope>
</dependency>
<dependency>
  <groupId>org.glassfish.web</groupId>
  <artifactId>jakarta.servlet.jsp.jstl</artifactId>
  <scope>provided</scope>
</dependency>
```

`provided` as coloca em `WEB-INF/lib-provided` no WAR reempacotado, que o `java -jar`
carrega e um contêiner standalone ignora — assim nenhum dos dois acaba com duas cópias do
Jasper. O `spring-boot-starter-tomcat` é `provided` pelo mesmo motivo.

O `tomcat-embed-jasper` traz o `org.eclipse.jdt:ecj`, o compilador que o Jasper usa para
transformar um `.jsp` em servlet na primeira requisição.

**Não há `web.xml`**, e nenhum é necessário: o `maven-war-plugin` tem
`failOnMissingWebXml` como false por padrão desde a 3.1.0 quando
`jakarta.servlet.annotation.WebServlet` está no classpath, o que o `tomcat-embed-core`
fornece.

## Cinco coisas que falham silenciosamente

Cada uma custou tempo real. Nenhuma produz um erro útil.

### 1. URIs de taglib precisam ser as do Jakarta

```jsp
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
```

A URI `http://java.sun.com/jsp/jstl/core` de todo tutorial anterior a 2020 **não é
declarada** pelo JSTL 3.0 e falha na tradução com "The absolute uri cannot be resolved".
O Jakarta EE renomeou os namespaces; os tutoriais não.

### 2. O CSS não pode ficar em `src/main/webapp`

O Boot 4 define `server.servlet.register-default-servlet` como **false** por padrão.
Nenhum `DefaultServlet` é registrado, então nada serve o document root do WAR exceto o
Jasper servindo `.jsp`. Uma folha de estilo em `webapp/` é um 404 sem explicação.

Assets estáticos vão em `src/main/resources/static/`, que o próprio
`ResourceHttpRequestHandler` do Spring MVC serve. `resources/static/css/app.css` →
`/css/app.css`.

### 3. `error.jsp` precisa do whitelabel desligado — na grafia certa

```yaml
spring:
  web:
    error:
      whitelabel:
        enabled: false
```

Duas coisas separadas precisam estar certas.

**A grafia.** Toda a família `server.error.*` está depreciada em nível `error` desde o
Boot 4.0.0, substituída por `spring.web.error.*`. A chave antiga não liga a nada e
silenciosamente não funciona.

**A ordem dos resolvers.** A página whitelabel do Boot é um bean `View` **chamado
`error`**, e o `BeanNameViewResolver` (ordem 10) tem precedência sobre o
`InternalResourceViewResolver` (`LOWEST_PRECEDENCE`). Enquanto o whitelabel estiver
ligado, retornar `"error"` de um `@ControllerAdvice` resolve para ele e o
`/WEB-INF/jsp/error.jsp` nunca é alcançado — não importa o que seu advice faça.

### 4. `<fmt:formatDate>` não aceita `java.time`

O JSTL 3.0 ainda só aceita `java.util.Date` e `Calendar`. Recebendo um `LocalDate`, ele
falha em tempo de *renderização*, dentro da página, com uma mensagem que não nomeia campo
nem página.

A correção não é converter no JSP — isso significa scriptlets. É formatar em Java. O
`Formats` é um `@Component` que produz strings, e os modelos de view carregam valores que
já estão certos. Tudo é UTC e diz isso; uma página que renderiza horários astronômicos no
fuso em que o servidor por acaso está fica silenciosamente errada para a maioria dos
leitores.

### 5. Arquivos JSP devem permanecer em ASCII

`pageEncoding` só pode ser declarado uma vez por unidade de tradução — está no
`head.jspf` — e um `.jspf` incluído estaticamente não o herda para efeito de ler os
*próprios* bytes. Um caractere UTF-8 literal em um fragmento é decodificado com o padrão
do contêiner e vira mojibake (`â` onde deveria haver `◎`).

Todo JSP aqui é ASCII e usa entidades HTML: `&#9678;`, `&middot;`, `&mdash;`. Portátil, e
não precisa de `web.xml`.

## `${...}` não escapa

**A coisa mais importante desta página.**

Diferente do `th:text` do Thymeleaf, o `${...}` do JSP escreve cru. Texto não confiável
chega a estas páginas por quatro caminhos:

- `explanation` e `copyright` do APOD;
- `note` e `sourceLocation` do DONKI;
- `caption` do EPIC;
- `notification_delivery.last_error` — que contém o que quer que um servidor SMTP tenha
  dito.

Por isso **todo texto dinâmico passa por `<c:out>`**, cujo `escapeXml` é `true` por
padrão:

```jsp
<td class="wrap-text"><c:out value="${delivery.lastError}"/></td>
```

O `WebUiPagesIT#escapesUntrustedText` injeta um título de APOD igual a
`<script>alert(1)</script>` e verifica que a resposta contém `&lt;script&gt;` e não a tag
crua.

Repare onde o risco realmente está. O campo mais perigoso não são os dados da NASA — é o
`last_error`, porque é uma string de terceiros que este sistema armazena e depois
renderiza.

## Layout: includes simples

JSP não tem mecanismo nativo de layout. As opções são Apache Tiles (aposentado pela ASF
em 2018, sem release para Jakarta EE), SiteMesh (um filtro de servlet e uma dependência)
ou o include estático do próprio JSP. Este projeto usa o terceiro:

```jsp
<%@ include file="layout/head.jspf" %>
   ... corpo da página ...
<%@ include file="layout/foot.jspf" %>
```

`<%@ include %>` é **estático** — resolvido em tempo de tradução, produzindo um servlet
compilado por página. `<jsp:include>` é **dinâmico** — um dispatch de requisição por
include, em toda requisição, para marcação que nunca varia. A extensão `.jspf` marca um
fragmento como algo que não é uma página a ser requisitada diretamente.

Tudo vive sob `WEB-INF/`, que um contêiner de servlet não serve diretamente, então
ninguém consegue requisitar um `.jsp` e contornar o controller que deveria preencher seu
modelo.

## Por que `@WebMvcTest` não basta

**`@WebMvcTest` não renderiza JSPs.** Não há contêiner de servlet em um slice, então o
`MockMvc` relata o forward para `/WEB-INF/jsp/apod.jsp` e para por aí. Uma URI de taglib
quebrada, um erro de digitação numa expressão EL, um método que não existe no modelo de
view — tudo passa.

Então testes de slice verificam o nome da view e o modelo, nunca HTML:

```java
.andExpect(view().name("apod"))
.andExpect(forwardedUrl("/WEB-INF/jsp/apod.jsp"))
```

…e o `WebUiPagesIT` — `@SpringBootTest(webEnvironment = RANDOM_PORT)` com WireMock no
lugar dos dois backends — é o único teste do projeto que de fato compila as páginas. É
parametrizado sobre todos os caminhos. Detalhes no [documento 07](07-testing.md).

## Experimente

```bash
./mvnw -pl web-ui spring-boot:run
```

Depois, com os backends parados, abra <http://localhost:8082>: cada painel fica cinza
individualmente e nomeia o comando que sobe o serviço que falta. Isso é o
`HomeController#optional`, e é o único lugar onde uma `BackendUnavailableException` é
engolida — em todo o resto a página *é* os dados do backend, então cair na view de erro é
o resultado honesto.

Quebre de propósito:

```bash
# troque jakarta.tags.core por http://java.sun.com/jsp/jstl/core no head.jspf,
# reinicie e carregue qualquer página. A falha nomeia a URI, que é a única
# mensagem de erro de JSP genuinamente útil que você vai receber.

# mova app.css de resources/static para webapp/css e recarregue:
# HTTP 200 para a página, 404 para a folha de estilo, nenhum erro em lugar nenhum.
./mvnw -pl web-ui verify -Dtest='!*' -Dit.test=WebUiPagesIT
```
