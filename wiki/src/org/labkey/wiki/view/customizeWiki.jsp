<%
/*
 * Copyright (c) 2004-2014 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.apache.commons.lang3.StringUtils"%>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.util.HString" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.wiki.WikiController" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!

    public LinkedHashSet<ClientDependency> getClientDependencies()
    {
        LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
        resources.add(ClientDependency.fromFilePath("Ext4"));
        return resources;
    }
%>
<%
    WikiController.CustomizeWikiPartView me = (WikiController.CustomizeWikiPartView) HttpView.currentView();
    Portal.WebPart webPart = me.getModelBean();
    Container currentContainer = getContainer();
%>
<script type="text/javascript">

//store current container id on client
var currentContainerId = <%=currentContainer==null ? "null" : PageFlowUtil.jsString(currentContainer.getId())%>;
var m = {};

function getForm()
{
    return document.forms[1];
}
function updatePageList()
{
    //get selection value
    var containerId = getForm().webPartContainer.value;
    var wikiPageList = containerId in m ? m[containerId] : null;

    if (null != wikiPageList)
    {
        loadPages(wikiPageList);
    }
    else
    {
        //disable the submit button while we're fetching the list of pages
        disableSubmit();

        //show a "loading..." option while AJAX request is happening
        var select = getForm().name;
        select.options.length = 0;
        o = new Option("loading...", "", true, true);
        select.options[select.options.length] = o;

        Ext4.Ajax.request({
            url: LABKEY.ActionURL.buildURL("wiki", "getPages"),
            success: onSuccess,
            failure: onError,
            method: 'GET',
            params: {'id' : containerId}
        });
    }
    return true;
}


function loadPages(wikiPageList)
{
    var select = getForm().name;
    if (null == select)
        return;

    select.options.length = 0;
    var text;
    var fDefaultSelected;
    var o;

    if (wikiPageList != null)
    {
        for(var i = 0; i < wikiPageList.length; i++)
        {
            text = wikiPageList[i].name + " (" + wikiPageList[i].title + ")";
            fDefaultSelected = wikiPageList[i].name.toLowerCase() == "default";
            o = new Option(text, wikiPageList[i].name, fDefaultSelected, fDefaultSelected);
            select.options[select.options.length] = o;
        }
    }
    else
    {
        o = new Option("<no pages>", "", true, true);
        select.options[select.options.length] = o;
    }

    //re-enable the submit button while we're fetching the list of pages
    enableSubmit();
}

function enableSubmit()
{
    var btn = document.getElementById("btnSubmit");
    btn.className = "labkey-button";
    btn.href = "";
    btn.disabled = false;
}

function disableSubmit()
{
    var btn = document.getElementById("btnSubmit");
    btn.disabled = true;
    btn.className = "labkey-disabled-button";
    btn.href = "javascript:return false;";
}

function onSuccess(response, config)
{
    //parse the response text as JSON
    var json = Ext4.decode(response.responseText);
    if (null != json)
    {
        //add the page list to the global map so that we don't need to fetch it again
        m[config.params.id] = json.pages;
        loadPages(json.pages);
    }
    else
        window.alert("Unable to parse the response from the server!");
}

function onError(response, config)
{
    if (response.status >= 500 && response.status <= 599)
    {
        //exception thrown within the server
        //parse the response text as JSON
        var json = Ext4.decode(response.responseText);
        window.alert("The server experienced the following error: " + json.exception);
    }
    else if (response.status >= 400 && response.status <= 499)
    {
        //invalid container id
        var json = Ext4.decode(response.responseText);
        window.alert("The server could not find the selected project or folder: " + json.exception);
    }
    else
        window.alert("Problem communicating with the server: " + response.statusText + " (" + response.status + ")");

    var select = getForm().name;
    select.options.length = 0;
    o = new Option("<error getting pages>", "", true, true);
    select.options[select.options.length] = o;

}

function restoreDefaultPage()
{
    if (!currentContainerId)
        return;

    //set webPartContainer select value to current container
    getForm().webPartContainer.value = currentContainerId;
    updatePageList();
}
</script>


<labkey:form name= "frmCustomize" method="post">
<table>
    <tr>
        <td colspan="2">
            To display a different wiki page in this web part, first select the folder containing the page
            you want to display, then select the name of the page.<br><br>
        </td>
    </tr>
    <tr>
        <td width="20%" nowrap="1">
        Folder containing the page to display:
        </td>
        <td width="80%">
        <select name="webPartContainer" onkeyup="updatePageList();" onchange="updatePageList();"><%
            String webPartContainer = StringUtils.trimToNull(webPart.getPropertyMap().get("webPartContainer"));
            for (Container c : me.getContainerList())
            {
                boolean selected = false;
                //if there's no property setting for container, select the current container.
                if (webPartContainer == null)
                {
                    if (null != currentContainer && c.getId().equals(currentContainer.getId()))
                        selected = true;
                }
                else if (c.getId().equals(webPartContainer))
                {
                    selected = true;
                }
                out.write("\n");
                %><option<%=selected(selected)%> value="<%=text(c.getId())%>"><%=h(c.getPath())%></option><%
            }
        %></select>
        <%=textLink("Reset to Folder Default Page", "javascript:restoreDefaultPage();")%>
        </td>
     </tr>
    <tr>
        <td width="20%" nowrap="1">
        Name and title of the page to display:
        </td>
        <td width="80%">
        <select name="name">
            <%
            //if current container has no pages
            if (null == me.getContainerNameTitleMap() || me.getContainerNameTitleMap().size() == 0)
            {%>
                <option selected value="">&lt;no pages&gt;</option>
            <%}
            else
            {
                for (Map.Entry<HString, HString> entry : me.getContainerNameTitleMap().entrySet())
                {
                    HString name = entry.getKey();
                    HString title = entry.getValue();

                    //if there's a "default" page and no other page has been selected as default, select it.
                    if (name.equalsIgnoreCase("default") && webPart.getPropertyMap().get("name") == null)
                    {%>
                        <option selected value="<%=h(name)%>"><%=h(name + " (" + title + ")")%></option>
                    <%}
                    else
                    {%>
                        <option<%=selected(name.equals(webPart.getPropertyMap().get("name")))%> value="<%=h(name)%>"><%=h(name + " (" + title + ")")%></option>
                    <%}
                }
            }%>
        </select>
        </td>
    </tr>
<tr>
    <td colspan="2" align="left">
        <table>
            <tr>
                <td align="left">
                    <%= button("Submit").submit(true).attributes("name=\"Submit\" id=\"btnSubmit\"") %>
                    <%= button("Cancel").href(getContainer().getStartURL(getUser())) %>
                </td>
            </tr>
        </table>
    </td>
</tr>
</table>
</labkey:form>
