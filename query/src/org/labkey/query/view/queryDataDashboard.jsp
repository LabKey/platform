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

<style type="text/css">
    .qd-dashboard { font-family: inherit; }
    .qd-section { margin-bottom: 16px; padding: 10px; background: #f5f5f5; border: 1px solid #ddd; }
    .qd-section h3 { margin: 0 0 8px 0; }
    .qd-section label { display: inline-block; margin-right: 12px; }
    .qd-section input[type=text], .qd-section input[type=number], .qd-section input[type=date], .qd-section input[type=datetime-local] { margin: 2px 4px; }
    .qd-section select { margin: 2px 4px; }
    .qd-status { padding: 8px; margin: 8px 0; }
    .qd-status.qd-info { background: #e8f0fe; border: 1px solid #a0c0e8; display: block; }
    .qd-status.qd-error { background: #fde8e8; border: 1px solid #e0a0a0; display: block; }
    .qd-status.qd-success { background: #e8fee8; border: 1px solid #a0e8a0; display: block; }
    .qd-results { margin-top: 16px; }
    .qd-summary-table { border-collapse: collapse; margin: 8px 0; }
    .qd-summary-table td { padding: 3px 12px 3px 0; }
    .qd-summary-table td.qd-label { font-weight: bold; white-space: nowrap; }
    .qd-stat-ok { color: #2a7e2a; font-weight: bold; }
    .qd-stat-warn { color: #c07000; font-weight: bold; }
    .qd-stat-error { color: #c00; font-weight: bold; }
    .qd-detail-section { margin: 8px 0; }
    .qd-detail-section summary { cursor: pointer; font-weight: bold; padding: 4px 0; color: #2563eb; text-decoration: underline; }
    .qd-detail-section summary:hover { color: #1a4ebd; }
    .qd-detail-section[open] summary { color: #1a4ebd; text-decoration: none; }
    .qd-diff-table { width: 100%; border-collapse: collapse; margin: 4px 0; font-size: 12px; }
    .qd-diff-table th { background: #f0f0f0; padding: 4px 8px; text-align: left; border: 1px solid #ddd; }
    .qd-diff-table td { padding: 4px 8px; border: 1px solid #ddd; max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .qd-diff-table tr:nth-child(even) { background: #fafafa; }
    .qd-change-baseline { background: #fde8e8; }
    .qd-change-live { background: #e8fee8; }
    .qd-readonly-field { background: #e8e8e8; }
    .qd-db-arrow { font-weight: bold; color: #2563eb; margin: 0 4px; }
    .qd-param-fields { display: flex; flex-wrap: wrap; gap: 6px 12px; }
    .qd-param-field { display: inline-flex; align-items: center; }
    .qd-param-field span { white-space: nowrap; }
    .qd-param-help { color: #888; font-size: 12px; margin-top: 4px; }
</style>

<div class="qd-dashboard">
    <div id="qd-status-bar" style="display:none;"></div>

    <div class="qd-section">
        <h3>Capture Baseline</h3>
        <div>
            <label>Schema*: <select id="qd-capture-schema" style="width:200px;"><option value="">-- loading schemas --</option></select></label>
            <label>Query/Table*: <select id="qd-capture-query" style="width:200px;" disabled><option value="">-- select a schema first --</option></select></label>
        </div>
        <div style="margin-top: 6px;">
            <label>Row Limit: <input type="number" id="qd-capture-rowLimit" value="0" min="0" style="width:80px;"> <span style="color:#888;font-size:12px;">(0 = all rows)</span></label>
            <label>Timeout (sec): <input type="number" id="qd-capture-timeout" value="120" min="1" max="600" style="width:60px;"></label>
        </div>
        <div style="margin-top: 6px;">
            <div id="qd-capture-params-section" style="display:none;">
                <div style="font-weight:bold; margin-bottom: 4px;">Query Parameters</div>
                <div id="qd-capture-params-container" class="qd-param-fields"></div>
                <div id="qd-capture-params-help" class="qd-param-help"></div>
            </div>
        </div>
        <div style="margin-top: 8px;">
            <a class="labkey-button" id="qd-btn-capture">Capture Baseline</a>
        </div>
    </div>

    <div class="qd-section">
        <h3>Compare Against Baseline</h3>
        <div>
            <label>Baseline: <select id="qd-baseline-select" style="width:500px;"><option value="">-- select a baseline --</option></select></label>
            <a class="labkey-button" id="qd-btn-refresh" style="margin-left:8px;">Refresh</a>
        </div>
        <div id="qd-baseline-info" style="margin-top: 8px; display: none;">
            <label>Schema: <input type="text" id="qd-diff-schema" readonly class="qd-readonly-field" style="width:200px;"></label>
            <label>Query: <input type="text" id="qd-diff-query" readonly class="qd-readonly-field" style="width:200px;"></label>
            <span id="qd-diff-db-info"></span>
            <span id="qd-diff-param-info" style="display:none; margin-left:12px;"></span>
        </div>
        <div style="margin-top: 6px;">
            <label>Key Columns: <input type="text" id="qd-diff-pkColumns" placeholder="e.g. col1, col2" style="width:300px;"></label>
            <span style="color:#888;font-size:12px;">(optional, overrides auto-detected PK)</span>
        </div>
        <div style="margin-top: 6px;">
            <label>Max Diffs: <input type="number" id="qd-diff-maxDiffs" value="500" min="1" max="10000" style="width:70px;"></label>
            <label>Timeout (sec): <input type="number" id="qd-diff-timeout" value="120" min="1" max="600" style="width:60px;"></label>
        </div>
        <div style="margin-top: 8px;">
            <a class="labkey-button" id="qd-btn-diff">Run Diff</a>
        </div>
    </div>

    <div class="qd-section">
        <h3>Previous Compares</h3>
        <div>
            <label>Saved Diff: <select id="qd-diff-history-select" style="width:500px;"><option value="">-- select a previous compare --</option></select></label>
            <a class="labkey-button" id="qd-btn-load-diff">Load</a>
            <a class="labkey-button" id="qd-btn-refresh-history" style="margin-left:8px;">Refresh</a>
        </div>
    </div>

    <div id="qd-results-pane" class="qd-results"></div>
</div>

<script type="text/javascript" nonce="<%=getScriptNonce()%>">
(function() {
    var currentBaselines = [];
    var currentLiveDb = 'unknown';
    var currentCaptureParameters = [];
    var currentCaptureParameterKey = null;
    var captureParametersLoading = false;

    function clearCaptureParameters() {
        currentCaptureParameters = [];
        currentCaptureParameterKey = null;
        captureParametersLoading = false;

        var section = document.getElementById('qd-capture-params-section');
        var container = document.getElementById('qd-capture-params-container');
        var help = document.getElementById('qd-capture-params-help');

        container.innerHTML = '';
        help.textContent = '';
        section.style.display = 'none';
    }

    function getParameterEntries(source) {
        var entries = [];
        var seenNames = {};

        if (source && source.namedParameters) {
            var parameterNames = Object.keys(source.namedParameters);
            for (var i = 0; i < parameterNames.length; i++) {
                var parameterName = parameterNames[i];
                entries.push({ name: parameterName, value: source.namedParameters[parameterName] });
                seenNames[parameterName] = true;
            }
        }

        if (source && source.startDate !== undefined && !seenNames.StartDate)
            entries.push({ name: 'StartDate', value: source.startDate });
        if (source && source.endDate !== undefined && !seenNames.EndDate)
            entries.push({ name: 'EndDate', value: source.endDate });

        return entries.filter(function(entry) {
            return entry.value !== null && entry.value !== undefined && String(entry.value).length > 0;
        });
    }

    function formatParameterSummaryHtml(source) {
        var entries = getParameterEntries(source);
        if (entries.length === 0)
            return '';

        var parts = [];
        for (var i = 0; i < entries.length; i++) {
            parts.push(
                LABKEY.Utils.encodeHtml(entries[i].name) + '=' +
                LABKEY.Utils.encodeHtml(String(entries[i].value))
            );
        }

        return parts.join(', ');
    }

    function getParameterInputConfig(parameter) {
        var jdbcType = (parameter.type || 'VARCHAR').toUpperCase();

        if (jdbcType === 'DATE')
            return { element: 'input', type: 'date' };

        if (jdbcType === 'TIMESTAMP' || jdbcType === 'DATETIME')
            return { element: 'input', type: 'datetime-local' };

        if (jdbcType === 'BIT' || jdbcType === 'BOOLEAN')
            return { element: 'select' };

        if (jdbcType === 'INTEGER' || jdbcType === 'BIGINT' || jdbcType === 'SMALLINT' || jdbcType === 'TINYINT')
            return { element: 'input', type: 'number', step: '1' };

        if (jdbcType === 'DECIMAL' || jdbcType === 'DOUBLE' || jdbcType === 'FLOAT' || jdbcType === 'NUMERIC' || jdbcType === 'REAL')
            return { element: 'input', type: 'number', step: 'any' };

        return { element: 'input', type: 'text' };
    }

    function renderCaptureParameters(parameters) {
        currentCaptureParameters = parameters || [];

        var section = document.getElementById('qd-capture-params-section');
        var container = document.getElementById('qd-capture-params-container');
        var help = document.getElementById('qd-capture-params-help');

        container.innerHTML = '';

        if (currentCaptureParameters.length === 0) {
            help.textContent = '';
            section.style.display = 'none';
            return;
        }

        for (var i = 0; i < currentCaptureParameters.length; i++) {
            var parameter = currentCaptureParameters[i];
            var config = getParameterInputConfig(parameter);
            var fieldWrapper = document.createElement('label');
            fieldWrapper.className = 'qd-param-field';

            var labelText = document.createElement('span');
            labelText.textContent = parameter.name + (parameter.required ? '*:' : ':');
            fieldWrapper.appendChild(labelText);

            var input;
            if (config.element === 'select') {
                input = document.createElement('select');

                var blankOption = document.createElement('option');
                blankOption.value = '';
                blankOption.textContent = '--';
                input.appendChild(blankOption);

                var trueOption = document.createElement('option');
                trueOption.value = 'true';
                trueOption.textContent = 'true';
                input.appendChild(trueOption);

                var falseOption = document.createElement('option');
                falseOption.value = 'false';
                falseOption.textContent = 'false';
                input.appendChild(falseOption);
            } else {
                input = document.createElement('input');
                input.type = config.type;
                if (config.step)
                    input.step = config.step;
                if (config.type === 'text')
                    input.placeholder = (parameter.type || 'VARCHAR').toUpperCase();
            }

            input.id = 'qd-capture-param-' + i;
            input.setAttribute('data-param-name', parameter.name);
            input.setAttribute('data-param-type', (parameter.type || 'VARCHAR').toUpperCase());
            input.style.width = config.type === 'date' ? '160px' : config.type === 'datetime-local' ? '210px' : '140px';
            if (parameter.required)
                input.required = true;

            fieldWrapper.appendChild(input);
            container.appendChild(fieldWrapper);
        }

        help.textContent = 'Values are stored with the baseline and reused automatically during compare.';
        section.style.display = 'block';
    }

    function loadCaptureParameters(schemaName, queryName) {
        clearCaptureParameters();

        if (!schemaName || !queryName)
            return;

        var requestKey = schemaName + '|' + queryName;
        currentCaptureParameterKey = requestKey;
        captureParametersLoading = true;

        var section = document.getElementById('qd-capture-params-section');
        var help = document.getElementById('qd-capture-params-help');
        section.style.display = 'block';
        help.textContent = 'Loading query parameters...';

        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('query', 'queryDataParameters.api'),
            method: 'GET',
            params: { schemaName: schemaName, queryName: queryName },
            success: LABKEY.Utils.getCallbackWrapper(function(data) {
                if (currentCaptureParameterKey !== requestKey)
                    return;

                captureParametersLoading = false;
                renderCaptureParameters(data.parameters || []);
            }),
            failure: LABKEY.Utils.getCallbackWrapper(function(data) {
                if (currentCaptureParameterKey !== requestKey)
                    return;

                clearCaptureParameters();
                showStatus('Failed to load query parameters for ' + schemaName + '.' + queryName + ': ' +
                    (data.exception || 'Unknown error'), 'error');
            })
        });
    }

    function normalizeParameterValue(value, jdbcType) {
        if ((jdbcType === 'TIMESTAMP' || jdbcType === 'DATETIME') && value.indexOf('T') !== -1)
            return value.replace('T', ' ');

        return value;
    }

    function loadCaptureSchemas() {
        var sel = document.getElementById('qd-capture-schema');
        sel.disabled = true;
        sel.innerHTML = '<option value="">-- loading schemas --</option>';

        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('query', 'getSchemas.api'),
            method: 'GET',
            params: { includeHidden: false },
            success: LABKEY.Utils.getCallbackWrapper(function(data) {
                var schemaNames = (data.schemas || []).slice();
                schemaNames.sort(function(a, b) { return a.toLowerCase().localeCompare(b.toLowerCase()); });

                sel.innerHTML = '<option value="">-- select a schema --</option>';
                for (var i = 0; i < schemaNames.length; i++) {
                    var opt = document.createElement('option');
                    opt.value = schemaNames[i];
                    opt.textContent = schemaNames[i];
                    sel.appendChild(opt);
                }
                sel.disabled = false;
            }),
            failure: LABKEY.Utils.getCallbackWrapper(function(data) {
                sel.innerHTML = '<option value="">-- failed to load schemas --</option>';
                sel.disabled = false;
                showStatus('Failed to load schemas: ' + (data.exception || 'Unknown error'), 'error');
            })
        });
    }

    function loadCaptureQueries(schemaName) {
        var sel = document.getElementById('qd-capture-query');
        clearCaptureParameters();

        if (!schemaName) {
            sel.innerHTML = '<option value="">-- select a schema first --</option>';
            sel.disabled = true;
            return;
        }

        sel.disabled = true;
        sel.innerHTML = '<option value="">-- loading queries --</option>';

        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('query', 'getQueries.api'),
            method: 'GET',
            params: { schemaName: schemaName, includeColumns: false },
            success: LABKEY.Utils.getCallbackWrapper(function(data) {
                var queries = (data.queries || []).sort(function(a, b) {
                    return a.name.toLowerCase().localeCompare(b.name.toLowerCase());
                });

                sel.innerHTML = '<option value="">-- select a query --</option>';
                for (var i = 0; i < queries.length; i++) {
                    var opt = document.createElement('option');
                    opt.value = queries[i].name;
                    opt.textContent = queries[i].name;
                    sel.appendChild(opt);
                }
                sel.disabled = false;
            }),
            failure: LABKEY.Utils.getCallbackWrapper(function(data) {
                sel.innerHTML = '<option value="">-- failed to load queries --</option>';
                sel.disabled = false;
                showStatus('Failed to load queries for ' + schemaName + ': ' + (data.exception || 'Unknown error'), 'error');
            })
        });
    }

    function showStatus(msg, type) {
        var el = document.getElementById('qd-status-bar');
        el.className = 'qd-status qd-' + (type || 'info');
        el.textContent = msg;
        el.style.display = 'block';
    }

    function clearStatus() {
        var el = document.getElementById('qd-status-bar');
        el.style.display = 'none';
    }

    function setButtonEnabled(btnId, enabled) {
        var btn = document.getElementById(btnId);
        btn.className = enabled ? 'labkey-button' : 'labkey-button labkey-disabled-button';
    }

    function loadBaselines() {
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('query', 'queryDataBaselineList.api'),
            method: 'GET',
            success: LABKEY.Utils.getCallbackWrapper(function(data) {
                currentBaselines = data.baselines || [];
                currentLiveDb = data.liveDatabase || 'unknown';
                populateBaselineDropdown();
            }),
            failure: LABKEY.Utils.getCallbackWrapper(function(data) {
                showStatus('Failed to load baselines: ' + (data.exception || 'Unknown error'), 'error');
            })
        });
    }

    function populateBaselineDropdown() {
        var sel = document.getElementById('qd-baseline-select');
        while (sel.options.length > 1) sel.remove(1);

        for (var i = 0; i < currentBaselines.length; i++) {
            var b = currentBaselines[i];
            var opt = document.createElement('option');
            opt.value = b.fileName;
            opt.textContent = b.schemaName + '.' + b.queryName + ' (' + b.database + ', ' +
                b.rowCount + ' rows, ' + b.capturedAt + ')';
            sel.appendChild(opt);
        }
    }

    function onBaselineSelected() {
        var sel = document.getElementById('qd-baseline-select');
        var fileName = sel.value;
        var infoDiv = document.getElementById('qd-baseline-info');

        if (!fileName) {
            infoDiv.style.display = 'none';
            return;
        }

        var baseline = null;
        for (var i = 0; i < currentBaselines.length; i++) {
            if (currentBaselines[i].fileName === fileName) {
                baseline = currentBaselines[i];
                break;
            }
        }

        if (baseline) {
            document.getElementById('qd-diff-schema').value = baseline.schemaName;
            document.getElementById('qd-diff-query').value = baseline.queryName;

            var dbInfo = document.getElementById('qd-diff-db-info');
            var liveDb = currentLiveDb || 'unknown';
            dbInfo.innerHTML = '<span class="qd-db-arrow">' +
                LABKEY.Utils.encodeHtml(baseline.database) + ' &rarr; ' + LABKEY.Utils.encodeHtml(liveDb) +
                '</span> <span style="color:#888;font-size:12px;">(' + baseline.rowCount + ' baseline rows)</span>';

            var paramInfo = document.getElementById('qd-diff-param-info');
            var parameterSummary = formatParameterSummaryHtml(baseline);
            if (parameterSummary) {
                paramInfo.innerHTML = '<span style="color:#555;">Parameters: ' + parameterSummary + '</span>';
                paramInfo.style.display = 'inline';
            } else {
                paramInfo.innerHTML = '';
                paramInfo.style.display = 'none';
            }

            infoDiv.style.display = 'block';
        }
    }

    function captureBaseline() {
        var schemaName = document.getElementById('qd-capture-schema').value.trim();
        var queryName = document.getElementById('qd-capture-query').value.trim();

        if (!schemaName) {
            showStatus('Schema name is required.', 'error');
            return;
        }
        if (!queryName) {
            showStatus('Query/table name is required.', 'error');
            return;
        }

        var rowLimit = parseInt(document.getElementById('qd-capture-rowLimit').value, 10) || 0;
        var queryTimeout = parseInt(document.getElementById('qd-capture-timeout').value, 10) || 120;

        if (captureParametersLoading) {
            showStatus('Query parameters are still loading. Please try again in a moment.', 'error');
            return;
        }

        var namedParameters = {};
        for (var i = 0; i < currentCaptureParameters.length; i++) {
            var parameter = currentCaptureParameters[i];
            var input = document.getElementById('qd-capture-param-' + i);
            var rawValue = input ? input.value : '';

            if (!rawValue) {
                if (parameter.required) {
                    if (input && typeof input.reportValidity === 'function')
                        input.reportValidity();
                    showStatus('Parameter "' + parameter.name + '" is required.', 'error');
                    return;
                }
                continue;
            }

            namedParameters[parameter.name] = normalizeParameterValue(rawValue, (parameter.type || 'VARCHAR').toUpperCase());
        }

        showStatus('Capturing baseline for ' + schemaName + '.' + queryName + '...', 'info');
        setButtonEnabled('qd-btn-capture', false);

        var jsonPayload = {
            schemaName: schemaName,
            queryName: queryName,
            rowLimit: rowLimit,
            queryTimeout: queryTimeout
        };
        if (Object.keys(namedParameters).length > 0)
            jsonPayload.namedParameters = namedParameters;

        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('query', 'queryDataCapture.api'),
            method: 'POST',
            jsonData: jsonPayload,
            success: LABKEY.Utils.getCallbackWrapper(function(data) {
                var meta = data.metadata || {};
                showStatus('Baseline captured: ' + meta.rowCount + ' rows in ' +
                    (data.elapsedSeconds || 0).toFixed(1) + 's. File: ' + (data.metaFileName || ''), 'success');
                setButtonEnabled('qd-btn-capture', true);
                loadBaselines();
            }),
            failure: LABKEY.Utils.getCallbackWrapper(function(data) {
                showStatus('Capture failed: ' + (data.exception || 'Unknown error'), 'error');
                setButtonEnabled('qd-btn-capture', true);
            })
        });
    }

    function runDiff() {
        var baselineFileName = document.getElementById('qd-baseline-select').value;
        if (!baselineFileName) {
            showStatus('Please select a baseline.', 'error');
            return;
        }

        var maxDiffs = parseInt(document.getElementById('qd-diff-maxDiffs').value, 10) || 500;
        var queryTimeout = parseInt(document.getElementById('qd-diff-timeout').value, 10) || 120;

        var pkColumnsRaw = document.getElementById('qd-diff-pkColumns').value.trim();
        var pkColumns = pkColumnsRaw ? pkColumnsRaw.split(',').map(function(s) { return s.trim(); }).filter(function(s) { return s.length > 0; }) : [];

        showStatus('Running diff against baseline...', 'info');
        setButtonEnabled('qd-btn-diff', false);

        var jsonPayload = {
            baselineFileName: baselineFileName,
            maxDiffs: maxDiffs,
            queryTimeout: queryTimeout
        };
        if (pkColumns.length > 0)
            jsonPayload.primaryKeyColumns = pkColumns;

        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('query', 'queryDataDiff.api'),
            method: 'POST',
            jsonData: jsonPayload,
            success: LABKEY.Utils.getCallbackWrapper(function(data) {
                var summary = data.summary || {};
                var totalDiffs = (summary.addedRows || 0) + (summary.deletedRows || 0) + (summary.modifiedRows || 0);
                if (totalDiffs === 0) {
                    showStatus('Diff complete: all ' + summary.matchedRows + ' rows match. ' +
                        (data.elapsedSeconds || 0).toFixed(1) + 's', 'success');
                } else {
                    showStatus('Diff complete: ' + totalDiffs + ' difference(s) found. ' +
                        (data.elapsedSeconds || 0).toFixed(1) + 's', 'warn');
                }
                setButtonEnabled('qd-btn-diff', true);
                renderDiffResults(data);
                loadDiffHistory();
            }),
            failure: LABKEY.Utils.getCallbackWrapper(function(data) {
                showStatus('Diff failed: ' + (data.exception || 'Unknown error'), 'error');
                setButtonEnabled('qd-btn-diff', true);
            })
        });
    }

    function renderDiffResults(data) {
        var pane = document.getElementById('qd-results-pane');
        var summary = data.summary || {};
        var meta = data.metadata || {};

        var html = '<div class="qd-section"><h3>Diff Results</h3>';

        // Summary table
        html += '<table class="qd-summary-table">';
        html += '<tr><td class="qd-label">Schema.Query:</td><td>' + LABKEY.Utils.encodeHtml(meta.schemaName + '.' + meta.queryName) + '</td></tr>';
        html += '<tr><td class="qd-label">Baseline:</td><td>' + LABKEY.Utils.encodeHtml(meta.baselineDatabase + ' (' + meta.baselineCapturedAt + ')') + '</td></tr>';
        html += '<tr><td class="qd-label">Live:</td><td>' + LABKEY.Utils.encodeHtml(meta.liveDatabase) + '</td></tr>';

        var paramStr = formatParameterSummaryHtml(meta);
        if (paramStr) {
            html += '<tr><td class="qd-label">Parameters:</td><td>' + paramStr + '</td></tr>';
        }

        if (meta.primaryKeyColumns && meta.primaryKeyColumns.length > 0) {
            var pkLabel = meta.syntheticPrimaryKey ? 'Key Columns (synthetic):' : 'Key Columns:';
            var pkCols = meta.primaryKeyColumns.map(function(c) { return LABKEY.Utils.encodeHtml(c); }).join(', ');
            var pkCls = meta.syntheticPrimaryKey ? 'qd-stat-warn' : '';
            html += '<tr><td class="qd-label">' + pkLabel + '</td><td class="' + pkCls + '">' + pkCols + '</td></tr>';
        }

        html += '<tr><td class="qd-label">Baseline Rows:</td><td>' + summary.baselineRowCount + '</td></tr>';
        html += '<tr><td class="qd-label">Live Rows:</td><td>' + summary.liveRowCount + '</td></tr>';

        var matchCls = summary.matchedRows === summary.baselineRowCount && summary.matchedRows === summary.liveRowCount ? 'qd-stat-ok' : '';
        html += '<tr><td class="qd-label">Matched:</td><td class="' + matchCls + '">' + summary.matchedRows + '</td></tr>';

        if (summary.addedRows > 0)
            html += '<tr><td class="qd-label">Added (in live):</td><td class="qd-stat-warn">' + summary.addedRows + '</td></tr>';
        if (summary.deletedRows > 0)
            html += '<tr><td class="qd-label">Deleted (in live):</td><td class="qd-stat-warn">' + summary.deletedRows + '</td></tr>';
        if (summary.modifiedRows > 0)
            html += '<tr><td class="qd-label">Modified:</td><td class="qd-stat-warn">' + summary.modifiedRows + '</td></tr>';
        if (summary.diffsTruncated)
            html += '<tr><td class="qd-label">Truncated:</td><td class="qd-stat-error">Yes (max diffs reached)</td></tr>';

        if (meta.hasPrimaryKey === false)
            html += '<tr><td class="qd-label">Warning:</td><td class="qd-stat-warn">' + LABKEY.Utils.encodeHtml(meta.warning || 'No primary key') + '</td></tr>';

        html += '</table>';

        // Modified rows detail
        var modified = data.modified || [];
        if (modified.length > 0) {
            html += '<details class="qd-detail-section" open>';
            html += '<summary>Modified Rows (' + modified.length + ')</summary>';
            for (var i = 0; i < modified.length; i++) {
                var mod = modified[i];
                var pk = mod.primaryKey;
                var changes = mod.changes || {};
                var changeKeys = Object.keys(changes);

                html += '<div style="margin: 8px 0; padding: 8px; background: #fff; border: 1px solid #ddd;">';
                if (pk) {
                    var pkParts = [];
                    for (var pkKey in pk) {
                        if (pk.hasOwnProperty(pkKey))
                            pkParts.push(LABKEY.Utils.encodeHtml(pkKey) + '=' + LABKEY.Utils.encodeHtml(String(pk[pkKey])));
                    }
                    html += '<div style="font-weight:bold;margin-bottom:4px;">PK: ' + pkParts.join(', ') + '</div>';
                }
                html += '<table class="qd-diff-table">';
                html += '<tr><th>Column</th><th>Baseline</th><th>Live</th></tr>';
                for (var j = 0; j < changeKeys.length; j++) {
                    var col = changeKeys[j];
                    var change = changes[col];
                    html += '<tr>';
                    html += '<td><b>' + LABKEY.Utils.encodeHtml(col) + '</b></td>';
                    html += '<td class="qd-change-baseline">' + formatValue(change.baseline) + '</td>';
                    html += '<td class="qd-change-live">' + formatValue(change.live) + '</td>';
                    html += '</tr>';
                }
                html += '</table></div>';
            }
            html += '</details>';
        }

        // Added rows detail
        var added = data.added || [];
        if (added.length > 0) {
            html += '<details class="qd-detail-section">';
            html += '<summary>Added Rows (' + added.length + ')</summary>';
            html += renderRowsTable(added);
            html += '</details>';
        }

        // Deleted rows detail
        var deleted = data.deleted || [];
        if (deleted.length > 0) {
            html += '<details class="qd-detail-section">';
            html += '<summary>Deleted Rows (' + deleted.length + ')</summary>';
            html += renderRowsTable(deleted);
            html += '</details>';
        }

        html += '</div>';
        pane.innerHTML = html;
    }

    function renderRowsTable(rows) {
        if (rows.length === 0) return '<p>None</p>';

        var cols = Object.keys(rows[0]);
        var html = '<table class="qd-diff-table"><tr>';
        for (var c = 0; c < cols.length; c++)
            html += '<th>' + LABKEY.Utils.encodeHtml(cols[c]) + '</th>';
        html += '</tr>';

        for (var r = 0; r < rows.length; r++) {
            html += '<tr>';
            for (var c2 = 0; c2 < cols.length; c2++)
                html += '<td>' + formatValue(rows[r][cols[c2]]) + '</td>';
            html += '</tr>';
        }
        html += '</table>';
        return html;
    }

    function formatValue(val) {
        if (val === null || val === undefined)
            return '<span style="color:#999;font-style:italic;">NULL</span>';
        return LABKEY.Utils.encodeHtml(String(val));
    }

    // ---- Previous Compares ----

    function loadDiffHistory() {
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('query', 'schemaCompareResults.api'),
            method: 'GET',
            success: LABKEY.Utils.getCallbackWrapper(function(data) {
                var files = (data.files || [])
                    .filter(function(f) { return f.name.indexOf('query-diff-') === 0; })
                    .sort(function(a, b) {
                        return a.modified < b.modified ? 1 : a.modified > b.modified ? -1 : 0;
                    });
                populateDiffHistoryDropdown(files);
            }),
            failure: LABKEY.Utils.getCallbackWrapper(function(data) {
                showStatus('Failed to load diff history: ' + (data.exception || 'Unknown error'), 'error');
            })
        });
    }

    function populateDiffHistoryDropdown(files) {
        var sel = document.getElementById('qd-diff-history-select');
        while (sel.options.length > 1) sel.remove(1);

        for (var i = 0; i < files.length; i++) {
            var f = files[i];
            var opt = document.createElement('option');
            opt.value = f.name;
            opt.textContent = f.name + ' (' + f.modified + ')';
            sel.appendChild(opt);
        }
    }

    function loadSavedDiff() {
        var fileName = document.getElementById('qd-diff-history-select').value;
        if (!fileName) {
            showStatus('Please select a previous compare.', 'error');
            return;
        }

        showStatus('Loading ' + fileName + '...', 'info');

        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('query', 'schemaCompareResults.api'),
            method: 'GET',
            params: { fileName: fileName },
            success: LABKEY.Utils.getCallbackWrapper(function(data) {
                var summary = data.summary || {};
                var totalDiffs = (summary.addedRows || 0) + (summary.deletedRows || 0) + (summary.modifiedRows || 0);
                if (totalDiffs === 0) {
                    showStatus('Loaded: all ' + summary.matchedRows + ' rows matched.', 'success');
                } else {
                    showStatus('Loaded: ' + totalDiffs + ' difference(s) found.', 'warn');
                }
                renderDiffResults(data);
            }),
            failure: LABKEY.Utils.getCallbackWrapper(function(data) {
                showStatus('Failed to load diff: ' + (data.exception || 'Unknown error'), 'error');
            })
        });
    }

    // Event listeners
    document.getElementById('qd-btn-capture').addEventListener('click', function(e) { e.preventDefault(); captureBaseline(); });
    document.getElementById('qd-btn-diff').addEventListener('click', function(e) { e.preventDefault(); runDiff(); });
    document.getElementById('qd-btn-refresh').addEventListener('click', function(e) { e.preventDefault(); loadBaselines(); });
    document.getElementById('qd-capture-schema').addEventListener('change', function() { loadCaptureQueries(this.value); });
    document.getElementById('qd-capture-query').addEventListener('change', function() {
        loadCaptureParameters(document.getElementById('qd-capture-schema').value, this.value);
    });
    document.getElementById('qd-baseline-select').addEventListener('change', onBaselineSelected);
    document.getElementById('qd-btn-load-diff').addEventListener('click', function(e) { e.preventDefault(); loadSavedDiff(); });
    document.getElementById('qd-btn-refresh-history').addEventListener('click', function(e) { e.preventDefault(); loadDiffHistory(); });

    // Initial load
    loadCaptureSchemas();
    loadBaselines();
    loadDiffHistory();
})();
</script>
