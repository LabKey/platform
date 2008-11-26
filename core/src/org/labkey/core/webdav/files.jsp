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
<html>
<head>
<title><%=h(path)%> -- WebDAV: <%=h(app.getServerName())%></title>
</head>
<script type="text/javascript" src="<%=context.getContextPath()%>/labkey.js?<%=AppProps.getInstance().getServerSessionGUID()%>"></script>
<script type="text/javascript" language="javascript">
    LABKEY.init(<%=PageFlowUtil.jsInitObject()%>);
</script>
<script type="text/javascript">
    LABKEY.requiresExtJs(true);
    LABKEY.requiresScript("FileTree.js", true);
</script>
<style type="text/css">
    A {text-decoration:none; behavior: url(#default#AnchorClick);}
    TR {margin-right:3px; margin-left:3px;}
    BODY, TD, TH { font-family: arial sans-serif; color: black; }
	html, body {
        font:normal 12px verdana;
        margin:0;
        padding:0;
        border:0 none;
        overflow:hidden;
        height:100%;
    }
	p {
	    margin:5px;
	}
    /*.settings {*/
        /*background-image:url(../shared/icons/fam/folder_wrench.png);*/
    /*}*/
    /*.nav {*/
        /*background-image:url(../shared/icons/fam/folder_go.png);*/
    /*}*/
</style> 
<body class="extContainer">

<script type="text/javascript">
function renderName(value, metadata, record, rowIndex, colIndex, store)
{
    var icon = record.get('iconHref');
    if (!icon)
        return "<img src='" + LABKEY.contextPath + "/_.gif' width=16 height=16>&nbsp;" + value;
    return "<img src='" + icon + "'>&nbsp;" + value;
}
Ext.onReady(function(){

    var base = "/labkey/_webdav/";
    var i = window.location.href.indexOf(base);
    var path = window.location.href.substr(i+base.length);

    // create the Data Store
    var store = new Ext.data.Store({

        proxy: new Ext.data.HttpProxy({
            method: "GET",
            headers: {"Method" : "PROPFIND", "Depth" : "1,noroot"},
            url: window.location.href}),

        // the return will be XML, so lets set up a reader
        reader: new Ext.data.XmlReader(
            {record: 'response', id: 'href'}, 
            ['creationdate', 'displayname', 'getcontentlength', 'getlastmodified', 'collection', 'iconHref']
        )
    });

    // create the grid
    var grid = new Ext.grid.GridPanel({
        store: store,
        border:false,
        columns: [
            {header: "Name", width: 180, dataIndex: 'displayname', sortable: true, hidden:false, renderer:renderName},
            {header: "Created", width: 120, dataIndex: 'creationdate', sortable: true, hidden:false},
            {header: "Size", width: 115, dataIndex: 'getcontentlength', sortable: true, hidden:false}
        ]
    });

    var treeloader = new LABKEY.ext.WebDavTreeLoader({url:base});
    var root = new Ext.tree.AsyncTreeNode({
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
            }});
    var tree = new Ext.tree.TreePanel({
        loader:treeloader,
        root:root,
        rootVisible:false,
        title: 'File Browser',
        useArrows:true,
        autoScroll:true,
        animate:true,
        enableDD:false,
        containerScroll:true,
        border:false
    });

    var border = new Ext.Viewport({
        title: 'Border Layout',
        layout:'border',
        items: [{
            title: 'South Panel',
            region: 'south',
            height: 100,
            minSize: 75,
            maxSize: 250,
            margins: '0 5 5 5',
            html:'south'
        },{
            region:'west',
            id:'west-panel',
            title:'West',
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
                tree,
                {
                    title:'Settings',
                    html:'<p>Some settings in here.</p>',
                    border:false,
                    iconCls:'settings'
                }]
        },{
            title: 'Main Content',
            region:'center',
            margins:'5 0 5 0',
            minSize: 200,
            items: [grid]
        },{
            title: 'Properties',
            region:'east',
            split:true,
            margins:'5 5 5 0',
            width: 200,
            minSize: 100,
            html: 'east'
        }]
    });

    border.render("files");
    store.load();
});

</script>



<div id="files" class="ext-container"></div>



</body>
</html>
