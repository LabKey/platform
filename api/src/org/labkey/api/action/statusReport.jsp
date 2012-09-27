<%
/*
 * Copyright (c) 2010-2011 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%> <%
    ViewContext context = HttpView.currentContext();
    ActionURL url = context.cloneActionURL();
%>

<div id="statusDiv"></div>

<script type="text/javascript">
    var div = document.getElementById("statusDiv");
    var offset = 0;
    var baseURL = <%=q(url.toString() + "offset=")%>;

    Ext.onReady(makeRequest);

    function makeRequest()
    {
        Ext.Ajax.request({
            url: baseURL + offset,
            method: 'POST',
            success: LABKEY.Utils.getCallbackWrapper(appendStatus),
            failure: function(){setTimeout(makeRequest, 1000)}
        });
    }

    function appendStatus(response)
    {
        var status = response["status"];

        if (status.length > 0)
        {
            div.innerHTML = div.innerHTML + status.join("<br>\n") + "<br>\n";
            offset = offset + status.length;
        }

        // If task is not complete then schedule another status update in one second.
        if (!response["complete"])
        {
            setTimeout(makeRequest, 1000);
        }
    }
</script>
