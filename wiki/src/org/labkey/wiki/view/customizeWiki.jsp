<%
/*
 * Copyright (c) 2004-2017 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.wiki.WikiController" %>
<%@ page import="java.util.Map" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
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
    return document.getElementById('change-wiki-form');
}
function updatePageList()
{
    //get selection value
    var containerId = getForm().webPartContainer.value;

    if (m[containerId])
    {
        loadPages(m[containerId]);
    }
    else
    {
        //disable the submit button while we're fetching the list of pages
        disableSubmit();

        //show a "loading..." option while AJAX request is happening
        var select = getForm().name;
        select.options.length = 0;
        select.options[select.options.length] = new Option('loading...', '', true, true);

        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('wiki', 'getPages.api', undefined, { id: containerId }),
            method: 'GET',
            success: onSuccess.bind(this, containerId),
            failure: onError
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

    if (wikiPageList != null)
    {
        for(var i = 0; i < wikiPageList.length; i++)
        {
            text = wikiPageList[i].name + " (" + wikiPageList[i].title + ")";
            fDefaultSelected = wikiPageList[i].name.toLowerCase() == "default";
            select.options[select.options.length] = new Option(text, wikiPageList[i].name, fDefaultSelected, fDefaultSelected);
        }
    }
    else
    {
        select.options[select.options.length] = new Option("<no pages>", "", true, true);
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

function onSuccess(containerId, response)
{
    //parse the response text as JSON
    var json = JSON.parse(response.responseText);
    if (null != json)
    {
        //add the page list to the global map so that we don't need to fetch it again
        m[containerId] = json.pages;
        loadPages(json.pages);
    }
    else
        LABKEY.Utils.alert("Unable to parse the response from the server!");
}

function onError(response, config)
{
    if (response.status >= 500 && response.status <= 599)
    {
        //exception thrown within the server
        //parse the response text as JSON
        var json = JSON.parse(response.responseText);
        LABKEY.Utils.alert("The server experienced the following error: " + json.exception);
    }
    else if (response.status >= 400 && response.status <= 499)
    {
        //invalid container id
        var json = JSON.parse(response.responseText);
        LABKEY.Utils.alert("The server could not find the selected project or folder: " + json.exception);
    }
    else
        LABKEY.Utils.alert("Problem communicating with the server: " + response.statusText + " (" + response.status + ")");

    var select = getForm().name;
    select.options.length = 0;
    select.options[select.options.length] = new Option("<error getting pages>", "", true, true);
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
<labkey:form id="change-wiki-form" className="col-md-6 col-lg-5" method="POST">
    <labkey:select
            label="Folder containing the page to display"
            message="You can also <a href=\"javascript:restoreDefaultPage();\">restore to this folder's default page.</a>"
            name="webPartContainer"
            onKeyUp="updatePageList();"
            onChange="updatePageList();">
        <%
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
                    selected = true;
        %>
        <option<%=selected(selected)%> value="<%=text(c.getId())%>"><%=h(c.getPath())%></option>
        <%
            }
        %>
    </labkey:select>
    <labkey:select label="Page to display" name="name">
        <%
            //if current container has no pages
            if (null == me.getContainerNameTitleMap() || me.getContainerNameTitleMap().size() == 0)
            {%>
        <option selected value="">&lt;no pages&gt;</option>
        <%}
        else
        {
            for (Map.Entry<String, String> entry : me.getContainerNameTitleMap().entrySet())
            {
                String name = entry.getKey();
                String title = entry.getValue();

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
    </labkey:select>
    <%= button("Submit").submit(true).id("btnSubmit") %>
    <%= button("Cancel").href(getContainer().getStartURL(getUser())) %>
</labkey:form>