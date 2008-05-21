/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
var selectTarget = null;
var selectMatch = "";
var selectTimer = null;
function selectKeyPress()
    {
    if (selectTarget != event.srcElement)
        selectMatch = "";
    
    selectTarget = event.srcElement;
    
    selectMatch += String.fromCharCode(event.keyCode).toLowerCase();
    
    updateSelection();
                    
    event.cancelBubble=true;
    event.keyCode = 0;
    
    resetTimer();
    return false;
    }

function clearMatch()
    {
    selectMatch = "";
    window.status = "";
    }

function updateSelection()
    {
    var options = selectTarget.options;
    for (var i = 0; i < options.length; i++)
        {
        if (selectMatch == options[i].text.substr(0, selectMatch.length).toLowerCase())
            {
            selectTarget.selectedIndex = i;
            break;
            }
        }

    window.status = selectMatch + ": " + selectTarget.options[selectTarget.selectedIndex].text;
    }
    
function selectKeyDown()
    {
    if (event.keyCode != 8)
        return true;
        
    if (selectMatch != "")
        selectMatch = selectMatch.substr(0, selectMatch.length - 1);

    updateSelection();
    resetTimer();
    return false;
    
    }

function resetTimer()
    {
    if (null != selectTimer)
        window.clearTimeout(selectTimer);
    
    selectTimer = window.setTimeout("clearMatch()", 5000);
    }
    
