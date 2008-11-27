<%
/*
 * Copyright (c) 2008 LabKey Corporation
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
<%@ page import="org.apache.commons.lang.time.FastDateFormat" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.webdav.WebdavResolver" %>
<%@ page import="java.util.*" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.core.webdav.DavController" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.apache.commons.lang.StringUtils" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
//FastDateFormat dateFormat = FastDateFormat.getInstance("EEE, dd MMM yyyy HH:mm:ss zzz", TimeZone.getTimeZone("GMT"), Locale.US);
FastDateFormat dateFormat = FastDateFormat.getInstance("EEE, dd MMM yyyy HH:mm:ss zzz");
%>
<%
    DavController.ListPage listpage = (DavController.ListPage) HttpView.currentModel();
    WebdavResolver.Resource resource = listpage.resource;
    String path = resource.getPath();
    ViewContext context = HttpView.currentContext();
    AppProps app = AppProps.getInstance();
    User user = context.getUser();
    String userAgent = StringUtils.trimToEmpty(request.getHeader("user-agent"));
    boolean supportsDavMount = false;
    boolean supportsDavScheme = false;
    boolean supportsWebdavScheme = userAgent.contains("Konqueror");
%>
<script type="text/javascript" language="javascript">
    LABKEY.init(<%=PageFlowUtil.jsInitObject()%>);
    LABKEY.requiresExtJs(true);
    LABKEY.requiresScript("FileTree.js", true);
</script>
<script type="text/javascript">
var h = Ext.util.Format.htmlEncode;

function renderName(value, metadata, record, rowIndex, colIndex, store)
{
    var icon = record.get('iconHref');
    if (!icon)
        return "<img src='" + LABKEY.contextPath + "/_.gif' width=16 height=16>&nbsp;" + h(value);
    return "<img src='" + icon + "'>&nbsp;" + h(value);
}

function renderDate(value, metadata, record, rowIndex, colIndex, store)
{
    return value;
    alert(value);
    var d = Date.parseDate(value, "Y-m-d\\TH:i:s");
    alert(d);
    var r =  Ext.util.Format.date(d, 'Y-m-d H:i:s');
    alert(r);
    return r;
}

function treePathFromId(id)
{
    // assumes id ends with "/"
    var parts = id.substring(0,id.length-1).split("/");
    var folder = "";;
    var treePath = "";
    for (var i=0 ; i<parts.length ; i++)
    {
        folder += parts[i] + "/";
        treePath += ";" + folder;
    }
    return treePath;
}

var EVENTS = {selectionchange:"selectionchange", directorychange:"directorychange"};

var fileBrowser =
{
    _debugName : 'fileBrowser',
    
    // instance variables
    grid: null,
    store: null,

    currentDirectory: "/",
    selectedPath: "/",
    
    //
    // actions
    //
    
    action : new Ext.Action({
        text: 'Alert',
        handler: function() {window.alert('Click','You clicked on "Action 1".');},
        iconCls: 'blist'
    }),

    changeDirectory : function(path)
    {
        if (fileBrowser.currentDirectory != path)
        {
            fileBrowser.currentDirectory = path;
            fileBrowser.events.fireEvent(EVENTS.directorychange, path);
        }   
    },

    selectPath : function(path)
    {
        if (fileBrowser.selectedPath != path)
        {
            fileBrowser.selectedPath = path;
            fileBrowser.events.fireEvent(EVENTS.selectionchange, path);
        }
    },
    
    //
    // event handlers
    //
    Grid_onRowselect : function(sm, rowIdx, r)
    {
        if (fileBrowser.tree)
            fileBrowser.tree.getSelectionModel().clearSelections();
        if (r)
        {
            var path = r.get("path");
            var collection = r.get("collection");
            if (collection && path.charAt(path.length-1) != '/')
                path = path + "/";
            fileBrowser.selectPath(path);
        }
    },

    Grid_onCelldblclick : function(grid, rowIndex, columnIndex, e)
    {
        var p = fileBrowser.selectedPath;
        if (p.charAt(p.length-1) == '/')
        {
            fileBrowser.changeDirectory(p);
            if (fileBrowser.tree)
            {
                var treePath = treePathFromId(p);
                fileBrowser.tree.expandPath(treePath);
                var node = fileBrowser.tree.getNodeById(p);
                if (node)
                {
                    node.ensureVisible();
                    node.select();
                }
            }
        }
    },
    
    Tree_onSelectionchange : function(sm, node)
    {
        if (fileBrowser.grid)
            fileBrowser.grid.getSelectionModel().clearSelections();
        if (node)
        {
            var folder = node.id;
            fileBrowser.selectPath(folder);
            fileBrowser.changeDirectory(folder);
        }
    },

    events : new Ext.util.Observable()
};
fileBrowser.events.addEvents(
{
    "selectionchange":true,
    "directorychange":true
});


fileBrowser.init = function(config)
{
    // 
    // GRID
    //
    this.store = new Ext.data.Store(
    {
        proxy: new Ext.data.HttpProxy({
            method: "GET",
            headers: {"Method" : "PROPFIND", "Depth" : "1,noroot"},
            url: config.url}),
        reader: new Ext.data.XmlReader(
            {record: 'response', id: 'path'},
            ['path', 'creationdate', 'displayname', 'getcontentlength', 'getlastmodified', 'collection', 'iconHref']
        )
    });
    this.store.load();
    this.grid = new Ext.grid.GridPanel(
    {
        store: this.store,
        border:false,
        columns: [
            {header: "Name", width: 180, dataIndex: 'displayname', sortable: true, hidden:false, renderer:renderName},
            {header: "Created", width: 120, dataIndex: 'creationdate', sortable: true, hidden:false, renderer:renderDate},
            {header: "Size", width: 115, dataIndex: 'getcontentlength', sortable: true, hidden:false}
        ]
    });
    this.grid.getSelectionModel().on('rowselect', fileBrowser.Grid_onRowselect);
    this.grid.on("celldblclick", fileBrowser.Grid_onCelldblclick);

    //
    // TREE
    //
    
    var treeloader = new LABKEY.ext.WebDavTreeLoader({url: config.url, displayFiles:false});
    var root = new Ext.tree.AsyncTreeNode(
    {
        id: "/",
        text:'<root>',
        listeners: {
            'load': function (node) {
                if (!node.hasChildNodes())
                {
                    node.appendChild({
                        text: "&lt;no files found in root>",
                        size: 0,
                        disabled: true,
                        leaf: true,
                        iconCls: 'labkey-tree-error-icon'
                    });
                }
            }
        }
    });
    this.tree = new Ext.tree.TreePanel(
    {
        loader:treeloader,
        root:root,
        rootVisible:false,
        title: 'File Browser',
        useArrows:true,
        autoScroll:true,
        animate:true,
        enableDD:false,
        containerScroll:true,
        border:false,
        pathSeparator:';'
    });
    this.tree.getSelectionModel().on("selectionchange", this.Tree_onSelectionchange);


    //
    // LAYOUT
    //
    var tbarConfig =
        [
            this.action, {                   // <-- Add the action directly to a toolbar
                text: 'Action Menu',
                menu: [this.action]          // <-- Add the action directly to a menu
            }
        ];
    var layoutItems = [
        {
            title: 'South Panel',
            region: 'south',
            height: 100,
            minSize: 75,
            maxSize: 250,
            margins: '0 5 5 5',
            layout: 'fit',
            items: [{html:'south', id:'file-details'}]
        },
        {
            region:'west',
            id:'west-panel',
            split:true,
            width: 200,
            minSize: 100,
            collapsible: true,
            margins:'5 0 5 5',
            layout:'accordion',
            layoutConfig:{
                animate:true
            },
            items: [
                this.tree,
                {
                    title:'Settings',
                    html:'<p>Some settings in here.</p>',
                    border:false,
                    iconCls:'settings'
                }]
        },
        {
            region:'center',
            margins:'5 0 5 0',
            minSize: 200,
            layout:'fit',
            items: [this.grid]
        },
        {
            title: 'Properties',
            region:'east',
            split:true,
            margins:'5 5 5 0',
            width: 200,
            minSize: 100,
            html: 'east'
        }];

    var border = new Ext.Panel(
    {
        id:'borderLayout',
        height:600, width:800,
        layout:'border',
        tbar: tbarConfig,
        items: layoutItems
    });

    border.render(config.renderTo);

    var resizer = new Ext.Resizable('borderLayout', {
        width:800, height:600,
        minWidth:640,
        minHeight:400});
    resizer.on("resize", function(o,width,height){
        border.setWidth(width);
        border.setHeight(height);
        resizer.setWidth(border.getWidth());
        resizer.setHeight(border.getHeight());
    });

    //
    // EVENTS (tie together components)
    //

    this.events.on(EVENTS.selectionchange, function(path)
    {
        var el = Ext.get('file-details');
        if (el) el.update(path + "<br>" + treePathFromId(path));
    });

    this.events.on(EVENTS.directorychange, function(path)
    {
        //fileBrowser.store.proxy.url = config.url + path;
        fileBrowser.store.proxy = new Ext.data.HttpProxy({
            method: "GET",
            headers: {"Method" : "PROPFIND", "Depth" : "1,noroot"},
            url: config.url + path});
        fileBrowser.store.reload();
    });
};



Ext.onReady(function()
{
    Ext.QuickTips.init();
    fileBrowser.init({url:window.location.pathname, renderTo:'files'});

});
</script>


<div id="files" class="extContainer" style="margin:20px;"></div>

<hr>
<ul>
    <li>should be able to root anywhere in the tree</li>
    <li>pipeline actions</li>
    <li>pipeline actions filtered by provider</li>
    <li>history, audit</li>
</ul>
