/*
 * Copyright (c) 2006-2010 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
var _hideTimer;
var _completeTimer;
var _elem;
var _completionURLPrefix;
var _optionCount;
var _optionSelectedIndex = 0;
var _eventSinceHidden = false;

var _completionDiv = null;
var DOWN_ARROW_KEYCODE = 40;
var UP_ARROW_KEYCODE = 38;
var ENTER_KEYCODE = 13;
var TAB_KEYCODE = 9;
var ESCAPE_KEYCODE = 27;
var BACKSPACE_KEYCODE = 8;

function getCompletionDiv()
{
    if (!_completionDiv)
        _completionDiv = document.getElementById("completionDiv");
    return _completionDiv;
}

function getKeyCode(event)
{
    if (window.event) // IE
        return event.keyCode;
    else if (event.which) // Netscape/Firefox/Opera
        return event.which;
}

function isCtrlKey(event)
{
    var keynum = getKeyCode(event);
    return (keynum == DOWN_ARROW_KEYCODE ||
            keynum == UP_ARROW_KEYCODE ||
            keynum == ENTER_KEYCODE ||
            keynum == TAB_KEYCODE ||
            keynum == BACKSPACE_KEYCODE ||
            keynum == ESCAPE_KEYCODE);
}

function ctrlKeyCheck(event)
{
    if (!isCtrlKey(event) || !isCompletionVisible())
        return true;

    var keynum = getKeyCode(event);

    switch (keynum)
    {
        case DOWN_ARROW_KEYCODE:
            changeSelectedOption(true);
            return false;
        case UP_ARROW_KEYCODE:
            changeSelectedOption(false);
            return false;
        case ENTER_KEYCODE:
        case TAB_KEYCODE:
            selectOption(-1);
            return false;
        case ESCAPE_KEYCODE:
            hideCompletionDiv();
            return false;
        case BACKSPACE_KEYCODE:
            hideCompletionDiv();
            return true;
    }
}

function isCompletionVisible()
{
    return getCompletionDiv().style.display == 'block';
}

function selectOption(index)
{
    if (index >= 0)
        _optionSelectedIndex = index;
    var newlineIndex = _elem.value.lastIndexOf('\n');
    var newValue = document.getElementById("insertSpan" + _optionSelectedIndex).innerHTML;
    if (newlineIndex >= 0)
        _elem.value = _elem.value.substring(0, newlineIndex + 1) + newValue;
    else
        _elem.value = newValue;
    hideCompletionDiv();
}

function changeSelectedOption(forward)
{
    var oldSpan = document.getElementById("completionTR" + _optionSelectedIndex);
    var newIndex = -1;
    if (forward)
        _optionSelectedIndex = _optionSelectedIndex < _optionCount - 1 ? _optionSelectedIndex + 1 : 0;
    else
        _optionSelectedIndex = _optionSelectedIndex > 0 ? _optionSelectedIndex - 1 : _optionCount - 1;
    var newSpan = document.getElementById("completionTR" + _optionSelectedIndex);
    oldSpan.className = "labkey-completion-nohighlight";
    newSpan.className = "labkey-completion-highlight";
}

function postProcess(responseXML)
{
    if (!responseXML)
        return;

    var completions = responseXML.getElementsByTagName("completions")[0];
    if (!completions)
        return;

    if (completions.childNodes.length == 0)
    {
        hideCompletionDiv();
        return;
    }

    var completionText = "<table class='labkey-completion' width='100%'>";
    var completionList = completions.getElementsByTagName("completion")
    for (var i = 0; i < completionList.length; i++)
    {
        var completion = completionList[i];
        var display = completion.getElementsByTagName("display")[0];
        var insert = completion.getElementsByTagName("insert")[0];
        var styleClass = "labkey-completion-nohighlight";
        if (i == _optionSelectedIndex)
            styleClass = "labkey-completion-highlight";

        completionText += "<tr class='" + styleClass + "' style='cursor:pointer' onclick='selectOption(" + i +
                          ")'><td id='completionTR" + i + "'>" + display.childNodes[0].nodeValue +
                          "<span style='display:none' id='insertSpan" + i + "'>" + insert.childNodes[0].nodeValue + "</span>" +
                          "</td></tr>\n";
    }
    _optionCount = completionList.length;
    completionText += "</table>";
    document.getElementById("completionBody").innerHTML = completionText;
    showCompletionDiv(_elem);
}

function handleChange(elem, event, completionURLPrefix)
{
    if (isCtrlKey(event))
        return false;

    _eventSinceHidden = true;
    clearTimeout(_completeTimer);
    _completionURLPrefix = completionURLPrefix;
    _elem = elem;
    _completeTimer = setTimeout("complete();", 250);
}

function complete()
{
    var typedString = _elem.value;
    var newline = typedString.lastIndexOf('\n');
    if (newline >= 0)
        typedString = typedString.substring(newline, typedString.length);
    if (!typedString)
        hideCompletionDiv();
    else
    {
        Ext.Ajax.request({
            url: _completionURLPrefix + escape(typedString),
            success: function (response, options) {
                if (response && response.responseXML)
                    postProcess(response.responseXML);
            }
        });
    }
}

function showCompletionDiv(elem)
{
    if (!_eventSinceHidden)
        return;
    
    var div = getCompletionDiv();
    if (isCompletionVisible())
        return;

    var posLeft = 0;
    var posTop = 0;
    var offsetElem = elem;
    if (offsetElem.tagName == "TEXTAREA")
    {
        var height = offsetElem.offsetHeight;
        if (height)
        {
            var rows = elem.rows;
            if (rows)
            {
                var heightPerRow = (0.0 + height) / (0.0 + rows);
                var lineCount = 1;
                for (var i = 0; i < offsetElem.value.length; i++)
                {
                    if (offsetElem.value.charAt(i) == '\n')
                        lineCount++;
                }
                var textHeight = Math.min(lineCount * (heightPerRow), height);
                // offset us back a sufficient number of lines if we're in a textarea:
                posTop = textHeight - height;
            }
        }
    }

    while (offsetElem.tagName != "BODY")
    {
        posLeft += offsetElem.offsetLeft;
        posTop += offsetElem.offsetTop;
        offsetElem = offsetElem.offsetParent;
    }

    posTop += elem.offsetHeight;
    div.style.top = posTop;
    div.style.left = posLeft;
    div.style.display = "block";
    return false;
}


function hideCompletionDiv()
{
    _hideTimer = setTimeout("hideCompletionDivImmediate()", 100);
    _eventSinceHidden = false;
    return false;
}

function hideCompletionDivImmediate()
{
    getCompletionDiv().style.display = 'none';
    _optionSelectedIndex = 0;
}

LABKEY.addMarkup(
'<div id="completionDiv" class="labkey-completion">' +
'  <table>' +
'    <tr>' +
'      <td>' +
'        <span id="completionBody"></span>' +
'      </td>' +
'    </tr>' +
'  </table>' +
'</div>'
);
