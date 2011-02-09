/*
 * Copyright (c) 2009-2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
var _runningRequests = 0;
var _queries = [];
var _numInvalid = 0;
var _includeAllColumns = true;

Ext.onReady(onReady);

function onReady()
{
    Ext.get("btn-validate").on("click", onValidate);
    Ext.get("rb-include-all").on("click", function(){_includeAllColumns = true;});
    Ext.get("rb-include-defvis").on("click", function(){_includeAllColumns = false;});

    setStatus("Loading schemas...");
    LABKEY.Query.getSchemas({
        success: onSchemas,
        failure: onError
    });
}

function onValidate()
{
    Ext.get("btn-validate").dom.disabled = true;
    setStatus("Validating queries...");
    setTimeout("startValidation()", 100); //give page time to repaint
}

function startValidation()
{
    _numInvalid = 0;

    for (var idx = 0; idx < _queries.length; ++idx)
    {
        setQueryValidationStatus(_queries[idx].schemaName, _queries[idx].queryName, "validating...");
        LABKEY.Query.validateQuery({
            schemaName: _queries[idx].schemaName,
            queryName: _queries[idx].queryName,
            successCallback: getOnValidQuery(_queries[idx].schemaName, _queries[idx].queryName),
            errorCallback: getOnInvalidQuery(_queries[idx].schemaName, _queries[idx].queryName),
            includeAllColumns: _includeAllColumns
        });
        ++_runningRequests;
    }
}

function setStatus(msg)
{
    var statusElem = Ext.get("vq-status");
    statusElem.update(msg);
    statusElem.addClass("labkey-status-info");
    statusElem.removeClass("labkey-status-error");
}

function setErrorStatus(msg)
{
    var statusElem = Ext.get("vq-status");
    statusElem.update(msg);
    statusElem.addClass("labkey-status-error");
    statusElem.removeClass("labkey-status-info");
}

function onError(errorInfo)
{
    setErrorStatus(errorInfo.exception || errorInfo.exceptionClass);
}

function onSchemas(data)
{
    renderSchemaTables(data.schemas);

    setStatus("Loading queries for each schema...");

    for (var idx = 0; idx < data.schemas.length; ++idx)
    {
        LABKEY.Query.getQueries({
            schemaName: data.schemas[idx],
            successCallback: onQueries,
            errorCallback: onError,
            includeColumns: false,
            includeUserQueries: true
        });
        ++_runningRequests;
    }
}

function renderSchemaTables(schemas)
{
    var html = "<table>";
    for (var idx = 0; idx < schemas.length; ++idx)
    {
        html += genSchemaTableRow(schemas[idx]);
    }
    html += "</table>";
    Ext.get("vq-schemas").update(html);
}

function genSchemaTableRow(schema)
{
    return "<tr id='vqs-" + schema + "'><td class='schema' colspan='2'>" + schema + "</td></tr>";
}

function onQueries(data)
{
    renderQueryRows(data.schemaName, data.queries);

    for (var idx = 0; idx < data.queries.length; ++idx)
    {
        _queries.push({
            schemaName: data.schemaName,
            queryName: data.queries[idx].name
        });
    }

    --_runningRequests;
    if (0 == _runningRequests)
    {
        setStatus("All queries loaded. Click button to begin validation.");
        Ext.get("btn-validate").dom.disabled = false;
    }
}

function renderQueryRows(schema, queries)
{
    var schemaTableRow = Ext.get("vqs-" + schema);
    if (!schemaTableRow)
        Ext.Msg.alert("Error", "Could not find table row for schema " + schema);

    if (0 == queries.length)
    {
        schemaTableRow.insertSibling({
            tag: 'tr',
            children: [
                {
                    tag: 'td',
                    html: '<span style=\"font-style: italic\">(no queries)</span>'
                },
                {
                    tag: 'td',
                    html: '&nbsp;'
                }
            ]
        }, "after");

    }
    else
    {
        for (var idx = 0; idx < queries.length; ++idx)
        {
            schemaTableRow.insertSibling({
                tag: 'tr',
                children: [
                    {
                        tag: 'td',
                        html: queries[idx].name
                    },
                    {
                        tag: 'td',
                        id: getQueryStatusCellId(schema, queries[idx].name),
                        html: '&nbsp;'
                    }
                ]
            }, "after");
        }
    }
}

function getQueryStatusCellId(schemaName, queryName)
{
    return "vqqs-" + schemaName + "-" + queryName;
}

function getOnValidQuery(schemaName, queryName)
{
    return function() {
        setQueryValidationStatus(schemaName, queryName, "OK", false);
        --_runningRequests;
        checkValidateComplete();
    };
}

function getOnInvalidQuery(schemaName, queryName)
{
    return function(errorInfo) {
        ++_numInvalid;
        setQueryValidationStatus(schemaName, queryName, "ERROR: " + (errorInfo.exception || errorInfo.exceptionClass), true);
        --_runningRequests;
        checkValidateComplete();
    };
}

function setQueryValidationStatus(schemaName, queryName, msg, isError)
{
    var queryStatusCell = Ext.get(getQueryStatusCellId(schemaName, queryName));
    if (!queryStatusCell)
        Ext.Msg.alert("Error", "Could not find the status cell for query " + schemaName + "." + queryName);
    queryStatusCell.update(msg);

    queryStatusCell.removeClass("invalid-msg");
    queryStatusCell.removeClass("valid-msg");
    
    if (isError === true)
        queryStatusCell.addClass("invalid-msg");
    else if(isError === false)
        queryStatusCell.addClass("valid-msg");
}

function checkValidateComplete()
{
    if (0 == _runningRequests)
    {
        var msg = "Validation complete. ";
        if (_numInvalid > 1)
            msg += _numInvalid + " queries failed validation (see below).";
        else if (1 == _numInvalid)
            msg += _numInvalid + " query failed validation (see below).";
        else
            msg += " All queries were validated successfully.";

        if (_numInvalid > 0)
            setErrorStatus(msg);
        else
            setStatus(msg);

        Ext.get("btn-validate").dom.disabled =false;
    }
}