<%
/*
 * Copyright (c) 2009 LabKey Corporation
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
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page import="org.labkey.api.attachments.AttachmentDirectory" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.files.FileContentService" %>
<%@ page import="org.labkey.api.files.FileUrls" %>
<%@ page import="org.labkey.api.files.view.FilesWebPart" %>
<%@ page import="org.labkey.api.pipeline.PipelineUrls" %>
<%@ page import="org.labkey.api.services.ServiceRegistry" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.security.permissions.InsertPermission" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<script type="text/javascript">
    LABKEY.requiresExtJs(true);
    LABKEY.requiresClientAPI(true);
    LABKEY.requiresScript("applet.js");
    LABKEY.requiresScript("fileBrowser.js");
    LABKEY.requiresScript("FileUploadField.js");
    LABKEY.requiresCss("verticalTabPanel/VerticalTabPanel.css");
    LABKEY.requiresScript("verticalTabPanel/VerticalTabPanel.js");
</script>

<%
    ViewContext context = HttpView.currentContext();
    FilesWebPart.FilesForm bean = (FilesWebPart.FilesForm)HttpView.currentModel();
    FilesWebPart me = (FilesWebPart) HttpView.currentView();

    AttachmentDirectory root = bean.getRoot();
    Container c = context.getContainer();

    // prefix is where we what the tree rooted
    // TODO: applet and fileBrowser could use more consistent configuration parameters
    //String rootName = c.getName();
    //String webdavPrefix = context.getContextPath() + "/" + WebdavService.getServletPath();

    //String rootPath = webdavPrefix + c.getEncodedPath();

    //if (bean.getDavLabel() != null)
    //    rootPath = webdavPrefix + c.getEncodedPath() + bean.getDavLabel();
//    String rootPath = webdavPrefix + c.getEncodedPath() + "%40myfiles/";
    //if (!rootPath.endsWith("/"))
    //    rootPath += "/";

    String startDir = "/";
//    if (me.getFileSet() != null)
//        startDir += "/@files/" + me.getFileSet();
    //String baseUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + rootPath;
%>


<div class="extContainer">
    <table>
        <tr><td><div id="toolbar"></div></td></tr>
        <tr><td><div id="files"></div></td></tr>
    </table>
</div>

<script type="text/javascript">
Ext.BLANK_IMAGE_URL = LABKEY.contextPath + "/_.gif";
Ext.QuickTips.init();

var autoResize = <%=bean.isAutoResize()%>;
var fileBrowser = null;
var fileSystem = null;
var actionsURL = <%=PageFlowUtil.jsString(PageFlowUtil.urlProvider(PipelineUrls.class).urlActions(context.getContainer()).getLocalURIString() + "path=")%>;
var buttonActions = [];

<%
    for (FilesWebPart.FilesForm.actions action  : bean.getButtonConfig())
    {
%>
        buttonActions.push('<%=action.name()%>');
<%
    }
%>
function renderBrowser(rootPath, dir)
{
    var configureAction = new Ext.Action({text: 'Configure', handler: function()
    {
        window.location = <%=PageFlowUtil.jsString(PageFlowUtil.urlProvider(FileUrls.class).urlShowAdmin(c).getLocalURIString())%>;
    }});

    var dropAction = new Ext.Action({text: 'Upload multiple files', scope:this, disabled:false, handler: function()
    {
        var dropUrl = <%=PageFlowUtil.jsString((new ActionURL("ftp","drop",c)).getEncodedLocalURIString() + (null == me.getFileSet() ? "" : "fileSetName=" + PageFlowUtil.encode(root.getLabel())))%>;
        window.open(dropUrl, '_blank', 'height=600,width=1000,resizable=yes');
    }});

    // TODO even better just refresh/rerender the browser not the whole page
    var combo = new Ext.form.ComboBox({
        name: 'filesetComboBox',
        store: fileSets,
        typeAhead: true,
        mode: 'local',
        triggerAction: 'all',
        selectOnFocus:true,
        width:135,
        value:selectedValue
    });
    combo.on("select",function(){
        var value = combo.getValue();
        if (value.indexOf("showAdmin.view") != -1)
            window.location=value;
        else
            fileBrowser.changeDirectory(value);
    });

    // subclass the filebrowser panel
    FilesWebPartPanel = Ext.extend(LABKEY.FileBrowser, {

        // additional actions panel
        extraActionsPanel : undefined,

        // all actions tab
        allActionsTab : undefined,

        actionsConnection : new Ext.data.Connection({autoAbort:true}),

        // pipeline actions
        pipelineActions : undefined,

        constructor : function(config)
        {
            FilesWebPartPanel.superclass.constructor.call(this, config);
        },

        initComponent : function()
        {
            FilesWebPartPanel.superclass.initComponent.call(this);
            this.createPanels();

            this.on(BROWSER_EVENTS.directorychange,function(record){this.onDirectoryChange(record);}, this);
            this.grid.getSelectionModel().on(BROWSER_EVENTS.selectionchange,function(record){this.onSelectionChange(record);}, this);
        },

        getTbarConfig : function()
        {
            // no toolbar on the filebrowser grid, we'll display our own so we can insert a ribbon panel
            return [];
        },

        /**
         * Initialize additional components
         */
        createPanels : function()
        {
            var buttons = [];

            if (this.buttonCfg && this.buttonCfg.length)
            {
                for (var i=0; i < this.buttonCfg.length; i++)
                {
                    var item = this.buttonCfg[i];
                    if (typeof item == "string" && typeof this.actions[item] == "object")
                        buttons.push(new Ext.Button(this.actions[item]));
                    else
                        buttons.push(item);
                }
            }

            buttons.push(new Ext.Button({text:'More Actions', tooltip: {text:'Displays additional actions that can be performed on selected files', title:'More Actions'},
                listeners:{click:function(button, event) {this.toggleActionsPanel();}, scope:this}}));
            //buttons.push(new Ext.Button(this.actions.uploadTool));

            new Ext.Toolbar({
                renderTo: 'toolbar',
                border: false,
                items: buttons
            });

            this.allActionsTab = new Ext.Panel({
                id: 'tabAllActions',
                title: 'All'
            });

            this.extraActionsPanel = new Ext.ux.VerticalTabPanel({
                renderTo: 'toolbar',
                hidden: true,
                activeTab: 'tabAllActions',
                tabPosition: 'left',
                tabWidth: 100,
                items: [
                    this.allActionsTab,
                    {title: 'Assays'},
                    {title: 'Flow'}
                ]});
        },

        toggleActionsPanel : function()
        {
            var el = this.extraActionsPanel.getEl();

            if (el.isVisible())
            {
                el.slideOut('t', {callback: function() {this.extraActionsPanel.setVisible(false);}, scope: this});
            }
            else
            {
                this.extraActionsPanel.setVisible(true);
                el.slideIn();
            }
        },

        createGrid : function()
        {
            // mild convolution to pass fileSystem to the _attachPreview function
            var iconRenderer = renderIcon.createDelegate(null,_attachPreview.createDelegate(this.fileSystem,[],true),true);
            var sm = new Ext.grid.CheckboxSelectionModel();

            var grid = new Ext.grid.GridPanel(
            {
                store: this.store,
                border:false,
                selModel : sm,
                columns: [
                    sm,
                    {header: "", width:20, dataIndex: 'iconHref', sortable: false, hidden:false, renderer:iconRenderer},
                    {header: "Name", width: 250, dataIndex: 'name', sortable: true, hidden:false, renderer:Ext.util.Format.htmlEncode},
                    {header: "Modified", width: 150, dataIndex: 'modified', sortable: true, hidden:false, renderer:renderDateTime},
                    {header: "Size", width: 80, dataIndex: 'size', sortable: true, hidden:false, align:'right', renderer:renderFileSize},
                    {header: "Usages", width: 150, dataIndex: 'actionHref', sortable: true, hidden:false, renderer:renderUsage}
                ]
            });
            return grid;
        },

        onDirectoryChange : function(record)
        {
            var path = record.data.path;
            if (startsWith(path,"/"))
                path = path.substring(1);
            var requestid = this.actionsConnection.request({
                autoAbort:true,
                url:actionsURL + encodeURIComponent(path),
                method:'GET',
                disableCaching:false,
                success : this.renderExtraActions,
                scope: this
            });
        },

        renderExtraActions : function(response)
        {
            var o = eval('var $=' + response.responseText + ';$;');
            var actions = o.success ? o.actions : [];

            // delete the actions container
            if (this.allActionsTab && this.pipelineActions != undefined)
                this.allActionsTab.remove('allActionsToolbar');

            if (actions && actions.length)
            {
                this.pipelineActions = [];

                for (var i=0; i < actions.length; i++)
                {
                    var action = actions[i];
                    if (action.links.items != undefined)
                    {
                        var items = [];
                        for (var j=0; j < action.links.items.length; j++)
                        {
                            var item = action.links.items[j];
                            if (item.text && item.href)
                            {
                                items.push({text: item.text, files: action.files, itemHref: item.href, listeners: {click: this.executePipelineAction, scope: this}});
                            }
                        }

                        if (items.length)
                            this.pipelineActions.push(new Ext.Action({text:action.links.text, files: action.files, menu: {cls: 'extContainer', items: items}}));
                        else
                            this.pipelineActions.push(new Ext.Action({text:action.links.text, files: action.files}));
                    }
                    else if (action.links.text && action.links.href)
                    {
                        this.pipelineActions.push(new Ext.Action({text:action.links.text, files: action.files, itemHref: action.links.href, listeners: {click: this.executePipelineAction, scope: this}}));
                    }
                }

                // add the actions to a toolbar and insert into the tabpanel
                var allActions = new Ext.Toolbar({
                    id: 'allActionsToolbar',
                    items: this.pipelineActions
                });
                this.allActionsTab.add(allActions);
                this.allActionsTab.doLayout();
            }
        },

        // selection change handler
        onSelectionChange : function(record)
        {
            if (this.pipelineActions)
            {
                var selections = this.grid.selModel.getSelections();

                if (selections.length)
                {
                    var selectionMap = {};

                    for (var i=0; i < selections.length; i++)
                        selectionMap[selections[i].data.name] = true;

                    for (var i=0; i <this.pipelineActions.length; i++)
                    {
                        var action = this.pipelineActions[i];
                        if (action.initialConfig.files && action.initialConfig.files.length)
                        {
                            var contains = false;
                            for (var j=0; j <action.initialConfig.files.length; j++)
                            {
                                if (action.initialConfig.files[j] in selectionMap)
                                {
                                    contains = true;
                                    break;
                                }
                            }
                            contains ? action.enable() : action.disable();
                        }
                    }
                }
                else
                {
                    for (var i=0; i <this.pipelineActions.length; i++)
                        this.pipelineActions[i].enable();
                }
            }
        },

        executePipelineAction : function(item, e)
        {
            var selections = this.grid.selModel.getSelections();

            // if there are no selections, treat as if all are selected
            if (selections.length == 0)
            {
                var selections = [];
                var store = this.grid.getStore();

                for (var i=0; i <store.getCount(); i++)
                {
                    var record = store.getAt(i);
                    if (record.data.file)
                        selections.push(record);
                }
            }

            if (item && item.itemHref)
            {
                if (selections.length == 0)
                {
                    Ext.Msg.alert("Rename Views", "There are no views selected");
                    return false;
                }

                var form = document.createElement("form");
                form.setAttribute("method", "post");
                form.setAttribute("action", item.itemHref);

                for (var i=0; i < selections.length; i++)
                {
                    for (var j = 0; j < item.files.length; j++)
                    {
                        if (item.files[j] == selections[i].data.name)
                        {
                            var fileField = document.createElement("input");
                            fileField.setAttribute("name", "file");
                            fileField.setAttribute("value", selections[i].data.name);
                            form.appendChild(fileField);
                            break;
                        }
                    }
                }

                document.body.appendChild(form);    // Not entirely sure if this is necessary
                form.submit();
            }
        }
    });


    if (!fileSystem)
        fileSystem = new LABKEY.WebdavFileSystem({
            baseUrl:rootPath,
            rootName:'fileset'
        });

    fileBrowser = new FilesWebPartPanel({
        fileSystem: fileSystem,
        helpEl:null,
        showAddressBar: <%=bean.isShowAddressBar()%>,
        showFolderTree: <%=bean.isShowFolderTree()%>,
        showProperties: false,
        showDetails: <%=bean.isShowDetails()%>,
        allowChangeDirectory: <%=bean.isAllowChangeDirectory()%>,
        actions: {drop:dropAction, configure:configureAction},
        buttonCfg: buttonActions
/*
        buttonCfg:['download','deletePath','refresh'
        <%=c.hasPermission(context.getUser(), InsertPermission.class)?",'uploadTool'":""%>
        ,'->'
        , new Ext.form.Label({html:'File Set:&nbsp;'}), combo
        <%=c.hasPermission(context.getUser(), AdminPermission.class)?",'configure'":""%>
        ]
*/
    });

    fileBrowser.height = 300;
