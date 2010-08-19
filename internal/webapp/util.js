/*
 * Copyright (c) 2006-2010 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

function setAllCheckboxes(form, value, elementName)
{
    if (form.dataRegion)
    {
        return form.dataRegion.selectPage(value);
    }
    
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

function getCheckedValues(form, elementName)
{
    var elems = form.elements;
    var l = elems.length;
    var values = [];
    for (var i = 0; i < l; i++)
    {
        var e = elems[i];
        if (e.type == 'checkbox' && !e.disabled && (elementName == null || elementName == e.name) && e.checked)
            values.push(e.value);
    }
    return values;
}

/**
 * Given a radio button, determine which one in the group is selected and return its value
 * @param radioButton one of the radio buttons in the group
 */
function getRadioButtonValue(radioButton)
{
    if (radioButton.form && radioButton.name)
    {
        var radioButtonElements = radioButton.form.elements[radioButton.name];
        for (var i = 0; i < radioButtonElements.length; i++)
        {
            if (radioButtonElements[i].checked)
            {
                return radioButtonElements[i].value;
            }
        }
    }
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
         if (nextRow.className.indexOf("labkey-header") != -1)
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
    Ext.Ajax.request({url: url});
}

var _helpDiv = null;
var _mouseInHelpDiv = false;
var _showTimer;
var _hideTimer;

function getHelpDiv()
{
    if (!_helpDiv)
        _helpDiv = document.getElementById("helpDiv");
    return _helpDiv;
}

function showHelpDivDelay(elem, titleText, bodyText, width)
{
    // IE support
    function go()
    {
        showHelpDiv(elem, titleText, bodyText, width);
    }
    _showTimer = setTimeout(go, 400);
}

