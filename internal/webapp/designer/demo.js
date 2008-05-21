/*
 * Copyright (c) 2006-2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
var ns1 = 'http://labkey.com/demo1';
var ns2 = 'http://labkey.com/demo2';
function DemoOnLoad()
{
    var xmlhttp;
    if (window.XMLHttpRequest)
        xmlhttp = new XMLHttpRequest();
    else if (window.ActiveXObject)
        xmlhttp = new ActiveXObject("Microsoft.XMLHTTP");
    xmlhttp.open("GET", "demo.xml", false);
    xmlhttp.send(null);
    window.designer = new DemoDesigner(xmlhttp.responseXML);
}

function DemoDesigner(doc)
{
    this.doc = doc;
    this.init();
}

DemoDesigner.prototype =
{
    updateDocument : function()
        {
            document.getElementById("docText").value = XMLUtil.serializeXML(this.doc);
        },

    init : function()
        {
            this.updateDocument();
            this.editors = [];
            var nl = this.doc.documentElement.childNodes;
            for (var i = 0; i < nl.length; i ++)
            {
                var child = nl.item(i);
                if (XMLUtil.tagMatches(child, "column", ns1))
                {
                    var editor = new ColumnEditor(this, child);
                    this.editors.push(editor);
                }
            }
            var table = document.createElement("table");
            var tbody = document.createElement("tbody");
            for (var i = 0; i < this.editors.length; i ++)
            {
                this.editors[i].createHTMLElements(tbody, null);
            }
            table.appendChild(tbody);
            document.getElementById("columns").appendChild(table);
        },
    setPropertySheet : function(ps)
        {
            var elProperties = document.getElementById("properties");
            XmlUtil.removeChildren(elProperties);
            ps.createHTMLElements(elProperties, null);
        }
}

function ColumnEditor(designer, dn)
{
    this.designer = designer;
    this.dn = dn;
}

ColumnEditor.prototype = {
        createHTMLElements : function(parent, insertBefore)
                {
                    var editor = this;
                    editor.tr = parent.ownerDocument.createElement("tr");
                    editor.td = parent.ownerDocument.createElement("td");
                    editor.td.onclick = function(event)
                            {
                                editor.designer.setPropertySheet(editor.getPropertySheet());
                            }
                    editor.td.appendChild(parent.ownerDocument.createTextNode("column"));
                    editor.tr.appendChild(editor.td);
                    parent.insertBefore(editor.tr, insertBefore);

                },
        getPropertySheet : function()
                {
                    var properties = [];
                    properties.push(new PS_PropertyBox('child1', new CTL_TextBox(this.designer, new Bind_Child(this.designer, this.dn, 'child1', ns1))));
                    properties.push(new PS_PropertyBox('attr1', new CTL_TextBox(this.designer, new Bind_Attr(this.designer, this.dn, 'attr1'))));
                    return new PropertySheet('Column Editor', properties);
                }
}