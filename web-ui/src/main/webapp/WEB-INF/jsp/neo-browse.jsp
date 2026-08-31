<%@ include file="layout/head.jspf" %>

<h1 class="page-title">The catalogue</h1>
<p class="lede">
    Every known near-Earth object &mdash; ${fmt.count(browse.page.totalElements)} of
    them, across ${fmt.count(browse.page.totalPages)} pages. This is genuinely
    paginated: there is no "fetch it all and filter in the browser" version of this
    page.
</p>

<section class="panel">
    <div class="table-scroll">
        <table class="table">
            <thead>
            <tr>
                <th>Object</th>
                <th>Class</th>
                <th class="num">Diameter</th>
                <th class="num">Magnitude</th>
                <th></th>
            </tr>
            </thead>
            <tbody>
            <c:forEach items="${browse.nearEarthObjects}" var="object">
                <tr>
                    <td>
                        <a href="${pageContext.request.contextPath}/neo/<c:out value='${object.id}'/>">
                            <c:out value="${object.name}"/></a>
                    </td>
                    <td>
                        <c:if test="${object.orbitalData != null and object.orbitalData.orbitClass != null}">
                            <c:out value="${object.orbitalData.orbitClass.type}"/>
                        </c:if>
                    </td>
                    <td class="num">${fmt.meters(object.averageDiameterMeters.orElse(null))}</td>
                    <td class="num">
                        <c:if test="${object.absoluteMagnitudeH != null}">${object.absoluteMagnitudeH}</c:if>
                    </td>
                    <td>
                        <c:if test="${object.potentiallyHazardous}">
                            <span class="badge badge--hazard">hazardous</span>
                        </c:if>
                    </td>
                </tr>
            </c:forEach>
            </tbody>
        </table>
    </div>
</section>

<nav class="pager">
    <c:choose>
        <c:when test="${browse.page.number > 0}">
            <a class="button button--ghost"
               href="${pageContext.request.contextPath}/neo/browse?page=${browse.page.number - 1}&size=${size}">&larr; Previous</a>
        </c:when>
        <c:otherwise><span class="pager__state">First page</span></c:otherwise>
    </c:choose>

    <span class="pager__state">
        Page ${fmt.count(browse.page.number + 1)} of ${fmt.count(browse.page.totalPages)}
    </span>

    <c:choose>
        <c:when test="${browse.page.number + 1 < browse.page.totalPages}">
            <a class="button button--ghost"
               href="${pageContext.request.contextPath}/neo/browse?page=${browse.page.number + 1}&size=${size}">Next &rarr;</a>
        </c:when>
        <c:otherwise><span class="pager__state">Last page</span></c:otherwise>
    </c:choose>
</nav>

<%@ include file="layout/foot.jspf" %>
