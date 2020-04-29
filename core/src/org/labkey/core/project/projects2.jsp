<%
/*
 * Copyright (c) 2011-2019 LabKey Corporation
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
<%@ page import="org.json.JSONObject" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.security.permissions.ReadPermission" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.data.SimpleFilter" %>
<%@ page import="org.labkey.api.query.FieldKey" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="static org.apache.commons.lang3.StringUtils.isBlank" %>
<%@ page import="org.labkey.api.data.ContainerFilter" %>
<%@ page import="org.labkey.api.query.QuerySchema" %>
<%@ page import="org.labkey.api.query.DefaultSchema" %>
<%@ page import="org.labkey.api.data.TableSelector" %>
<%@ page import="java.util.Set" %>
<%@ page import="org.labkey.api.data.Sort" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.data.JdbcType" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("extWidgets/IconPanel.css");
    }
%>
<%
    JspView<Portal.WebPart> me = (JspView<Portal.WebPart>) HttpView.currentView();
    Portal.WebPart webPart = me.getModelBean();
    int webPartId = webPart.getRowId();
    boolean isRootAdmin = getUser().hasRootAdminPermission();
    boolean hasPermission;

    Map<String,String> defaultProperties = Map.of("containerTypes", "project", "containerFilter", "CurrentAndSiblings", "hasCreateButton", "false", "iconSize", "large", "labelPosition", "bottom", "noun", "Project");
    Map<String,String> properties = new HashMap<>(defaultProperties);
    properties.putAll(me.getModelBean().getPropertyMap());

    Container target;
    String containerPath = properties.get("containerPath");
    if (isBlank(containerPath))
    {
        hasPermission = true;
        target = getContainer();
    }
    else
    {
        target = ContainerManager.getForPath(containerPath);
        if (target == null)
            target = ContainerManager.getForId(containerPath);
        hasPermission = target != null && target.hasPermission(getUser(), ReadPermission.class);
    }

    if (target == null)
    {
        %>The target project/folder has been deleted. To reset, remove the webpart and re-add it.<%
        return;
    }
    else if (!hasPermission)
    {
        %>You do not have permission to view this folder.<%
        return;
    }

    SimpleFilter filter = new SimpleFilter();
    filter.addInClause(new FieldKey(null,"containerType"), Arrays.asList(StringUtils.split(properties.get("containerTypes"),",")));
    filter.addClause(new SimpleFilter.InClause(new FieldKey(null,"entityId"), Set.of(ContainerManager.getHomeContainer().getId(), ContainerManager.getSharedContainer().getId()), false, true));
    ContainerFilter cf = ContainerFilter.getContainerFilterByName(properties.get("containerFilter"),getUser());
    QuerySchema core = DefaultSchema.get(getUser(),target).getSchema("core");
    // consider: could use ContainerManager, but webpart is setup to do filtering
    var containers = new TableSelector(core.getTable("Containers",cf), Set.of("name","entityId"),filter,new Sort("name")).getMapCollection();

    if (containers.isEmpty())
    {
        if (getUser().isGuest())
        {
            %>Please log in to view the <%=h(properties.get("noun").toLowerCase())%> list.<%
        }
        else {
            %>No <%=h(properties.get("noun").toLowerCase())%>s to display.<%
        }
        return;
    }


    boolean details = false;
    HtmlString faX;
    HtmlString width;
    if (StringUtils.equals("small",properties.get("iconSize")))
    {
        faX = HtmlString.of("fa-lg");
        width = HtmlString.of("67px");
        details = StringUtils.equals("side",properties.get("labelPosition"));
    }
    else if (StringUtils.equals("medium",properties.get("iconSize")))
    {
        faX = HtmlString.of("fa-3x");
        width = HtmlString.of("67px");
    }
    else
    {
        faX = HtmlString.of("fa-5x");
        width = HtmlString.of("100px");
    }
    %>
    <div class="labkey-projects-container" style="background-color: transparent; border-width: 0;">
    <div class="labkey-iconpanel" style="width: 100%; right: auto; left: 0; top: 0; margin: 0;">
<%
    for (Map<String,Object> m : containers)
    {
        Container c = ContainerManager.getForId((String)m.get("entityId"));
        if (null != c)
        {
            HtmlString projectName = HtmlString.of(c.getProject().getName());
            // data-project can be use in style sheet to hide projects e.g.
            // <style>div[data-project="StudyVerifyProject"]{display:none !important;}</style>
            if (details) {
                %><div data-project="<%=projectName%>" class="thumb-wrap"><div style="width: 100%;" class="tool-icon thumb-wrap thumb-wrap-side"><a href="<%=h(c.getStartURL(getUser()))%>"><div class="thumb-img-side"><span class="fa fa-folder-open fa-lg"></span></div><span class="thumb-label-side"><%=h(c.getName())%></span></a></div></div><%
            } else {
                %><div data-project="<%=projectName%>" style="display: inline-block;" class="thumb-wrap"><div style="width: <%=width%>;" class="tool-icon thumb-wrap thumb-wrap-bottom"><a href="<%=h(c.getStartURL(getUser()))%>"><div class="thumb-img-bottom"><span class="fa fa-folder-open <%=faX%>"></span></div><span class="thumb-label-bottom"><%=h(c.getName())%></span></a></div></div><%
            }
        }
    }
%>
    </div>
    <div><%
        if (Boolean.TRUE != JdbcType.BOOLEAN.convert(properties.get("hideCreateButton")))
        {
            if ((StringUtils.equals("Project",properties.get("noun")) && isRootAdmin) ||
                StringUtils.equals("Subfolder",properties.get("noun")) && getContainer().hasPermission(getUser(), AdminPermission.class))
            {
                Container c = getContainer();
                if (StringUtils.equals("project",properties.get("containerTypes")))
                    c = ContainerManager.getRoot();
                %><%=button("Create New " + properties.get("noun")).href(urlProvider(AdminUrls.class).getCreateFolderURL(c, getActionURL()))%><%
            }
        }%>
    </div>
</div>


<script type="text/javascript">

    function customizeProjectWebpart<%=webPartId%>(webpartId, pageId, index)
    {
        LABKEY.requiresScript(["Ext4","Ext4ClientApi"], function()
        {
            Ext4.onReady(function() {
                var config = <%= HtmlString.unsafe(new JSONObject(properties).toString()) %>;
                _customizeProjectWebpart<%=webPartId%>(Ext4, <%=webPart.getRowId()%>, <%=q(webPart.getPageId())%>, <%=webPart.getIndex()%>, config);
            });
        });
    }

    function _customizeProjectWebpart<%=webPartId%>(Ext4, webpartId, pageId, index, config)
    {
        function shouldCheck(btn){
            return (btn.iconSize===config.iconSize && btn.labelPosition===config.labelPosition)
        }
        Ext4.create('Ext.window.Window', {
            title: 'Customize Webpart',
            modal: true,
            width: 400,
            border: false,
            layout: 'fit',
            items: [{
                xtype: 'form',
                border: false,
                bodyStyle: 'padding: 5px;',
                items: [{
                    xtype: 'textfield',
                    name: 'title',
                    fieldLabel: 'Title',
                    itemId: 'title',
                    value: config.title
                },{
                    xtype: 'radiogroup',
                    name: 'style',
                    itemId: 'style',
                    fieldLabel: 'Icon Style',
                    border: false,
                    columns: 1,
                    defaults: {
                        xtype: 'radio',
                        width: 300
                    },
                    items: [{
                        boxLabel: 'Details',
                        inputValue: {iconSize: 'small',labelPosition: 'side'},
                        checked: shouldCheck({iconSize: 'small',labelPosition: 'side'}),
                        name: 'style'
                    },{
                        boxLabel: 'Medium',
                        inputValue: {iconSize: 'medium',labelPosition: 'bottom'},
                        checked: shouldCheck({iconSize: 'medium',labelPosition: 'bottom'}),
                        name: 'style'
                    },{
                        boxLabel: 'Large',
                        inputValue: {iconSize: 'large',labelPosition: 'bottom'},
                        checked: shouldCheck({iconSize: 'large',labelPosition: 'bottom'}),
                        name: 'style'
                    }]
                },{
                    xtype: 'radiogroup',
                    name: 'folderTypes',
                    itemId: 'folderTypes',
                    fieldLabel: 'Folders To Display',
                    border: false,
                    columns: 1,
                    defaults: {
                        xtype: 'radio',
                        width: 300
                    },
                    items: [{
                        boxLabel: 'All Projects',
                        inputValue: 'project',
                        checked: config.containerTypes && config.containerTypes.match(/project/),
                        name: 'folderTypes'
                    },{
                        boxLabel: 'Subfolders',
                        inputValue: 'subfolders',
                        checked: config.containerTypes && config.containerTypes.match(/folder/) && !config.containerPath,
                        name: 'folderTypes'
                    },{
                        boxLabel: 'Specific Folder',
                        inputValue: 'folder',
                        checked: config.containerTypes && config.containerTypes.match(/folder/) && config.containerPath,
                        name: 'folderTypes'
                    },{
                        xtype: 'labkey-combo',
                        itemId: 'containerPath',
                        width: 200,
                        disabled: config.containerTypes && !config.containerTypes.match(/folder/) || !config.containerPath,
                        displayField: 'Path',
                        valueField: 'EntityId',
                        initialValue: config.containerPath,
                        value: config.containerPath,
                        store: Ext4.create('LABKEY.ext4.Store', {
                            schemaName: 'core',
                            queryName: 'Containers',
                            containerFilter: 'AllFolders',
                            columns: 'Name,Path,EntityId',
                            autoLoad: true,
                            //sort: '-Path',
                            filterArray: [
                                LABKEY.Filter.create('type', 'workbook', LABKEY.Filter.Types.NOT_EQUAL),
                                LABKEY.Filter.create('name', LABKEY.Security.getHomeContainer(), LABKEY.Filter.Types.NOT_EQUAL),
                                LABKEY.Filter.create('name', LABKEY.Security.getSharedContainer(), LABKEY.Filter.Types.NOT_EQUAL)
                            ],
                            listeners: {
                                load: function(store){
                                    //NOTE: the raw value of the path column is name, so we sort locally
                                    store.sort('Path', 'ASC');
                                    store.fireEvent('datachanged');
                                }
                            }
                        })
                    },{
                        xtype: 'checkbox',
                        boxLabel: 'Include Direct Children Only',
                        disabled: (config.containerTypes && config.containerTypes.match(/project/)) || !config.containerPath,
                        checked: (config.containerFilter === 'CurrentAndFirstChildren'),
                        itemId: 'directDescendants'
                    },{
                        xtype: 'checkbox',
                        boxLabel: 'Include Workbooks',
                        disabled: (config.containerTypes && config.containerTypes.match(/project/)) || !config.containerPath,
                        checked: (config.containerTypes.match(/project/) || config.containerTypes.match(/workbook/)),
                        itemId: 'includeWorkbooks'
                    },{
                        xtype: 'checkbox',
                        boxLabel: 'Hide Create Button',
                        checked: config.hideCreateButton,
                        itemId: 'hideCreateButton'
                    }],
                    listeners: {
                        buffer: 20,
                        change: function(field, val){
                            var window = field.up('form');
                            window.down('#containerPath').setDisabled(val.folderTypes !== 'folder');
                            window.down('#includeWorkbooks').setDisabled(val.folderTypes !== 'folder');
                            window.down('#directDescendants').setDisabled(val.folderTypes !== 'folder');

                            window.doLayout();
                            field.up('window').doLayout();

                        }
                    }
                }]
            }],
            buttons: [{
                text: 'Submit',
                handler: function(btn) {
                    var mode = btn.up('window').down('#folderTypes').getValue().folderTypes;

                    if(mode === 'project'){
                        config.containerFilter = 'CurrentAndSiblings';
                        config.containerTypes = 'project';
                        config.containerPath = LABKEY.Security.getHomeContainer();
                        config.noun = 'Project';
                    }
                    else if(mode === 'subfolders'){
                        config.containerFilter = 'CurrentAndFirstChildren';
                        config.containerTypes = 'folder';
                        config.containerPath = null;
                        config.noun = 'Folder';
                    }
                    else {
                        var container = btn.up('window').down('#containerPath').getValue();
                        if(!container){
                            alert('Must choose a folder');
                            return;
                        }
                        config.containerPath = container;

                        config.containerFilter = 'Current'; //null;  //use default
                        config.containerTypes = ['folder'];
                        if(btn.up('window').down('#includeWorkbooks').getValue())
                            config.containerTypes.push('workbook');
                        config.containerTypes = config.containerTypes.join(';');
                        config.noun = 'Subfolder';

                        var directDescendants = btn.up('window').down('#directDescendants').getValue();
                        config.containerFilter = directDescendants ? 'CurrentAndFirstChildren' : 'CurrentAndSubfolders';
                    }

                    config.hideCreateButton =  btn.up('window').down('#hideCreateButton').getValue();
                    config.iconSize = btn.up('window').down('#style').getValue().style.iconSize;
                    config.labelPosition = btn.up('window').down('#style').getValue().style.labelPosition;
                    config.title = btn.up('window').down('#title').getValue();
                    config.webPartId = webpartId;

                    Ext4.Ajax.request({
                        url    : LABKEY.ActionURL.buildURL('project', 'customizeWebPartAsync.api', null, config),
                        method : 'POST',
                        failure : LABKEY.Utils.onError,
                        success : function() {window.location.reload();},
                        scope : this
                    });
                },
                scope: this
            },{
                text: 'Cancel',
                handler: function(btn){
                    btn.up('window').hide();
                },
                scope: this
            }]
        }).show();
    }

</script>
