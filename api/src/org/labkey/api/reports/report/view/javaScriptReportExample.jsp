var jsDiv;

// When the report is viewed, LabKey calls the render() function, passing a query config and a div element.
// This example stashes the div, initializes the callback functions, and calls selectRows() to get the data. 
function render(queryConfig, div)
{
    jsDiv = div;
    queryConfig.successCallback = onSuccess;
    queryConfig.errorCallback = onError;
    LABKEY.Query.selectRows(queryConfig);
}

function onSuccess(results)
{
    jsDiv.innerHTML = results.rows.length + ' rows returned';
}

function onError(errorInfo)
{
    jsDiv.innerHTML = errorInfo.exception;
}