/*
    fileBrowser.on("doubleclick", function(record){
        var contentType = record.data.contentType || "attachment";
        var location = "<%=PageFlowUtil.encodePath(request.getContextPath())%>/files<%=c.getEncodedPath()%>" + encodeURI(record.data.name) + "?renderAs=DEFAULT<%=me.getFileSet()==null ? "" : "&fileSet=" + PageFlowUtil.encode(me.getFileSet())%>";
        if (0 == contentType.indexOf("image/") || 0 == contentType.indexOf("text/"))
            window.open(location,"_blank");
        else
            window.location = location;
        });
*/

//    var resizer = new Ext.Resizable('files', {width:800, height:600, minWidth:640, minHeight:400});
//    resizer.on("resize", function(o,width,height){ this.setWidth(width); this.setHeight(height); }.createDelegate(fileBrowser));

    fileBrowser.render('files');

    var _resize = function(w,h)
    {
        if (!fileBrowser.rendered)
            return;
        var padding = [20,20];
        var xy = fileBrowser.el.getXY();
        var size = {
            width : Math.max(100,w-xy[0]-padding[0]),
            height : Math.max(100,h-xy[1]-padding[1])};
        fileBrowser.setSize(size);
        fileBrowser.doLayout();
    };

    if (autoResize)
    {
        Ext.EventManager.onWindowResize(_resize);
        Ext.EventManager.fireWindowResize();
    }

    fileBrowser.start(dir);
}
    var fileSets = [
<%
        boolean navigate = false;
        String selectedValue = null;
        ActionURL url = PageFlowUtil.urlProvider(FileUrls.class).urlBegin(c);
        FileContentService svc = ServiceRegistry.get().getService(FileContentService.class);
        AttachmentDirectory main = svc.getMappedAttachmentDirectory(c, false);
        if (null != main && null != main.getFileSystemDirectory())
        {
            String value = navigate ? url.getLocalURIString() : "/";
            out.write("[" + q(value) + ",'Default']");
            if (StringUtils.isEmpty(me.getFileSet()) || StringUtils.equals(me.getFileSet(),"Default"))
                selectedValue = value;
        }
        for (AttachmentDirectory attDir : svc.getRegisteredDirectories(c))
        {
            String name = attDir.getLabel();
            url.replaceParameter("fileSetName",name);
            String value = navigate ? url.getLocalURIString() : "/@files/" + name;
            out.write(",[" + q(value) + "," + q(name) + "]");
            if (StringUtils.equals(me.getFileSet(),name))
                selectedValue = value;
        }
        if (c.hasPermission(context.getUser(), AdminPermission.class))
        {
    //        out.write(",[" + q(new ActionURL(FileContentController.ShowAdminAction.class,c).getLocalURIString()) + ",'[configure]']");
        }
%>
    ];

    var selectedValue = <%=q(selectedValue)%>;

    Ext.onReady(function(){renderBrowser(<%=q(bean.getRootPath())%>, <%=q(startDir)%>);});
</script>