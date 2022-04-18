/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
    {
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
        const helpDiv = document.getElementById("helpDiv");
        if (helpDiv) {
            _helpDiv = helpDiv;
            document.addEventListener('keyup', helpDivHideHandler);
            document.addEventListener('click', helpDivHideHandler);
        }
    }
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

    while (offsetElem && offsetElem.tagName != "BODY" && offsetElem.tagName != "HTML")
    {
        posLeft += offsetElem.offsetLeft  - offsetElem.scrollLeft;
        posTop += offsetElem.offsetTop - offsetElem.scrollTop;
        offsetElem = offsetElem.offsetParent;
    }
    if (!offsetElem)
        return;

    posTop += elem.offsetHeight;

    // helpDiv can potentially not render if the web page is in a loading state
    const div = getHelpDiv();
    if (!div) {
        return false;
    }
    div.anchorElem = elem;

    document.getElementById("helpDivTitle").innerHTML = titleText;
    document.getElementById("helpDivBody").innerHTML = bodyText;

    var $ = jQuery;
    var bd = $(document);
    var sz = { height: bd.height(), width: bd.width() };
    var pos = { left: bd.scrollLeft(), top: bd.scrollTop() };
    div.style.top = posTop + "px";
    div.style.display = "block";
    div.style.zIndex = "1050";

    var table = document.getElementById("helpDivTable");

    table.style.width = (width ? width : '250px');

    var maxWidth = table.offsetWidth;

    if (sz.width + pos.left < maxWidth + posLeft + 10)
    {
        posLeft = sz.width + pos.left - maxWidth - 25;
    }

    var maxHeight = table.clientHeight;
    if (maxHeight && (sz.height + pos.top < maxHeight + posTop + 10))
    {
        posTop = sz.height + pos.top - maxHeight - 25;
        div.style.top = posTop + "px";
    }
    div.style.left = posLeft + "px";

    return false;
}

function hideHelpDiv(force)
{
    if (force || !_mouseInHelpDiv) {
        // helpDiv can potentially not render if the web page is in a loading state
        const helpDiv = getHelpDiv();
        if (helpDiv) {
            helpDiv.style.display = "none";
        }
    }
    return false;
}

function hideHelpDivDelay()
{
    if (_showTimer)
        clearTimeout(_showTimer);
    _hideTimer = setTimeout("hideHelpDiv(false);", 500);
}

function helpDivHideHandler(e)
{
    // Returns true if el is a parent node of child.
    function contains(el, child) {
        var up = child.parentNode;
        return el === up || !!(up && up.nodeType === 1 && el.contains(up));
    }

    // check help is visible
    if (getHelpDiv().style.display != "none")
    {
        // escape pressed or the click target is not the offset elem and is outside the help div
        var isEscPress = e.type == 'keyup' && e.which == 27;
        var isAnchorElem = e.target == getHelpDiv().anchorElem || contains(getHelpDiv().anchorElem, e.target);
        if (isEscPress || (e.type == 'click' && !isAnchorElem && !contains(getHelpDiv(), e.target)))
            hideHelpDiv(true);
    }
}

function showPathname(filechooser, elementId)
{
    var $ = jQuery;
    var pathname = filechooser.value;
    var slash = pathname.indexOf('/') > -1 ? '/' : '\\';
    var filename = pathname.substring(pathname.lastIndexOf(slash) + 1, pathname.length);

    // backwards compatibility for elementId being an actual element
    var el = LABKEY.Utils.isString(elementId) ? $('#' + elementId) : $(elementId);

    if (el.length) {
        // As of issue 18142, don't show an icon if no file is selected
        el.html([
            '<table>',
                '<tr>',
                    '<td style="padding: 0 5px;">' + (filename == '' ? '' : ('<img src=\"' + LABKEY.Utils.getFileIconUrl(filename) + '\"/>')) + '</td>',
                    '<td>' + LABKEY.Utils.encodeHtml(filename) + '</td>',
                '</tr>',
            '</table>'
        ].join(''));
    }
    LABKEY.setDirty(true);
    return true;
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
    filePickerCell.innerHTML = '<input type="file" id="' + filePickerId + '" name="formFiles[' + twoDigit(filePickerIndex) + ']" size="45" onChange="showPathname(this, \'filename' + twoDigit(filePickerIndex) + '\')" style="border: none; background-color: transparent;">';
    var removePathnameCell = newRow.insertCell(1);
    removePathnameCell.innerHTML = '<table><tr><td><a href="javascript:removeFilePicker(\'' + tblId + '\', \'' + linkId + '\', \'' + newRow.id + '\')">&nbsp;remove</a></td>' +
        '<td><span id="filename' + twoDigit(filePickerIndex) + '"></span></td></tr></table>';
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
    console.error('A menu still exists that is attempting to use showMenu(). Element id: ' + menuElementId);
}

// If at least one checkbox on the form is selected then GET/POST url.  Otherwise, display an error.
function verifySelected(form, url, method, pluralNoun, pluralConfirmText, singularConfirmText)
{
    var checked = 0;
    var elems = form.elements;
    var l = elems.length;

    for (var i = 0; i < l; i++)
    {
        var e = elems[i];

        if (e.type == 'checkbox' && e.checked && e.name == '.select')
        {
            checked++;
        }
    }

    if (checked > 0)
    {
        if ((window.parent == window) && (null != pluralConfirmText))
        {
            var confirmText = (1 == checked && null != singularConfirmText ? singularConfirmText : pluralConfirmText);

            if (!window.confirm(confirmText.replace("${selectedCount}", checked)))
                return false;
        }

        form.action = url;
        form.method = method;
        return true;
    }
    else
    {
        LABKEY.Utils.alert('Error', 'Please select one or more ' + pluralNoun + '.');
        return false;
    }
}

