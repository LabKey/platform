<%
/*
 * Copyright (c) 2020 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
%>
<%@ page import="org.labkey.api.pipeline.PipelineJob.TaskStatus" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.pipeline.status.LogFileParser" %>
<%@ page import="org.labkey.pipeline.status.StatusController" %>
<%@ page import="org.labkey.pipeline.status.StatusDetailsBean" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.Map" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    StatusController.DetailsBean bean = (StatusController.DetailsBean) getModelBean();
    StatusDetailsBean status = bean.status;
    long nextOffset = status.log != null ? status.log.nextOffset : -1;

    ActionURL cancelURL = bean.cancelUrl;
    ActionURL retryURL = bean.retryUrl;
    ActionURL browseFilesURL = bean.browseFilesUrl;
    ActionURL showListURL = bean.showListUrl;
    ActionURL showDataURL = bean.dataUrl;
    Date modified = bean.modified;
%>
<%!
    // keep in sync with JavaScript logTextClass function
    String logTextClass(LogFileParser.Record record)
    {
        StringBuilder sb = new StringBuilder();

        switch (record.getLevel())
        {
            case "DEBUG":
                sb.append("text-muted hidden");
                break;
            case "WARN":
                sb.append("text-warning");
                break;
            case "ERROR":
            case "FATAL":
                sb.append("text-danger");
                break;
            case "INFO":
            default:
                String lower = record.getLines().toLowerCase();
                if (lower.contains("success") || lower.contains("completed"))
                    sb.append("text-success");
                break;
        }

        if (record.isMultiline())
        {
            sb.append(" multiline");
            if (record.isStackTrace())
                sb.append(" collapsed");
            else
                sb.append(" expanded");
        }

        return sb.toString();
    }
%>
<style type="text/css">
    #log-container {
        /* viewport height minus a bit for the panel header and log controls and body margin-bottom (50px) */
        height: calc(100vh - 10em - 50px);
        overflow: auto;
    }
    td.split-job-status {
        width: 15em;
    }
</style>
<div id="error-list">
    <labkey:errors/>
