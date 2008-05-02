<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.query.controllers.QueryControllerSpring" %>
<%
    JspView me = (JspView) HttpView.currentView();
    ActionURL defGetUrl = new ActionURL(QueryControllerSpring.GetQueryAction.class, me.getViewContext().getContainer());
    defGetUrl.addParameter("schemaName", "**schema**");
    defGetUrl.addParameter("query.queryName", "**query**");

    ActionURL defPostUrl = new ActionURL(QueryControllerSpring.UpdateRowsAction.class, me.getViewContext().getContainer());
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

<script type="text/javascript">
    function getXmlHttpRequest()
    {
        if(window.XMLHttpRequest)
            return new XMLHttpRequest();
        else if(window.ActiveXObject)
            return new ActiveXObject("Msxml2.XMLHTTP");
        else
            return null;
    }

    function getUrl()
    {
        var url = document.getElementById("txtUrlGet").value;
        var req = getXmlHttpRequest();
        if(null == url || url.length == 0)
            return;

        if(null == req)
        {
            onError("Couldn't get the XMLHttpRequest object!");
            return;
        }

        setStatus("Getting URL...");

        try
        {
            req.onreadystatechange = function()
            {
                if(req.readyState == 4)
                {
                    if(req.status == 200 && onSuccess)
                        onSuccess(req.responseText);
                    if(req.status != 200 && onError)
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

    function onError(message, responseText)
    {
        setError(message);
        var resp = document.getElementById("lblResponse");
        if(null != resp && null != responseText)
            resp.innerHTML = responseText;
    }

    function onSuccess(responseText)
    {
        var resp = document.getElementById("lblResponse");
        if(null != resp)
        {
            resp.innerHTML = responseText;
            setStatus("Request Complete.");
        }
        else
            window.alert("Could not find the response text area!");
    }

    function postUrl()
    {
        var url = document.getElementById("txtUrlPost").value;
        var req = getXmlHttpRequest();
        var postMsg = document.getElementById("txtPost").value;
        if(null == url || url.length == 0)
            return;

        if(null == req)
        {
            onError("Couldn't get the XMLHttpRequest object!");
            return;
        }

        setStatus("Posting content to URL...");

        try
        {
            req.onreadystatechange = function()
            {
                if(req.readyState == 4)
                {
                    if(req.status == 200 && onSuccess)
                        onSuccess(req.responseText);
                    if(req.status != 200 && onError)
                        onError(req.statusText, req.responseText);
                }
            };
            req.open("POST", url, true);
            req.setRequestHeader("content-type", "application/json");
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
        if(null != div)
        {
            div.innerHTML = msg;
            if(null != styleClass)
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
            var obj = eval("(" + document.getElementById("lblResponse").innerHTML + ")");
            window.alert("JSON is valid.");
        }
        catch(e)
        {
            window.alert("JSON eval failed: " + e);
        }
    }

</script>
<div id="lblStatus" class="status">Enter a Url in the "Get Url" box and click "Get"</div>
<table border="0" width="100%">
    <tr>
        <td width="1%">Get&nbsp;Url:</td>
        <td width="98%"><input type="text" id="txtUrlGet" style="width:100%"
                               value=""/>
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
<table border="0" width="100%">
    <tr>
        <td>Response:</td>
    </tr>
    <tr>
        <td width="100%"><pre id="lblResponse" class="response">&nbsp;</pre></td>
    </tr>
    <tr>
        <td width="100%"><input type="button" id="btnEval" value="Eval" onclick="evalResponse()"/></td>
    </tr>
</table>
