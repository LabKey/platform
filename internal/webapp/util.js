/*
 * Copyright (c) 2006-2013 LabKey Corporation
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
    LABKEY.ExtAdapter.Ajax.request({url: url});
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

function showHelpDivDelay(elem, titleText, bodyText, width, delay)
{
    // IE support
    function go()
    {
        showHelpDiv(elem, titleText, bodyText, width);
    }
    _showTimer = setTimeout(go, delay ? delay : 400);
}

function showHelpDiv(elem, titleText, bodyText, width)
{
    var posLeft = 12;
    var posTop = 8;
    var offsetElem = elem;
    if (_hideTimer)
        clearTimeout(_hideTimer);

    while (offsetElem && offsetElem.tagName != "BODY")
    {
        posLeft += offsetElem.offsetLeft  - offsetElem.scrollLeft;
        posTop += offsetElem.offsetTop - offsetElem.scrollTop;
        offsetElem = offsetElem.offsetParent;
    }
    if (!offsetElem)
        return;

    posTop += elem.offsetHeight;

    var div = getHelpDiv();

    document.getElementById("helpDivTitle").innerHTML = titleText;
    document.getElementById("helpDivBody").innerHTML = bodyText;

    var bd = LABKEY.ExtAdapter.get(document.body);
    var sz = bd.getViewSize();
    var viewportWidth = sz.width;
    var viewportHeight = sz.height;
    var pos = bd.getScroll();
    var leftScroll = pos.left;
    var topScroll = pos.top;

    div.style.top = posTop + "px";
    div.style.display = "block";
    div.style.zIndex = "1050";

    var table = document.getElementById("helpDivTable");

    table.style.width = (width ? width : '250px');

    var maxWidth = table.offsetWidth;

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

/** Element is anything that can be resolved using Ext.get() - an element, an id, etc */
function showPathname(filechooser, element)
{
    var pathname = filechooser.value;
    var filename;
    if (pathname.indexOf('/') > -1)
         filename = pathname.substring(pathname.lastIndexOf('/')+1,pathname.length);
    else
         filename = pathname.substring(pathname.lastIndexOf('\\')+1,pathname.length);
    Ext4.get(element).dom.innerHTML = "<table><tr><td><img src=\"" + LABKEY.Utils.getFileIconUrl(filename) + "\"/></td><td>" + filename + "</td></tr></table>";
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
    newRow.style.minHeight = '20px';
    var filePickerCell = newRow.insertCell(0);
    var filePickerId = "formFile" + twoDigit(filePickerIndex);
    filePickerCell.innerHTML = '<input type="file" id="' + filePickerId + '" name="formFiles[' + twoDigit(filePickerIndex) + ']" size="45" onChange="showPathname(this, \'filename' + twoDigit(filePickerIndex) + '\')">';
    var removePathnameCell = newRow.insertCell(1);
    removePathnameCell.innerHTML = '<table><tr><td><a href="javascript:removeFilePicker(\'' + tblId + '\', \'' + linkId + '\', \'' + newRow.id + '\')">remove</a></td>' +
        '<td><label id="filename' + twoDigit(filePickerIndex) + '"></label></td></tr></table>';
    updateInstructions(document.getElementById(linkId), table.rows.length);
}

function twoDigit(num)
{
	if (num < 10)
        return "0" + num;
    return "" + num;
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
    {
        align = "tl-bl?";
    }

    var menu, oldExt = false, extPresent = false;
    if (typeof(Ext) != 'undefined') {
        oldExt = true;
        extPresent = true;
        menu = Ext.menu.MenuMgr.get(menuElementId);
    }

    if (typeof(Ext4) != 'undefined' && !menu) {
        menu = Ext4.menu.Manager.get(menuElementId);
        extPresent = true;
        oldExt = false;
    }

    if (menu && extPresent)
    {
        // While the menu's open, highlight the button that caused it to open
        if (oldExt)
        {
            Ext.get(parent).addClass('labkey-menu-button-active');

            //provide mechanism for menu to identify owner.  primarily used for animations
            menu.floatParent = parent;

            menu.show(parent, align);
            var listener = function()
            {
                // Get rid of the highlight when the menu disappears, and remove the listener since the menu
                // can be reused
                menu.removeListener('beforehide', listener);
                Ext.get(parent).removeClass('labkey-menu-button-active');
                menu.floatParent = null;
            };
            menu.on('beforehide', listener);
        }
        else
        {
            Ext4.get(parent).addCls('labkey-menu-button-active');

            menu.show();
            menu.alignTo(parent, align);
        }
    }
    else
        console.error("No menu registered :" + menuElementId);
}


