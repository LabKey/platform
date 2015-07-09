<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.visualization.VisualizationController" %>
<%@ page extends="org.labkey.api.jsp.JspBase"%>
<%--
/*
 * Copyright (c) 2014-2015 LabKey Corporation
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
--%>
<%
    ViewContext context = HttpView.currentContext();
    ActionURL endpoint = new ActionURL(VisualizationController.GetDataAction.class,context.getContainer());
    if (context.getActionURL().getAction().startsWith("cds"))
        endpoint = new ActionURL(VisualizationController.cdsGetDataAction.class,context.getContainer());
%>
<textarea id="json" style="height:400px; width: 100%;">

</textarea>
<button onclick="getData()">get data</button>
<script>
if (Ext4||Ext)
{
    var resizer = new (Ext4||Ext).Resizable("json", {
        handles: 'se',
        minWidth: 200,
        minHeight: 100,
        maxWidth: 1200,
        maxHeight: 800,
        pinned: true
    });
}

function getData()
{
    document.getElementById("response").innerHTML = "";

    var config, json = document.getElementById("json").value;
    try
    {
        config = JSON.parse(json);
    }
    catch (error)
    {
        // try more forgiving parsing
        eval("_json_=(" + json + ")");
        config = _json_;
    }


    config.endpoint = <%=q(endpoint.getLocalURIString(false))%>;
    config.success = function(json, response)
    {
        var r = response.responseText;
        if (Ext4||Ext)
            r = (Ext4||Ext).util.Format.htmlEncode(r);
        document.getElementById("response").innerHTML = r;
    };
    LABKEY.Query.Visualization.getData(config);
}
</script>

<pre id="response" style="border:solid 1px grey; min-height:100px;"></pre>
<div id="grid" style="border:solid 1px grey; min-height:100px;"></div>