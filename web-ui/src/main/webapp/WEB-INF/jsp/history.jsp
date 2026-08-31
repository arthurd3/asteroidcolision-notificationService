<%@ include file="layout/head.jspf" %>

<h1 class="page-title">Alert history</h1>
<p class="lede">
    Everything notification-service has stored, newest first, with how each alert's
    emails went. Before this page existed the answer to "did that alert reach anyone"
    was a MySQL query the README told you to type.
</p>

<section class="panel">
    <c:choose>
        <c:when test="${empty history.content}">
            <p class="muted">
                Nothing stored yet.
                <a href="${pageContext.request.contextPath}/scan">Run a scan</a>
                to put something through the pipeline.
            </p>
        </c:when>
        <c:otherwise>
            <div class="table-scroll">
                <table class="table">
                    <thead>
                    <tr>
                        <th>Asteroid</th>
                        <th>Approach</th>
                        <th class="num">Miss distance</th>
                        <th class="num">Diameter</th>
                        <th>Stored</th>
                        <th>Delivery</th>
                    </tr>
                    </thead>
                    <tbody>
                    <c:forEach items="${history.content}" var="alert">
                        <tr>
                            <td>
                                <a href="${pageContext.request.contextPath}/history/<c:out value='${alert.eventId}'/>">
                                    <c:out value="${alert.asteroidName}"/></a>
                            </td>
                            <td>${fmt.date(alert.closeApproachDate)}</td>
                            <td class="num">${fmt.kilometers(alert.missDistanceKilometers)}</td>
                            <td class="num">${fmt.meters(alert.estimatedDiameterAvgMeters)}</td>
                            <td>${fmt.instant(alert.createdAt)}</td>
                            <td>
                                <c:if test="${alert.sent > 0}">
                                    <span class="badge badge--ok">${alert.sent} sent</span>
                                </c:if>
                                <c:if test="${alert.pending > 0}">
                                    <span class="badge badge--pending">${alert.pending} pending</span>
                                </c:if>
                                <c:if test="${alert.failed > 0}">
                                    <span class="badge badge--hazard">${alert.failed} failed</span>
                                </c:if>
                            </td>
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
            </div>
        </c:otherwise>
    </c:choose>
</section>

<c:if test="${history.totalPages > 1}">
    <nav class="pager">
        <c:choose>
            <c:when test="${history.hasPrevious}">
                <a class="button button--ghost"
                   href="${pageContext.request.contextPath}/history?page=${history.page - 1}&size=${size}">&larr; Newer</a>
            </c:when>
            <c:otherwise><span class="pager__state">Newest</span></c:otherwise>
        </c:choose>

        <span class="pager__state">
            Page ${history.displayPage} of ${history.totalPages}
            &middot; ${fmt.count(history.totalElements)} alerts
        </span>

        <c:choose>
            <c:when test="${history.hasNext}">
                <a class="button button--ghost"
                   href="${pageContext.request.contextPath}/history?page=${history.page + 1}&size=${size}">Older &rarr;</a>
            </c:when>
            <c:otherwise><span class="pager__state">Oldest</span></c:otherwise>
        </c:choose>
    </nav>
</c:if>

<%@ include file="layout/foot.jspf" %>
