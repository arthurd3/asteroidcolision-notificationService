<%@ include file="layout/head.jspf" %>

<h1 class="page-title"><c:out value="${asteroid.name}"/></h1>
<p class="lede">
    <c:if test="${asteroid.potentiallyHazardous}">
        <span class="badge badge--hazard">potentially hazardous</span>
    </c:if>
    <c:if test="${asteroid.sentryObject}">
        <span class="badge badge--pending">tracked by Sentry</span>
    </c:if>
    <c:if test="${not empty asteroid.nasaJplUrl}">
        <a href="<c:out value='${asteroid.nasaJplUrl}'/>" rel="noreferrer noopener">JPL Small-Body Database &rarr;</a>
    </c:if>
</p>

<div class="grid-2">
    <section class="panel">
        <h2 class="panel__title">Object</h2>
        <dl class="definitions">
            <dt>Reference id</dt><dd class="mono"><c:out value="${asteroid.id}"/></dd>
            <c:if test="${not empty asteroid.designation}">
                <dt>Designation</dt><dd><c:out value="${asteroid.designation}"/></dd>
            </c:if>
            <dt>Diameter</dt><dd>${fmt.meters(asteroid.averageDiameterMeters.orElse(null))} (mean estimate)</dd>
            <c:if test="${asteroid.absoluteMagnitudeH != null}">
                <dt>Absolute magnitude</dt><dd>${asteroid.absoluteMagnitudeH} H</dd>
            </c:if>
        </dl>
    </section>

    <%-- orbital_data is present on lookup and browse, and absent from the feed.
         One record covers all three, so this has to be null-safe. --%>
    <section class="panel">
        <h2 class="panel__title">Orbit</h2>
        <c:choose>
            <c:when test="${asteroid.orbitalData != null}">
                <dl class="definitions">
                    <c:if test="${asteroid.orbitalData.orbitClass != null}">
                        <dt>Class</dt>
                        <dd>
                            <c:out value="${asteroid.orbitalData.orbitClass.type}"/>
                            &mdash; <c:out value="${asteroid.orbitalData.orbitClass.description}"/>
                        </dd>
                    </c:if>
                    <dt>Eccentricity</dt><dd><c:out value="${asteroid.orbitalData.eccentricity}"/></dd>
                    <dt>Inclination</dt><dd><c:out value="${asteroid.orbitalData.inclination}"/>&deg;</dd>
                    <dt>Semi-major axis</dt><dd><c:out value="${asteroid.orbitalData.semiMajorAxis}"/> AU</dd>
                    <dt>Perihelion</dt><dd><c:out value="${asteroid.orbitalData.perihelionDistance}"/> AU</dd>
                    <dt>Aphelion</dt><dd><c:out value="${asteroid.orbitalData.aphelionDistance}"/> AU</dd>
                    <dt>Orbital period</dt><dd><c:out value="${asteroid.orbitalData.orbitalPeriod}"/> days</dd>
                    <dt>MOID</dt><dd><c:out value="${asteroid.orbitalData.minimumOrbitIntersection}"/> AU</dd>
                    <dt>Uncertainty</dt><dd><c:out value="${asteroid.orbitalData.orbitUncertainty}"/> (0 = best)</dd>
                    <dt>Observations</dt><dd>${asteroid.orbitalData.observationsUsed}</dd>
                    <dt>Observed</dt>
                    <dd>${fmt.date(asteroid.orbitalData.firstObservationDate)}
                        &ndash; ${fmt.date(asteroid.orbitalData.lastObservationDate)}</dd>
                </dl>
                <p class="muted">
                    MOID is how close the two orbits come to each other regardless of
                    where the bodies are. It is the number that decides whether an
                    object is classified as potentially hazardous.
                </p>
            </c:when>
            <c:otherwise>
                <p class="muted">The feed does not include orbital data for this object.</p>
            </c:otherwise>
        </c:choose>
    </section>
</div>

<section class="panel">
    <h2 class="panel__title">Close approaches</h2>
    <c:choose>
        <c:when test="${empty asteroid.closeApproachData}">
            <p class="muted">No approaches on record.</p>
        </c:when>
        <c:otherwise>
            <div class="table-scroll">
                <table class="table">
                    <thead>
                    <tr>
                        <th>Date</th>
                        <th>Body</th>
                        <th class="num">Miss distance</th>
                        <th class="num">Lunar</th>
                        <th class="num">Relative speed</th>
                    </tr>
                    </thead>
                    <tbody>
                    <c:forEach items="${asteroid.closeApproachData}" var="approach">
                        <tr>
                            <td><c:out value="${approach.closeApproachDateFull}"/></td>
                            <%-- lookup returns approaches to Mercury, Venus, Mars and
                                 Jupiter as well as Earth, which is worth showing --%>
                            <td><c:out value="${approach.orbitingBody}"/></td>
                            <td class="num">${fmt.kilometers(approach.missDistance.kilometers)}</td>
                            <td class="num">${fmt.lunar(approach.missDistance.lunar)}</td>
                            <td class="num">${fmt.kilometersPerSecond(approach.relativeVelocity.kilometersPerSecond)}</td>
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
            </div>
        </c:otherwise>
    </c:choose>
</section>

<p><a class="more" href="${pageContext.request.contextPath}/neo">&larr; Back to the feed</a></p>

<%@ include file="layout/foot.jspf" %>
