/*
 * Copyright (c) 2006-2014 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
var nsQuery = "http://query.labkey.org/design";
var nsData = "http://labkey.org/data/xml";

function Bind_Metadata(designer, dn, metadataTagName)
{
    this.designer = designer;
    this.dn = dn;
    this.metadataTagName = metadataTagName;
}

Bind_Metadata.prototype =
{
    strDef : '',
    getValue : function()
    {
        var dnMetadata = XMLUtil.getChildWithTagName(this.dn, 'metadata', nsQuery);
        if (!dnMetadata)
            return this.strDef;
        var dnAttr = XMLUtil.getChildWithTagName(dnMetadata, this.metadataTagName, nsData);
        if (!dnAttr)
            return this.strDef;
        return XMLUtil.getInnerText(dnAttr);
    },
    setValue : function(value)
    {
        if (value == this.getValue())
            return;
        var dnMetadata = XMLUtil.getChildWithTagName(this.dn, 'metadata', nsQuery);
        if (!dnMetadata)
        {
            dnMetadata = XMLUtil.createElement(this.dn.ownerDocument, 'metadata', nsQuery);
            this.dn.appendChild(dnMetadata);
        }
        var dnAttr = XMLUtil.getChildWithTagName(dnMetadata, this.metadataTagName, nsData);
        if (!dnAttr)
        {
            dnAttr = XMLUtil.createElement(this.dn.ownerDocument, this.metadataTagName, nsData);
            dnMetadata.appendChild(dnAttr);
        }
        if (!value)
        {
            dnMetadata.removeChild(dnAttr);
        }
        else
        {
            XMLUtil.setInnerText(dnAttr, value);
        }
        this.designer.updateDocument();
    }
}

function getColumnAlias(dn)
{
    var dnValue = XMLUtil.getChildWithTagName(dn, "value", nsQuery);
    if (!dnValue)
    {
        return "Invalid Column";
    }
    var strAlias = dn.getAttribute("alias");
    if (strAlias)
        return strAlias;
    var dnField = XMLUtil.getChildWithTagName(dnValue, "field", nsQuery);
    if (dnField)
    {
        return LABKEY.FieldKey.fromString(XMLUtil.getInnerText(dnField)).name;
    }
    else
    {
        return "<Expression>";
    }
}

function createColumnEditor(tab, dn, parent, insertBefore)
{
    var doc = parent.ownerDocument;
    var tr = doc.createElement('tr');
    var td = doc.createElement('td');
    td.style.backgroundColor = "white";
    td.style.cursor = 'default';
    var ret = {
        dn: dn,
        select : function()
        {
            td.style.backgroundColor = "silver";
        },
        deselect : function()
        {
            td.style.backgroundColor = "white";
        },
        refresh: function()
        {
            XMLUtil.setInnerText(td, tab.designer.getColumnLabel(dn));
        }
    }
    td.onclick = function(event)
    {
        tab.select(event, dn);
    }
    td.onselectstart = function(event)
    {
        return false;        
    }
    td.onmousedown = function()
    {
        return false;
    }


    td.appendChild(doc.createTextNode(tab.designer.getColumnLabel(dn)));
    var fieldKey = tab.designer.getFieldKeyString(dn);
    if (fieldKey)
    {
        var field = { key : fieldKey };
        new Ext.ToolTip({target: td, html:getColumnHelpHtml(field), trackMouse:true});
    }
    tr.appendChild(td);
    parent.insertBefore(tr, insertBefore);
    return ret;
}


function Tab(designer, dn)
{
    this.selectedNodes = [];
    this.dn = dn;
    this.designer = designer;
    this.editors = [];
}

Tab.prototype = {
    activate: function()
    {
        if (this.elTab)
        {
            this.elTab.className = 'labkey-tab-selected';
        }
        for (var i = 0; i < this.rgEl.length; i ++)
        {
            var el = this.rgEl[i];
            if (el)
                el.style.display = '';
        }
        if (this.elList)
        {
            XMLUtil.removeChildren(this.elList);
            this.createHTMLElements(this.elList, null);
        }
        this.setSelection(this.selectedNodes);
    },
    deactivate : function()
    {
        if (this.elTab)
        {
            this.elTab.className = 'labkey-tab';
        }
        for (var i = 0; i < this.rgEl.length; i ++)
        {
            var el = this.rgEl[i];
            if (el)
                el.style.display = 'none';
        }
    },
    createHTMLElements : function(parent, insertBefore)
    {
        var doc = parent.ownerDocument;
        var table = doc.createElement('table');
        table.cellSpacing = 0;
        table.cellPadding = 0;
        table.width = "100%";
        var tbody = doc.createElement('tbody');
        this.editors = [];
        var nl = this.getNodes();
        for (var i = 0; i < nl.length; i ++)
        {
            var dn = nl[i];
            var editor = this.createEditor(dn, tbody, null);
            if (this.isSelected(dn))
            {
                editor.select();
            }
            this.editors.push(editor);
        }
        table.appendChild(tbody);
        parent.insertBefore(table, insertBefore);
    },
    isSelected : function(dn)
    {
        return this.selectedNodes.indexOf(dn) >= 0;
    },
    getNodes : function()
    {
        var ret = [];
        var nl = this.dn.childNodes;
        for (var i = 0; i < nl.length; i ++)
        {
            var dn = nl[i];
            if (dn.nodeType == 1)
            {
                ret.push(dn);
            }
        }
        return ret;
    },
    setNodes : function(nl)
    {
        var ret = [];
        XMLUtil.removeChildren(this.dn);
        for (var i = 0; i < nl.length; i ++)
        {
            this.dn.appendChild(nl[i]);
        }
        this.designer.updateDocument();
        this.updateDisplay();
    },
    updateDisplay : function()
    {
        this.designer.setActiveTab(this);
    },
    refresh : function()
    {
        for (var i = 0; i < this.editors.length; i ++)
        {
            var editor = this.editors[i];
            if (editor.refresh)
            {
                editor.refresh();
            }
        }
    },
    moveUp : function()
    {
        var rgDn = this.getNodes();
        var iLastUnselected = -1;
        for (i = 0; i < rgDn.length; i ++)
        {
            var dn = rgDn[i];
            if (this.isSelected(dn))
            {
                if (iLastUnselected != -1)
                {
                    rgDn.splice(i, 1);
                    rgDn.splice(iLastUnselected, 0, dn);
                    iLastUnselected ++;
                }
            }
            else
            {
                iLastUnselected = i;
            }
        }
        this.setNodes(rgDn);
    },
    moveDown : function()
    {
        var rgDn = this.getNodes();
        var iLastUnselected = -1;
        for (var i = rgDn.length - 1; i >= 0; i --)
        {
            var dn = rgDn[i];
            if (this.isSelected(dn))
            {
                if (iLastUnselected != -1)
                {
                    rgDn.splice(i, 1);
                    rgDn.splice(iLastUnselected, 0, dn);
                    iLastUnselected --;
                }
            }
            else
            {
                iLastUnselected = i;
            }
        }
        this.setNodes(rgDn);
    },
    remove : function()
    {
        var rgDn = this.getNodes();
        var iFirstSel = -1;
        var rgFields = [];
        for (var i = rgDn.length - 1; i >= 0; i --)
        {
            if (this.isSelected(rgDn[i]))
            {
                var field = this.fieldFromNode(rgDn[i]);
                if (field)
                    rgFields.push(field.toString());
                if (iFirstSel == -1)
                    iFirstSel = i;
                rgDn.splice(i, 1);
            }
        }
        this.setNodes(rgDn);
        if (rgDn.length > 0)
        {
            if (iFirstSel <= 0)
            {
                this.setSelection([rgDn[0]]);
            }
            else
            {
                this.setSelection([rgDn[iFirstSel - 1]]);
            }
        }

        var selectedFields = this.designer.getSelectedFields();
        for (var i = 0; i < rgFields.length; i ++)
        {
            this.designer.columnPicker.setFieldSelected(rgFields[i], selectedFields[rgFields[i]]);
        }
    },
    addNodes : function(newNodes)
    {
        var rgDn = this.getNodes();
        var iInsert = rgDn.length;
        for (var i = 0; i < rgDn.length; i ++)
        {
            if (this.isSelected(rgDn[i]))
            {
                iInsert = i + 1;
            }
        }

        rgDn = rgDn.slice(0, iInsert).concat(newNodes, rgDn.slice(iInsert, rgDn.length));
        this.setNodes(rgDn);
        this.setSelection([newNodes[newNodes.length - 1]]);
    },
    addNode : function(newNode)
    {
        if (!newNode)
            return;
        return this.addNodes([newNode]);
    },
    addFields : function(fields)
    {
        var existing = {};
        if (!this.allowDuplicateFields)
        {
            var nodes = this.getNodes();
            for (var i = 0; i < nodes.length; i ++)
            {
                existing[this.fieldFromNode(nodes[i]).toString()] = true;
            }
        }
        var nodes = [];
        for (var i = 0; i < fields.length; i ++)
        {
            if (existing[fields[i]])
            {
                continue;
            }
            var key = LABKEY.FieldKey.fromString(fields[i]);
            var node = this.createNodeForField(key, this.designer.columnPicker.fields[key.toString()]);
            if (node)
            {
                nodes.push(node);
            }
        }
        this.addNodes(nodes);
        var selectedFields = this.designer.getSelectedFields();
        for (var i = 0; i < fields.length; i ++)
        {
            this.designer.columnPicker.setFieldSelected(fields[i], selectedFields[fields[i]])
        }
    },
    insertSQL : function(sql)
    {
        this.addNode(this.createNodeForSQL(sql));
    },
    doubleClickField : function(field)
    {
        this.addFields([field]);
    },
    select : function(event, dn)
    {
        if (!event)
            event = window.event;
        if (event.ctrlKey || event.metaKey)
        {
            var idx = this.selectedNodes.indexOf(dn);
            if (idx < 0)
            {
                this.setSelection(this.selectedNodes.concat(dn));
            }
            else
            {
                var newSel = this.selectedNodes.concat();
                newSel.splice(idx, 1);
                this.setSelection(newSel);
            }
            return;
        }
        if (event.shiftKey && this.selectedNodes.length)
        {
            var idx1 = -1;
            var idx2 = -1;
            for (var i = 0; i < this.editors.length; i ++)
            {
                if (this.editors[i].dn == this.selectedNodes[0])
                {
                    idx1 = i;
                }
                if (this.editors[i].dn == dn)
                {
                    idx2 = i;
                }
            }
            if (idx1 >= 0 && idx2 >= 0)
            {
                var idxMin, idxMax;
                if (idx1 > idx2)
                {
                    idxMin = idx2;
                    idxMax = idx1;
                }
                else
                {
                    idxMin = idx1;
                    idxMax = idx2;
                }
                var newSel = [];
                for (var i = idxMin; i <= idxMax; i ++)
                {
                    newSel.push(this.editors[i].dn);
                }
                this.setSelection(newSel);
                return;
            }

        }
        this.setSelection([dn]);
    },
    setSelection : function(selectedNodes)
    {
        this.selectedNodes = selectedNodes;
        var selectedEditors = [];
        for (var i = 0; i < this.editors.length; i ++)
        {
            var editor = this.editors[i];
            if (this.isSelected(editor.dn))
            {
                editor.select();
                selectedEditors.push(editor);
            }
            else
            {
                editor.deselect();
            }
        }
    }
}

function Bind_Op(designer, dn, tdValue)
{
    var ret = new Bind_Child(designer, dn, 'op', nsQuery);
    var setValueOld = ret.setValue;
    ret.setValue = function(value)
    {
        setValueOld.call(this, value);
        if (value == '' || value == 'isblank' || value == 'isnonblank' || value == 'hasqcvalue' || value == 'noqcvalue')
        {
            tdValue.style.display = "none";
        }
        else
        {
            tdValue.style.display = '';
        }
        this.designer.updateDocument();
    }
    return ret;
}

function createFilterEditor(tab, dn, parent, insertBefore)
{
    var doc = parent.ownerDocument;
    var trTitle = doc.createElement('tr');
    trTitle.className = 'labkey-wp-header';
    var tdTitle = doc.createElement('td');
    tdTitle.colSpan = 3;
    if (parent.getElementsByTagName('TR').length == 0)
    {
        XMLUtil.setInnerText(tdTitle, 'Show Records Where');
    }
    else
    {
        XMLUtil.setInnerText(tdTitle, 'And');
    }
    trTitle.appendChild(tdTitle);
    parent.insertBefore(trTitle, insertBefore);

    var tr = doc.createElement('tr');
    if (XMLUtil.tagMatches(dn, 'compare', nsQuery))
    {
        var tdField = doc.createElement('td');
        var tdOp = doc.createElement('td');
        var tdValue = doc.createElement('td');

        var field = tab.fieldFromNode(dn);
        var strField = field.toString();
        var label = field.toDisplayString();
        if (tab.designer.fieldInfo)
        {
            var oField = tab.designer.fieldInfo(strField);
            label = oField.label;
        }
        tdField.appendChild(doc.createTextNode(label));
        tdField.title = field.toDisplayString();
        var rgFilterOps = tab.designer.rgFilterOps(strField);
        var rgFilterDisplay = tab.designer.rgFilterDisplay(strField);
       
        var bindOp = new Bind_Op(tab.designer, dn, tdValue);
        bindOp.setValue(bindOp.getValue())
        var select = new CTL_Select(tab.designer, bindOp, rgFilterOps);
        select.rgDisplay = rgFilterDisplay;
        select.createHTMLElements(tdOp, null);
        new CTL_TextBox(tab.designer, new Bind_Child(tab.designer, dn, 'literal', nsQuery)).createHTMLElements(tdValue, null);
        tr.appendChild(tdField);
        tr.appendChild(tdOp);
        tr.appendChild(tdValue);
    }
    else
    {
        var bindSQL = new Bind_Text(tab.designer, dn);
        var td = document.createElement('td');
        td.colSpan = 3;
        new SQLBuilder(tab.designer, 'Edit SQL Clause', bindSQL).createHTMLElements(td, null);
        var textbox = new CTL_TextBox(tab.designer, bindSQL);
        textbox.readOnly = true;
        textbox.createHTMLElements(td, null);
        tr.appendChild(td);
    }

    parent.insertBefore(tr, insertBefore);
    tr.onclick = function(event)
    {
        tab.select(event, dn);
    }
    return {
        dn: dn,
        select : function()
        {
            tr.style.backgroundColor = "silver";
            //tdField.style.borderColor = "black";
            //tdDir.style.borderColor = "black";
        },
        deselect : function()
        {
            tr.style.backgroundColor = "white";
            //tdField.style.borderColor = "white";
            //tdDir.style.borderColor = "white";
        }
    }
}

function FilterTab(designer, dn)
{
    var ret = new Tab(designer, dn);
    ret.allowDuplicateFields = true;
    ret.createNodeForField = function(key, field)
    {
        var doc = dn.ownerDocument;
        var ret = XMLUtil.createElement(doc, 'compare', nsQuery);
        var dnField = XMLUtil.createElement(doc, 'field', nsQuery);
        XMLUtil.setInnerText(dnField, key.toString());
        ret.appendChild(dnField);
        if (field && field.datatype)
        {
            var dnDatatype = XMLUtil.createElement(doc, 'datatype', nsQuery);
            XMLUtil.setInnerText(dnDatatype, field.datatype);
            ret.appendChild(dnDatatype);
        }
        return ret;
    };
    ret.createNodeForSQL = function(strSQL)
    {
        var doc = dn.ownerDocument;
        var ret = XMLUtil.createElement(doc, 'sql', nsQuery);
        XMLUtil.setInnerText(ret, strSQL);
        return ret;
    };
    ret.createEditor = function(dn, parent, insertBefore)
    {
        if (designer.filterTabActivated)
            designer.filterTabActivated();
        return createFilterEditor(this, dn, parent, insertBefore);
    }
    ret.fieldFromNode = function(dn)
    {
        if (!XMLUtil.tagMatches(dn, 'compare', nsQuery))
            return null;
        var dnField = XMLUtil.getChildWithTagName(dn, 'field', nsQuery);
        return LABKEY.FieldKey.fromString(XMLUtil.getInnerText(dnField));
    }
    ret.elTab = document.getElementById('filter.tab');
    ret.elList = document.getElementById('filter.list.div');
    ret.rgEl = [document.getElementById('filter.list'), document.getElementById('filter.controls')];

    return ret;
}

function createSortEditor(tab, dn, parent, insertBefore)
{
    var doc = parent.ownerDocument;
    var tr = doc.createElement('tr');
    var tdField = doc.createElement('td');
    var tdDir = doc.createElement('td');
    var ret = {
        dn: dn,
        select : function()
        {
            tr.style.backgroundColor = "silver";
        },
        deselect : function()
        {
            tr.style.backgroundColor = "white";
        }
    }
    tr.onclick = function(event)
    {
        tab.select(event, dn);
    }
    if (XMLUtil.tagMatches(dn, 'field', nsQuery))
    {
        var field = tab.fieldFromNode(dn);
        var label = field.toDisplayString();
        if (tab.designer.fieldInfo)
        {
            var oField = tab.designer.fieldInfo(field.toString());
            label = oField.label;
        }
        tdField.appendChild(doc.createTextNode(label));
        tdField.title = field.toDisplayString();
    }
    else
    {
        var bind = new Bind_Text(dn);
        new SQLBuilder(tab.designer, 'Edit Order By Clause', bind).createHTMLElements(tdField, null);
        var ctl = new CTL_TextBox(tab.designer, bind);
        ctl.readOnly = true;
        ctl.createHTMLElements(tdField, null);
    }
    new CTL_Select(tab.designer, new Bind_Attr(tab.designer, dn, 'dir'), ['ASC', 'DESC']).createHTMLElements(tdDir, null)
    tr.appendChild(tdField);
    tr.appendChild(tdDir);
    parent.insertBefore(tr, insertBefore);
    return ret;

}

function SortTab(designer, dn)
{
    var ret = new Tab(designer, dn);
    ret.allowDuplicateFields = false;
    ret.createNodeForField = function(field)
    {
        var doc = dn.ownerDocument;
        var ret = XMLUtil.createElement(doc, 'field', nsQuery);
        ret.setAttribute('dir', 'ASC');
        XMLUtil.setInnerText(ret, field.toString());
        return ret;
    }
    ret.fieldFromNode = function(dn)
    {
        if (!XMLUtil.tagMatches(dn, 'field', nsQuery))
            return null;
        return LABKEY.FieldKey.fromString(XMLUtil.getInnerText(dn));
    }
    ret.createNodeForSQL = function(strSQL)
    {
        var doc = this.dn.ownerDocument;
        var ret = XMLUtil.createElement(doc, 'sql', nsQuery);
        ret.setAttribute('dir', 'ASC');
        XMLUtil.setInnerText(ret, strSQL);
        return ret;
    }
    ret.createEditor = function(dn, parent, insertBefore)
    {
        return createSortEditor(this, dn, parent, insertBefore);
    }
    ret.elTab = document.getElementById('sort.tab');
    ret.elList = document.getElementById('sort.list.div');
    ret.rgEl = [document.getElementById('sort.list'), document.getElementById('sort.controls')];
    return ret;
}

function SQLBuilder(designer, title, value)
{
    return {
        createHTMLElements: function(parent, insertBefore)
        {
            var doc = parent.ownerDocument;
            var img = doc.createElement('img');
            img.src = LABKEY.contextPath + '/designerx/wand.gif';
            img.onclick = function()
            {
                showSQLEditor(designer, title, value);
            }
            parent.insertBefore(img, insertBefore);
        }
    }
}


function ColumnsTab(designer, dn)
{
    var ret = new Tab(designer, dn);
    ret.decideAlias = function(name)
    {
        var nodes = this.getNodes();
        var map = {};
        for (var i = 0; i < nodes.length; i ++)
        {
            map[getColumnAlias(nodes[i])] = true;
        }
        if (!map[name])
            return name;
        for (var i = 1; ; i ++)
        {
            if (!map[name + i])
                return name + i;
        }
    }
    ret.createNodeForField = function(field)
    {
        var doc = this.dn.ownerDocument;
        var ret = XMLUtil.createElement(doc, 'column', nsQuery);
        var dnValue = XMLUtil.createElement(doc, 'value', nsQuery);
        var dnField = XMLUtil.createElement(doc, 'field', nsQuery);
        var alias = this.decideAlias(field.name);
        if (alias != field.name)
        {
            ret.setAttribute('alias', alias);
        }
        XMLUtil.setInnerText(dnField, field.toString());
        dnValue.appendChild(dnField);
        ret.appendChild(dnValue);
        return ret;
    };
    ret.fieldFromNode = function(dn)
    {
        var dnValue = XMLUtil.getChildWithTagName(dn, "value", nsQuery);
        if (!dnValue)
        {
            return null;
        }
        var dnField = XMLUtil.getChildWithTagName(dnValue, "field", nsQuery);
        if (dnField)
        {
            return LABKEY.FieldKey.fromString(XMLUtil.getInnerText(dnField));
        }
        else
        {
            return null;
        }
    }
    ret.createNodeForSQL = function(strSQL)
    {
        var doc = this.dn.ownerDocument;
        var nodes = this.getNodes();
        var ret = XMLUtil.createElement(doc, 'column', nsQuery);
        ret.setAttribute('alias', this.decideAlias('expr'));
        var value = XMLUtil.createElement(doc, 'value', nsQuery);
        ret.appendChild(value);
        var sql = XMLUtil.createElement(doc, 'sql', nsQuery);
        XMLUtil.setInnerText(sql, strSQL);
        value.appendChild(sql);
        return ret;
    }
    ret.createEditor = function(dn, parent, insertBefore)
    {
        if (!XMLUtil.tagMatches(dn, 'column', nsQuery))
        {
            return null;
        }
        return createColumnEditor(this, dn, parent, insertBefore);
    }
    ret.getPropertySheet = function(dn)
    {
        var properties = [];
        properties.push(new PS_PropertyBox('Column Alias', new CTL_TextBox(this.designer, new Bind_Alias(this.designer, dn))));
        properties.push(new PS_PropertyBox('Column Caption', new CTL_TextBox(this.designer, new Bind_Metadata(this.designer, dn, 'columnTitle'))));
        var dnValue = XMLUtil.getChildWithTagName(dn, 'value', nsQuery);
        if (dnValue)
        {
            var dnSql = XMLUtil.getChildWithTagName(dnValue, 'sql', nsQuery);
            if (dnSql)
            {
                var value = new Bind_Child(designer, dnValue, 'sql', nsQuery);
                var ctl = new CTL_TextBox(this.designer, value);
                ctl.readOnly = true;
                var pb = new PS_PropertyBox('SQL Expression', ctl);
                pb.builder = new SQLBuilder(designer, "SQL Expression Editor", value);
                properties.push(pb);
            }
            var dnField = XMLUtil.getChildWithTagName(dnValue, 'field', nsQuery);
            if (dnField)
            {
                var value = new Bind_FieldKey(designer, dnField);
                var ctl = new CTL_TextBox(this.designer, value);
                ctl.readOnly = true;
                var pb = new PS_PropertyBox('Field', ctl);
                properties.push(pb);
            }
        }
        return new PropertySheet('Properties', properties);
    }
    var setSelectionOld = ret.setSelection;
    ret.setSelection = function(selectedNodes)
    {
        setSelectionOld.call(this, selectedNodes);
        var elPropertySheet = document.getElementById("columns.properties.div");
        if (elPropertySheet)
        {
            XMLUtil.removeChildren(elPropertySheet);
            if (selectedNodes.length == 1)
            {
                var ps = this.getPropertySheet(selectedNodes[0]);
                if (ps)
                {
                    ps.createHTMLElements(elPropertySheet, null);
                }
            }
        }
    }
    ret.elTab = document.getElementById("columns.tab");
    ret.elList = document.getElementById('columns.list.div');
    ret.rgEl = [document.getElementById('columns.list'), document.getElementById('columns.controls'), document.getElementById('columns.properties')];
    return ret;
}

function SqlTab(designer)
{
    var ret = {
        elEditor :document.getElementById('sql.editor.value'),
        activate : function()
        {
            document.getElementById('sql.editor').style.display = '';
            this.elEditor.value = this.value.getValue();
            XMLUtil.setInnerText(document.getElementById('sql.editor.title'), this.title);
            var tdErrors = document.getElementById('sql.editor.errors');
            XMLUtil.removeChildren(tdErrors);
            this.elEditor.focus();
        },
        deactivate : function()
        {
            document.getElementById('sql.editor').style.display = 'none';
        },
        save : function()
        {
            var errors = designer.checkSyntax(document.getElementById('sql.editor.value').value);
            if (errors.length == 0)
            {
                this.value.setValue(this.elEditor.value);
                designer.setActiveTab(this.returnTab);
                return;
            }
            var tdErrors = document.getElementById('sql.editor.errors');
            XMLUtil.removeChildren(tdErrors);
            for (var i = 0; i < errors.length; i ++)
            {
                var p = document.createElement('p');
                p.style.color = 'red';
                XMLUtil.setInnerText(p, errors[i]);
                tdErrors.appendChild(p);
            }
        },
        cancel : function()
        {
            designer.setActiveTab(this.returnTab);
            return;
        },
        addFields: function(fields)
        {
            if (fields.length != 1)
            {
                return;
            }
            if (this.elEditor)
            {
                var str = LABKEY.FieldKey.fromString(fields[0]).toSQLString();
                if (this.elEditor.selectionStart || this.elEditor.selectionStart == '0')
                {
                    var startPos = this.elEditor.selectionStart;
                    var endPos = this.elEditor.selectionEnd;
                    this.elEditor.value = this.elEditor.value.substring(0, startPos) + str
                            + this.elEditor.value.substring(endPos, this.elEditor.value.length);
                    this.elEditor.selectionStart = this.elEditor.selectionEnd = startPos + str.length;
                }
                else
                {
                    this.elEditor.value += str;
                }
            }

        },
        doubleClickField: function(field)
        {
            return this.addFields([field]);
        },

        designer: designer
    }
    return ret;
}

function showSQLEditor(designer, title, value)
{
    var tab = designer.tabs.sql;
    tab.returnTab = designer.activeTab;
    tab.title = title;
    tab.value = value;
    designer.setActiveTab(tab);
}

function insertSQLExpression(designer, title)
{
    var tab = designer.activeTab;
    var value = {
            getValue : function()
            {
                return '';
            },
            setValue : function(str)
            {
                if (str)
                {
                    tab.insertSQL(str);
                }
            }
    }
    showSQLEditor(designer, title, value);
}

function QueryOrViewDesigner(tableInfoService)
{
    this.tabs = {};
    this.columnPicker = new ColumnPicker(tableInfoService);
    var designer = this;
    this.defaultTab = 'columns';
    this.columnPicker.getSelectedFields = function()
    {
        return designer.getSelectedFields();
    }
    this.columnPicker.doubleClickField = function(field)
    {
        designer.activeTab.doubleClickField(field);
    }
    this.tableInfoService = tableInfoService;

    this.needToPrompt = false;

    function beforeUnload()
    {
        if (this.needToPrompt && document.getElementById("ff_dirty").value == "true")
            return "Your changes have not been saved.";
    }

    this.init = function ()
    {
        Ext.EventManager.on(window, 'beforeunload', beforeUnload);
        this.setDesignDocument(XMLUtil.loadXML(document.getElementById("ff_designXML").value));
        if (window.designerInitCallback)
            window.designerInitCallback();
    };

    this.uninit = function ()
    {
        Ext.EventManager.on(window, 'beforeunload', beforeUnload);
    };
}

QueryOrViewDesigner.prototype =
{
    setDesignDocument : function(designDoc)
    {
        this.designDoc = designDoc;
        var root = designDoc.documentElement;
        this.tabs.columns = new ColumnsTab(this, XMLUtil.getChildWithTagName(root, 'select', nsQuery));
        this.tabs.columns.allowDuplicateFields = this.allowDuplicateColumns;
        this.tabs.filter = new FilterTab(this, XMLUtil.getChildWithTagName(root, 'where', nsQuery));
        this.tabs.sort = new SortTab(this, XMLUtil.getChildWithTagName(root, 'orderBy', nsQuery));
        this.tabs.sql = new SqlTab(this);
        this.initColumnPicker();
        if (this.defaultTab == 'filter')
        {
            this.setActiveTab(this.tabs.filter);
        }
// see issue #9091
//        else if (this.defaultTab == 'sort')
//        {
//            this.setActiveTab(this.tabs.sort);
//        }
        else
        {
            this.setActiveTab(this.tabs.columns);
        }
    },
    setActiveTab : function(newTab)
    {
        var oldTab = this.activeTab;
        newTab.activate();
        this.activeTab = newTab;
        if (oldTab && oldTab != newTab)
        {
            oldTab.deactivate();
        }
    },
    getSelectedFields : function()
    {
        var ret = {};
        var dnSelect = XMLUtil.getChildWithTagName(this.designDoc.documentElement, "select", nsQuery);
        var rgColumns = XMLUtil.getChildrenWithTagName(dnSelect, "column", nsQuery);
        for (var i = 0; i < rgColumns.length; i ++)
        {
            var dnColumn = rgColumns[i];
            var dnValue = XMLUtil.getChildWithTagName(dnColumn, "value", nsQuery);
            if (!dnValue)
            {
                continue;
            }
            var dnField = XMLUtil.getChildWithTagName(dnValue, "field", nsQuery);
            if (!dnField)
                continue;
            ret[XMLUtil.getInnerText(dnField)] = true;
        }
        return ret;
    },
    add : function()
    {
        this.activeTab.addFields(this.columnPicker.getFocusedFields());
    },
    moveUp : function()
    {
        this.activeTab.moveUp();
    },
    moveDown : function()
    {
        this.activeTab.moveDown();
    },
    remove : function()
    {
        this.activeTab.remove();
    },
    updateDocument : function ()
    {
        var newDoc = XMLUtil.serializeXML(this.designDoc);
        if (document.getElementById("ff_designXML").value != newDoc)
        {
            document.getElementById("ff_designXML").value = newDoc;
            document.getElementById("ff_dirty").value = "true";
        }
    },
    validateFilter : function(field, value, comparison)
    {
        var oField = this.tableInfoService.getOutputColumnInfos([field])[field];
        if (!oField)
            return true;
        var mappedType = _typeMap[oField.datatype.toUpperCase()];
        if (!mappedType)
            return true;
        if (comparison == 'in')
        {
            return validateMultiple(value, mappedType, field);
        }
        else
        {
            return validate(value, mappedType, field);
        }
    },
    validate : function ()
    {
        // check at least one column is selected
        var selected = this.getSelectedFields();
        var hasSelection = false;
        for (var x in selected)
        {
            hasSelection = true;
            break;
        }

        if (!hasSelection)
        {
            alert("You must select at least one field to display in the grid.");
            return false;
        }

        // check for invalid filter values
        var dnWhere = XMLUtil.getChildWithTagName(this.designDoc.documentElement, "where", nsQuery);
        var rgCompare = XMLUtil.getChildrenWithTagName(dnWhere, "compare", nsQuery);
        for (var i = 0; i < rgCompare.length; i ++)
        {
            var dnCompare = rgCompare[i];
            var dnField = XMLUtil.getChildWithTagName(dnCompare, "field", nsQuery);
            if (!dnField)
                continue;

            var dnOp = XMLUtil.getChildWithTagName(dnCompare, "op", nsQuery);
            if (!dnOp)
                continue;
            var strOp = XMLUtil.getInnerText(dnOp);
            if (strOp == "" || strOp == "isblank" || strOp == "isnonblank" || strOp == "hasqcvalue" || strOp == "noqcvalue")
                continue;
            
            var dnValue = XMLUtil.getChildWithTagName(dnCompare, "literal", nsQuery);
            if (!dnValue)
                continue;

            if (!this.validateFilter(XMLUtil.getInnerText(dnField), XMLUtil.getInnerText(dnValue), strOp))
                return false;
        }
        return true;
    }
};

function expandColumnScript(columnPicker, key)
{
    return function(event)
    {
        if (!event)
            event = window.event;
        columnPicker.expandColumn(key, 0);
        event.cancelBubble = true;
        event.returnValue = false;
        return false;
    }
}

function QueryDesigner(urlCheckSyntax, tableInfoService)
{
    var ret = new QueryOrViewDesigner(tableInfoService);
    ret.urlCheckSyntax = urlCheckSyntax;
    ret.setPropertySheet = function(ps)
    {
        var el = document.getElementById("properties");
        XMLUtil.removeChildren(el);
        ps.createHTMLElements(el, null);
    }
    ret.checkSyntax = function(sql)
    {
        var url = this.urlCheckSyntax + '&sql=' + urlEncode(sql);
        var xmlhttp;
        if (window.XMLHttpRequest)
            xmlhttp = new XMLHttpRequest();
        else if (window.ActiveXObject)
            xmlhttp = new ActiveXObject("Microsoft.XMLHTTP");
        xmlhttp.open("GET", url, false);
        xmlhttp.send(null);
        result = xmlhttp.responseXML;
        var errors = XMLUtil.getChildrenWithTagName(result.documentElement, 'error', nsQuery);
        var ret = [];
        for (var i = 0; i < errors.length; i ++)
        {
            ret.push(XMLUtil.getInnerText(errors[i]));
        }
        return ret;
    }
    ret.insertSQL = function()
    {
        insertSQLExpression(this, 'New SQL Expression');        
    }
    ret.initColumnPicker = function()
    {
        var elColumnPicker = document.getElementById("columnPicker");
        var designer = this;
        XMLUtil.removeChildren(elColumnPicker);
        var dnFrom = XMLUtil.getChildWithTagName(this.designDoc.documentElement, 'from', nsQuery);
        var nlFrom = XMLUtil.getChildrenWithTagName(dnFrom, 'table', nsQuery);
        for (var i = 0; i < nlFrom.length; i ++)
        {
            var dnTable = nlFrom[i];
            var key = dnTable.getAttribute('alias');
            this.tableInfoService.processTable(key, XMLUtil.getChildWithTagName(dnTable, 'metadata', nsQuery));
            var tr = document.createElement('tr');
            var td = document.createElement('td');
            td.id = 'column_' + key;
            td.style.fontStyle = 'italic';
            var aExpand = document.createElement('a');
            aExpand.href = "#";
            var imgExpand = document.createElement('img');
            imgExpand.src = LABKEY.contextPath + '/_images/plus.gif';
            imgExpand.onclick = expandColumnScript(this.columnPicker, key);
            imgExpand.id = 'expand_' + key;
            imgExpand.style.cursor = 'pointer';
            aExpand.appendChild(imgExpand);
            td.appendChild(aExpand);
            td.appendChild(document.createTextNode('\u00A0' + key));
            tr.appendChild(td);
            elColumnPicker.appendChild(tr);
        }
    }
    ret.rgFilterOps = function(type)
            {
                return ['', 'eq', 'in', 'neq', 'isblank', 'isnonblank', 'gt', 'lt', 'gte', 'lte', 'startswith', 'doesnotstartwith', 'contains', 'doesnotcontain', 'dateeq', 'dateneq', 'hasqcvalue', 'noqcvalue'];
            }
    ret.rgFilterDisplay = function(type )
            {
                return ['Has Any Value', 'Equals', 'Equals One Of (example usage: a;b;c)', 'Does Not Equal', 'Is Blank', 'Is Not Blank', 'Is Greater Than', 'Is Less Than',
                        'Is Greater Than Or Equal To', 'Is Less Than Or Equal To', 'Starts With', 'Does Not Start With', 'Contains', 'Does Not Contain', 'Equals', 'Does Not Equal', 'Has a QC Value', 'Does not have a QC Value'];
            }
    ret.updateDocument = function ()
    {
        var newDoc = XMLUtil.serializeXML(this.designDoc);
        if (document.getElementById("ff_designXML").value != newDoc)
        {
            document.getElementById("ff_designXML").value = newDoc;
            document.getElementById("ff_dirty").value = "true";
        }
    };
    ret.columnPicker.setShowHiddenFields(true);
    ret.getColumnLabel = getColumnAlias;
    ret.getColumnTitle = function()
    {
        return null;
    }
//    ret.fieldInfo = function(field)
//    {
//        return tableInfoService.getOutputColumnInfos([field])[field];
//    }
    ret.getFieldKeyString = function(dn)
    {
        var dnValue = XMLUtil.getChildWithTagName(dn, 'value', nsQuery);
        var dnField = XMLUtil.getChildWithTagName(dnValue, 'field', nsQuery);
        return XMLUtil.getInnerText(dnField);
    };
    ret.allowDuplicateColumns = true;
    return ret;
}

function ViewDesigner(tableInfoService)
{
    var ret = new QueryOrViewDesigner(tableInfoService);

    function isBoolType(oField)
    {
        if (!oField)
            return false;
        var mappedType = _typeMap[oField.datatype.toUpperCase()];
        if (!mappedType)
            return false;
        return 'BOOL' == mappedType;
    }

    function isStringType(oField)
    {
        if (!oField)
            return true;
        var mappedType = _typeMap[oField.datatype.toUpperCase()];
        if (!mappedType)
            return true;
        return 'TEXT' == mappedType || 'LONGTEXT' == mappedType;
    }

    function isDateType(oField)
    {
        if (!oField)
            return false;
        var mappedType = _typeMap[oField.datatype.toUpperCase()];
        if (!mappedType)
            return false;
        return 'DATE' == mappedType;
    }

    ret.fieldInfo = function(field)
    {
        return tableInfoService.getOutputColumnInfos([field])[field];
    }

    ret.rgFilterOps = function(field)
    {
        var ret = ['']
        var oField = this.fieldInfo(field);
        if (!isDateType(oField))
        {
            ret = ret.concat(['eq']);
        }
        else
        {
            ret = ret.concat(['dateeq']);
        }
        if (!isBoolType(oField))
        {
            ret = ret.concat(['in']);
        }
        if (!isDateType(oField))
        {
            ret = ret.concat(['neqornull']);
        }
        else
        {
            ret = ret.concat(['dateneq']);
        }

        ret = ret.concat(['isblank', 'isnonblank']);
        if (!isBoolType(oField))
        {
            ret = ret.concat(['gt', 'lt', 'gte', 'lte']);
        }
        if (isStringType(oField))
        {
            ret = ret.concat(['startswith', 'doesnotstartwith', 'contains', 'doesnotcontain']);
        }
        return ret;
    };

    ret.rgFilterDisplay = function(field)
    {
        var ret = ['Has Any Value', 'Equals'];
        var oField = this.fieldInfo(field);
        if (!isBoolType(oField))
        {
            ret = ret.concat(['Equals One Of (example usage: a;b;c)']);
        }
        ret = ret.concat(['Does Not Equal', 'Is Blank', 'Is Not Blank']);
        if (!isBoolType(oField))
        {
            ret = ret.concat(['Is Greater Than', 'Is Less Than', 'Is Greater Than Or Equal To', 'Is Less Than Or Equal To']);
        }
        if (isStringType(oField))
        {
            ret = ret.concat(['Starts With', 'Does Not Start With', 'Contains', 'Does Not Contain']);
        }
        return ret;
    }

    ret.initColumnPicker = function()
    {
        var elColumnPicker = document.getElementById("columnPicker");
        var designer = this;
        XMLUtil.removeChildren(elColumnPicker);
        var dnFrom = XMLUtil.getChildWithTagName(this.designDoc.documentElement, 'from', nsQuery);
        var nlTables = XMLUtil.getChildrenWithTagName(dnFrom, 'table', nsQuery);
        var table = this.tableInfoService.processTable('', XMLUtil.getChildWithTagName(nlTables[0], 'metadata', nsQuery));
        this.tableInfoService.processOutputColumns(XMLUtil.getChildWithTagName(nlTables[1], 'metadata', nsQuery));
        var dnTable = XMLUtil.getChildWithTagName(dnFrom, 'table', nsQuery);
        this.columnPicker.addTable(elColumnPicker, table, 0);
    }
    ret.showColumnProperties = function()
    {
        var designer = this;
        if (this.tabs.columns.selectedNodes.length == 0)
        {
            alert("Select a field in the grid, and then push this button to change its caption.");
            return;
        }
        if (this.tabs.columns.selectedNodes.length > 1)
        {
            this.tabs.columns.setSelection([this.tabs.columns.selectedNodes[0]]);
        }
        var dn = this.tabs.columns.selectedNodes[0];
        var bind = new Bind_Metadata(this, dn, "columnTitle");
        var dnValue = XMLUtil.getChildWithTagName(dn, "value", nsQuery);
        if (!dnValue)
        {
            return;
        }
        var dnField = XMLUtil.getChildWithTagName(dnValue, "field", nsQuery);
        if (!dnField)
        {
            return;
        }
        var oField = this.fieldInfo(XMLUtil.getInnerText(dnField));
        if (oField)
        {
            bind.strDef = oField.label;
        }
        var title = "Set Caption For '" + LABKEY.FieldKey.fromString(XMLUtil.getInnerText(dnField)).toDisplayString() + "' Column";

        function onClose(buttonId, newValue)
        {
            if (buttonId == "ok")
            {
                bind.setValue(newValue);
                designer.tabs.columns.updateDisplay();
            }
        }
        Ext.MessageBox.prompt(title, "Column Caption:", onClose, window, false, bind.getValue());
    }
    ret.setShowHiddenFields = function(show)
    {
        this.columnPicker.setShowHiddenFields(show);        
    }
    ret.getFieldKeyString = function(dn)
    {
        var dnValue = XMLUtil.getChildWithTagName(dn, 'value', nsQuery);
        var dnField = XMLUtil.getChildWithTagName(dnValue, 'field', nsQuery);
        return XMLUtil.getInnerText(dnField);
    }
    ret.getColumnLabel = function(dn)
    {
        var label = new Bind_Metadata(ret, dn, 'columnTitle').getValue();
        if (label)
        {
            return label;
        }
        var field = this.getFieldKeyString(dn);
        var oField = this.fieldInfo(field);
        if (oField)
        {
            return oField.label;
        }
        return field;
    }
    ret.getColumnTitle = function(dn)
    {
        var field = this.getFieldKeyString(dn);
        return LABKEY.FieldKey.fromString(field).toDisplayString();
    }
    ret.filterTabActivated = function()
    {
        var elCbx = document.getElementById("ff_saveFilterCbx");
        if (elCbx)
            elCbx.checked = true;
    }
    ret.allowDuplicateColumns = false;

    return ret;
}

function Bind_Alias(designer, dn)
{
    this.designer = designer;
    this.dn = dn;
}

Bind_Alias.prototype = {
    getValue : function()
    {
        var ret = this.dn.getAttribute("alias");
        if (!ret)
            return '';
        return ret;
    },
    setValue : function(value)
    {
        if (this.getValue() == value)
            return;
        if (!value)
        {
            this.dn.removeAttribute("alias");
        }
        else
        {
            this.dn.setAttribute("alias", value);
        }
        this.designer.updateDocument();
        this.designer.tabs.columns.refresh();
    }
};

function Bind_FieldKey(designer, dn)
{
    this.designer = designer;
    this.dn = dn;
}
Bind_FieldKey.prototype =
{
    getValue : function()
    {
        return LABKEY.FieldKey.fromString(XMLUtil.getInnerText(this.dn)).toDisplayString();
    },
    setValue : function()
    {

    }
};

