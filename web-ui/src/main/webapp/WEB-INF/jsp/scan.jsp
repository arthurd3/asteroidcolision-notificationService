<%@ include file="layout/head.jspf" %>

<h1 class="page-title">Run a scan</h1>
<p class="lede">
    The only button on this site that changes anything. It asks asteroid-service to
    read the NASA feed and publish an event for every potentially hazardous close
    approach it finds &mdash; through Kafka, into MySQL, and eventually into an inbox.
</p>

<section class="panel">
    <form class="form" method="post" action="${pageContext.request.contextPath}/scan">
        <div class="field">
            <label for="from">From (optional)</label>
            <input type="date" id="from" name="from">
        </div>
        <div class="field">
            <label for="to">To (optional)</label>
            <input type="date" id="to" name="to">
        </div>
        <button class="button" type="submit">Scan</button>
    </form>
    <p class="muted">
        Leave both empty to scan today through the configured lookahead. The window
        cannot exceed seven days &mdash; that is the NEO feed's own limit.
    </p>
</section>

<c:if test="${summary != null}">
    <section class="panel">
        <h2 class="panel__title">Result</h2>
        <div class="tiles">
            <div class="tile">
                <span class="tile__value">${summary.scanned}</span>
                <span class="tile__label">objects scanned</span>
            </div>
            <div class="tile ${summary.hazardous > 0 ? 'tile--failed' : ''}">
                <span class="tile__value">${summary.hazardous}</span>
                <span class="tile__label">potentially hazardous</span>
            </div>
            <div class="tile">
                <span class="tile__value">${summary.published}</span>
                <span class="tile__label">events published</span>
            </div>
        </div>
        <p class="muted">
            Window ${fmt.date(summary.from)} &ndash; ${fmt.date(summary.to)}.
            <a href="${pageContext.request.contextPath}/history">See what arrived &rarr;</a>
        </p>
        <p class="muted">
            Running this again over the same window publishes the same events and
            changes nothing downstream. The event id is derived from the asteroid and
            its approach date rather than generated randomly, and the consumer has a
            unique constraint on it, so a repeated scan adds no rows and sends no
            duplicate email.
        </p>
    </section>
</c:if>

<%@ include file="layout/foot.jspf" %>
