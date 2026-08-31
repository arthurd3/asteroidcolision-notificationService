<%@ include file="layout/head.jspf" %>

<h1 class="page-title">Near-Earth objects</h1>
<p class="lede">
    Objects with a close approach between two dates. NASA caps this window at seven
    days, which is why the form below will not accept a wider one.
</p>

<form class="form" method="get" action="${pageContext.request.contextPath}/neo">
    <div class="field">
        <label for="from">From</label>
        <input type="date" id="from" name="from" value="${requestedFrom}">
    </div>
    <div class="field">
        <label for="to">To</label>
        <input type="date" id="to" name="to" value="${requestedTo}">
    </div>
    <button class="button" type="submit">Show</button>
    <a class="button button--ghost" href="${pageContext.request.contextPath}/neo/browse">Browse the catalogue</a>
</form>

<section class="panel">
    <h2 class="panel__title">
        ${fmt.date(feed.from)} &ndash; ${fmt.date(feed.to)}
        &middot; ${feed.elementCount} objects
        &middot; ${feed.hazardousCount} potentially hazardous
    </h2>

    <c:choose>
        <c:when test="${empty feed.objects}">
            <p class="muted">Nothing is approaching in this window.</p>
        </c:when>
        <c:otherwise>
            <div class="table-scroll">
                <table class="table">
                    <thead>
                    <tr>
                        <th>Object</th>
                        <th>Approach</th>
                        <th class="num">Miss distance</th>
                        <th class="num">Lunar</th>
                        <th class="num">Speed</th>
                        <th class="num">Diameter</th>
                        <th></th>
                    </tr>
                    </thead>
                    <tbody>
                    <c:forEach items="${feed.objects}" var="object">
                        <tr>
                            <td>
                                <a href="${pageContext.request.contextPath}/neo/<c:out value='${object.id}'/>">
                                    <c:out value="${object.name}"/></a>
                            </td>
                            <c:set var="approach" value="${object.firstApproach.orElse(null)}"/>
                            <td>${fmt.date(approach.closeApproachDate)}</td>
                            <td class="num">${fmt.kilometers(approach.missDistance.kilometers)}</td>
                            <td class="num">${fmt.lunar(approach.missDistance.lunar)}</td>
                            <td class="num">${fmt.kilometersPerSecond(approach.relativeVelocity.kilometersPerSecond)}</td>
                            <td class="num">${fmt.meters(object.averageDiameterMeters.orElse(null))}</td>
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
            <p class="muted">
                "Lunar" is the distance in multiples of the average Earth&ndash;Moon
                distance, which is easier to picture than a seven-digit number of
                kilometres.
            </p>
        </c:otherwise>
    </c:choose>
</section>

<%@ include file="layout/foot.jspf" %>
