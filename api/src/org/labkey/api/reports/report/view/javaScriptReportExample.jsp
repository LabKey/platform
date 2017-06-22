<%
/*
 * Copyright (c) 2011-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
%>
var jsDiv;

// When a JavaScript report is viewed, LabKey calls the render() function, passing a query config
// and a div element. This sample code simply stashes the div, initializes callback functions, and
// calls getRawData() to retrieve the data from the server. See the "Help" tab for more details.
function render(queryConfig, div)
{
    jsDiv = div;
    queryConfig.success = onSuccess;
    queryConfig.error = onError;
    LABKEY.Query.GetData.getRawData(queryConfig);

    // If not using the GetData API you can use SelectRows instead:
    // LABKEY.Query.selectRows(queryConfig);
}

function onSuccess(results)
{
    jsDiv.innerHTML = results.rows.length + ' rows returned';
}

function onError(errorInfo)
{
    jsDiv.innerHTML = errorInfo.exception;
}
