<%@ include file="layout/head.jspf" %>

<section class="panel panel--error">
    <h1 class="page-title">
        <c:out value="${pageTitle != null ? pageTitle : 'Something went wrong'}"/>
    </h1>

    <c:if test="${service != null}">
        <p class="lede">
            <strong><c:out value="${service}"/></strong>
            <c:choose>
                <c:when test="${unreachable}">could not be reached.</c:when>
                <c:otherwise>answered with HTTP <c:out value="${status}"/>.</c:otherwise>
            </c:choose>
        </p>
    </c:if>

    <%-- The backend's own RFC 9457 title and detail. Written once, in the service
         that actually knows what went wrong, and shown here verbatim. --%>
    <c:if test="${problemTitle != null}">
        <div class="problem">
            <h2 class="problem__title"><c:out value="${problemTitle}"/></h2>
            <c:if test="${problemDetail != null}">
                <p class="problem__detail"><c:out value="${problemDetail}"/></p>
            </c:if>
        </div>
    </c:if>

    <c:if test="${startCommand != null}">
        <p>An unreachable service is usually one that was never started:</p>
        <pre class="command"><c:out value="${startCommand}"/></pre>
    </c:if>

    <p><a class="more" href="${pageContext.request.contextPath}/">&larr; Back to the overview</a></p>
</section>

<%@ include file="layout/foot.jspf" %>
