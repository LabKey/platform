/*
 * Copyright (c) 2006-2010 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
function TableInfoService(urlTableInfo)
{
    var ret = {};

    function makeRequest(url)
    {
        var xmlhttp;
        if (window.XMLHttpRequest)
            xmlhttp = new XMLHttpRequest();
        else if (window.ActiveXObject)
            xmlhttp = new ActiveXObject("Microsoft.XMLHTTP");
        xmlhttp.open("GET", url, false);
        xmlhttp.send(null);
        return xmlhttp.responseXML;
    }

    function innerText(el)
    {
        var ret = "";
        for (var i = 0; i < el.childNodes.length; i ++)
        {
            ret += el.childNodes.item(i).nodeValue;
        }
        return ret;
    }

    function elementText(xmlElement, childName)
    {
        var nl = XMLUtil.getChildrenWithTagName(xmlElement, childName, nsData);
        if (nl.length <= 0)
            return '';
        return innerText(nl[0]);
    }

    // This has to match org.labkey.api.query.FieldKey.encodePart
    function encodeKey(s)
    {
        return s.replace(/\$/g, "$D").replace(/\//g, "$S").replace(/\&/g, "$A");
    }

    function fieldKey(table, field)
    {
        if (!table)
            return field;
        return table + "/" + encodeKey(field);
    }

    function processTable(xmlTable, fieldsObject)
    {
        var table = {};
        table.fields = [];
        table.children = [];
        table.key = xmlTable.getAttribute("tableName");
        table.label = elementText(xmlTable, "tableTitle");
        var elColumns = XMLUtil.getChildWithTagName(xmlTable, "columns", nsData);
        var nlColumns = XMLUtil.getChildrenWithTagName(elColumns, "column", nsData);
        for (var iColumn = 0; iColumn < nlColumns.length; iColumn ++)
        {
            var xmlColumn = nlColumns[iColumn];
            var column = {};
            var name = xmlColumn.getAttribute("columnName");
            column.label = elementText(xmlColumn, "columnTitle");
            column.table = table.key;
            column.key = fieldKey(table.key, name);
            column.caption = elementText(xmlColumn, "columnTitle");
            column.hidden = elementText(xmlColumn, "isHidden") == "true";
            column.description = elementText(xmlColumn, "description");
            column.datatype = elementText(xmlColumn, "datatype");
            column.unselectable = elementText(xmlColumn, "isUnselectable") == "true";
            var nlFK = XMLUtil.getChildrenWithTagName(xmlColumn, "fk", nsData);
            for (var iFK = 0; iFK < nlFK.length; iFK ++)
            {
                var xmlFK = nlFK[iFK];
                column.lookupTable = elementText(xmlFK, "fkTable");
                table.children.push(column.lookupTable);
            }
            table.fields.push(column.key);
            if (fieldsObject)
            {
                fieldsObject[column.key] = column;
            }
        }
        return table;
    }

    function escapeField(field)
    {
        var ret = window.escape(field);
        ret = ret.replace(/\+/g, "%2B");
        return ret;
    }

    function fetchTables(urlTableInfo, tableNames, fieldsObject)
    {
        var url = urlTableInfo;
        for (var i = 0; i < tableNames.length; i ++)
        {
            url = url + "&tableKey=" + escape(tableNames[i]);
        }
        var xml = makeRequest(url);
        var ret = [];
        var nlTables = xml.documentElement.childNodes;
        for (var iTable = 0; iTable < nlTables.length; iTable ++)
        {
            var xmlTable = nlTables.item(iTable);
            if (xmlTable.tagName != "table")
                continue;
            var table = processTable(xmlTable, fieldsObject);
            ret.push(table);

        }
        return ret;
    }

    function fetchColumns(urlColumnInfo, fieldKeys, fieldsObject)
    {
        var url = urlColumnInfo;
        for (var i = 0; i < fieldKeys.length; i ++)
        {
            url = url + "&fieldKey=" + escapeField(fieldKeys[i]);

        }
        var xml = makeRequest(url);
        var ret = [];
        var nlTables = xml.documentElement.childNodes;
        for (var iTable = 0; iTable < nlTables.length; iTable ++)
        {
            var xmlTable = nlTables.item(iTable);
            if (xmlTable.tagName != "table")
                continue;
            processTable(xmlTable, fieldsObject);
        }
    }

    ret.getTableInfos = function(tableNames)
    {
        var rgToFetch = [];
        for (var i = 0; i < tableNames.length; i ++)
        {
            var tableName = tableNames[i];
            if (this.tables[tableName])
            {
                continue;
            }
            rgToFetch.push(tableName);
        }
        if (rgToFetch.length)
        {
            var tables = fetchTables(urlTableInfo, rgToFetch, this.fields);
            for (var i = 0; i < tables.length; i ++)
            {
                this.tables[tables[i].key] = tables[i];
            }
        }
        var ret = [];
        for (var i = 0; i < tableNames.length; i ++)
        {
            ret.push(this.tables[tableNames[i]]);
        }
        return ret;
    }

    ret.getOutputColumnInfos = function(fieldKeys)
    {
        var rgToFetch = [];
        for (var i = 0; i < fieldKeys.length; i ++)
        {
            var field = fieldKeys[i];
            if (this.outputColumns[field])
            {
                continue;
            }
            rgToFetch.push(field);
        }
        if (rgToFetch.length)
        {
            fetchColumns(urlTableInfo, rgToFetch, this.outputColumns);
        }
        return this.outputColumns;
    }

    ret.processTable = function(name, xmlTable)
    {
        var table = processTable(xmlTable, ret.fields);
        ret.tables[name] = table;
        return table;
    }
    ret.processOutputColumns = function(xmlTable)
    {
        processTable(xmlTable, ret.outputColumns);            
    }
    ret.tables = {};
    ret.fields = {};
    ret.outputColumns = {};
    return ret;
}

function ColumnPicker(tableInfoService)
{
    var showHiddenFields;
    var rgFocusedFields = [];
    var columnPicker = {};
    columnPicker.fields = tableInfoService.fields;
    columnPicker.tables = tableInfoService.tables;

    function getColumnTd(column)
    {
        return document.getElementById("column_" + column);
    }

    function getColumnName(td)
    {
        var id = td.id;
        if (!id)
            return null;
        if (id.length < 7)
            return null;
        if (id.substring(0, 7 ) != "column_")
            return null;
        return id.substring(7);
    }

    function columnFromTr(tr)
    {
        if (!showHiddenFields && tr.getAttribute("hiddenField"))
        {
            return null;
        }
        var children = tr.childNodes;
        for (var i = 0; i < children.length; i ++)
        {
            var td = children.item(i);
            var ret = getColumnName(td);
            if (ret)
                return ret;
        }
        return null;
    }

    function focusField(event, column)
    {
        if (!event)
            event = window.event;
        for (var i = 0; i < rgFocusedFields.length; i ++)
        {
            var td = getColumnTd(rgFocusedFields[i]);
            if (td)
            {
                td.style.border = "solid 1px white";
            }
        }
        var range = false;
        if (event.shiftKey)
        {
            if (rgFocusedFields.length > 0)
            {
                var columnAnchor = rgFocusedFields[0];
                var tdAnchor = getColumnTd(columnAnchor);
                var tdDot = getColumnTd(column);
                if (tdAnchor && tdDot && tdAnchor.parentNode.parentNode == tdDot.parentNode.parentNode)
                {
                    var columns = [];
                    var idxAnchor = -1;
                    var idxDot = -1;
                    var nlRows = tdDot.parentNode.parentNode.childNodes;
                    for (var i = 0; i < nlRows.length; i ++)
                    {
                        var columnSib = columnFromTr(nlRows.item(i));
                        if (columnSib)
                        {
                            if (columnSib == column)
                            {
                                idxDot = columns.length;
                            }
                            if (columnSib == columnAnchor)
                            {
                                idxAnchor = columns.length;
                            }
                            columns.push(columnSib);
                        }
                    }
                    rgFocusedFields = [];
                    if (idxAnchor != -1 && idxDot != -1)
                    {
                        if (idxAnchor < idxDot)
                        {
                            for (var i = idxAnchor; i <= idxDot; i ++)
                            {
                                rgFocusedFields.push(columns[i]);
                            }
                        }
                        else
                        {
                            for (var i = idxAnchor; i >= idxDot; i --)
                            {
                                rgFocusedFields.push(columns[i]);
                            }
                        }
                        range = true;
                    }
                }
            }
        }

        if (!range)
        {
            rgFocusedFields = [column];
        }
        for (var i = 0; i < rgFocusedFields.length; i ++)
        {
            var td = getColumnTd(rgFocusedFields[i]);
            if (td)
            {
                td.style.border = "solid 1px black";
            }
        }
    }

    function indexOf(array, value)
    {
        for (var i = 0; i < array.length; i ++)
        {
            if (array[i] == value)
                return i;
        }
        return -1;
    }

    function contains(array, value)
    {
        return indexOf(array, value) >= 0;
    }

    function expandColumn(key, depth)
    {
        var trChildren = document.getElementById("children_" + key);
        var elExpand = document.getElementById("expand_" + key);
        var strExpanded = LABKEY.contextPath + "/_images/minus.gif";
        var strCollapsed = LABKEY.contextPath + "/_images/plus.gif";
        if (trChildren)
        {
            if (trChildren.getAttribute("collapsed"))
            {
                trChildren.style.display = "";
                elExpand.src = strExpanded;
                trChildren.removeAttribute("collapsed");
            }
            else
            {
                trChildren.style.display = "none";
                elExpand.src = strCollapsed;
                trChildren.setAttribute("collapsed", "true");
            }
            return;
        }
        var table = tableInfoService.getTableInfos([key])[0];
        trChildren = document.createElement("tr");
        trChildren.setAttribute("id", "children_" + key);
        var tdChildren = document.createElement("td");
        trChildren.appendChild(tdChildren);
        addTable.call(this, tdChildren, table, depth);
        var trParent = document.getElementById("column_" + key).parentNode;
        trParent.parentNode.insertBefore(trChildren, trParent.nextSibling);
        elExpand.src = strExpanded;
    }

    function onClickHandler(columnPicker, key)
    {
        return function(event)
        {
            if (!event)
                event = window.event;
            columnPicker.focusField(event, key);
        }
    }
    function onDblClickHandler(columnPicker, key)
    {
        return function(event)
        {
            columnPicker.doubleClickField(key);        
        }
    }

    function expandColumnHandler(columnPicker, key, depth)
    {
        return function()
        {
            columnPicker.expandColumn(key, depth);        
        }
    }

    function addTable(el, table, depth)
    {
        var doc = el.ownerDocument;
        var elTable = doc.createElement('table');
        var tbody = doc.createElement('tbody');
        elTable.width = "100%";
        var selectedFields = this.getSelectedFields();
        for (var iColumn = 0; iColumn < table.fields.length; iColumn ++)
        {
            var column = tableInfoService.fields[table.fields[iColumn]];
            var hasChildren = contains(table.children, column.key);
            var selected = selectedFields[column.key] ? true : false;
            var unselectable = column.unselectable;
            var tr = doc.createElement('tr');
            var columnPicker = this;

            if (column.hidden)
            {
                tr.setAttribute('hiddenField', 'true');
                if (!showHiddenFields)
                {
                    tr.style.display = 'none';
                }
            }
            var td = doc.createElement('td');
            td.id = 'column_' + column.key;
            td.style.border = 'solid 1px white';
            if (unselectable)
            {
                td.style.fontStyle = 'italic';
            }
            else
            {
                td.style.cursor = 'default';
            }
            td.style.padding = '3px';
            td.style.paddingLeft = '' + ((10 * depth) + 5) + 'px';
            if (selected)
            {
                td.style.fontWeight = 'bold';
            }

            td.sourceColumn = column;
            new Ext.ToolTip({target: td, html:getColumnHelpHtml(td.sourceColumn), trackMouse:true});

            if (!unselectable)
            {
                td.onclick = onClickHandler(columnPicker, column.key);
                td.ondblclick = onDblClickHandler(columnPicker, column.key);
            }
            if (hasChildren)
            {
                var a = doc.createElement('a');
                a.href = '#';
                a.onclick = expandColumnHandler(columnPicker, column.key, depth + 1);
                var img = doc.createElement('img');
                img.id = 'expand_' + column.key;
                img.border = 0;
                img.src = LABKEY.contextPath + '/_images/plus.gif';
                a.appendChild(img);
                td.appendChild(a);
                td.appendChild(doc.createTextNode('\240'));
            }
            else
            {
                var img = doc.createElement('img');
                img.src = LABKEY.contextPath + '/_.gif';
                img.width = 9;
                td.appendChild(img);
                td.appendChild(doc.createTextNode('\240'));
            }
            td.appendChild(doc.createTextNode(column.caption));
            tr.appendChild(td);
            tbody.appendChild(tr);
        }
        elTable.appendChild(tbody);
        el.appendChild(elTable);
    }

    function setShowHiddenFields(show)
    {
        var nl = document.getElementsByTagName("TR");
        var count = nl.length;
        for (var i = 0; i < count; i ++)
        {
            var tr = nl.item(i);
            if (tr.getAttribute("hiddenField"))
            {
                if (show)
                {
                    if (!tr.getAttribute("collapsed"))
                    {
                        tr.style.display = "";
                    }
                }
                else
                {
                    tr.style.display = "none";
                }
            }
        }
        showHiddenFields = show;
    }


    columnPicker.addTable = addTable;
    columnPicker.setShowHiddenFields = setShowHiddenFields;
    columnPicker.setFieldSelected = function(field, selected)
    {
        var tr = getColumnTd(field);
        if (!tr)
            return;
        if (selected)
        {
            tr.style.fontWeight = 'bold';
        }
        else
        {
            tr.style.fontWeight = 'normal';
        }
    }
    columnPicker.doubleClickField = function(key) {
    }
    columnPicker.getFocusedFields = function()
    {
        return rgFocusedFields;
    }
    columnPicker.getSelectedFields = function(field)
    {
        return {};
    }
    columnPicker.focusField = focusField;
    columnPicker.expandColumn = expandColumn;
    return columnPicker;
}


function getColumnHelpHtml(field)
{
    var body = "<table>";
    if (field.description)
    {
        body += "<tr><td valign='top'><strong>Description:</strong></td><td>" + field.description + "</td></tr>";
    }
    body += "<tr><td valign='top'><strong>Field&nbsp;key:</strong></td><td>" + field.key + "</td></tr>";
    if (field.datatype)
    {
        var datatype = _typeMap[field.datatype.toUpperCase()];
        if (datatype)
        {
            body += "<tr><td valign='top'><strong>Data&nbsp;type:</strong></td><td>" + datatype.toLowerCase() + "</td></tr>";
        }
    }
    if (field.hidden)
    {
        body += "<tr><td valign='top'><strong>Hidden:</strong></td><td>" + field.hidden + "</td></tr>";
    }
    body += "</table>";
    return body;
}