LABKEY.addMarkup(
'<div id="helpDiv" onMouseOver="mouseEnteredHelpDiv()" onMouseOut="mouseExitedHelpDiv()"' +
'   style="display:none;">'+
'  <table id="helpDivTable">'+
'    <tr class="labkey-wp-header" width="100%">'+
'      <td title="Help" class="labkey-wp-title-left" nowrap>'+
'        <div><span id="helpDivTitle" class="labkey-wp-title">Title</span></div>'+
'      </td>'+
'      <td class="labkey-wp-title-right" align="right" style="border-left:0; padding-bottom: 0;">'+
'      <img alt="close" src="' + LABKEY.imagePath + '/partdelete.png" onclick="hideHelpDiv(true)">'+
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

// generator function to create a function to call when flag field is clicked
// This is used in FlagColumnRenderer
function showFlagDialogFn(config)
{
    // TODO: Fix the explicit dependency on Ext 3 || 4.
    if (typeof(Ext4) != 'undefined' || typeof(Ext) != 'undefined') {

        var EXT = window.Ext4||window.Ext;
        config = EXT.apply({}, config, {
            url            : LABKEY.ActionURL.buildURL('experiment', 'setFlag.api'),
            dataRegionName : null,
            defaultComment : "Flagged for review",
            dialogTitle    : "Review",
            imgTitle       : "Flag for review",
            imgSrcFlagged  : LABKEY.contextPath + "/Experiment/flagDefault.gif",
            imgClassFlagged : "",
            imgSrcUnflagged : LABKEY.contextPath + "/Experiment/unflagDefault.gif",
            imgClassUnflagged : "",
            translatePrimaryKey : null
        });

        function getDataRegion()
        {
            if (LABKEY.DataRegions && typeof config.dataRegionName == 'string')
                return LABKEY.DataRegions[config.dataRegionName];
            return null;
        }

        var setFlag = function(flagId)
        {
            EXT.QuickTips.init();

            var clickedComment;
            var flagImages = EXT.DomQuery.select("IMG[flagId='" + flagId + "']");
            if (!flagImages || 0==flagImages.length)
                return;
            var img = flagImages[0];
            if (img.title != config.imgTitle)
                clickedComment = img.title;

            var checkedLsids = [];
            var dr = getDataRegion();
            if (dr && typeof config.translatePrimaryKey == 'function')
            {
                var pks = dr.getChecked() || [];
                for (var i=0 ; i<pks.length ; i++)
                    checkedLsids.push(config.translatePrimaryKey(pks[i]));
            }

            var msg = 'Enter a comment';
            var comment = clickedComment || config.defaultComment;
            if (checkedLsids.length > 0)
            {
                msg = "Enter comment for " + checkedLsids.length + " selected " + (checkedLsids.length==1?"row":"rows");
                comment = config.defaultComment;        // consider inspect all for equal comments
            }

            var lsids = checkedLsids.length==0 ? [flagId] : checkedLsids;
            var successFn = function(response, options)
            {
                var comment = options.params.comment;
                for (var i=0 ; i<lsids.length ; i++)
                {
                    var lsid = lsids[i];
                    var flagImages = EXT.DomQuery.select("IMG[flagId='" + lsid + "']");
                    if (!flagImages || 0==flagImages.length)
                        continue;
                    el = EXT.get(flagImages[0]);
                    if (comment)
                    {
                        el.dom.src = config.imgSrcFlagged;
                        el.dom.title = comment;
                        if (config.imgClassUnflagged)
                            (el.removeCls||el.removeClass)(config.imgClassUnflagged);
                        (el.addCls||el.addClass)(config.imgClassFlagged);
                    }
                    else
                    {
                        el.dom.src = config.imgSrcUnflagged;
                        el.dom.title = config.imgTitle;
                        if (config.imgClassFlagged)
                            (el.removeCls||el.removeClass)(config.imgClassFlagged);
                        (el.addCls||el.addClass)(config.imgClassUnflagged);
                    }
                }
            };

            var el = EXT.get(img);
            var box = EXT.MessageBox.show(
                    {
                        title   : config.dialogTitle,
                        prompt  : true,
                        msg     : msg,
                        value   : comment,
                        width   : 300,
                        fn      : function(btnId, value)
                        {
                            if (btnId == 'ok')
                            {
                                Ext.Ajax.request(
                                        {
                                            url    : config.url,
                                            params :
                                            {
                                                lsid    : lsids,
                                                comment : value,
                                                unique  : new Date().getTime()
                                            },
                                            success : successFn,
                                            failure : function()
                                            {
                                                alert("Failure!");
                                            }
                                        });
                            }
                        },
                        buttons : EXT.MessageBox.OKCANCEL
                    });
        };

        if (EXT.isReady)
            return setFlag;

        return function(flagId)
        {
            EXT.onReady(function(){setFlag(flagId)});
        };
    }

    console.warn('Unable to find ExtJS for use in showFlagDialogFn()');
    return function() {};
}
