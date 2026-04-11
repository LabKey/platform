<%
/*
 * Copyright (c) 2025 LabKey Corporation
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
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    int defaultConcurrency = org.labkey.api.data.DbScope.getLabKeyScope().getSqlDialect().isSqlServer() ? 20 : 8;
%>

<style type="text/css">
    .sc-dashboard { font-family: inherit; }
    .sc-toolbar { margin-bottom: 12px; }
    .sc-toolbar .labkey-button { margin-right: 6px; }
    .sc-columns { display: flex; gap: 16px; }
    .sc-file-list { flex: 1; min-width: 340px; }
    .sc-detail-pane { flex: 2; }
    .sc-file-table { width: 100%; border-collapse: collapse; }
    .sc-file-table th, .sc-file-table td { padding: 4px 8px; text-align: left; border-bottom: 1px solid #ddd; }
    .sc-file-table th { background: #f0f0f0; font-weight: bold; }
    .sc-file-table tr.sc-selected { background: #d0e8ff; }
    .sc-file-table tr:hover { background: #e8e8e8; cursor: pointer; }
    .sc-file-table tr.sc-selected:hover { background: #d0e8ff; }
    .sc-summary { padding: 8px; background: #fafafa; border: 1px solid #ddd; }
    .sc-summary h3 { margin: 0 0 8px 0; }
    .sc-summary table { margin: 4px 0; }
    .sc-summary td { padding: 2px 12px 2px 0; }
    .sc-summary td.sc-label { font-weight: bold; white-space: nowrap; }
    .sc-stat-ok { color: #2a7e2a; font-weight: bold; }
    .sc-stat-warn { color: #c07000; font-weight: bold; }
    .sc-stat-error { color: #c00; font-weight: bold; }
    .sc-compare-form { margin-top: 16px; padding: 10px; background: #f5f5f5; border: 1px solid #ddd; }
    .sc-compare-form h3 { margin: 0 0 8px 0; }
    .sc-compare-form label { display: inline-block; margin-right: 12px; }
    .sc-compare-form select, .sc-compare-form input[type=number], .sc-compare-form input[type=text] { margin: 2px 4px; }
    .sc-status { padding: 8px; margin: 8px 0; }
    .sc-status.sc-info { background: #e8f0fe; border: 1px solid #a0c0e8; }
    .sc-status.sc-error { background: #fde8e8; border: 1px solid #e0a0a0; }
    .sc-detail-section { margin: 8px 0; }
    .sc-detail-section summary { cursor: pointer; font-weight: bold; padding: 4px 0; color: #2563eb; text-decoration: underline; }
    .sc-detail-section summary:hover { color: #1a4ebd; }
    .sc-detail-section[open] summary { color: #1a4ebd; text-decoration: none; }
    .sc-detail-section pre { max-height: 300px; overflow: auto; background: #f8f8f8; padding: 6px; border: 1px solid #ddd; font-size: 12px; }
    .sc-tag { display: inline-block; font-size: 10px; padding: 1px 5px; border-radius: 3px; margin-left: 6px; vertical-align: middle; font-weight: bold; }
    .sc-tag-table { background: #e0eaff; color: #1a4ebd; border: 1px solid #a0c0e8; }
    .sc-tag-query { background: #fff3e0; color: #c07000; border: 1px solid #e0c090; }
</style>

<div class="sc-dashboard">
    <div id="sc-status-bar"></div>

    <div class="sc-compare-form">
        <h3>Options</h3>
        <div>
            <label><input type="checkbox" id="sc-opt-skipChecksums"> Skip Checksums</label>
            <label>Concurrency: <input type="number" id="sc-opt-concurrency" value="<%= defaultConcurrency %>" min="1" max="20" style="width:50px;"></label>
            <label>Query Timeout (sec): <input type="number" id="sc-opt-queryTimeout" value="300" min="1" max="600" style="width:60px;"></label>
            <label>Checksum Row Limit: <input type="number" id="sc-opt-checksumRowLimit" value="10000" min="1" max="100000" style="width:70px;"></label>
        </div>
        <div style="margin-top: 6px;">
            <label>Schema: <input type="text" id="sc-opt-schema" placeholder="e.g. core, lists" style="width:200px;"></label>
            <label>Query: <input type="text" id="sc-opt-query" placeholder="e.g. users, myList" style="width:200px;"></label>
            <span style="color:#888; font-size:12px;">(optional &ndash; leave blank for all)</span>
        </div>
        <div style="margin-top: 8px;">
            <a class="labkey-button" id="sc-btn-capture">Capture New Baseline</a>
            <label style="margin-left: 16px;">Baseline: <select id="sc-baseline-select"><option value="">-- select baseline --</option></select></label>
            <a class="labkey-button" id="sc-btn-compare">Run Compare</a>
            <a class="labkey-button" id="sc-btn-refresh">Refresh</a>
        </div>
    </div>

    <div class="sc-columns">
        <div class="sc-file-list">
            <h3>Saved Files</h3>
            <table class="sc-file-table">
                <thead><tr><th>Name</th><th>Size</th><th>Modified</th></tr></thead>
                <tbody id="sc-file-tbody"><tr><td colspan="3">Loading...</td></tr></tbody>
            </table>
        </div>
        <div class="sc-detail-pane">
            <div id="sc-summary-pane">
                <p>Select a file to view its summary.</p>
            </div>
        </div>
    </div>
</div>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">
(function() {
    var selectedFile = null;
    var fileList = [];

    function showStatus(msg, isError) {
        var el = document.getElementById('sc-status-bar');
        el.className = 'sc-status ' + (isError ? 'sc-error' : 'sc-info');
        el.textContent = msg;
        el.style.display = 'block';
    }

    function clearStatus() {
        var el = document.getElementById('sc-status-bar');
        el.style.display = 'none';
        el.textContent = '';
    }

    function formatSize(bytes) {
        if (bytes < 1024) return bytes + ' B';
        if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
        return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
    }

    function isTimeoutError(errorStr) {
        if (!errorStr) return false;
        return errorStr.indexOf('timed out') !== -1 ||
               errorStr.indexOf('canceling statement due to user request') !== -1;
    }

    function queryTypeTag(isUserDefined) {
        if (isUserDefined)
            return '<span class="sc-tag sc-tag-query">Custom Query</span>';
        return '<span class="sc-tag sc-tag-table">Table</span>';
    }

    function countByType(entries) {
        var tables = 0, customs = 0;
        for (var i = 0; i < entries.length; i++) {
            if (entries[i].isUserDefined) customs++;
            else tables++;
        }
        return { tables: tables, customs: customs };
    }

    function typeBreakdown(total, tables, customs) {
        if (tables === 0 && customs === 0) return '' + total;
        return total + ' <span style="font-size:12px;color:#666;">(' + tables + ' table' + (tables !== 1 ? 's' : '') + ', ' + customs + ' custom quer' + (customs !== 1 ? 'ies' : 'y') + ')</span>';
    }

    function getOptions() {
        var opts = {
            skipChecksums: document.getElementById('sc-opt-skipChecksums').checked,
            concurrency: parseInt(document.getElementById('sc-opt-concurrency').value, 10) || <%= defaultConcurrency %>,
            queryTimeout: parseInt(document.getElementById('sc-opt-queryTimeout').value, 10) || 120,
            checksumRowLimit: parseInt(document.getElementById('sc-opt-checksumRowLimit').value, 10) || 100
        };
        var schemaVal = document.getElementById('sc-opt-schema').value.trim();
        var queryVal = document.getElementById('sc-opt-query').value.trim();
        if (schemaVal) opts.schema = schemaVal;
        if (queryVal) opts.query = queryVal;
        return opts;
    }

    function loadFileList() {
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('query', 'schemaCompareResults.api'),
            method: 'GET',
            success: LABKEY.Utils.getCallbackWrapper(function(data) {
                fileList = (data.files || []).filter(function(f) {
                    return f.name.indexOf('schema-compare') === 0 || f.name.indexOf('schema-baseline') === 0;
                }).sort(function(a, b) {
                    return a.modified < b.modified ? 1 : a.modified > b.modified ? -1 : 0;
                });
                renderFileTable();
                populateBaselineDropdown();
                clearStatus();
            }),
            failure: LABKEY.Utils.getCallbackWrapper(function(data) {
                showStatus('Failed to load file list: ' + (data.exception || 'Unknown error'), true);
            })
        });
    }

    function renderFileTable() {
        var tbody = document.getElementById('sc-file-tbody');
        if (fileList.length === 0) {
            tbody.innerHTML = '<tr><td colspan="3">No saved files found.</td></tr>';
            return;
        }
        var html = '';
        for (var i = 0; i < fileList.length; i++) {
            var f = fileList[i];
            var cls = (selectedFile === f.name) ? ' class="sc-selected"' : '';
            html += '<tr data-filename="' + LABKEY.Utils.encodeHtml(f.name) + '"' + cls + '>' +
                '<td>' + LABKEY.Utils.encodeHtml(f.name) + '</td>' +
                '<td>' + formatSize(f.size) + '</td>' +
                '<td>' + LABKEY.Utils.encodeHtml(f.modified) + '</td>' +
                '</tr>';
        }
        tbody.innerHTML = html;

        var rows = tbody.querySelectorAll('tr[data-filename]');
        for (var j = 0; j < rows.length; j++) {
            rows[j].addEventListener('click', (function(row) {
                return function() { selectFile(row.getAttribute('data-filename')); };
            })(rows[j]));
        }
    }

    function populateBaselineDropdown() {
        var sel = document.getElementById('sc-baseline-select');
        // Keep the first placeholder option
        while (sel.options.length > 1) sel.remove(1);
        for (var i = 0; i < fileList.length; i++) {
            var name = fileList[i].name;
            if (name.indexOf('schema-baseline') === 0) {
                var opt = document.createElement('option');
                opt.value = name;
                opt.textContent = name;
                sel.appendChild(opt);
            }
        }
    }

    function applyOptionsFromBaseline(fileName) {
        if (!fileName) return;
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('query', 'schemaCompareResults.api'),
            method: 'GET',
            params: { fileName: fileName },
            success: LABKEY.Utils.getCallbackWrapper(function(data) {
                var meta = data.metadata || {};
                if (meta.skipChecksums != null)
                    document.getElementById('sc-opt-skipChecksums').checked = !!meta.skipChecksums;
                if (meta.queryTimeout != null)
                    document.getElementById('sc-opt-queryTimeout').value = meta.queryTimeout;
                if (meta.checksumRowLimit != null)
                    document.getElementById('sc-opt-checksumRowLimit').value = meta.checksumRowLimit;
                document.getElementById('sc-opt-schema').value = meta.schema || '';
                document.getElementById('sc-opt-query').value = meta.query || '';
            }),
            failure: LABKEY.Utils.getCallbackWrapper(function() {})
        });
    }

    function selectFile(fileName) {
        selectedFile = fileName;
        renderFileTable();
        showStatus('Loading ' + fileName + '...', false);
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('query', 'schemaCompareResults.api'),
            method: 'GET',
            params: { fileName: fileName },
            success: LABKEY.Utils.getCallbackWrapper(function(data) {
                clearStatus();
                renderFileSummary(fileName, data);
            }),
            failure: LABKEY.Utils.getCallbackWrapper(function(data) {
                showStatus('Failed to load file: ' + (data.exception || 'Unknown error'), true);
            })
        });
    }

    function renderFileSummary(fileName, data) {
        var pane = document.getElementById('sc-summary-pane');
        var html = '<div class="sc-summary"><h3>' + LABKEY.Utils.encodeHtml(fileName) + '</h3>';

        if (fileName.indexOf('schema-baseline') === 0) {
            html += renderBaselineSummary(data);
        } else if (fileName.indexOf('schema-compare') === 0) {
            html += renderCompareSummary(data);
        } else {
            html += '<p>Unknown file type.</p>';
        }

        html += '</div>';
        pane.innerHTML = html;
    }

    function renderBaselineSummary(data) {
        var meta = data.metadata || {};
        var schemas = data.schemas || {};
        var errors = data.errors || [];
        var schemaCount = Object.keys(schemas).length;
        var tableCount = 0, customQueryCount = 0;
        var tablesWithRows = 0, customQueriesWithRows = 0;
        var tablesTimedOut = 0, customQueriesTimedOut = 0;
        var skippedEntries = [];
        var checksumSkippedWithDataEntries = [];
        for (var s in schemas) {
            if (schemas.hasOwnProperty(s)) {
                var qs = schemas[s].queries || {};
                var qNames = Object.keys(qs);
                for (var qi = 0; qi < qNames.length; qi++) {
                    var q = qs[qNames[qi]];
                    var isCustom = !!q.isUserDefined;
                    if (isCustom) customQueryCount++; else tableCount++;
                    if (q.rowCount !== null && q.rowCount !== undefined && q.rowCount > 0) {
                        if (isCustom) customQueriesWithRows++; else tablesWithRows++;
                    }
                    if (isTimeoutError(q.error)) {
                        if (isCustom) customQueriesTimedOut++; else tablesTimedOut++;
                    }
                    if (q.skippedReason) {
                        skippedEntries.push({ schema: s, query: qNames[qi], isUserDefined: isCustom, reason: q.skippedReason });
                    }
                    if (q.checksumSkippedReason && q.rowCount !== null && q.rowCount !== undefined && q.rowCount > 0) {
                        checksumSkippedWithDataEntries.push({ schema: s, query: qNames[qi], isUserDefined: isCustom, reason: q.checksumSkippedReason, rowCount: q.rowCount });
                    }
                }
            }
        }
        var queryCount = tableCount + customQueryCount;
        var queriesWithRows = tablesWithRows + customQueriesWithRows;
        var queriesTimedOut = tablesTimedOut + customQueriesTimedOut;
        var skippedTypes = countByType(skippedEntries);
        var checksumSkippedTypes = countByType(checksumSkippedWithDataEntries);

        var html = '<table>';
        html += '<tr><td class="sc-label">Captured At:</td><td>' + LABKEY.Utils.encodeHtml(meta.capturedAt || 'N/A') + '</td></tr>';
        html += '<tr><td class="sc-label">Base URL:</td><td>' + LABKEY.Utils.encodeHtml(meta.baseUrl || 'N/A') + '</td></tr>';
        html += '<tr><td class="sc-label">Container Path:</td><td>' + LABKEY.Utils.encodeHtml(meta.containerPath || 'N/A') + '</td></tr>';
        html += '<tr><td class="sc-label">Database:</td><td>' + LABKEY.Utils.encodeHtml(meta.database || 'N/A') + '</td></tr>';
        html += '<tr><td class="sc-label">Skip Checksums:</td><td>' + (meta.skipChecksums != null ? meta.skipChecksums : 'N/A') + '</td></tr>';
        html += '<tr><td class="sc-label">Concurrency:</td><td>' + (meta.concurrency != null ? meta.concurrency : 'N/A') + '</td></tr>';
        html += '<tr><td class="sc-label">Query Timeout (sec):</td><td>' + (meta.queryTimeout != null ? meta.queryTimeout : 'N/A') + '</td></tr>';
        html += '<tr><td class="sc-label">Checksum Row Limit:</td><td>' + (meta.checksumRowLimit != null ? meta.checksumRowLimit : 'N/A') + '</td></tr>';
        if (meta.schema)
            html += '<tr><td class="sc-label">Schema Filter:</td><td>' + LABKEY.Utils.encodeHtml(meta.schema) + '</td></tr>';
        if (meta.query)
            html += '<tr><td class="sc-label">Query Filter:</td><td>' + LABKEY.Utils.encodeHtml(meta.query) + '</td></tr>';
        html += '<tr><td class="sc-label">Schemas:</td><td>' + schemaCount + '</td></tr>';
        html += '<tr><td class="sc-label">Queries:</td><td>' + typeBreakdown(queryCount, tableCount, customQueryCount) + '</td></tr>';
        html += '<tr><td class="sc-label">Queries With Data:</td><td>' + typeBreakdown(queriesWithRows, tablesWithRows, customQueriesWithRows) + '</td></tr>';
        var toCls = queriesTimedOut > 0 ? ' class="sc-stat-warn"' : '';
        html += '<tr><td class="sc-label">Queries Timed Out:</td><td' + toCls + '>' + typeBreakdown(queriesTimedOut, tablesTimedOut, customQueriesTimedOut) + '</td></tr>';
        var skCls = skippedEntries.length > 0 ? ' class="sc-stat-warn"' : '';
        html += '<tr><td class="sc-label">Queries Skipped:</td><td' + skCls + '>' + typeBreakdown(skippedEntries.length, skippedTypes.tables, skippedTypes.customs) + '</td></tr>';
        var csCls = checksumSkippedWithDataEntries.length > 0 ? ' class="sc-stat-warn"' : '';
        html += '<tr><td class="sc-label">Checksums Skipped (with data):</td><td' + csCls + '>' + typeBreakdown(checksumSkippedWithDataEntries.length, checksumSkippedTypes.tables, checksumSkippedTypes.customs) + '</td></tr>';
        var errCls = errors.length > 0 ? ' class="sc-stat-error"' : '';
        html += '<tr><td class="sc-label">Errors:</td><td' + errCls + '>' + errors.length + '</td></tr>';
        html += '</table>';

        if (skippedEntries.length > 0) {
            html += '<details class="sc-detail-section"><summary>Skipped Queries (' + skippedEntries.length + ')</summary>';
            html += renderSkippedQueryDetail(skippedEntries);
            html += '</details>';
        }
        if (checksumSkippedWithDataEntries.length > 0) {
            html += '<details class="sc-detail-section"><summary>Checksums Skipped — with data (' + checksumSkippedWithDataEntries.length + ')</summary>';
            html += renderChecksumSkippedDetail(checksumSkippedWithDataEntries);
            html += '</details>';
        }

        if (errors.length > 0) {
            html += '<details class="sc-detail-section"><summary>Errors (' + errors.length + ')</summary>';
            html += '<pre>' + LABKEY.Utils.encodeHtml(JSON.stringify(errors, null, 2)) + '</pre></details>';
        }

        return html;
    }

    function renderRowCountMismatchDetail(entries) {
        var html = '<div style="padding: 6px;">';
        for (var i = 0; i < entries.length; i++) {
            var entry = entries[i];
            var label = LABKEY.Utils.encodeHtml((entry.schema || '') + '.' + (entry.query || ''));
            var baseline = entry.baselineRowCount;
            var live = entry.liveRowCount;
            var diff = (baseline != null && live != null) ? live - baseline : null;
            var diffStr = diff != null ? (diff > 0 ? '+' + diff : '' + diff) : '';
            html += '<div style="margin-bottom: 6px;">';
            html += '<b>' + label + '</b>' + queryTypeTag(entry.isUserDefined) + ': ';
            html += 'baseline <b>' + baseline + '</b> &rarr; live <b>' + live + '</b>';
            if (diffStr) {
                html += ' (<b>' + LABKEY.Utils.encodeHtml(diffStr) + '</b>)';
            }
            html += '</div>';
        }
        html += '</div>';
        return html;
    }

    function renderChecksumMismatchDetail(entries) {
        var html = '<div style="padding: 6px;">';
        for (var i = 0; i < entries.length; i++) {
            var entry = entries[i];
            var label = LABKEY.Utils.encodeHtml((entry.schema || '') + '.' + (entry.query || ''));
            html += '<div style="margin-bottom: 10px; border-bottom: 1px solid #ddd; padding-bottom: 8px;">';
            html += '<div style="font-weight: bold; margin-bottom: 4px;">' + label + queryTypeTag(entry.isUserDefined) + '</div>';
            if (entry.baselineRowCount != null && entry.liveRowCount != null) {
                var diff = entry.liveRowCount - entry.baselineRowCount;
                var diffStr = diff > 0 ? '+' + diff : '' + diff;
                html += '<div>Row count: baseline <b>' + entry.baselineRowCount + '</b> &rarr; live <b>' + entry.liveRowCount + '</b>';
                if (diff !== 0) html += ' (<b>' + LABKEY.Utils.encodeHtml(diffStr) + '</b>)';
                html += '</div>';
            }
            if (entry.baselineChecksum || entry.liveChecksum) {
                var bcs = entry.baselineChecksum ? entry.baselineChecksum.substring(0, 20) + '...' : 'N/A';
                var lcs = entry.liveChecksum ? entry.liveChecksum.substring(0, 20) + '...' : 'N/A';
                html += '<div>Checksum: <span style="font-family:monospace;font-size:11px;">' + LABKEY.Utils.encodeHtml(bcs) + '</span> &rarr; <span style="font-family:monospace;font-size:11px;">' + LABKEY.Utils.encodeHtml(lcs) + '</span></div>';
            }
            if (entry.checksumColumns && entry.checksumColumns.length > 0) {
                html += '<div style="color:#666;font-size:12px;">Columns: ' + LABKEY.Utils.encodeHtml(entry.checksumColumns.join(', ')) + '</div>';
            }
            if (entry.checksumSkippedReason) {
                html += '<div style="color:#c07000;font-size:12px;">Skipped: ' + LABKEY.Utils.encodeHtml(entry.checksumSkippedReason) + '</div>';
            }
            html += '</div>';
        }
        html += '</div>';
        return html;
    }

    function renderMetadataMismatchDetail(entries) {
        var html = '<div style="padding: 6px;">';
        for (var i = 0; i < entries.length; i++) {
            var entry = entries[i];
            var label = LABKEY.Utils.encodeHtml((entry.schema || '') + '.' + (entry.query || ''));
            html += '<div style="margin-bottom: 10px; border-bottom: 1px solid #ddd; padding-bottom: 8px;">';
            html += '<div style="font-weight: bold; margin-bottom: 4px;">' + label + queryTypeTag(entry.isUserDefined) + '</div>';
            var diffs = entry.metadataDiffs;
            if (diffs) {
                if (diffs.missingColumns && diffs.missingColumns.length > 0) {
                    html += '<div>Missing columns: <b>' + LABKEY.Utils.encodeHtml(diffs.missingColumns.join(', ')) + '</b></div>';
                }
                if (diffs.newColumns && diffs.newColumns.length > 0) {
                    html += '<div>New columns: <b>' + LABKEY.Utils.encodeHtml(diffs.newColumns.join(', ')) + '</b></div>';
                }
                if (diffs.typeChanges && diffs.typeChanges.length > 0) {
                    for (var t = 0; t < diffs.typeChanges.length; t++) {
                        var tc = diffs.typeChanges[t];
                        html += '<div>Type change: <b>' + LABKEY.Utils.encodeHtml(tc.name) + '</b>: ' +
                            LABKEY.Utils.encodeHtml(tc.baseline) + ' &rarr; <b>' + LABKEY.Utils.encodeHtml(tc.live) + '</b></div>';
                    }
                }
                if (diffs.keyFieldChanges && diffs.keyFieldChanges.length > 0) {
                    for (var k = 0; k < diffs.keyFieldChanges.length; k++) {
                        var kc = diffs.keyFieldChanges[k];
                        html += '<div>Key field change: <b>' + LABKEY.Utils.encodeHtml(kc.name) + '</b>: ' +
                            String(kc.baseline) + ' &rarr; <b>' + String(kc.live) + '</b></div>';
                    }
                }
            }
            html += '</div>';
        }
        html += '</div>';
        return html;
    }

    function renderErrorDetail(entries) {
        var html = '<div style="padding: 6px;">';
        for (var i = 0; i < entries.length; i++) {
            var entry = entries[i];
            var label = LABKEY.Utils.encodeHtml((entry.schema || '') + '.' + (entry.query || ''));
            html += '<div style="margin-bottom: 6px;">';
            html += '<b>' + label + '</b>' + queryTypeTag(entry.isUserDefined) + ': ';
            if (entry.error) {
                html += LABKEY.Utils.encodeHtml(entry.error);
            }
            if (entry.baselineError) {
                html += ' <span style="color:#888;">(baseline: ' + LABKEY.Utils.encodeHtml(entry.baselineError) + ')</span>';
            }
            html += '</div>';
        }
        html += '</div>';
        return html;
    }

    function renderSkippedQueryDetail(entries) {
        var html = '<div style="padding: 6px;">';
        for (var i = 0; i < entries.length; i++) {
            var entry = entries[i];
            var label = LABKEY.Utils.encodeHtml((entry.schema || '') + '.' + (entry.query || ''));
            html += '<div style="margin-bottom: 6px;">';
            html += '<b>' + label + '</b>' + queryTypeTag(entry.isUserDefined) + ': ';
            html += LABKEY.Utils.encodeHtml(entry.reason || entry.skippedReason || 'skipped');
            html += '</div>';
        }
        html += '</div>';
        return html;
    }

    function renderChecksumSkippedDetail(entries) {
        var html = '<div style="padding: 6px;">';
        for (var i = 0; i < entries.length; i++) {
            var entry = entries[i];
            var label = LABKEY.Utils.encodeHtml((entry.schema || '') + '.' + (entry.query || ''));
            html += '<div style="margin-bottom: 6px;">';
            html += '<b>' + label + '</b>' + queryTypeTag(entry.isUserDefined) + ': ';
            html += LABKEY.Utils.encodeHtml(entry.reason || entry.checksumSkippedReason || 'skipped');
            if (entry.rowCount != null) {
                html += ' (rows: <b>' + entry.rowCount + '</b>)';
            }
            html += '</div>';
        }
        html += '</div>';
        return html;
    }

    function renderCompareSummary(data) {
        var summary = data.summary || {};
        var meta = data.metadata || {};

        // Compute type breakdowns from detail entries
        var details = data.details || {};
        var detailEntries = [];
        for (var key in details) {
            if (details.hasOwnProperty(key))
                detailEntries.push(details[key]);
        }
        var totalTypes = countByType(detailEntries);
        var matchedEntries = detailEntries.filter(function(e) { return e.status === 'matched'; });
        var matchedTypes = countByType(matchedEntries);

        // Compute breakdowns for specific detail arrays
        var rcTypes = countByType(data.rowCountMismatches || []);
        var csTypes = countByType(data.checksumMismatches || []);
        var metaTypes = countByType(data.metadataMismatches || []);
        var skTypes = countByType(data.skippedQueries || []);
        var missingTypes = countByType((data.queryErrors || []).filter(function(e) { return e.status === 'missing_from_live'; }));
        var newTypes = countByType(data.newQueries || []);
        var errTypes = countByType((data.queryErrors || []).filter(function(e) { return e.status !== 'missing_from_live'; }));

        var html = '<table>';
        html += '<tr><td class="sc-label">Skip Checksums:</td><td>' + (meta.skipChecksums != null ? meta.skipChecksums : 'N/A') + '</td></tr>';
        html += '<tr><td class="sc-label">Concurrency:</td><td>' + (meta.concurrency != null ? meta.concurrency : 'N/A') + '</td></tr>';
        html += '<tr><td class="sc-label">Query Timeout (sec):</td><td>' + (meta.queryTimeout != null ? meta.queryTimeout : 'N/A') + '</td></tr>';
        html += '<tr><td class="sc-label">Checksum Row Limit:</td><td>' + (meta.checksumRowLimit != null ? meta.checksumRowLimit : 'N/A') + '</td></tr>';
        if (meta.schema)
            html += '<tr><td class="sc-label">Schema Filter:</td><td>' + LABKEY.Utils.encodeHtml(meta.schema) + '</td></tr>';
        if (meta.query)
            html += '<tr><td class="sc-label">Query Filter:</td><td>' + LABKEY.Utils.encodeHtml(meta.query) + '</td></tr>';

        var fields = [
            { key: 'schemasCompared', label: 'Schemas Compared' },
            { key: 'queriesCompared', label: 'Queries Compared', types: totalTypes },
            { key: 'queriesMatched', label: 'Queries Matched', cls: 'sc-stat-ok', types: matchedTypes },
            { key: 'rowCountMismatches', label: 'Row Count Mismatches', cls: 'sc-stat-warn', types: rcTypes },
            { key: 'checksumMismatches', label: 'Checksum Mismatches', cls: 'sc-stat-warn', types: csTypes },
            { key: 'metadataMismatches', label: 'Metadata Mismatches', cls: 'sc-stat-warn', types: metaTypes },
            { key: 'queriesSkipped', label: 'Queries Skipped', cls: 'sc-stat-warn', types: skTypes },
            { key: 'missingInLive', label: 'Missing in Live', cls: 'sc-stat-warn', types: missingTypes },
            { key: 'missingInBaseline', label: 'Missing in Baseline', cls: 'sc-stat-warn', types: newTypes },
            { key: 'errors', label: 'New Errors', cls: 'sc-stat-error', types: errTypes },
            { key: 'errorsChanged', label: 'Changed Errors', cls: 'sc-stat-warn' },
            { key: 'errorsResolved', label: 'Resolved Errors', cls: 'sc-stat-ok' },
            { key: 'errorsConsistent', label: 'Consistent Errors' }
        ];

        for (var i = 0; i < fields.length; i++) {
            var f = fields[i];
            var val = summary[f.key];
            if (val === undefined) continue;
            var tdCls = (f.cls && val > 0) ? ' class="' + f.cls + '"' : '';
            var valStr = '' + val;
            if (f.types && val > 0 && (f.types.tables > 0 || f.types.customs > 0))
                valStr = typeBreakdown(val, f.types.tables, f.types.customs);
            html += '<tr><td class="sc-label">' + f.label + ':</td><td' + tdCls + '>' + valStr + '</td></tr>';
        }
        html += '</table>';

        // Expandable detail sections
        var detailSections = ['mismatches', 'missingQueries', 'errorDetails', 'queryErrors', 'skippedQueries', 'resolvedErrors', 'consistentErrors', 'rowCountMismatches', 'checksumMismatches', 'metadataMismatches'];
        var detailLabels = { mismatches: 'Mismatches', missingQueries: 'Missing Queries', errorDetails: 'Error Details', queryErrors: 'Query Errors', skippedQueries: 'Skipped Queries', resolvedErrors: 'Resolved Errors', consistentErrors: 'Consistent Errors', rowCountMismatches: 'Row Count Mismatches', checksumMismatches: 'Checksum Mismatches', metadataMismatches: 'Metadata Mismatches' };
        for (var j = 0; j < detailSections.length; j++) {
            var section = detailSections[j];
            var sectionData = data[section];
            if (sectionData && ((Array.isArray(sectionData) && sectionData.length > 0) || (!Array.isArray(sectionData) && Object.keys(sectionData).length > 0))) {
                html += '<details class="sc-detail-section"><summary>' + detailLabels[section] + '</summary>';
                if (section === 'metadataMismatches') {
                    html += renderMetadataMismatchDetail(sectionData);
                } else if (section === 'rowCountMismatches') {
                    html += renderRowCountMismatchDetail(sectionData);
                } else if (section === 'checksumMismatches') {
                    html += renderChecksumMismatchDetail(sectionData);
                } else if (section === 'skippedQueries') {
                    html += renderSkippedQueryDetail(sectionData);
                } else if (section === 'queryErrors' || section === 'resolvedErrors' || section === 'consistentErrors') {
                    html += renderErrorDetail(sectionData);
                } else {
                    html += '<pre>' + LABKEY.Utils.encodeHtml(JSON.stringify(sectionData, null, 2)) + '</pre>';
                }
                html += '</details>';
            }
        }

        return html;
    }

    function captureBaseline() {
        var opts = getOptions();
        if (opts.query && !opts.schema) {
            showStatus('Query filter requires a Schema filter to be specified.', true);
            return;
        }
        showStatus('Capturing baseline... this may take a while. See LabKey log for progress updates.', false);
        var btn = document.getElementById('sc-btn-capture');
        btn.className = 'labkey-button labkey-disabled-button';

        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('query', 'schemaCompareCapture.api'),
            method: 'GET',
            params: getOptions(),
            success: LABKEY.Utils.getCallbackWrapper(function() {
                showStatus('Baseline captured successfully.', false);
                btn.className = 'labkey-button';
                loadFileList();
            }),
            failure: LABKEY.Utils.getCallbackWrapper(function(data) {
                showStatus('Failed to capture baseline: ' + (data.exception || 'Unknown error'), true);
                btn.className = 'labkey-button';
            })
        });
    }

    function runCompare() {
        var baselineFileName = document.getElementById('sc-baseline-select').value;
        if (!baselineFileName) {
            showStatus('Please select a baseline file.', true);
            return;
        }

        var jsonData = getOptions();
        if (jsonData.query && !jsonData.schema) {
            showStatus('Query filter requires a Schema filter to be specified.', true);
            return;
        }
        jsonData.baselineFileName = baselineFileName;

        showStatus('Running comparison against ' + baselineFileName + '... this may take a while. See LabKey log for progress updates.', false);
        var btn = document.getElementById('sc-btn-compare');
        btn.className = 'labkey-button labkey-disabled-button';

        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('query', 'schemaCompare.api'),
            method: 'POST',
            jsonData: jsonData,
            success: LABKEY.Utils.getCallbackWrapper(function(data) {
                showStatus('Comparison complete.', false);
                btn.className = 'labkey-button';
                loadFileList();
                renderFileSummary('comparison-result', data);
            }),
            failure: LABKEY.Utils.getCallbackWrapper(function(data) {
                showStatus('Comparison failed: ' + (data.exception || 'Unknown error'), true);
                btn.className = 'labkey-button';
            })
        });
    }

    // Attach event listeners
    document.getElementById('sc-btn-capture').addEventListener('click', function(e) { e.preventDefault(); if (this.classList.contains('labkey-disabled-button')) return; captureBaseline(); });
    document.getElementById('sc-btn-refresh').addEventListener('click', function(e) { e.preventDefault(); loadFileList(); });
    document.getElementById('sc-btn-compare').addEventListener('click', function(e) { e.preventDefault(); runCompare(); });
    document.getElementById('sc-baseline-select').addEventListener('change', function() {
        var captureBtn = document.getElementById('sc-btn-capture');
        if (this.value) {
            captureBtn.classList.add('labkey-disabled-button');
        } else {
            captureBtn.classList.remove('labkey-disabled-button');
        }
        applyOptionsFromBaseline(this.value);
    });

    // Initial load
    loadFileList();
})();
</script>
