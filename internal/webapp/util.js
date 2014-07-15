/*
 * Copyright (c) 2006-2014 LabKey Corporation
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

    var bd = Ext4.get(document.body);
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

function handleTabsInTextArea()
{
    console.warn('handleTabsInTextArea() has been migrated to LABKEY.ext.Utils.handleTabsInTextArea()');
}

function showMenu(parent, menuElementId, align) {
    if (!align)
    {
        align = "tl-bl?";
    }

    var menu, cls = 'labkey-menu-button-active';
    if (typeof(Ext4) != 'undefined')
    {
        menu = Ext4.menu.Manager.get(menuElementId);

        if (menu)
        {
            // attach class listeners
            menu.on('show', function() { Ext4.get(this).addCls(cls); }, parent);
            menu.on('beforehide', function() { Ext4.get(this).removeCls(cls); }, parent);

            menu.show();
            menu.alignTo(parent, align);
        }
        else
        {
            console.error('Menu is not available:', menuElementId);
        }
    }
    else
    {
        console.warn("No menu registered :" + menuElementId);
    }

    return menu;
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