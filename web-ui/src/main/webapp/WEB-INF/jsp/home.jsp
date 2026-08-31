<%@ include file="layout/head.jspf" %>

<h1 class="page-title">Overview</h1>
<p class="lede">
    Everything below is fetched live. Each panel is requested separately and fails on
    its own, so one backend being down greys out one card rather than the whole page.
</p>

<%-- ------------------------------------------------------------ delivery stats --%>
<section class="panel">
    <h2 class="panel__title">The pipeline</h2>
    <c:choose>
        <c:when test="${stats != null}">
            <div class="tiles">
                <div class="tile">
                    <span class="tile__value">${fmt.count(stats.notifications)}</span>
                    <span class="tile__label">alerts stored</span>
                </div>
                <div class="tile">
                    <span class="tile__value">${fmt.count(stats.enabledSubscribers)}</span>
                    <span class="tile__label">subscribers</span>
                </div>
                <div class="tile">
                    <span class="tile__value">${fmt.count(stats.sent)}</span>
                    <span class="tile__label">emails sent</span>
                </div>
                <div class="tile ${stats.pending > 0 ? 'tile--pending' : ''}">
                    <span class="tile__value">${fmt.count(stats.pending)}</span>
                    <span class="tile__label">pending</span>
                </div>
                <div class="tile ${stats.failed > 0 ? 'tile--failed' : ''}">
                    <span class="tile__value">${fmt.count(stats.failed)}</span>
                    <span class="tile__label">failed</span>
                </div>
            </div>
            <p class="muted">
                <c:choose>
                    <c:when test="${stats.everIngested}">
                        Last alert stored ${fmt.instant(stats.lastIngestedAt)}.
                    </c:when>
                    <c:otherwise>
                        Nothing ingested yet &mdash;
                        <a href="${pageContext.request.contextPath}/scan">run a scan</a>
                        to put something through the pipeline.
                    </c:otherwise>
                </c:choose>
                <a href="${pageContext.request.contextPath}/history">See the full history</a>.
            </p>
        </c:when>
        <c:otherwise>
            <p class="unavailable">
                <c:out value="${notificationServiceName}"/> is not responding, so the
                pipeline figures are unavailable. Start it with
                <code>./mvnw -pl notification-service spring-boot:run</code>.
            </p>
        </c:otherwise>
    </c:choose>
</section>

<div class="grid-2">
    <%-- ------------------------------------------------------------------ APOD --%>
    <section class="panel">
        <h2 class="panel__title">Picture of the day</h2>
        <c:choose>
            <c:when test="${apod != null}">
                <%-- APOD publishes video about one day a week; rendering that day in
                     an <img> is the bug this branch exists to prevent --%>
                <c:if test="${apod.image}">
                    <a href="${pageContext.request.contextPath}/apod">
                        <img class="hero" src="<c:out value='${apod.displayUrl}'/>"
                             alt="<c:out value='${apod.title}'/>" loading="lazy">
                    </a>
                </c:if>
                <c:if test="${apod.video}">
                    <p class="muted">Today's entry is a video.</p>
                </c:if>
                <h3 class="card__heading"><c:out value="${apod.title}"/></h3>
                <p class="muted">${fmt.date(apod.date)}</p>
                <p class="clamp"><c:out value="${apod.explanation}"/></p>
                <a class="more" href="${pageContext.request.contextPath}/apod">Read more &rarr;</a>
            </c:when>
            <c:otherwise>
                <p class="unavailable">
                    <c:out value="${asteroidServiceName}"/> did not return today's picture.
                </p>
            </c:otherwise>
        </c:choose>
    </section>

    <%-- ------------------------------------------------------------------- NEO --%>
    <section class="panel">
        <h2 class="panel__title">Close approaches, next 7 days</h2>
        <c:choose>
            <c:when test="${feed != null}">
                <div class="tiles">
                    <div class="tile">
                        <span class="tile__value">${feed.elementCount}</span>
                        <span class="tile__label">objects</span>
                    </div>
                    <div class="tile ${feed.hazardousCount > 0 ? 'tile--failed' : ''}">
                        <span class="tile__value">${feed.hazardousCount}</span>
                        <span class="tile__label">potentially hazardous</span>
                    </div>
                </div>
                <p class="muted">
                    ${fmt.date(feed.from)} &ndash; ${fmt.date(feed.to)}
                </p>
                <c:if test="${not empty feed.objects}">
                    <ul class="mini-list">
                        <c:forEach items="${feed.objects}" var="object" end="4">
                            <li>
                                <a href="${pageContext.request.contextPath}/neo/<c:out value='${object.id}'/>">
                                    <c:out value="${object.name}"/></a>
                                <c:if test="${object.potentiallyHazardous}">
                                    <span class="badge badge--hazard">hazardous</span>
                                </c:if>
                            </li>
                        </c:forEach>
                    </ul>
                </c:if>
                <a class="more" href="${pageContext.request.contextPath}/neo">See the feed &rarr;</a>
            </c:when>
            <c:otherwise>
                <p class="unavailable">
                    <c:out value="${asteroidServiceName}"/> did not return the feed.
                </p>
            </c:otherwise>
        </c:choose>
    </section>
</div>

<%-- ------------------------------------------------------------------- EPIC --%>
<section class="panel">
    <h2 class="panel__title">Earth, most recently</h2>
    <c:choose>
        <c:when test="${epic != null and not empty epic}">
            <div class="thumbs">
                <c:forEach items="${epic}" var="frame" end="5">
                    <figure class="thumb">
                        <%-- imagePath is a path on this service. No API key appears in
                             the page source, which is the whole point of the proxy. --%>
                        <img src="${pageContext.request.contextPath}<c:out value='${frame.proxyPath}'/>"
                             alt="<c:out value='${frame.caption}'/>" loading="lazy">
                        <figcaption>${fmt.dateTime(frame.date)}</figcaption>
                    </figure>
                </c:forEach>
            </div>
            <a class="more" href="${pageContext.request.contextPath}/epic">All frames &rarr;</a>
        </c:when>
        <c:otherwise>
            <p class="unavailable">
                <c:out value="${asteroidServiceName}"/> did not return any Earth imagery.
            </p>
        </c:otherwise>
    </c:choose>
</section>

<%@ include file="layout/foot.jspf" %>
