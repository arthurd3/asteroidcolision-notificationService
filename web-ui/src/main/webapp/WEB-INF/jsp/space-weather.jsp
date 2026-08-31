<%@ include file="layout/head.jspf" %>

<h1 class="page-title">Space weather</h1>
<p class="lede">
    NASA's DONKI database: what the Sun has been doing, and what reached Earth.
    Thematically the closest neighbour to the asteroid feed &mdash; both answer
    "what is happening out there that could affect us".
</p>

<%-- The warning is not decoration. A DONKI query genuinely takes 60-90 seconds
     upstream, which is why asteroid-service gives it a 120s read timeout of its own
     and a bulkhead of two concurrent calls. A user who is not told will assume the
     page has hung. --%>
<p class="notice notice--slow">
    <strong>This request is slow.</strong> DONKI can take up to two minutes to answer.
    Only two of these run at once across the whole system, so if several people ask at
    the same time you may get a "too many concurrent requests" page rather than a wait.
</p>

<form class="form" method="get" action="${pageContext.request.contextPath}/space-weather">
    <div class="field">
        <label for="type">Event type</label>
        <select id="type" name="type">
            <option value="cme" ${type == 'cme' ? 'selected' : ''}>Coronal mass ejections</option>
            <option value="gst" ${type == 'gst' ? 'selected' : ''}>Geomagnetic storms</option>
            <option value="flr" ${type == 'flr' ? 'selected' : ''}>Solar flares</option>
        </select>
    </div>
    <div class="field">
        <label for="from">From</label>
        <input type="date" id="from" name="from" value="${requestedFrom}">
    </div>
    <div class="field">
        <label for="to">To</label>
        <input type="date" id="to" name="to" value="${requestedTo}">
    </div>
    <button class="button" type="submit">Show</button>
</form>

<%-- ------------------------------------------------- coronal mass ejections --%>
<c:if test="${type == 'cme'}">
    <section class="panel">
        <h2 class="panel__title">Coronal mass ejections (${fn:length(ejections)})</h2>
        <c:choose>
            <c:when test="${empty ejections}">
                <p class="muted">Nothing reported in this window.</p>
            </c:when>
            <c:otherwise>
                <div class="table-scroll">
                    <table class="table">
                        <thead>
                        <tr>
                            <th>Started</th><th>Source</th><th>Active region</th>
                            <th>Instruments</th><th>Note</th>
                        </tr>
                        </thead>
                        <tbody>
                        <c:forEach items="${ejections}" var="cme">
                            <tr>
                                <td>
                                    <a href="<c:out value='${cme.link}'/>" rel="noreferrer noopener">
                                        ${fmt.offsetDateTime(cme.startTime)}</a>
                                </td>
                                <td>
                                    <c:choose>
                                        <c:when test="${not empty cme.sourceLocation}">
                                            <c:out value="${cme.sourceLocation}"/>
                                        </c:when>
                                        <c:otherwise><span class="muted">not located</span></c:otherwise>
                                    </c:choose>
                                </td>
                                <%-- null means "source not identified", not "region zero",
                                     which is why the record boxes this field --%>
                                <td>
                                    <c:choose>
                                        <c:when test="${cme.activeRegionNum != null}">${cme.activeRegionNum}</c:when>
                                        <c:otherwise><span class="muted">unknown</span></c:otherwise>
                                    </c:choose>
                                </td>
                                <td>
                                    <c:forEach items="${cme.instruments}" var="instrument" varStatus="s">
                                        <c:out value="${instrument.displayName}"/><c:if test="${!s.last}">, </c:if>
                                    </c:forEach>
                                </td>
                                <td class="wrap-text"><c:out value="${cme.note}"/></td>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>
                </div>
            </c:otherwise>
        </c:choose>
    </section>
</c:if>

