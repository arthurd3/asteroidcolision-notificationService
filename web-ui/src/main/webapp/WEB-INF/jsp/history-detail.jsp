<%@ include file="layout/head.jspf" %>

<h1 class="page-title"><c:out value="${notification.asteroidName}"/></h1>
<p class="lede">
    Close approach ${fmt.date(notification.closeApproachDate)}
    &middot; ${fmt.kilometers(notification.missDistanceKilometers)}
    &middot; ${fmt.meters(notification.estimatedDiameterAvgMeters)} across
</p>

<div class="grid-2">
    <section class="panel">
        <h2 class="panel__title">Alert</h2>
        <dl class="definitions">
            <dt>Event id</dt><dd class="mono"><c:out value="${notification.eventId}"/></dd>
            <dt>Asteroid id</dt>
            <dd>
                <a href="${pageContext.request.contextPath}/neo/<c:out value='${notification.asteroidId}'/>">
                    <c:out value="${notification.asteroidId}"/></a>
            </dd>
            <dt>Observed</dt><dd>${fmt.instant(notification.occurredAt)}</dd>
            <dt>Stored</dt><dd>${fmt.instant(notification.createdAt)}</dd>
        </dl>
        <p class="muted">
            The event id is derived from the asteroid and its approach date, not
            generated randomly. That is what makes a repeated scan a no-op: the
            consumer has a unique constraint on this value.
        </p>
    </section>

    <section class="panel">
        <h2 class="panel__title">Delivery</h2>
        <p class="muted">
            One row per enabled subscriber. A notification fans out into a row each,
            and a worker claims them in batches with
            <code>SELECT ... FOR UPDATE SKIP LOCKED</code>, marking one SENT only
            after the mail server has accepted it.
        </p>
    </section>
</div>

<section class="panel">
    <h2 class="panel__title">Recipients (${fn:length(notification.deliveries)})</h2>
    <c:choose>
        <c:when test="${empty notification.deliveries}">
            <p class="muted">
                No deliveries were queued, which means no subscriber had notifications
                enabled when this alert arrived.
            </p>
        </c:when>
        <c:otherwise>
            <div class="table-scroll">
                <table class="table">
                    <thead>
                    <tr>
                        <th>Recipient</th><th>Status</th>
                        <th class="num">Attempts</th><th>Sent</th><th>Last error</th>
                    </tr>
                    </thead>
                    <tbody>
                    <c:forEach items="${notification.deliveries}" var="delivery">
                        <tr>
                            <td>
                                <c:out value="${delivery.recipientName}"/><br>
                                <span class="muted mono"><c:out value="${delivery.recipientEmail}"/></span>
                            </td>
                            <td>
                                <span class="badge ${delivery.status == 'SENT' ? 'badge--ok' : (delivery.status == 'FAILED' ? 'badge--hazard' : 'badge--pending')}">
                                    <c:out value="${delivery.status}"/></span>
                            </td>
                            <td class="num">${delivery.attempts}</td>
                            <td>${fmt.instant(delivery.sentAt)}</td>
                            <%-- lastError is whatever an SMTP server said: text from
                                 outside this system entirely. c:out escapes it; ${}
                                 alone would not, and this is the likeliest field on
                                 the whole site to carry something hostile. --%>
                            <td class="wrap-text"><c:out value="${delivery.lastError}"/></td>
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
            </div>
        </c:otherwise>
    </c:choose>
</section>

<p><a class="more" href="${pageContext.request.contextPath}/history">&larr; Back to the history</a></p>

<%@ include file="layout/foot.jspf" %>