</div>
<labkey:panel title="Job Status" id="job-status" type="portal">
    <table>
        <tbody>
        <tr>
            <td class="lk-form-label">Created:</td>
            <td id="created"><%=h(status.created)%></td>
        </tr>
        <tr>
            <td class="lk-form-label">Modified:</td>
            <td id="modified"><%=h(status.modified)%></td>
        </tr>
        <tr>
            <td class="lk-form-label">Email:</td>
            <td id="email"><%=h(status.email)%></td>
        </tr>
        <tr>
            <td class="lk-form-label">Status:</td>
            <td id="status">
                <% if (status.active) { %>
                <i id="status-spinner" class="fa fa-spinner fa-pulse"></i>
                <% } %>
                <span id="status-text"><%=h(status.status)%></span>
            </td>
        </tr>
        <tr>
            <td class="lk-form-label">Info:</td>
            <td id="info"><%=h(status.info)%></td>
        </tr>
        <% if (bean.queuePosition != null) { %>
            <tr>
                <td class="lk-form-label">Queue Position:</td>
                <td id="queuePosition"><%=h(bean.queuePosition)%></td>
            </tr>
        <% } %>
        <tr>
            <td class="lk-form-label">Description:</td>
            <td id="description"><%=h(status.description)%></td>
        </tr>
        <tr>
            <td class="lk-form-label">File Path:</td>
            <td id="file-path"><%=h(status.filePath)%></td>
        </tr>
        <tr class="<%=h(status.files.isEmpty() ? "hidden" : "")%>">
            <td class="lk-form-label">Files:</td>
            <td id="files-list">
                <% for (var file : status.files) { %>
                <a href="<%=h(file.viewUrl)%>"><%=h(file.name)%></a><br>
                <% } %>
            </td>
        </tr>
        <tr class="<%=h(status.parentStatus == null ? "hidden" : "")%>">
            <td class="lk-form-label">Join job:</td>
            <td id="parent-job">
                <table class="table-bordered table-condensed">
                    <thead>
                    <tr>
                        <th>Status</th>
                        <th>Description</th>
                    </tr>
                    </thead>
                    <tbody id="parent-job-table-body">
                    <% if (status.parentStatus != null) { %>
                    <tr class="labkey-alternate-row" data-rowid="<%=status.parentStatus.rowId%>">
                        <td class="split-job-status">
                            <a href="<%=h(StatusController.urlDetails(getContainer(), status.parentStatus.rowId))%>">
                                <%=h(status.parentStatus.status)%>
                            </a>
                        </td>
                        <td class="split-job-description">
                            <%=h(status.parentStatus.description)%>
                        </td>
                    </tr>
                    <% } %>
                    </tbody>
                </table>
            </td>
        </tr>
        <tr class="<%=h(status.splitStatus == null || status.splitStatus.isEmpty() ? "hidden" : "")%>">
            <td class="lk-form-label">Split jobs:</td>
            <td id="split-jobs">
                <table class="table-bordered table-condensed">
                    <thead>
                    <tr>
                        <th>Status</th>
                        <th>Description</th>
                    </tr>
                    </thead>
                    <tbody id="split-jobs-table-body">
                    <% if (status.splitStatus != null) { %>
                    <% for (int i = 0; i < status.splitStatus.size(); i++) { %>
                        <% var split = status.splitStatus.get(i); %>
                    <tr class="<%=getShadeRowClass(i)%>" data-rowid="<%=split.rowId%>">
                        <td class="split-job-status">
                            <a href="<%=h(StatusController.urlDetails(getContainer(), split.rowId))%>">
                                <%=h(split.status)%>
                            </a>
                        </td>
                        <td class="split-job-description">
                            <%=h(split.description)%>
                        </td>
                    </tr>
                    <% } %>
                    <% } %>
                    </tbody>
                </table>
            </td>
        </tr>
        <tr class="<%=h(status.runs.isEmpty() ? "hidden" : "")%>">
            <td class="lk-form-label">Completed Runs:</td>
            <td id="runs-list">
                <% for (var run : status.runs) { %>
                <a href="<%=h(run.url)%>" id="run-<%=run.rowId%>"><%=h(run.name)%></a><br>
                <% } %>
            </td>
        </tr>
        </tbody>
    </table>
    <div class="labkey-button-bar-separate">
        <%=button("Show Grid").href(showListURL)%>

        <%-- NOTE: showDataURL is null until the job is complete.  When complete, the button will be shown. --%>
        <%=button("Data").id("show-data-btn").href(showDataURL).addClass(status.active || showDataURL == null ? "hidden" : "")%>

        <% if (browseFilesURL != null) { %>
        <%=button("Browse Files").href(browseFilesURL)%>
        <% } %>

        <% if (status.active && cancelURL != null) { %>
        <%=button("Cancel").id("cancel-btn").usePost().href(cancelURL)%>
        <% } %>

        <% if (retryURL != null) { %>
        <%=button("Retry").id("retry-btn").usePost().href(retryURL).addClass(!(TaskStatus.error.matches(status.status) || TaskStatus.cancelled.matches(status.status)) ? "hidden" : "")%>
        <% } %>
    </div>
</labkey:panel>
<labkey:panel title="Log File" type="portal">
    <div id="log-controls">
        <%=link("Show full log file").id("show-full-log").attributes(Map.of("data-details", "false")).onClick("toggleShowFullLog();return false;")%>
        <%
            URLHelper url = status.getWebDavUrl();
            if (url != null)
            {
        %>
            <%=link("Show raw log file").id("show-raw-log").attributes(Map.of("data-details", "false")).href(url).target("_blank")%>
        <%
            }
        %>
        <%=link("Copy to clipboard").id("copy-log-text").onClick("copyLogText(event);return false;")%>
    </div>
    <br>
    <div id="log-container">
        <% if (status.log == null) { %>
        <em>Log file doesn't exist.</em>
        <% } else { %>
        <div id="log-data">
            <% if (!status.log.success && status.log.message != null) { %>
            <pre class="labkey-log-text bg-warning text-danger"><%=h(status.log.message)%></pre>
            <% } else if (status.log.records != null) { %>
                <% for (var record : status.log.records) { %>
                <pre class="labkey-log-text <%=h(logTextClass(record))%>"
                     data-multiline="<%=record.isMultiline()%>"
                     data-stacktrace="<%=record.isStackTrace()%>"
                     data-level="<%=h(record.getLevel())%>"
                ><div><%=h(record.getLines())%></div></pre>
                <% } %>
        </div>
            <% } %>
        <% } %>
    </div>
