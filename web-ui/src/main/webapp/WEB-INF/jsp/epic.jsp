<%@ include file="layout/head.jspf" %>

<h1 class="page-title">Earth imagery</h1>
<p class="lede">
    Full-disc photographs from the EPIC camera aboard NOAA's DSCOVR spacecraft, about
    a million miles out at the Earth&ndash;Sun L1 point. Roughly a dozen frames a day.
</p>

<c:if test="${not empty availableDates}">
    <form class="form" method="get" action="${pageContext.request.contextPath}/epic">
        <div class="field">
            <label for="date">Date</label>
            <select id="date" name="date">
                <c:forEach items="${availableDates}" var="day" end="120">
                    <option value="${day}" ${day eq selectedDate ? 'selected' : ''}>${fmt.date(day)}</option>
                </c:forEach>
            </select>
        </div>
        <button class="button" type="submit">Show</button>
        <a class="button button--ghost" href="${pageContext.request.contextPath}/epic">Most recent</a>
    </form>
</c:if>

<section class="panel">
    <h2 class="panel__title">${fn:length(frames)} frames</h2>
    <c:choose>
        <c:when test="${empty frames}">
            <p class="muted">No frames for this date.</p>
        </c:when>
        <c:otherwise>
            <div class="thumbs">
                <c:forEach items="${frames}" var="frame">
                    <figure class="thumb">
                        <%-- proxyPath is a path on THIS service, which proxies
                             asteroid-service, which proxies NASA. No api_key appears
                             anywhere in this page's source - view it and check. --%>
                        <img src="${pageContext.request.contextPath}<c:out value='${frame.proxyPath}'/>"
                             alt="<c:out value='${frame.caption}'/>" loading="lazy">
                        <figcaption>
                            ${fmt.dateTime(frame.date)}<br>
                            <c:if test="${frame.centroidCoordinates != null}">
                                <fmt:formatNumber value="${frame.centroidCoordinates.lat}" maxFractionDigits="2"/>,
                                <fmt:formatNumber value="${frame.centroidCoordinates.lon}" maxFractionDigits="2"/>
                            </c:if>
                        </figcaption>
                    </figure>
                </c:forEach>
            </div>
        </c:otherwise>
    </c:choose>
</section>

<section class="panel">
    <h2 class="panel__title">Why these images are proxied</h2>
    <p>
        NASA's EPIC archive requires the API key as a query parameter, so an archive
        URL cannot be written into a page &mdash; it would publish the credential to
        every browser, proxy and history file that saw it. asteroid-service therefore
        fetches the bytes server-side and hands out a key-free path, and this service
        proxies that again so the browser only ever talks to one origin.
    </p>
    <p class="muted">
        Honestly: <code>epic.gsfc.nasa.gov</code> serves the same PNGs with no key at
        all, which would make both hops unnecessary. What proxying buys is a single
        origin and a cache in front of a rate-limited upstream; what it costs is
        moving a couple of megabytes through two JVMs for a photograph. Compare the
        <a href="${pageContext.request.contextPath}/apod">picture of the day</a>,
        whose URLs need no key and are linked directly.
    </p>
</section>

<%@ include file="layout/foot.jspf" %>
