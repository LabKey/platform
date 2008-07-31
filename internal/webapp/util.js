/*
 * Copyright (c) 2006-2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
function setAllCheckboxes(form, value, elementName)
{
    var elems = form.elements;
    var l = elems.length;
    var ids = [];
    for (var i = 0; i < l; i++)
    {
        var e = elems[i];
        if (e.type == 'checkbox' && !e.disabled && (elementName == null || elementName == e.name))
        {
            e.checked = value;
            if (e.name != ".toggle")
                ids.push(e.value);
        }
    }
    return ids;
}

function sendCheckboxes(el, key, value)
{
    var form = el;
    do
    {
        form = form.parentNode;
    } while (form.tagName != "FORM");

    sendCheckbox(el, key, setAllCheckboxes(form, value, '.select'), value);
    return false;
}

function sendCheckbox(el, key, ids, checked)
{
    if (!key || ids.length == 0)
        return;
    var path = window.location.pathname;
    var url = replaceAction(path, "setCheck.api") +
              "?key=" + key +
              "&checked=" + checked;
    for (var i = 0; i < ids.length; i++)
        url += "&id=" + ids[i];

    var xmlhttp = new XMLRequest(url);
    xmlhttp.get();
}

function selectNone(el, key)
{
    var path = window.location.pathname;
    var url = replaceAction(path, "selectNone.api") + "?key=" + key;
    var xmlhttp = new XMLRequest(url);
    xmlhttp.get();

    var form = el;
    do
    {
        form = form.parentNode;
    } while (form.tagName != "FORM");
    setAllCheckboxes(form, false);
}

function getChildWithClassName(root, tagName, className)
{
    if (!root) return undefined;
    var children = root.childNodes;
    for (var j = children.length-1; j >= 0; j--)
    {
        var child = children[j];
        if (child.tagName == tagName && child.className == className)
            return child;
    }
    return undefined;
}

function replaceAction(path, action)
{
    var i = path.lastIndexOf("/");
    if (i > 0)
        return path.substring(0, i+1) + action;
    return path + "/" + action;
}

function toggleLink(link, notify)
{
    collapseExpand(link, notify);
    var i = 0;
    while (typeof(link.childNodes[i].src) == "undefined" )
        i++;

    if (link.childNodes[i].src.search("plus.gif") >= 0)
        link.childNodes[i].src = link.childNodes[i].src.replace("plus.gif", "minus.gif");
    else
        link.childNodes[i].src = link.childNodes[i].src.replace("minus.gif", "plus.gif");
    return false;
}

function collapseExpand(elem, notify)
 {
     var collapse = false;
     var url = elem.href;
     while (elem.tagName != 'TR')
         elem = elem.parentNode;

     var nextRow = getNextRow(elem);
     if (null != nextRow && nextRow.style.display != "none")
        collapse = true;

    while (nextRow != null)
    {
         if (nextRow.className == "labkey-header")
            break;
        toggleDisplay(nextRow);
        nextRow = getNextRow(nextRow);
    }

     if (null != url && notify)
        notifyExpandCollapse(url, collapse);
     return false;
 }

function toggleDisplay(elem)
{
    if (elem.style.display != "none")
        elem.style.display = "none";
    else
       elem.style.display = "";
}

 function toggleTable (tocTable, expand, notify)
 {
    //Structure of a navtree table:
     //  Each row contains either a node in the tree, or the children of the node in the previous row
     //  For each, row we check the first TD for an anchor.  If it's there, this is a node row and
     //  we toggle it appropriately
     //  We then check the second TD for a table, if it's there we recurse
     //  Note taht some rows have neither (non-expandable nodes)
     
    if (tocTable)
    {
        if (0 == tocTable.childNodes.length)
            return false;

        var topics = tocTable.childNodes.item(0);
        while (topics && topics.nodeName != "TBODY")
           { topics = topics.nextSibling; }

        if (!topics)
            return false;

        for (var i = 0; i < topics.childNodes.length; i++)
        {
            var topic = topics.childNodes.item(i);
            if (topic.nodeName == "TR")
            {
                var firstTD = topic.childNodes.item(0);
                while (firstTD && firstTD.nodeName != "TD")
                    { firstTD = firstTD.nextSibling; }
                if (!firstTD) continue;
                
                var link = firstTD.childNodes.item(0);
                 while (link && link.nodeName != "A")
                    { link = link.nextSibling; }
                if (link != null)
                {
                    //First we need to get the current state by looking at the img
                    var img = link.childNodes.item(0);
                    var expanded = (img.src.indexOf("minus.gif") != -1) ? true : false;
                    //now if we are expanded and want to collapse, or are collapsed and want to expand, do it
                    if ( (expanded && !expand) || (!expanded && expand))
                      toggleLink(link, notify);
                    else if (expanded && expand && notify && link.href != null)
                    {
                        //hack to handle the case where the node is expanded because it or a child is in view
                        //but the user has selected 'expand all'.  We still need to notify the server to ensure
                        //that the state is saved
                        notifyExpandCollapse(link.href, false);
                    }
                }

                var secondTD = firstTD.nextSibling;
                while (secondTD && secondTD.nodeName != "TD")
                    { secondTD = secondTD.nextSibling; }
                if (!secondTD) continue;
                var table = secondTD.childNodes.item(0);
                while (table && table.nodeName != "TABLE")
                    { table = table.nextSibling; }
                if (table != null)
                {
                    //if there's a table in the second td, recursively process it
                    toggleTable(table, expand, notify);
                }
            }
        }
    }
 }

 function toggleAll (nodeLink, parentId, notify)
 {
    var tocParent = document.getElementById (parentId);
    var tocTable = tocParent.childNodes.item(0);
    while (tocTable && tocTable.nodeName != "TABLE")
         { tocTable = tocTable.nextSibling; }
    if (tocTable)
    {
        var linkText = nodeLink.childNodes.item (0);
        if (linkText.nodeValue == 'expand all') {
            toggleTable(tocTable, true, notify);
            linkText.nodeValue = 'collapse all';
        } else {
            toggleTable(tocTable, false, notify);
            linkText.nodeValue = 'expand all';
        }
    }

    return false;
 }

function adjustAllTocEntries(parentId, notify, expand)
{
    var tocParent = document.getElementById (parentId);
    var tocTable = tocParent.childNodes.item(0);
    while (tocTable && tocTable.nodeName != "TABLE")
         { tocTable = tocTable.nextSibling; }
    if (tocTable)
        toggleTable(tocTable, expand, notify);
}

 function getNextRow(rowElem)
 {
    if (null == rowElem)
        return null;


     var nextRow = rowElem.nextSibling;
     while (nextRow != null && !nextRow.tagName)
        nextRow = nextRow.nextSibling;

        if (nextRow == null || nextRow.tagName != "TR")
            return null;

     return nextRow;
 }

function notifyExpandCollapse(url, collapse)
{
    if (collapse)
        url += "&collapse=true";
    var xmlhttp = new XMLRequest(url);
    xmlhttp.get();
}

function XMLRequest(url, callback)
{
    var req = init();
    req.onreadystatechange = processRequest;

    function init()
    {
        if (window.XMLHttpRequest)
            return new XMLHttpRequest();
        else if (window.ActiveXObject)
            return new ActiveXObject("Microsoft.XMLHTTP");
    }

    function processRequest()
    {
        if (req.readyState == 4 && req.status == 200 && callback)
            callback(req.responseXML);
    }

    this.post = function(postdata)
    {
        this.send("POST", postdata);
    }

    this.get = function()
    {
        this.send("GET", null);
    }

    this.send = function(method, postdata)
    {
        req.open(method, url, true);
        req.send(postdata);
    }
}

var _helpDiv = null;
var _mouseInHelpDiv = false;
var _hideTimer;

function getHelpDiv()
{
    if (!_helpDiv)
        _helpDiv = document.getElementById("helpDiv");
    return _helpDiv;
}

LABKEY.requiresYahoo('yahoo', false);
LABKEY.requiresYahoo('dom', false);
LABKEY.requiresYahoo('event', false);
LABKEY.requiresYahoo('dragdrop', false);

function showHelpDiv(elem, titleText, bodyText, width)
{
    var posLeft = 0;
    var posTop = 0;
    var offsetElem = elem;
    if (_hideTimer)
        clearTimeout(_hideTimer);

    while (offsetElem.tagName != "BODY")
    {
        posLeft += offsetElem.offsetLeft;
        posTop += offsetElem.offsetTop;
        offsetElem = offsetElem.offsetParent;
    }

    posTop += elem.offsetHeight;
    //alert("posTop, posLeft: " +posTop + "," + posLeft);

    var div = getHelpDiv();

    document.getElementById("helpDivTitle").innerHTML = titleText;
    document.getElementById("helpDivBody").innerHTML = bodyText;

    var viewportWidth = YAHOO.util.Dom.getViewportWidth();
    var leftScroll = YAHOO.util.DragDropMgr.getScrollLeft();

    div.style.top = posTop;
    div.style.display = "block";

    if (width)
        div.style.width =  width;
    else
        div.style.width =  "250px";

    if (viewportWidth + leftScroll < div.offsetWidth + posLeft)
    {
        posLeft = viewportWidth + leftScroll - div.offsetWidth - 10;
        div.style.left = posLeft;
    }

    div.style.left = posLeft;

    return false;
}

function hideHelpDiv(force)
{
    if (force || !_mouseInHelpDiv)
        getHelpDiv().style.display = "none";
    return false;
}

function hideHelpDivDelay()
{
    _hideTimer = setTimeout("hideHelpDiv(false);", 500);
}

function showPathname(filechooser, id)
{
    var pathname = filechooser.value;
    var filename;
    if (pathname.indexOf('/') > -1)
         filename = pathname.substring(pathname.lastIndexOf('/')+1,pathname.length);
    else
         filename = pathname.substring(pathname.lastIndexOf('\\')+1,pathname.length);
    document.getElementById(id).innerHTML = filename;
    return(true);
}

// This index increases on every add, but doesn't decrease on remove.  Indexes will be sparse if rows are removed from
// the middle, which is important so a remove plus an add doesn't reuse an index.
var filePickerIndex = -1;

// All indexes are zero-based
function addFilePicker(tblId, linkId)
{
    var table = document.getElementById(tblId);
    var newRow = table.insertRow(-1);
    filePickerIndex++;
    newRow.id = "row" + filePickerIndex;
    var filePickerCell = newRow.insertCell(0);
    var filePickerId = "formFile" + filePickerIndex;
    filePickerCell.innerHTML = '<input type="file" id="' + filePickerId + '" name="formFiles[' + filePickerIndex + ']" size="60" onChange="showPathname(this, \'filename' + filePickerIndex + '\')">';
    var removePathnameCell = newRow.insertCell(1);
    removePathnameCell.innerHTML = '<a href="javascript:removeFilePicker(\'' + tblId + '\', \'' + linkId + '\', \'' + newRow.id + '\')">remove</a>' +
        '&nbsp;&nbsp;<label id="filename' + filePickerIndex + '"></label>';
    updateInstructions(document.getElementById(linkId), table.rows.length);
}

function removeFilePicker(tblId, linkId, rowId)
{
    var table = document.getElementById(tblId);

    for (var i = 0; i < table.rows.length; i++)
    {
        var row = table.rows[i];

        if (row.id == rowId)
        {
            table.deleteRow(row.rowIndex);
            break;
        }
    }

    updateInstructions(document.getElementById(linkId), table.rows.length);
}

function updateInstructions(instructions, rowCount)
{
    if (0 == rowCount)
        instructions.innerHTML = instructions.innerHTML.replace(" another ", " a ");
    else
        instructions.innerHTML = instructions.innerHTML.replace(" a ", " another ");
}

function mouseEnteredHelpDiv()
{
    _mouseInHelpDiv = true;
}

function mouseExitedHelpDiv()
{
    _mouseInHelpDiv = false;
    hideHelpDivDelay();
}

LABKEY.addMarkup(
'<div id="helpDiv" onMouseOver="mouseEnteredHelpDiv()" onMouseOut="mouseExitedHelpDiv()"' +
'   style="display:none;">'+
'  <table>'+
'    <tr>'+
'      <td title="Help" class="labkey-wp-title-left" nowrap>'+
'        <div><span id="helpDivTitle" class="labkey-wp-title">Title</span></div>'+
'      </td>'+
'      <td class="labkey-wp-title-right" align="right" style="border-left:0px">'+
'      <img alt="close" src="' + LABKEY.imagePath + '/partdelete.gif" onclick="hideHelpDiv(true)">'+
'      </td>'+
'     </tr>'+
'    <tr>'+
'      <td colspan=2 style="padding:5px;">'+
'        <span id="helpDivBody">Body</span>'+
'      </td>'+
'    </tr>'+
'  </table>'+
'</div>'
);