</labkey:panel>

<script type="application/javascript">
    let createdEl = document.getElementById('created');
    let modifiedEl = document.getElementById('modified');
    let emailEl = document.getElementById('email');
    let statusSpinnerEl = document.getElementById('status-spinner');
    let statusTextEl = document.getElementById('status-text');
    let infoEl = document.getElementById('info');
    let descriptionEl = document.getElementById('description');
    let filePathEl = document.getElementById('file-path');
    let queuePosition = document.getElementById('queuePosition');

    let filesListEl = document.getElementById('files-list');
    let runsListEl = document.getElementById('runs-list');

    let parentJobTableBodyEl = document.getElementById('parent-job-table-body');
    let splitJobsTableBodyEl = document.getElementById('split-jobs-table-body');

    let logContainerEl = document.getElementById('log-container');
    let logDataEl = document.getElementById('log-data');

    // controls
    let showDataBtnEl = document.getElementById('show-data-btn');
    let cancelBtnEl = document.getElementById('cancel-btn');
    let retryBtnEl = document.getElementById('retry-btn');
    let showFullLogEl = document.getElementById('show-full-log');

    function isShowingDetails() {
        return showFullLogEl.dataset.details === "true";
    }

    // show-hide DEBUG messages and expand-collapse multiline blocks
    function toggleShowFullLog() {
        if (isShowingDetails()) {
            showFullLogEl.dataset.details = "false";
            showFullLogEl.innerHTML = "Show full log file";
            if (logDataEl) {
                logDataEl.querySelectorAll("pre[data-level='DEBUG']").forEach(function (n) {
                    n.classList.add('hidden');
                });
                logDataEl.querySelectorAll("pre[data-multiline='true']").forEach(function (n) {
                    n.classList.replace('expanded', 'collapsed');
                });
            }
        }
        else {
            showFullLogEl.dataset.details = "true";
            showFullLogEl.innerHTML = "Show summary";
            if (logDataEl) {
                logDataEl.querySelectorAll("pre[data-level='DEBUG']").forEach(function (n) {
                    n.classList.remove('hidden');
                });
                logDataEl.querySelectorAll("pre[data-multiline='true']").forEach(function (n) {
                    n.classList.replace('collapsed', 'expanded');
                });
            }
        }
    }

    // expand-collapse single block
    logContainerEl.addEventListener('click', function (e) {
        let n = e.target;
        if (n.classList.contains('multiline')) {
            if (n.classList.contains('expanded')) {
                n.classList.replace('expanded', 'collapsed');
            }
            else {
                n.classList.replace('collapsed', 'expanded');
            }
        }
    });

    function copyLogText(e) {
        if (logDataEl) {
            navigator.clipboard.writeText(logDataEl.innerText).then(
                    function () {
                        let orig = e.target.innerHTML;
                        e.target.innerHTML = "Copied!";
                        setTimeout(function () {
                            e.target.innerHTML = orig;
                        }, 2000);
                    },
                    function () {
                        e.target.innerHTML = "Copy failed. Ensure the browser's security policy allows clipboard APIs and the page is using HTTPS.";
                    }).catch(function(error) {
                        console.log(error);
                        e.target.innerText = "Copy failed: " + error;
                    }
            );
        }
    }

    function scrollLog(smooth) {
        if (logDataEl) {
            logContainerEl.scrollTo({
                top: logDataEl.offsetHeight,
                left: 0,
                behavior: smooth ? 'smooth' : 'auto'
            });
        }
    }

    scrollLog(false);