if (!LABKEY.internal)
    LABKEY.internal = {};

LABKEY.internal.SortUtil = new function()
{
    return {
        naturalSort : function (aso, bso)
        {
            // http://stackoverflow.com/questions/19247495/alphanumeric-sorting-an-array-in-javascript
            var a, b, a1, b1, i= 0, n, L,
                    rx=/(\.\d+)|(\d+(\.\d+)?)|([^\d.]+)|(\.\D+)|(\.$)/g;
            if (aso === bso) return 0;
            a = aso.toLowerCase().match(rx);
            b = bso.toLowerCase().match(rx);

            if (a == 'null' || b == 'null') {
                var aEmpty = a == 'null';
                var bEmpty = b == 'null';

                // both are empty
                if (aEmpty && bEmpty) {
                    return 0;
                }

                return aEmpty ? -1 : 1;
            }

            L = a.length;
            while (i < L) {
                if (!b[i]) return 1;
                a1 = a[i]; b1 = b[i++];
                if (a1 !== b1) {
                    n = a1 - b1;
                    if (!isNaN(n)) return n;
                    return a1 > b1 ? 1 : -1;
                }
            }
            return b[i] ? -1 : 0;
        }
    }
};


LABKEY.internal.UserNotificationPanel =
{
    init : function ($, notificationCountId, notificationPanelId)
    {
        // need to create the div as a direct child of body so that the z-index will keep it in front
        const notificationPanelDiv = document.createElement("div");
        notificationPanelDiv.id = notificationPanelId;
        notificationPanelDiv.className = 'labkey-notification-panel labkey-hidden';
        document.body.appendChild(notificationPanelDiv);

        LABKEY.Notification.setElementIds(notificationCountId, notificationPanelId);

        function renderNotifications() {
            // if we have the notification unreadCount, update that in the header
            if (LABKEY.notifications && LABKEY.notifications.unreadCount !== undefined) {
                LABKEY.Notification.updateUnreadCount();
            }

            let html = "<div class='labkey-notification-header'><div class='labkey-notification-title'>Notifications</div></div>";
            if (!LABKEY.notifications || !LABKEY.notifications.grouping) {
                html += '<div style="padding: 10px;"><i class="fa fa-spinner fa-pulse"></i> Loading...</div>';
            }
            else {
                html += "<div class='labkey-notification-clear-all " + (LABKEY.notifications.unreadCount > 0 ? "" : "labkey-hidden")
                        + "' onclick='LABKEY.Notification.clearAllUnread(); return true;'>Clear all</div>";

                html += "<div class='labkey-notification-none " + (LABKEY.notifications.unreadCount == 0 ? "" : "labkey-hidden")
                        + "'>No new notifications</div>";

                html += "<div class='labkey-notification-area'>";
                if (LABKEY.notifications.unreadCount > 0) {
                    const groupings = LABKEY.notifications.grouping ? Object.keys(LABKEY.notifications.grouping) : [];

                    // sort groups alphabetically, with "Other" at the bottom
                    groupings.sort(function(a, b) {
                        return a === "Other" ? 1 : (b === "Other" ? -1 : a.localeCompare(b));
                    });

                    for (let i = 0; i < groupings.length; i++) {
                        html += "<div id='notificationtype-" + groupings[i] + "' class='labkey-notification-type'>";
                        html += "<div class='labkey-notification-type-label'>" + LABKEY.Utils.encodeHtml(groupings[i]) + "</div>";

                        let groupRowIds = LABKEY.notifications.grouping[groupings[i]];
                        for (let j = 0; j < groupRowIds.length; j++) {
                            const info = LABKEY.notifications[rowId];
                            if (isNaN(parseInt(groupRowIds[j])))
                                continue;
                            const rowId = parseInt(groupRowIds[j]);

                            // get the date/time display string based on the current date
                            const monthNames = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"],
                                    d = new Date(info.Created), today = new Date(),
                                    dStr = d.toDateString() == today.toDateString() ? 'Today' : monthNames[d.getMonth()] + ' ' + d.getDate();

                            html += "<div class='labkey-notification' id='notification-" + rowId + "' onclick='LABKEY.Notification.goToActionLink(event, " + rowId + "); return true;'>"
                                    + "   <div class='fa " + info.IconCls + " labkey-notification-icon'></div>"
                                    + "   <div class='labkey-notification-close'>"
                                    + "      <div class='fa fa-times labkey-notification-times' onclick='LABKEY.Notification.markAsRead(" + rowId + "); return true;'></div>"
                                    + "      <div class='fa fa-angle-down labkey-notification-toggle' onclick='LABKEY.Notification.toggleBody(this); return true;'></div>"
                                    + "   </div>"
                                    + "   <div class='labkey-notification-createdby'>" + dStr + " - " + LABKEY.Utils.encodeHtml(info.CreatedBy) + "</div>"
                                    + "   <div class='labkey-notification-body'>" + info.HtmlContent + "</div>"
                                    + "</div>";
                        }
                        html += "</div>";
                    }
                }
                html += "</div>";

                if (LABKEY.notifications.unreadCount > 0 || LABKEY.notifications.hasRead) {
                    html += "<div class='labkey-notification-footer' onclick='LABKEY.Notification.goToViewAll(); return true;'><span>View all notifications</span></div>";
                }
            }

            $('#' + notificationPanelId).html(html);
        }

        LABKEY.Notification.onChange(renderNotifications);
        renderNotifications();
    }
};
