var jsDiv;

// When a JavaScript report is viewed, LabKey calls the render() function, passing a query config
// and a div element. This sample code simply stashes the div, initializes callback functions, and
// calls selectRows() to retrieve the data from the server. See the "Help" tab for more details.  
function render(queryConfig, div)
{
    jsDiv = div;
    queryConfig.success = onSuccess;
    queryConfig.error = onError;
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