</script>

<%-- fetch updates if the job is active, there was an error reading the log file on first render, or the job has
ended very recently. --%>
<%
    long diffTime = new Date().getTime() - modified.getTime();
    long diffMin = diffTime / (60 * 1000);

    if (status.active || status.log == null || !status.log.success || diffMin < 1) { %>
<script type="application/javascript">
    (function () {

        let active = <%=status.active%>;
        let nextOffset = <%=nextOffset%>;
        let fetchCount = 0;

        let offsetUnchangedCount = 0;
        const MAX_UNCHANGED_COUNT = 3;

        function updateField(el, text) {
            if (text !== null && text !== undefined && el)
                el.innerText = text;
        }

        function updateStatus(active, status, hadError) {
            if (!active) {
                if (statusSpinnerEl)
                    statusSpinnerEl.classList.add('hidden');
            }

            updateField(statusTextEl, status);
            if (hadError && statusTextEl.classList.value.indexOf('labkey-error') !== -1) {
                statusTextEl.classList.add('labkey-error');
            }
        }

        function updateRuns(runs) {
            for (let i = 0; i < runs.length; i++) {
                let run = runs[i];
                let runLinkEl = document.getElementById('run-' + run.rowId);
                if (!runLinkEl) {
                    let link = document.createElement('A');
                    link.id = 'run-' + run.rowId;
                    link.href = run.url;
                    link.innerText = run.name;
                    runsListEl.appendChild(link);
                    runsListEl.appendChild(document.createElement('BR'));

                    // show the <tr> if hidden
                    runsListEl.parentElement.classList.remove('hidden');
                }
            }
        }

        function updateFiles(files) {
            for (let i = 0; i < files.length; i++) {
                let file = files[i];

                // find the link based on file name
                let found = false;
                let links = filesListEl.querySelectorAll("a");
                for (let j = 0; j < links.length; j++) {
                    if (file.name === links[j].innerText) {
                        found = true;
                        break;
                    }
                }

                if (!found) {
                    let link = document.createElement('A');
                    link.href = file.viewUrl;
                    link.innerText = file.name;
                    filesListEl.appendChild(link);
                    filesListEl.appendChild(document.createElement('BR'));

                    // show the <tr> if hidden
                    filesListEl.parentElement.classList.remove('hidden');
                }
            }
        }


        function renderSplitJobStatusRow(status, alt) {
            let html = '<tr class="' + (alt ? 'labkey-alternate-row' : 'labkey-row') + '"' +
                    ' data-rowid="' + LABKEY.Utils.encodeHtml(status.rowId) + '">' +
                    '<td class="split-job-status">' +
                    '<a href="' + LABKEY.ActionURL.buildURL('pipeline-status', 'details.view', null, {'rowId': status.rowId}) + '">' +
                    LABKEY.Utils.encodeHtml(status.status) +
                    '</a>' +
                    '</td>' +
                    '<td class="split-job-description">' +
                    LABKEY.Utils.encodeHtml(status.description) +
                    '</td>' +
                    '</tr>';

            return createDomNode(html);
        }

        function findSplitJobStatusRow(tableBodyEl, rowId) {
            // dataset values are string, so we need to convert the rowId
            let rowIdStr = ""+rowId;
            let rows = tableBodyEl.querySelectorAll("tr");
            for (let i = 0; i < rows.length; i++) {
                let row = rows[i];
                if (row.dataset.rowid === rowIdStr) {
                    return row;
                }
            }

            return null;
        }

        function updateSplitJobStatusRow(status, rowEl) {
            if (!rowEl)
                return;

            let statusLinkEl = rowEl.querySelector('td.split-job-status > a');
            statusLinkEl.innerText = status.status;

            let descriptionEl = rowEl.querySelector('td.split-job-description');
            descriptionEl.innerText = status.description;
        }

        function updateStatusRow(tableBodyEl, status) {
            if (!status)
                return;

            let rowEl = findSplitJobStatusRow(tableBodyEl, status.rowId);
            if (!rowEl) {
                let alt = (tableBodyEl.childNodes.length % 2) === 1;
                rowEl = renderSplitJobStatusRow(status, alt);
                tableBodyEl.appendChild(rowEl);
            }
            else {
                updateSplitJobStatusRow(status, rowEl);
            }
        }

        function updateParentStatus(parentStatus) {
            if (!parentStatus)
                return;

            updateStatusRow(parentJobTableBodyEl, parentStatus);

            // show the <tr> if hidden
            document.getElementById('parent-job').parentElement.classList.remove('hidden');
        }

        function updateSplitStatus(splitStatus) {
            if (!splitStatus || splitStatus.length === 0)
                return;

            for (let i = 0; i < splitStatus.length; i++) {
                let status = splitStatus[i];
                updateStatusRow(splitJobsTableBodyEl, status);
            }

            // show the <tr> if hidden
            document.getElementById('split-jobs').parentElement.classList.remove('hidden');
        }

        // keep in sync with Java logTextClass function
        function logTextClass(record, showDetails) {
            let classes = '';

            switch (record.level) {
                case 'DEBUG':
                    classes = "text-muted hidden";
                    break;
                case 'WARN':
                    classes = "text-warning";
                    break;
                case 'ERROR':
                case 'FATAL':
                    classes = "text-danger";
                    break;
                case 'INFO':
                default:
                    let lower = record.lines.toLowerCase();
                    if (lower.indexOf('success') !== -1 || lower.indexOf('completed') !== -1) {
                        classes = "text-success"
                    }
                    break;
            }

            if (record.multiline) {
                classes += " multiline";
                if (record.stackTrace && !showDetails)
                    classes += " collapsed";
                else
                    classes += " expanded";
            }

            return classes;
        }

        function renderLog(records) {
            let nodes = [];
            for (let i = 0; i < records.length; i++) {
                let record = records[i];
                let css = logTextClass(record, isShowingDetails());

                let html = '<pre class="labkey-log-text ' + css + '"' +
                        'data-multiline="' + record.multiline + '"' +
                        'data-stacktrace="' + record.stackTrace + '"' +
                        'data-level="' + LABKEY.Utils.encodeHtml(record.level) + '">' +
                        '<div>' +
                        LABKEY.Utils.encodeHtml(record.lines) +
                        '</div>' +
                        '</pre>';

                nodes.push(createDomNode(html));
            }
            return nodes;
        }

        function updateLog(log) {
            if (log) {
                if (!logDataEl) {
                    // job is active, but the log file didn't exist during first page load
                    logContainerEl.innerHTML = '<div id="log-data"></div>';
                    logDataEl = document.getElementById('log-data');
                }

                const scrolledToBottom = logContainerEl.scrollHeight - logContainerEl.scrollTop === logContainerEl.clientHeight;

                if (log.success) {
                    // successfully read the status log file
                    if (log.records && log.records.length > 0) {
                        let chunks = renderLog(log.records);
                        chunks.forEach(function (chunk) { logDataEl.appendChild(chunk); });
                    }
                }
                else {
                    // error reading the status log file.
                    let message = log.message || 'Error reading log file';

                    // Add the error if the previous message was not the same error.
                    let lastChild = logDataEl.lastElementChild;
                    if (!lastChild || lastChild.innerText !== message) {
                        let errEl = document.createElement('PRE');
                        errEl.innerText = message;
                        errEl.classList.add('labkey-log-text');
                        errEl.classList.add('bg-warning');
                        errEl.classList.add('text-danger');
                        logDataEl.appendChild(errEl);
                    }
                    else {
                        console.warn("Skipping duplicate error: " + message);
                    }
                }

                const selection = window.getSelection();
                const hasSelection = selection && !selection.isCollapsed;

                // scroll if we are at the bottom of the scrollable pane and nothing is selected
                if (scrolledToBottom && !hasSelection) {
                    scrollLog(true);
                }
            }
        }

        function createDomNode(html) {
            let template = document.createElement('template');
            template.innerHTML = html;
            return template.content.firstChild;
        }

        function createDomNodes(html) {
            let template = document.createElement('template');
            template.innerHTML = html;
            return template.content.childNodes;
        }

        // Return true if the process is not complete or it is complete but the offset
        // hasn't changed after fetching a few times. This can happen if the job state
        // is COMPLETE or ERROR and we failed to read the last chunk of the log file.
        function shouldFetchUpdate() {
            return active || offsetUnchangedCount < MAX_UNCHANGED_COUNT;
        }

        function fetchStatus() {

            LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL('pipeline-status', 'statusDetails.api', null, {
                    rowId: <%=status.rowId%>,
                    offset: nextOffset,
                    count: fetchCount++
                }),
                method: 'GET',
                success: LABKEY.Utils.getCallbackWrapper(function (result, xhr) {
                    if (result.success) {
                        let status = result.data;
                        active = status.active;

                        if (status.log && status.log.nextOffset > nextOffset) {
                            nextOffset = status.log.nextOffset;
                            offsetUnchangedCount = 0;
                        }
                        else {
                            // we failed to read the log file or the log file hasn't changed since last fetch
                            offsetUnchangedCount++;
                        }

                        if (!active) {
                            // disable the cancel button
                            if (cancelBtnEl) {
                                cancelBtnEl.classList.add('labkey-disabled-button');
                            }

                            // if status is error or cancelled, show retry
                            if (retryBtnEl && (status.status.toLowerCase() === <%=q(TaskStatus.error.name())%> || status.status.toLowerCase() === <%=q(TaskStatus.cancelled.name())%>)) {
                                retryBtnEl.classList.remove('hidden');
                            }

                            // reveal the data button
                            if (status.dataUrl) {
                                showDataBtnEl.href = status.dataUrl;
                                showDataBtnEl.classList.remove('hidden');
                            }

                            // if complete and redirect=1, navigate to the imported run
                            if (status.status.toLowerCase() === <%=q(TaskStatus.complete.name())%> && LABKEY.ActionURL.getParameter('redirect') === '1') {
                                let redirect = status.dataUrl;
                                if (redirect) {
                                    setTimeout(function () {
                                        window.location = redirect;
                                    }, 300);
                                }
                            }
                        }

                        updateField(createdEl, status.created);
                        updateField(modifiedEl, status.modified);
                        updateField(emailEl, status.email);
                        updateField(infoEl, status.info ?? "");
                        updateField(descriptionEl, status.description);
                        updateField(queuePosition, status.queuePosition);
                        updateStatus(active, status.status, status.hadError);
                        updateRuns(status.runs);
                        updateFiles(status.files);
                        updateParentStatus(status.parentStatus);
                        updateSplitStatus(status.splitStatus);
                        updateLog(status.log);
                    }
                    else {
                        offsetUnchangedCount++;
                    }

                    // fetch again if the process is active or the offset hasn't
                    // changed for a while or there was an error fetching the log
                    if (shouldFetchUpdate()) {
                        setTimeout(fetchStatus, 500 + (500 * offsetUnchangedCount));
                    }
                }),
                failure: LABKEY.Utils.getCallbackWrapper(function (err) {
                    let msg = "Error getting pipeline status";
                    if (err && err.exception) {
                        msg = err.exception;
                    }

                    let errorListEl = document.getElementById('error-list');
                    let msgEl = document.createElement("DIV");
                    msgEl.classList.add('labkey-error');
                    msgEl.innerHTML = LABKEY.Utils.encodeHtml(msg);
                    errorListEl.appendChild(msgEl);
                }, false)
            });
        }

        if (shouldFetchUpdate()) {
            setTimeout(fetchStatus, 500);
        }
    })();
</script>
<% } %>
