<%
/*
 * Copyright (c) 2010-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<%
    ViewContext context = getViewContext();
    ActionURL url = context.cloneActionURL();
    String controller = url.getController();
    String action = url.getAction();
    String method = context.getRequest().getMethod();
%>

<div id="statusDiv"></div>

<script type="text/javascript">
    var div = document.getElementById("statusDiv");
    var offset = 0;
    var parameters = LABKEY.ActionURL.getParameters();
    var controller = <%=q(controller)%>;
    var action = <%=q(action)%>;
    var method = <%=q(method)%>;
    var isRecursiveCall = false;

    Ext4.onReady(makeRequest);

    function makeRequest()
    {
        // If called with GET with the temp=true, then first POST from here needs to kick off Runnable (don't add offset)
        if ('POST' == method || isRecursiveCall)
            parameters.offset = offset;
        else if ('GET' == method && true != parameters.temp && 'true' != parameters.temp)
            parameters.offset = 0;      // Just a GET; don't force POST

        Ext4.Ajax.request({
            url: LABKEY.ActionURL.buildURL(controller, action, null, parameters),
            method: 'POST',
            success: LABKEY.Utils.getCallbackWrapper(appendStatus),
            failure: function(){setTimeout(makeRequest, 1000)}
        });
    }

    function appendStatus(json)
    {
        isRecursiveCall = true;
        if (json)
        {

            var status = json["status"];

            if (status.length > 0)
            {
                div.innerHTML = div.innerHTML + status.join("<br>\n") + "<br>\n";
                offset = offset + status.length;
            }

            // If task is not complete then schedule another status update in one second.
            if (!json["complete"])
            {
                setTimeout(makeRequest, 1000);
            }
        }
        else
        {
            // no json in response means this request had to kick off the Runnable
            parameters.offset = 0;
            setTimeout(makeRequest, 1000);
        }
    }
</script>
