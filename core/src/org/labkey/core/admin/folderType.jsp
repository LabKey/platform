<%
/*
 * Copyright (c) 2005-2014 LabKey Corporation
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
<%@ page import="org.json.JSONObject"%>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.module.FolderType" %>
<%@ page import="org.labkey.api.module.Module" %>
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.view.WebPartView" %>
<%@ page import="org.labkey.core.admin.FolderManagementAction" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Collections" %>
<%@ page import="java.util.Comparator" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="java.util.Set" %>
<%@ page import="org.labkey.api.study.StudyService" %>
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
<labkey:errors/>

<script>
var requiredModules = new Object();
var defaultModules = new Object();
<% //Generate javascript objects...
    FolderManagementAction.FolderManagementForm form = (FolderManagementAction.FolderManagementForm) HttpView.currentModel();
    final ViewContext context = getViewContext();
    Container c = getContainer();
    boolean userHasEnableRestrictedModulesPermission = c.hasEnableRestrictedModules(getUser());
    Collection<FolderType> allFolderTypes = ModuleLoader.getInstance().getFolderTypes(userHasEnableRestrictedModulesPermission);
    List<Module> allModules = new ArrayList<>(ModuleLoader.getInstance().getModules(userHasEnableRestrictedModulesPermission));
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

    Module defaultModule = c.getDefaultModule(getUser());
    FolderType folderType = c.getFolderType();
    String path = c.getPath();
    boolean includeDataspace = c.isProject();       // Only include Dataspace as an option if container is a project

    for (FolderType ft : allFolderTypes)
    {
        if (!ft.isWorkbookType())
        {
            String arraySep = "";
%>
            requiredModules["<%=h(ft.getName())%>"] =  [<%
            for (Module m : ft.getActiveModules())
            {
                if (null != m) //FIX: 4612: active module might not be present in the build
                {
                    out.print(text(arraySep + "\"" + h(m.getName()) + "\""));
                    arraySep = ",";
                }
            } %>];<%
            if (null != ft.getDefaultModule())
                out.print(text("defaultModules[\"" + h(ft.getName()) + "\"] = \"" + h(ft.getDefaultModule().getName()) + "\";\n"));
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
        if(!cb.checked && dependencyMap[cb.value])
        {
            var dm = [];
            //only warn about dependencies if they are active
            Ext4.each(dependencyMap[cb.value], function(m){
                var els = Ext4.Element.select('input[value='+m+'][type="checkbox"]');
                if(!els.elements.length){
                    return;
                }
                if(els.elements[0].checked)
                    dm.push(m);
            }, this);

            if(dm && dm.length)
            {
                Ext4.Msg.confirm('Warning', 'This module is required by the following other active modules: ' + dm.join(', ') + '. Disabling this module will also disable these modules.  Do you want to continue?', function(btn){
                    if(btn == 'yes')
                    {
                        //uncheck boxes without firing onclick events
                        uncheckEl(cb.value);
                        Ext4.each(dm, function(m){
                            uncheckEl(m);
                        }, this);

                        function uncheckEl(m){
                            var el = Ext4.Element.select('input[value='+m+'][type="checkbox"]');
                            el.elements[0].checked = false;
                        }
                    }
                }, this);
                return false;
            }
        }
        else
        {
            //also turn on dependencies, silently
            for (var m in dependencyMap)
            {
                if(dependencyMap[m].indexOf(cb.value) > -1)
                {
                    var el = Ext4.Element.select('input[value='+m+'][type="checkbox"]');
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
    return false;
}

function checkChangedType()
{
    var currentType = '<%=h(folderType.getName())%>';
    if (currentType.toLowerCase() != getSelectedFolderType().toLowerCase())
    {
        return confirm("Are you sure you want to change a container tab's folder type?");
    }
    return true;
}

</script>
<form name="folderModules" id="folderModules" method=POST action="<%=text(buildURL(FolderManagementAction.class))%>" onsubmit="return validate();">
    <input type="hidden" name="tabId" value="folderType">
    <table width="100%">
        <tr>
            <td valign="top"><%WebPartView.startTitleFrame(out, "Folder Type", null, "100%", null);%>
                <table>
    <%
        int radioIndex = 0;
        for (FolderType ft : allFolderTypes)
        {
            if (!ft.isWorkbookType() && (includeDataspace || !StudyService.DATASPACE_FOLDERTYPE_NAME.equalsIgnoreCase(ft.getName())))
            {
    %>
                <tr>
                    <td valign="top">
                        <input type="radio" name="folderType" value="<%=h(ft.getName())%>"<%=checked(folderType.equals(ft))%> onclick="changeFolderType();">
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
                <div id="UpdateFolderButtonDiv"/>
    <%WebPartView.endTitleFrame(out);%>
    </td>
    <td width="30%" valign="top">
        <div id="defaultTabDiv" style="display:<%=text("None".equals(h(folderType.getName())) ? "" : "none")%>">
            <%WebPartView.startTitleFrame(out, "Default Tab", null, "100%", null);%>
            <select name="defaultModule" value="<%=h(defaultModule.getName())%>">
            <%
                    for (Module module : allModules)
                        {
                        if (activeModules.contains(module))
                            {
                            %>
                                <option value="<%= h(module.getName()) %>"<%=selected(module.getName().equals(defaultModule.getName()))%>><%= h(module.getTabName(context)) %></option>
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
        <input type="checkbox" id="activeModules[<%= i %>]" name="activeModules[<%= i %>]"
               title="<%= h(module.getTabName(context))%>"
               value="<%= h(module.getName())%>"
               <%=disabled(!enabled)%><%=checked(active)%>
               onClick="return updateDefaultOptions(this);">
        <label for="activeModules[<%= i %>]"><%= h(module.getTabName(context)) %></label>
        <br>
        <%
        i++;
    }
}
%>
    <%WebPartView.endTitleFrame(out);%>
</td></tr>
</table>
</form>
</div>
<script type="text/javascript">

    var updateButton = Ext4.create('Ext.button.Button', {
        text: '<%=text(form.isWizard() ? "Next" : "Update Folder")%>',
        renderTo: 'UpdateFolderButtonDiv',
        handler: function() {
            if (!validate())
            {
                Ext4.Msg.alert("Error", "Please select at least one tab to display.");
            }
            else
            {
                var currentType = "<%=h(folderType.getName())%>";
                var isContainerTab = <%=c.isContainerTab()%>;
                var newFolderType = getSelectedFolderType();
                if (isContainerTab)
                {
                    if (currentType.toLowerCase() != newFolderType.toLowerCase())
                    {
                        Ext4.Msg.confirm("Change Folder Type", "Are you sure you want to change a tab folder's type?", function(btn) {
                            if (btn == "yes")
                            {
                                submitForm(document.getElementById('folderModules'));
                            }
                        }, this);
                    }
                    else
                    {
                        submitForm(document.getElementById('folderModules'));
                    }
                }
                else
                {
                    this.checkDeletedTabFolders(newFolderType);
                }
            }
        },

        checkDeletedTabFolders: function(newFolderType) {
            // call to find out if container had deleted tab folders from the new folder type
            Ext4.Ajax.request({
                url     : LABKEY.ActionURL.buildURL('core', 'getContainerInfo.api'),
                method  : 'POST',
                jsonData: {
                    containerPath : "<%=text(path)%>",
                    newFolderType : newFolderType
                },
                success: function (resp) {
                    var containerInfo = Ext4.decode(resp.responseText);
                    if (containerInfo.success)
                    {
                        if (containerInfo.deletedFolders && containerInfo.deletedFolders.length > 0)
                        {
                            var userQuestion = Ext4.create('Ext.window.Window', {
                                title: 'Change Folder Type',
                                width: 540,
                                defaults: {margin: '0 2 0 2'},
                                items: [
                                    {xtype: 'displayfield',
                                        value: 'This folder was previously typed as the new folder type and had deleted tab folders.'},
                                    {xtype: 'displayfield', value: 'Check the deleted tab folders that you want to be recreated.'}
                                ],
                                layout: 'vbox',
                                buttons: [{
                                    text: 'OK',
                                    margin: '2 2 2 2',
                                    handler: function() {
                                        // grab answers from userQuestion and tell server to clear properties for those tab folders; then submit
                                        var resurrect = [];
                                        var itemCount = userQuestion.items.length;
                                        for (var index = 2; index < itemCount; index += 1)
                                        {
                                            var checkbox = userQuestion.getComponent(index);
                                            if (checkbox.getValue())
                                                resurrect.push(checkbox.name);
                                        }
                                        userQuestion.destroy();
                                        if (resurrect.length > 0)
                                        {
                                            Ext4.Ajax.request({
                                                url     : LABKEY.ActionURL.buildURL('admin', 'clearDeletedTabFolders.api'),
                                                method  : 'POST',
                                                jsonData: {
                                                    containerPath : "<%=h(path)%>",
                                                    resurrectFolders : resurrect,
                                                    newFolderType : newFolderType
                                                },
                                                success: function (resp) {
                                                    var o = Ext4.decode(resp.responseText);
                                                    if (o.success)
                                                    {
                                                        submitForm(document.getElementById('folderModules'));
                                                    }
                                                }
                                            });
                                        }
                                        else
                                        {
                                            submitForm(document.getElementById('folderModules'));
                                        }

                                    },
                                    scope: this
                                }],
                                buttonAlign: 'center'
                            });
                            for (var index = 0; index < containerInfo.deletedFolders.length; index += 1)
                            {
                                var folder = containerInfo.deletedFolders[index];
                                userQuestion.add({
                                    xtype: 'checkbox',
                                    boxLabel: folder.label,
                                    name: folder.name,
                                    margin: '0 2 2 4'
                                });
                            }
                            userQuestion.show();
                        }
                        else
                        {
                            submitForm(document.getElementById('folderModules'));
                        }
                    }
                }
            });


        }
    });

    updateDefaultOptions()
</script>
