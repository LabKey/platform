<%
/*
 * Copyright (c) 2008-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.query.controllers.QueryController" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("clientapi/ext3"); // LABKEY.ext.Utils.handleTabsInTextArea
        dependencies.add("Ext4");
    }
%>
<%
    JspView me = (JspView) HttpView.currentView();
    ActionURL defGetUrl = new ActionURL(QueryController.SelectRowsAction.class, getContainer());
    String schemaName = HttpView.currentRequest().getParameter("schemaName");
    if (schemaName == null)
        schemaName = "**schema**";
    defGetUrl.addParameter("schemaName", schemaName);

    String queryName = HttpView.currentRequest().getParameter("query.queryName");
    if (queryName == null)
        queryName = "**query**";
    defGetUrl.addParameter("query.queryName", queryName);
%>
<style type="text/css">
    .error
    {
        background-color: #FF0000;
        color: #FFFFFF;
        border:1px solid #990000;
        padding:2px
    }
    .status
    {
        background-color:#FFFF99;
        border:1px solid #CCCC99;
        padding:2px
    }
    pre.response
    {
        font-family: 'Courier New', 'Courier', 'monospace';
        font-size: 10pt;
        width:100%;
        border:1px solid #A0A0A0
    }
</style>


<div id="lblStatus" class="status">Enter a Url in the "Get Url" box and click "Get"</div>
<table width="100%">
    <tr>
        <td width="1%">Get&nbsp;Url:</td>
        <td width="98%"><input type="text" id="txtUrlGet" style="width:100%"
                               value="" onkeydown="onGetUrlKeyDown(event)"/>
        </td>
        <td width="1%">
            <input type="button" value="Get" id="btnGet" onclick="getUrl()"/>
        </td>
    </tr>
    <tr>
        <td width="1%">Post&nbsp;Url:</td>
        <td width="98%"><input type="text" id="txtUrlPost" style="width:100%"
                               value=""/>
        </td>
        <td width="1%">
            <input type="button" value="Post" id="btnPost" onclick="postUrl()"/>
        </td>
    </tr>
    <tr>
        <td colspan="3" width="100%">Post Body:<br/><textarea id="txtPost" rows="10" cols="80" style="width:100%"></textarea></td>
    </tr>
</table>
<table width="100%">
    <tr>
        <td>Response size: <span id="respSize" /></td>
    </tr>
    <tr>
        <td>Response time: <span id="respTime" /></td>
    </tr>
    <tr>
        <td>Response body:</td>
    </tr>
    <tr>
        <td width="100%"><pre id="lblResponse" class="response">&nbsp;</pre></td>
    </tr>
    <tr>
        <td width="100%">
            <input type="button" id="btnEval" value="Eval" onclick="evalResponse()"/>&nbsp;
            <input type="button" id="btnSaveTest" value="Record Test" onclick="recordTest()"/></td>
    </tr>
</table>

<script type="text/javascript">
    var startTime;
    var responseJSON;

    function getXmlHttpRequest()
    {
        if (window.XMLHttpRequest)
            return new XMLHttpRequest();
        else if (window.ActiveXObject)
            return new ActiveXObject("Msxml2.XMLHTTP");
        else
            return null;
    }

    function getUrl()
    {
        startTime = new Date();
        var url = document.getElementById("txtUrlGet").value;
        var req = getXmlHttpRequest();
        if (null == url || url.length == 0)
            return;

        if (null == req)
        {
            onError("Couldn't get the XMLHttpRequest object!");
            return;
        }

        setStatus("Getting URL...");

        try
        {
            req.onreadystatechange = function()
            {
                if (req.readyState == 4)
                {
                    if (req.status == 200 && onSuccess)
                        onSuccess(req.responseText);
                    if (req.status != 200 && onError)
                        onError(req.statusText, req.responseText);
                }
            };
            req.open("GET", url, true);
            req.send(null);
        }
        catch(e)
        {
            onError(e);
        }
    }

    function updateStats(responseText)
    {
        var endTime = new Date();
        var ms = endTime - startTime;
        var respTime = document.getElementById("respTime");
        if (ms < 1000)
            respTime.innerHTML = "" + ms + "ms";
        else
            respTime.innerHTML = "" + (ms/1000) + "s";

        var respSize = document.getElementById("respSize");
        var sizeBytes = responseText.length;
        var units = ['bytes', 'kb', 'mb', 'gb'];
        for (var unitIndex = 0; unitIndex < units.length && sizeBytes > 1000; unitIndex++)
            sizeBytes /= 1000;
        // shift left, round to nearest int, shift right (to get two decimal places):
        var truncated = Math.round(sizeBytes*100)/100;
        respSize.innerHTML = '' + truncated + units[unitIndex];
        startTime = undefined;
    }

    function onError(message, responseText)
    {
        updateStats(responseText);
        setError(message);
        var resp = document.getElementById("lblResponse");
        if (null != resp && null != responseText)
            resp.innerHTML = responseText;
    }

    function onSuccess(responseText)
    {
        var resp = document.getElementById("lblResponse");
        if (null != resp)
        {
            // save the json response
            responseJSON = responseText;
            updateStats(responseText);
            resp.innerHTML = responseText;
            setStatus("Request Complete.");
        }
        else
            window.alert("Could not find the response text area!");
    }

    function postUrl()
    {
        startTime = new Date();
        var url = document.getElementById("txtUrlPost").value;
        var req = getXmlHttpRequest();
        var postMsg = document.getElementById("txtPost").value;
        if (null == url || url.length == 0)
            return;

        if (null == req)
        {
            onError("Couldn't get the XMLHttpRequest object!");
            return;
        }

        setStatus("Posting content to URL...");

        try
        {
            req.onreadystatechange = function()
            {
                if (req.readyState == 4)
                {
                    if (req.status == 200 && onSuccess)
                        onSuccess(req.responseText);
                    if (req.status != 200 && onError)
                        onError(req.statusText, req.responseText);
                }
            };
            req.open("POST", url, true);
            req.setRequestHeader("content-type", "application/json");
            req.setRequestHeader('X-LABKEY-CSRF', LABKEY.CSRF);
            req.send(postMsg);
        }
        catch(e)
        {
            onError(e);
        }
    }

    function setStatus(msg, styleClass)
    {
        var div = document.getElementById("lblStatus");
        if (null != div)
        {
            div.innerHTML = msg;
            if (null != styleClass)
                div.className = styleClass;
            else
                div.className = "status";
        }
    }

    function setError(msg)
    {
        setStatus("ERROR: " + msg, "error");
    }

    function evalResponse()
    {
        try
        {
            if (responseJSON != undefined)
                var obj = eval("(" + responseJSON + ")");
            else
                var obj = eval("(" + document.getElementById("lblResponse").innerHTML + ")");
            window.alert("JSON is valid.");
        }
        catch(e)
        {
            window.alert("JSON eval failed: " + e);
        }
    }

    function recordTest()
    {
        var pairs = [];
        var getUrl = document.getElementById("txtUrlGet").value;
        var postUrl = document.getElementById("txtUrlPost").value;
        var postData = document.getElementById("txtPost").value;
        var response = responseJSON;

        pairs.push('getUrl=' + encodeURIComponent(encodeURI(getUrl)));
        pairs.push('postUrl=' + encodeURIComponent(encodeURI(postUrl)));

        if (postData != null)
            pairs.push('postData=' + encodeURIComponent(postData));
        if (response != null)
            pairs.push('response=' + encodeURIComponent(response));

        var req = getXmlHttpRequest();

        if (null == req)
        {
            onError("Couldn't get the XMLHttpRequest object!");
            return;
        }

        setStatus("Recording the data as a Test Case...");
        try
        {
            req.onreadystatechange = function()
            {
                if (req.readyState == 4)
                {
                    if (req.status == 200 && onSuccess)
                        showTest(req.responseText);
                    if (req.status != 200 && onError)
                        onError(req.statusText, req.responseText);
                }
            };
            req.open("POST", "<%=new ActionURL(QueryController.SaveApiTestAction.class, getContainer()).getLocalURIString()%>", true);
            req.setRequestHeader("content-type", "application/x-www-form-urlencoded");
            req.send(pairs.join('&'));
        }
        catch(e)
        {
            onError(e);
        }
    }

    function showTest(responseText)
    {
        var obj = eval("(" + responseText + ")");
        var win = Ext4.create('Ext.Window', {
            title: 'Recorded Test',
            border: false,
            constrain: true,
            closeAction: 'close',
            autoScroll: true,
            autoShow: true,
            modal: true,
            items: [{
                name: 'description',
                xtype: 'textarea',
                grow: true,
                growMax: 600,
                width: 750,
                value: obj.xml
            }],
            buttons: [{
                text: 'Close',
                id: 'btn_close',
                handler: function(){win.close();}
            }]
        });
    }

    function onGetUrlKeyDown(event)
    {
        var evt = event || window.event;
        var code = evt.charCode || evt.keyCode;
        if (code === 13)
            getUrl();
    }

    Ext.onReady(function() {
        Ext.EventManager.on('txtPost', 'keydown', LABKEY.ext.Utils.handleTabsInTextArea);
    });
</script>