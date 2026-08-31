<%@ include file="layout/head.jspf" %>

<h1 class="page-title"><c:out value="${apod.title}"/></h1>
<p class="lede">${fmt.date(apod.date)}
    <c:if test="${not empty apod.copyright}">
        &middot; <c:out value="${apod.copyright}"/>
    </c:if>
</p>

<section class="panel">
    <%-- The media_type branch. About one entry a week is a video, and on those days
         url is an embed rather than an image and hdurl is absent entirely. --%>
    <c:choose>
        <c:when test="${apod.image}">
            <a href="<c:out value='${apod.displayUrl}'/>" rel="noreferrer noopener">
                <img class="hero" src="<c:out value='${apod.displayUrl}'/>"
                     alt="<c:out value='${apod.title}'/>">
            </a>
        </c:when>
        <c:when test="${apod.video}">
            <p class="notice">
                Today's entry is a video.
                <a href="<c:out value='${apod.url}'/>" rel="noreferrer noopener">Watch it &rarr;</a>
            </p>
        </c:when>
        <c:otherwise>
            <p class="notice">
                This entry is neither an image nor a video
                (<code><c:out value="${apod.mediaType}"/></code>).
                <a href="<c:out value='${apod.url}'/>" rel="noreferrer noopener">Open it &rarr;</a>
            </p>
        </c:otherwise>
    </c:choose>

    <p><c:out value="${apod.explanation}"/></p>

    <p class="muted">
        These URLs point at apod.nasa.gov and carry no API key, so the browser can
        load them directly. Compare the
        <a href="${pageContext.request.contextPath}/epic">Earth imagery</a> page,
        where the archive needs a key and every frame has to be proxied.
    </p>
</section>

<nav class="pager">
    <c:choose>
        <c:when test="${previousDate != null}">
            <a class="button button--ghost"
               href="${pageContext.request.contextPath}/apod?date=${previousDate}">&larr; Previous day</a>
        </c:when>
        <c:otherwise><span class="pager__state">Start of the archive</span></c:otherwise>
    </c:choose>

    <span class="pager__state">${fmt.date(apod.date)}</span>

    <c:choose>
        <c:when test="${nextDate != null}">
            <a class="button button--ghost"
               href="${pageContext.request.contextPath}/apod?date=${nextDate}">Next day &rarr;</a>
        </c:when>
        <c:otherwise><span class="pager__state">Today</span></c:otherwise>
    </c:choose>
</nav>

<%@ include file="layout/foot.jspf" %>