<%-- ------------------------------------------------------ geomagnetic storms --%>
<c:if test="${type == 'gst'}">
    <section class="panel">
        <h2 class="panel__title">Geomagnetic storms (${fn:length(storms)})</h2>
        <c:choose>
            <c:when test="${empty storms}">
                <p class="muted">Nothing reported in this window.</p>
            </c:when>
            <c:otherwise>
                <div class="table-scroll">
                    <table class="table">
                        <thead>
                        <tr><th>Started</th><th class="num">Peak Kp</th><th>Readings</th><th>Caused by</th></tr>
                        </thead>
                        <tbody>
                        <c:forEach items="${storms}" var="storm">
                            <tr>
                                <td>
                                    <a href="<c:out value='${storm.link}'/>" rel="noreferrer noopener">
                                        ${fmt.offsetDateTime(storm.startTime)}</a>
                                </td>
                                <td class="num">
                                    <c:set var="peak" value="${storm.peakKpIndex.orElse(null)}"/>
                                    <c:choose>
                                        <c:when test="${peak != null and peak >= 8}">
                                            <span class="badge badge--hazard">${peak}</span>
                                        </c:when>
                                        <c:when test="${peak != null}">${peak}</c:when>
                                        <c:otherwise><span class="muted">&mdash;</span></c:otherwise>
                                    </c:choose>
                                </td>
                                <td>${fn:length(storm.kpIndexReadings)}</td>
                                <%-- the one genuinely interesting relationship in this
                                     data: a storm points back at the CME that caused it --%>
                                <td>
                                    <c:forEach items="${storm.linkedEvents}" var="linked" varStatus="s">
                                        <span class="mono"><c:out value="${linked.activityId}"/></span><c:if test="${!s.last}">, </c:if>
                                    </c:forEach>
                                    <c:if test="${empty storm.linkedEvents}"><span class="muted">unlinked</span></c:if>
                                </td>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>
                </div>
                <p class="muted">
                    Kp runs 0 to 9. A storm is reported from 5 upwards; 8 or 9 is the
                    range where power grids and satellites are affected.
                </p>
            </c:otherwise>
        </c:choose>
    </section>
</c:if>

<%-- ----------------------------------------------------------- solar flares --%>
<c:if test="${type == 'flr'}">
    <section class="panel">
        <h2 class="panel__title">Solar flares (${fn:length(flares)})</h2>
        <c:choose>
            <c:when test="${empty flares}">
                <p class="muted">Nothing reported in this window.</p>
            </c:when>
            <c:otherwise>
                <div class="table-scroll">
                    <table class="table">
                        <thead>
                        <tr><th>Class</th><th>Began</th><th>Peaked</th><th>Ended</th>
                            <th>Source</th><th>Active region</th></tr>
                        </thead>
                        <tbody>
                        <c:forEach items="${flares}" var="flare">
                            <tr>
                                <td>
                                    <span class="badge ${flare.classLetter == 'X' ? 'badge--hazard' : (flare.classLetter == 'M' ? 'badge--pending' : 'badge--muted')}">
                                        <c:out value="${flare.classType}"/></span>
                                </td>
                                <td>
                                    <a href="<c:out value='${flare.link}'/>" rel="noreferrer noopener">
                                        ${fmt.offsetDateTime(flare.beginTime)}</a>
                                </td>
                                <td>${fmt.offsetDateTime(flare.peakTime)}</td>
                                <%-- a flare still going when DONKI was asked has no endTime --%>
                                <td>
                                    <c:choose>
                                        <c:when test="${flare.inProgress}">
                                            <span class="badge badge--pending">in progress</span>
                                        </c:when>
                                        <c:otherwise>${fmt.offsetDateTime(flare.endTime)}</c:otherwise>
                                    </c:choose>
                                </td>
                                <td><c:out value="${flare.sourceLocation}"/></td>
                                <td>
                                    <c:choose>
                                        <c:when test="${flare.activeRegionNum != null}">${flare.activeRegionNum}</c:when>
                                        <c:otherwise><span class="muted">unknown</span></c:otherwise>
                                    </c:choose>
                                </td>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>
                </div>
                <p class="muted">
                    The class scale is logarithmic: an X flare is ten times an M and a
                    hundred times a C.
                </p>
            </c:otherwise>
        </c:choose>
    </section>
</c:if>

<%@ include file="layout/foot.jspf" %>
