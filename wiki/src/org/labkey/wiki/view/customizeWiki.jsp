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
<%@ page import="org.labkey.api.util.element.Option.OptionBuilder" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.wiki.WikiController" %>
<%@ page import="java.util.stream.Stream" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    WikiController.CustomizeWikiPartView me = (WikiController.CustomizeWikiPartView) HttpView.currentView();
    Portal.WebPart webPart = me.getModelBean();
    Container currentContainer = getContainer();
%>
<script type="text/javascript" nonce="<%=getScriptNonce()%>">

//store current container id on client
var currentContainerId = LABKEY.container.id;
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
            fDefaultSelected = wikiPageList[i].name.toLowerCase() === "default";
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
    btn.className = "labkey-button primary";
    btn.href = "";
    btn.disabled = false;
}

function disableSubmit()
{
    var btn = document.getElementById("btnSubmit");
    btn.disabled = true;
    btn.className = "labkey-disabled-button primary";
    btn.href = "#";
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
    var json;
    if (response.status >= 500 && response.status <= 599)
    {
        //exception thrown within the server
        //parse the response text as JSON
        json = JSON.parse(response.responseText);
        LABKEY.Utils.alert("The server experienced the following error: " + json.exception);
    }
    else if (response.status >= 400 && response.status <= 499)
    {
        //invalid container id
        json = JSON.parse(response.responseText);
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
    <%
        String webPartContainer = StringUtils.trimToNull(webPart.getPropertyMap().get("webPartContainer"));
    %>
    <%=select().label("Folder containing the page to display").name("webPartContainer").onChange("updatePageList();").onKeyUp("updatePageList();").addOptions(
        me.getContainerList().stream()
            .map(c->{
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
                return new OptionBuilder(c.getPath(), c.getId()).selected(selected);
            }))%>
    <span class="help-block">You can also <%=link("restore to this folder's default page.").onClick("restoreDefaultPage();").clearClasses()%></span><br>
    <%
        final Stream<OptionBuilder> builders;

        //if current container has no pages
        if (null == me.getContainerNameTitleMap() || me.getContainerNameTitleMap().isEmpty())
        {
            builders = Stream.of(new OptionBuilder().value("").label("no pages"));
        }
        else
        {
            builders = me.getContainerNameTitleMap().entrySet().stream()
                .map(entry->{
                    String name = entry.getKey();
                    String title = entry.getValue();
                    //if there's a "default" page and no other page has been selected as default, select it.
                    boolean selected = (name.equalsIgnoreCase("default") && webPart.getPropertyMap().get("name") == null) || name.equals(webPart.getPropertyMap().get("name"));

                    return new OptionBuilder().value(name).label(name + " (" + title + ")").selected(selected);
                });
        }
    %>
    <%=select().name("name").label("Page to display").addOptions(builders)%>
    <br>
    <%= button("Submit").submit(true).id("btnSubmit") %>
    <%= button("Cancel").href(getContainer().getStartURL(getUser())) %>
</labkey:form>