function showHelpDiv(elem, titleText, bodyText, width)
{
    var posLeft = 12;
    var posTop = 8;
    var offsetElem = elem;
    if (_hideTimer)
        clearTimeout(_hideTimer);

    while (offsetElem.tagName != "BODY")
    {
        posLeft += offsetElem.offsetLeft  - offsetElem.scrollLeft;
        posTop += offsetElem.offsetTop - offsetElem.scrollTop;
        offsetElem = offsetElem.offsetParent;
    }

    posTop += elem.offsetHeight;
    //alert("posTop, posLeft: " +posTop + "," + posLeft);

    var div = getHelpDiv();

    Ext.get("helpDivTitle").update(titleText);
    Ext.get("helpDivBody").update(bodyText);

    var bd = Ext.get(document.body);
    var sz = bd.getViewSize();
    var viewportWidth = sz.width;
    var viewportHeight = sz.height;
    var pos = bd.getScroll();
    var leftScroll = pos.left;
    var topScroll = pos.top;

    div.style.top = posTop + "px";
    div.style.display = "block";
    div.style.zIndex = "25";

    var table = document.getElementById("helpDivTable");

    if (width)
        table.style.width =  width;
    else
        table.style.width =  "250px";

    var maxWidth = document.getElementById("helpDivTable").offsetWidth;

    if (viewportWidth + leftScroll < maxWidth + posLeft + 10)
    {
        posLeft = viewportWidth + leftScroll - maxWidth - 25;
    }

    var maxHeight = table.clientHeight;
    if (maxHeight && (viewportHeight + topScroll < maxHeight + posTop + 10))
    {
        posTop = viewportHeight + topScroll - maxHeight - 25;
        div.style.top = posTop + "px";
    }
    div.style.left = posLeft + "px";

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
    if (_showTimer)
        clearTimeout(_showTimer);
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

function submitForm(form)
{
    if (form == null)
        return;
    if (!form.onsubmit || (form.onsubmit() !== false))
        form.submit();
}

function isTrueOrUndefined(obj)
{
    return obj === undefined || obj === true;
}

// IE doesn't submit forms on enter, so this method allows one to hook up input elements to submit forms
function addInputSubmitEvent(form, input) {
    input.onkeydown = function(e) {
        e = e || window.event;
        if (e.keyCode == 13) {
            form.submit();
            return false;
        }
    };
}


/**
 * Event handler that can be attached to text areas to let them handle indent/outdent with TAB/SHIFT-TAB.
 * Handles region selection for multi-line indenting as well.
 * Note that this overrides the browser's standard focus traversal keystrokes.
 * Based off of postings from http://ajaxian.com/archives/handling-tabs-in-textareas
 * Wire it up with a call like:
 *     Ext.EventManager.on('queryText', 'keydown', handleTabsInTextArea);
 * @param event an Ext.EventObject for the keydown event
 */
function handleTabsInTextArea(event)
{
    // Check if the user hit TAB or SHIFT-TAB
    if (event.getKey() == Ext.EventObject.TAB && !event.ctrlKey && !event.altKey)
    {
        var t = event.target;

        if (Ext.isIE)
        {
            var range = document.selection.createRange();
            var stored_range = range.duplicate();
            stored_range.moveToElementText(t);
            stored_range.setEndPoint('EndToEnd', range);
            t.selectionStart = stored_range.text.length - range.text.length;
            t.selectionEnd = t.selectionStart + range.text.length;
            t.setSelectionRange = function(start, end)
            {
                var range = this.createTextRange();
                range.collapse(true);
                range.moveStart("character", start);
                range.moveEnd("character", end - start);
                range.select();
            };
        }

        var ss = t.selectionStart;
        var se = t.selectionEnd;
        var newSelectionStart = ss;
        var scrollTop = t.scrollTop;

        if (ss != se)
        {
            // In case selection was not the entire line (e.g. selection begins in the middle of a line)
            // we need to tab at the beginning as well as at the start of every following line.
            var pre = t.value.slice(0,ss);
            var sel = t.value.slice(ss,se);
            var post = t.value.slice(se,t.value.length);

            // If our selection starts in the middle of the line, include the full line
            if (pre.length > 0 && pre.lastIndexOf('\n') != pre.length - 1)
            {
                // Add the beginning of the line to the indented area
                sel = pre.slice(pre.lastIndexOf('\n') + 1, pre.length).concat(sel);
                // Remove it from the prefix
                pre = pre.slice(0, pre.lastIndexOf('\n') + 1);
                if (!event.shiftKey)
                {
                    // Add one to the starting index since we're going to add a tab before it
                    newSelectionStart++;
                }
            }
            // If our last selected character is a new line, don't add a tab after it since that's
            // part of the next line
            if (sel.lastIndexOf('\n') == sel.length - 1)
            {
                sel = sel.slice(0, sel.length - 1);
                post = '\n' + post;
            }

            // Shift means remove indentation
            if (event.shiftKey)
            {
                // Remove one tab after each newline
                sel = sel.replace(/\n\t/g,"\n");
                if (sel.indexOf('\t') == 0)
                {
                    // Remove one leading tab, if present
                    sel = sel.slice(1, sel.length);
                    // We're stripping out a tab before the selection, so march it back one character
                    newSelectionStart--;
                }
            }
            else
            {
                pre = pre.concat('\t');
                sel = sel.replace(/\n/g,"\n\t");
            }

            var originalLength = t.value.length;
            t.value = pre.concat(sel).concat(post);
            t.setSelectionRange(newSelectionStart, se + (t.value.length - originalLength));
        }
        // No text is selected
        else
        {
            // Shift means remove indentation
            if (event.shiftKey)
            {
                // Figure out where the current line starts
                var lineStart = t.value.slice(0, ss).lastIndexOf('\n');
                if (lineStart < 0)
                {
                    lineStart = 0;
                }
                // Look for the first tab
                var tabIndex = t.value.slice(lineStart, ss).indexOf('\t');
                if (tabIndex != -1)
                {
                    // The line has a tab - need to remove it
                    tabIndex += lineStart;
                    t.value = t.value.slice(0, tabIndex).concat(t.value.slice(tabIndex + 1, t.value.length));
                    if (ss == se)
                    {
                        ss--;
                        se = ss;
                    }
                    else
                    {
                        ss--;
                        se--;
                    }
                }
            }
            else
            {
                // Shove a tab in at the cursor
                t.value = t.value.slice(0,ss).concat('\t').concat(t.value.slice(ss,t.value.length));
                if (ss == se)
                {
                    ss++;
                    se = ss;
                }
                else
                {
                    ss++;
                    se++;
                }
            }
            t.setSelectionRange(ss, se);
        }
        t.scrollTop = scrollTop;

        // Don't let the browser treat it as a focus traversal
        event.preventDefault();
    }
}


function showMenu(parent, menuElementId, align) {
    if (!align)
        align = "tl-bl?";
    Ext.menu.MenuMgr.get(menuElementId).show(parent, align);
}


LABKEY.addMarkup(
'<div id="helpDiv" onMouseOver="mouseEnteredHelpDiv()" onMouseOut="mouseExitedHelpDiv()"' +
'   style="display:none;">'+
'  <table id="helpDivTable">'+
'    <tr class="labkey-wp-header" width="100%">'+
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
