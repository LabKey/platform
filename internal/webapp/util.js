/*
 * Copyright (c) 2006-2013 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
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
    _showTimer = setTimeout(function() { showHelpDiv(elem, titleText, bodyText, width); }, delay ? delay : 400);
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
// This is the jQuery equivalent
//    var bd = $(document.body);
//    var viewportWidth = bd.outerWidth();
//    var viewportHeight = bd.outerHeight();
//    var leftScroll = bd.scrollLeft();
//    var topScroll = bd.scrollTop();

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
    // As of issue 18142, don't show an icon if no file is selected
    Ext4.get(element).dom.innerHTML = "<table><tr><td>" + (filename == '' ? "" : ("<img src=\"" + LABKEY.Utils.getFileIconUrl(filename) + "\"/>")) + "</td><td>" + filename + "</td></tr></table>";
    return(true);
}

// This index increases on every add, but doesn't decrease on remove.  Indexes will be sparse if rows are removed from
// the middle, which is important so a remove plus an add doesn't reuse an index.
var filePickerIndex = -1;

// All indexes are zero-based
function addFilePicker(tblId, linkId)
{
    var twoDigit = function(num) { return ((num < 10) ? "0" : "") + num};
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

//_menuMgr = new function() {
//    var menus = {};
//
//    return {
//        register: function(id, config) {
//            menus[id] = config;
//        },
//        get: function(id) {
//            return menus[id];
//        }
//    };
//};

function showMenu(parent, menuElementId, align) {
    if (!align)
    {
        align = "tl-bl?";
    }

    var menu, menuCfg, cls = 'labkey-menu-button-active';
    if (typeof(Ext) != 'undefined') {
        menu = Ext.menu.MenuMgr.get(menuElementId);

//        if (!menu) {
//            menuCfg = _menuMgr.get(menuElementId);
//            if (menuCfg) {
//                menu = new Ext.menu.Menu(menuCfg);
//            }
//        }

        // attach class listeners
        menu.on('beforeshow', function() { Ext.get(this).addClass(cls); menu.floatParent = this; }, parent);
        menu.on('beforehide', function() { Ext.get(this).removeClass(cls); menu.floatParent = null; }, parent);

        menu.show(parent, align);
    }
    else
    {
        console.error("No menu registered :" + menuElementId);
    }
    return menu;

    // TODO: Ext 4 Menus do not escape id's properly, must fix before dependency on Ext 3 can be dropped
//    if (typeof(Ext4) != 'undefined') {
//        menu = Ext4.menu.Manager.get(menuElementId);
//
//        if (!menu) {
//            menuCfg = _menuMgr.get(menuElementId);
//            if (menuCfg) {
//                menu = Ext4.create('Ext.menu.Menu', menuCfg);
//            }
//
//            // attach class listeners
//            menu.on('show', function() { Ext4.get(this).addCls(cls); }, parent);
//            menu.on('beforehide', function() { Ext4.get(this).removeCls(cls); }, parent);
//        }
//
//        menu.show();
//        menu.alignTo(parent, align);
//    }
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
//$(document.body).ready(function() {
//    $(document.body).append(
//        '<div id="helpDiv" onMouseOver="mouseEnteredHelpDiv()" onMouseOut="mouseExitedHelpDiv()"' +
//        '   style="display:none;">'+
//        '  <table id="helpDivTable">'+
//        '    <tr class="labkey-wp-header" width="100%">'+
//        '      <td title="Help" class="labkey-wp-title-left" nowrap>'+
//        '        <div><span id="helpDivTitle" class="labkey-wp-title">Title</span></div>'+
//        '      </td>'+
//        '      <td class="labkey-wp-title-right" align="right" style="border-left:0; padding-bottom: 0;">'+
//        '      <img alt="close" src="' + LABKEY.imagePath + '/partdelete.png" onclick="hideHelpDiv(true)">'+
//        '      </td>'+
//        '     </tr>'+
//        '    <tr>'+
//        '      <td colspan=2 style="padding:5px;">'+
//        '        <span id="helpDivBody">Body</span>'+
//        '      </td>'+
//        '    </tr>'+
//        '  </table>'+
//        '</div>'
//    );
//});