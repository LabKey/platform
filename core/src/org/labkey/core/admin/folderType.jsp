<%
/*
 * Copyright (c) 2005-2012 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.module.FolderType" %>
<%@ page import="org.labkey.api.module.Module" %>
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.Comparator" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Set" %>
<%@ page import="org.labkey.core.admin.FolderManagementAction" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.json.JSONObject" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<labkey:errors/>

<script>
var requiredModules = new Object();
var defaultModules = new Object();
<% //Generate javascript objects...
final ViewContext context = HttpView.currentContext();
Container c = context.getContainerNoTab();
FolderManagementAction.FolderManagementForm form = (FolderManagementAction.FolderManagementForm) HttpView.currentModel();
Collection<FolderType> allFolderTypes = ModuleLoader.getInstance().getFolderTypes();
List<Module> allModules = new ArrayList<Module>(ModuleLoader.getInstance().getModules());
Collections.sort(allModules, new Comparator<Module>()
{
    public int compare(Module o1, Module o2)
    {
        return o1.getTabName(context).compareToIgnoreCase(o2.getTabName(context));
    }
});
Set<Module> activeModules = c.getActiveModules();
Set<Module> requiredModules = c.getRequiredModules();
Map<String, Set<String>> dependencyMap = c.getModuleDependencyMap();
JSONObject dependencyMapJson = new JSONObject();
for (String m : dependencyMap.keySet())
{
    dependencyMapJson.put(m, dependencyMap.get(m));
}

Module defaultModule = c.getDefaultModule();
FolderType folderType = c.getFolderType();

for (FolderType ft : allFolderTypes)
{
if (!ft.isWorkbookType())
{
    String arraySep = "";
%>
        requiredModules["<%=ft.getName()%>"] =  [<%
        for (Module m : ft.getActiveModules())
        {
            if (null != m) //FIX: 4612: active module might not be present in the build
            {
                out.print(arraySep + "\"" + m.getName() + "\"");
                arraySep = ",";
            }
        } %>];<%
        if (null != ft.getDefaultModule())
            out.print("defaultModules[\"" + ft.getName() + "\"] = \"" + ft.getDefaultModule().getName() + "\";\n");
    }
}
%>
var switchedOnLast = new Object();
function changeFolderType()
{
    var i;
    var name = getSelectedFolderType();

    var required = requiredModules[name];
    //Make a map
    var map = new Object();
    for (i = 0; i < required.length; i++)
        map[required[i]] = true;

    var switchedOn = new Object();
    for (i = 0; i < <%= allModules.size() %>; i++)
    {
        var current = document.folderModules['activeModules[' + i + ']'];
        if (current != undefined)
        {
            if (switchedOnLast[current.value])
                current.checked = false;
            if (!current.checked && map[current.value])
            {
                current.checked = true;
                switchedOn[current.value] = true;
            }
            current.disabled = map[current.value];
        }
    }
    switchedOnLast = switchedOn;
    updateDefaultOptions();
}

function getSelectedFolderType()
{
    var options = document.folderModules.folderType;
    for (var i = 0; i < options.length; i++)
        if (options[i].checked)
            return options[i].value;

    return null;
}

function updateDefaultOptions(cb)
    {
    var dependencyMap = <%=dependencyMapJson%>;
    //if this module is required by others, we alert before disabling it
    if(cb)
    {
        if(!cb.checked && dependencyMap[cb.value]){
            var dm = [];
            //only warn about dependencies if they are active
            Ext.each(dependencyMap[cb.value], function(m){
                var els = Ext.Element.select('input[value='+m+'][type="checkbox"]');
                if(!els.elements.length){
                    return;
                }
                if(els.elements[0].checked)
                    dm.push(m);
            }, this);

            if(dm && dm.length){
                Ext.Msg.confirm('Warning', 'This module is required by the following other active modules: ' + dm.join(', ') + '. Disabling this module will also disable these modules.  Do you want to continue?', function(btn){
                    if(btn == 'yes'){
                        //uncheck boxes without firing onclick events
                        uncheckEl(cb.value);
                        Ext.each(dm, function(m){
                            uncheckEl(m);
                        }, this);

                        function uncheckEl(m){
                            var el = Ext.Element.select('input[value='+m+'][type="checkbox"]');
                            el.elements[0].checked = false;
                        }
                    }
                }, this);
                return false;
            }
        }
        else {
            //also turn on dependencies, silently
            for (var m in dependencyMap){
                if(dependencyMap[m].indexOf(cb.value) > -1)
                {
                    var el = Ext.Element.select('input[value='+m+'][type="checkbox"]');
                    el.elements[0].checked = true;
                }
            }
        }
    }

    var defaultDropdown = document.folderModules.defaultModule;

    var defaultName = "";
    if (defaultDropdown.selectedIndex > -1 && defaultDropdown.options.length > 0)
        defaultName = defaultDropdown.options[defaultDropdown.selectedIndex].value;

    defaultDropdown.options.length = 0;
    var optionIndex = 0;
    var folderTypename = getSelectedFolderType();
    if (null == folderTypename)
        folderTypename = "None";

    if ("None" != folderTypename)
    {
        document.getElementById("defaultTabDiv").style.display = "none";
        //setNodeText(document.getElementById("tabsHeader"), "Available Modules:");
        return true;
    }
    else
    {
        document.getElementById("defaultTabDiv").style.display = "";
        //setNodeText(document.getElementById("tabsHeader"), "Available Folder Tabs:");
    }
        
    for (var i = 0; i < <%= allModules.size() %>; i++)
        {
        var current = document.folderModules['activeModules[' + i + ']'];

        if (current != undefined && current.checked)
            {
            defaultDropdown.options[optionIndex] = new Option(current.value);
            defaultDropdown.options[optionIndex].value = current.value;
            defaultDropdown.options[optionIndex].text = current.title
            if (defaultName.length != 0 && current.value == defaultName)
                defaultDropdown.selectedIndex = optionIndex;
            optionIndex++;
            }
        }
    return true;
    }

function setNodeText(parent, text)
{
    parent.innerHTML = text;
    /* Test runner blows up with this...
    var child = parent.firstChild;
    var textNode = document.createTextNode(text);
    if (null != child)
        parent.replaceChild(textNode, child);
    else
        parent.appendChild(child);
    */
}
function validate()
{
    for (var i = 0; i < <%= allModules.size() %>; i++)
        {
        var module = document.folderModules['activeModules[' + i + ']'];
        if (module != undefined && module.checked)
            {
                return true;
            }
        }
    alert("Error: Please select at least one tab to display.");
    return false;
}
</script>
<form name="folderModules" method=POST action="<%=buildURL(FolderManagementAction.class)%>" onsubmit="return validate();">
    <input type="hidden" name="tabId" value="folderType">
    <table width="100%">
        <tr>
            <td valign="top"><%WebPartView.startTitleFrame(out, "Folder Type", null, "100%", null);%>
                <table>
    <%
        int radioIndex = 0;
        for (FolderType ft : allFolderTypes)
        {
            if (!ft.isWorkbookType())
            {
    %>
                <tr>
                    <td valign="top">
                        <input type="radio" name="folderType" value="<%=h(ft.getName())%>" <%=folderType.equals(ft) ? "checked" : "" %> onclick="changeFolderType();">
                     </td>
                    <td valign="top">
                       <span style="cursor:pointer;font-weight:bold" onclick="document.folderModules.folderType[<%=radioIndex%>].checked = true;"><%=h(ft.getLabel())%></span><br>
                        <%=h(ft.getDescription())%>
                    </td>
                </tr>
    <%
            radioIndex++;
            }
        }
    %>
                </table>
                <input type="hidden" name="wizard" value="<%=h(form.isWizard())%>">
                <%=generateSubmitButton((form.isWizard() ? "Next" : "Update Folder"))%>
    <%WebPartView.endTitleFrame(out);%>
    </td>
    <td width="30%" valign="top">
        <div id="defaultTabDiv" style="display:<%="None".equals(folderType.getName()) ? "" : "none"%>">
            <%WebPartView.startTitleFrame(out, "Default Tab", null, "100%", null);%>
            <select name="defaultModule" value="<%=defaultModule.getName()%>">
            <%
                    for (Module module : allModules)
                        {
                        if (activeModules.contains(module))
                            {
                            %>
                                <option value="<%= module.getName() %>" <%= module.getName().equals(defaultModule.getName()) ? "selected" : "" %>><%= module.getTabName(HttpView.currentContext()) %></option>
                            <%
                            }
                        }
            %>
            </select>
            <%WebPartView.endTitleFrame(out);%>
        </div>

        <%WebPartView.startTitleFrame(out, "Modules", null, "100%", null);%>

<%
    int i = 0;

for (Module module : allModules)
    {
    boolean active = activeModules.contains(module) || requiredModules.contains(module);
    boolean enabled = (module.getTabDisplayMode() == Module.TabDisplayMode.DISPLAY_USER_PREFERENCE ||
                module.getTabDisplayMode() == Module.TabDisplayMode.DISPLAY_USER_PREFERENCE_DEFAULT) && !requiredModules.contains(module);

    if (active || enabled)
        {
        %>
        <input type="checkbox" name="activeModules[<%= i++ %>]" title="<%= module.getTabName(HttpView.currentContext())%>" value="<%= module.getName()%>"
        <%= enabled ? "" : "disabled" %> <%= active ? "checked" : "" %> onClick="return updateDefaultOptions(this);"><%= module.getTabName(HttpView.currentContext()) %><br>
        <%
        }
    }
%>
    <%WebPartView.endTitleFrame(out);%>
</td></tr>
</table>
</form>
</div>
<script type="text/javascript">updateDefaultOptions()</script>
