/*
 * Copyright (c) 2011-2012 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
function clickLink(controller, action, parameters)
{
    document.location = LABKEY.ActionURL.buildURL(controller, action, LABKEY.ActionURL.getContainer(), parameters);
    return false;
}

function addOption(selectEl, text, value)
{
    var option = document.createElement("option");
    option.text = text;
    option.value = value != null ? value : text;
    if (selectEl != null && selectEl.options != null)    // IE7 compatible
        selectEl.options[selectEl.options.length] = option;
}

function getDropDownPopulator(elementId, defaultOptionText, defaultOptionValue, optionCallback)
{
    return function(data)
    {
        var selectEl = document.getElementById(elementId);
        // Clear the list before re-populating:
        while (selectEl.length > 0)
            selectEl.remove(selectEl.length - 1);
        addOption(selectEl, defaultOptionText, defaultOptionValue);
        for (var i = 0; i < data.rows.length; i++)
        {
            var option = optionCallback(data.rows[i]);
            addOption(selectEl, option.text, option.value);
        }
    }
}

function getDropDownValue(elementId)
{
    var dropDown = document.getElementById(elementId);
    return dropDown.options[dropDown.selectedIndex].value;
}

function populateDropDown(elementId, schemaName, queryName, columnName, defaultOptionText, defaultOptionValue, optionCallback)
{
    if (!optionCallback)
        optionCallback = function(row) { return { text: row[columnName], value: row[columnName] };};
    LABKEY.Query.selectRows({
        schemaName: schemaName,
        queryName: queryName,
        columns: columnName,
        sort: columnName,
        success: getDropDownPopulator(elementId, defaultOptionText, defaultOptionValue, optionCallback)
    });
}