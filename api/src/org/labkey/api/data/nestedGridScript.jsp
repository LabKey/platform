<%
/*
 * Copyright (c) 2006-2016 LabKey Corporation
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
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<script type="text/javascript"><!--

var requestedURLs = {};

function toggleNestedGrid(dataRegionName, url, elementName)
{
    var contentElement = document.getElementById(dataRegionName + "-Content" + elementName);
    var rowElement = document.getElementById(dataRegionName + "-Row" + elementName);
    var toggleElement = document.getElementById(dataRegionName + "-Handle" + elementName);

    if (contentElement.innerHTML == "" && url)
    {
        if (requestedURLs[url] == null)
        {
            Ext.Ajax.request(
            {
                url: url,
                method: "GET",
                callback: function(options, success, response)
                {
                    if (success && response.status == 200)
                    {
                        var rowElement = document.getElementById(dataRegionName + "-Row" + elementName);
                        var toggleElement = document.getElementById(dataRegionName + "-Handle" + elementName);
                        var contentElement = document.getElementById(dataRegionName + "-Content" + elementName);

                        contentElement.innerHTML = response.responseText;
                        toggleElement.src = "<%=getWebappURL("_images/minus.gif")%>";
                        rowElement.style.display = "";
                    }
                    else
                    {
                        requestedURLs[url] = null;
                    }
                }
            });
            requestedURLs[url] = elementName;
        }
        return false;
    }

    if (rowElement.style.display == "none")
    {
        rowElement.style.display = "";
        toggleElement.src = "<%=getWebappURL("_images/minus.gif")%>";
    }
    else
    {
        rowElement.style.display = "none";
        toggleElement.src = "<%=getWebappURL("_images/plus.gif")%>";
    }
}

--></script>
