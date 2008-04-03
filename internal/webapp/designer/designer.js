if (!Array.prototype.indexOf)
	Array.prototype.indexOf = function(item, startIndex) {
		var len = this.length;
		if (startIndex == null)
			startIndex = 0;
		else if (startIndex < 0) {
			startIndex += len;
			if (startIndex < 0)
				startIndex = 0;
		}
		for (var i = startIndex; i < len; i++) {
			var val = this[i];
			if (val == item)
				return i;
		}
		return -1;
	};

if (!Array.prototype.lastIndexOf)
	Array.prototype.lastIndexOf = function(item, startIndex) {
		var len = this.length;
		if (startIndex == null || startIndex >= len)
			startIndex = len - 1;
		else if (startIndex < 0)
			startIndex += len;
		for (var i = startIndex; i >= 0; i--) {
			var val = this[i];
			if (val == item)
				return i;
		}
		return -1;
	};

function urlEncode(s)
{
    s = escape(s);
    s = s.replace(/\+/g, '%2B');
    return s;
}


var XMLUtil = {
    getChildrenWithTagName : function(dn, tagName, ns)
    {
        var ret = [];
        for (var i = 0; i < dn.childNodes.length; i ++)
        {
            var child = dn.childNodes.item(i);
            if (XMLUtil.tagMatches(child, tagName, ns))
            {
                ret[ret.length] = child;
            }
        }
        return ret;
    },
    createElement : function(doc, tagName, ns)
    {
        if (ns)
        {
            if (doc.createElementNS)
            {
                return doc.createElementNS(ns, tagName);
            }
            return doc.createNode(1, tagName, ns);
        }
        return doc.createElement(tagName);
    },
    tagMatches : function(dn, tagName, ns)
    {
        if (!dn.tagName)
            return false;
        if (!ns)
        {
            return dn.tagName == tagName;
        }
        var localName = dn.tagName;
        var idxColon = localName.indexOf(':');
        if (idxColon >= 0)
        {
            localName = localName.substring(idxColon + 1);
        }

        return localName == tagName && dn.namespaceURI == ns;
    },
    getChildWithTagName : function(dn, tagName, ns)
    {
        for (var i = 0; i < dn.childNodes.length; i ++)
        {
            var child = dn.childNodes.item(i);
            if (XMLUtil.tagMatches(child, tagName, ns))
                return child;
        }
        return null;
    },
    getInnerText :function(node)
    {
        var ret = '';
        for (var i = 0; i < node.childNodes.length; i ++)
        {
            var child = node.childNodes.item(i);
            ret += child.nodeValue;
        }
        return ret;
    },

    setInnerText : function(node, value)
    {
        for (var i = node.childNodes.length - 1; i >= 0; i --)
        {
            node.removeChild(node.childNodes.item(i));
        }
        if (value)
        {
            node.appendChild(node.ownerDocument.createTextNode(value));
        }
    },
    serializeXML : function(doc)
    {
        if (typeof XMLSerializer != "undefined")
        {
            return (new XMLSerializer()).serializeToString(doc);
        }
        return doc.xml;
    },
    removeChildren : function(el)
    {
        while (el.childNodes.length)
        {
            el.removeChild(el.childNodes.item(0));
        }
    },
    loadXML : function(text)
    {
        if (typeof DOMParser != "undefined") {
            // Mozilla, Firefox, and related browsers
            return (new DOMParser()).parseFromString(text, "application/xml");
        }
        else if (typeof ActiveXObject != "undefined") {
            // Internet Explorer.
            var doc = new ActiveXObject("Microsoft.XMLDOM");  // Create an empty document
            doc.loadXML(text);            // Parse text into it
            return doc;                   // Return it
        }
        else {
            // As a last resort, try loading the document from a data: URL
            // This is supposed to work in Safari. Thanks to Manos Batsis and
            // his Sarissa library (sarissa.sourceforge.net) for this technique.
            var url = "data:text/xml;charset=utf-8," + encodeURIComponent(text);
            var request = new XMLHttpRequest();
            request.open("GET", url, false);
            request.send(null);
            return request.responseXML;
        }

    }
}

function Bind_Attr(designer, dn, attr)
{
    this.designer = designer;
    this.dn = dn;
    this.attr = attr;
}
Bind_Attr.prototype = {
    strDef : '',
    getValue : function()
    {
        var ret = this.dn.getAttribute(this.attr);
        if (!ret)
            return this.strDef;
        return ret;
    },
    setValue : function(str)
    {
        if (str != this.getValue())
        {
            if (str)
            {
                this.dn.setAttribute(this.attr, str);
            }
            else
            {
                this.dn.removeAttribute(this.attr);
            }
        }
        this.designer.updateDocument();
    }
};

function Bind_Text(designer, dn)
{
    this.designer = designer;
    this.dn = dn;
}

Bind_Text.prototype = {
        getValue: function()
        {
            return XMLUtil.getInnerText(this.dn);
        },
        setValue : function(value)
                {
                    if (value == this.getValue())
                        return;
                    XMLUtil.setInnerText(this.dn, value);
                    this.designer.updateDocument();
                }
}

function Bind_Child(designer, dn, tagName, ns)
{
    this.designer = designer;
    this.dn = dn;
    this.tagName = tagName;
    this.ns = ns;
    this.fDeleteIfEmpty = true;
}



Bind_Child.prototype = {
    getValue : function()
    {
        var child = XMLUtil.getChildWithTagName(this.dn, this.tagName, this.ns);
        if (!child)
            return '';
        return XMLUtil.getInnerText(child);
    },
    setValue : function(str)
    {
        if (str == this.getValue())
            return;
        var dnChild = XMLUtil.getChildWithTagName(this.dn, this.tagName, this.ns);
        if (!dnChild && str)
        {
            dnChild = XMLUtil.createElement(this.dn.ownerDocument, this.tagName, this.ns);
            this.dn.appendChild(dnChild);
        }
        if (dnChild && this.fDeleteIfEmpty && !str)
        {
            this.dn.removeChild(dnChild);
            dnChild = null;
        }
        if (str && dnChild)
        {
            XMLUtil.setInnerText(dnChild, str);
        }
        this.designer.updateDocument();
    }
};

function CTL_TextBox(designer, value)
{
    this.designer = designer;
    this.value = value;
}

function CTL_Label(designer, value)
{
    this.designer = designer;
    this.value = value;
}

function EventHandler(obj, method, el)
{
    return function(event)
    {
        if (!event)
        {
            event = window.event;
        }
        return method.call(obj, event, el);
    }
}


CTL_TextBox.prototype = {
    createHTMLElements: function(parent, insertBefore)
    {
        var ctl = this;
        var textbox = parent.ownerDocument.createElement("input");
        if (this.readOnly)
        {
            textbox.readOnly = true;
        }
        textbox.type = "text";
        textbox.value = this.value.getValue();
        textbox.onblur =
        function (event)
        {
            ctl.value.setValue(textbox.value);
        }
        parent.insertBefore(textbox, insertBefore);
        return textbox;
    }
}

function CTL_Select(designer, value, rgValues)
{
    this.designer = designer;
    this.value = value;
    this.rgValues = rgValues;
}

CTL_Select.prototype =
{
    createHTMLElements : function(parent, insertBefore)
    {
        var ctl = this;
        var select = parent.ownerDocument.createElement('select');
        select.onchange = function(event)
        {
            ctl.value.setValue(select.options[select.selectedIndex].value);
        }
        var selectedIndex = this.rgValues.indexOf(this.value.getValue());
        if (selectedIndex == -1)
        {
            select.options[0] = new Option(this.value.getValue(), this.value.getValue(), false, true);
            selectedIndex = 0;
        }
        for (var i = 0; i < this.rgValues.length; i ++)
        {
            var value = this.rgValues[i];
            var display = this.rgDisplay ? this.rgDisplay[i] : this.rgValues[i];
            select.options[select.options.length] = new Option(display, value, false, value == this.value.getValue());
        }
        select.selectedIndex = selectedIndex;
        parent.insertBefore(select, insertBefore);
    }
}

function PropertySheet(title, children)
{
    this.title = title;
    this.children = children;
}

PropertySheet.prototype = {
        createHTMLElements : function(parent, insertBefore)
                {
                    var htmlDoc = parent.ownerDocument;
                    var ret = htmlDoc.createElement("table");
                    var tbody = htmlDoc.createElement("tbody");
                    var trTitle = htmlDoc.createElement("tr");
                    var tdTitle = htmlDoc.createElement("td");
                    tdTitle.appendChild(htmlDoc.createTextNode(this.title));
                    tdTitle.colspan = 2;
                    trTitle.appendChild(tdTitle);
                    tbody.appendChild(trTitle);
                    for (var i = 0; i < this.children.length; i ++)
                    {
                        this.children[i].createHTMLElements(tbody, null);
                    }
                    ret.appendChild(tbody);
                    parent.insertBefore(ret, insertBefore);
                }
}

function PS_PropertyBox(title, input)
{
    this.title = title;
    this.input = input;
}

PS_PropertyBox.prototype = {
        createHTMLElements : function(parent, insertBefore)
                {
                    var htmlDoc = parent.ownerDocument;
                    var trTitle = htmlDoc.createElement("tr");
                    var tdTitle = htmlDoc.createElement("td");
                    tdTitle.colSpan = 2;
                    tdTitle.appendChild(htmlDoc.createTextNode(this.title));
                    trTitle.appendChild(tdTitle);
                    parent.insertBefore(trTitle, insertBefore);
                    var trInput = htmlDoc.createElement("tr");
                    var tdBuilder = htmlDoc.createElement("td");
                    trInput.appendChild(tdBuilder);
                    if (this.builder)
                    {
                        this.builder.createHTMLElements(tdBuilder, null);
                    }
                    var tdInput = htmlDoc.createElement("td");
                    trInput.appendChild(tdInput);
                    this.input.createHTMLElements(tdInput, null);
                    parent.insertBefore(trInput, insertBefore);
                }
}


function rememberSelection(el)
{
    if (document.selection)
    {
        el.trSave = document.selection.createRange();
    }
}