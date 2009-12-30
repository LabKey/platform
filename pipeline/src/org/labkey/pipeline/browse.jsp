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
<%@ page import="org.apache.commons.lang.time.FastDateFormat" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.webdav.WebdavService" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.pipeline.PipelineController" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.pipeline.PipeRoot" %>
<%@ page import="org.labkey.api.util.URIUtil" %>
<%@ page import="org.labkey.api.security.permissions.AdminPermission" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
//FastDateFormat dateFormat = FastDateFormat.getInstance("EEE, dd MMM yyyy HH:mm:ss zzz", TimeZone.getTimeZone("GMT"), Locale.US);
FastDateFormat dateFormat = FastDateFormat.getInstance("EEE, dd MMM yyyy HH:mm:ss zzz");
%>
<%
    ViewContext context = HttpView.currentContext();
    PipelineController.BrowseWebPart me = (PipelineController.BrowseWebPart) HttpView.currentView();
    PipeRoot root = null;//me.getPipeRoot();
    Container c = null;// == root ? me.getContainer() : root.getContainer();

    // prefix is where we want the tree rooted
    // TODO: applet and fileBrowser could use more consistent configuration parameters
    String rootName = c.getName();
    String webdavPrefix = context.getContextPath() + "/" + WebdavService.getServletPath();
    String rootPath = webdavPrefix + c.getEncodedPath() + "%40pipeline/";

    if (root == null || !URIUtil.exists(root.getUri()))
    {
        %>Pipeline directory is not set or does not exist on disk.<br><%
        if (c.hasPermission(context.getUser(), AdminPermission.class))
        {
            %><%=PageFlowUtil.generateButton("Setup", new ActionURL(PipelineController.SetupAction.class, c))%><%
        }
        return;
    }
%>
<script type="text/javascript" language="javascript">
    LABKEY.requiresExtJs(true);
    LABKEY.requiresScript("FileUploadField.js");
    LABKEY.requiresScript("applet.js");
    LABKEY.requiresScript("fileBrowser.js");
</script>


<div style="display:none;">
<div id="help">
    help helpy help.
</div>
</div>

<script type="text/javascript">
Ext.BLANK_IMAGE_URL = LABKEY.contextPath + "/_.gif";
var rootPath = <%=PageFlowUtil.jsString(rootPath)%>;
var actionsURL = <%=PageFlowUtil.jsString(new ActionURL(PipelineController.ActionsAction.class,context.getContainer()).getLocalURIString() + "path=")%>;

function $c(a,b)
{
    return fileSystem.concatPaths(a,b);
}

var actionsConnection = new Ext.data.Connection({autoAbort:true});
var activeMenus = {};
var fileMap = {};
var actionDivs = [];
var actionMenuCounter = 0;
var actionDivCounter = 0;


function updatePipelinePanel(record)
{
    var pipelinePanel = Ext.ComponentMgr.get('pipelinePanel');
    if (!pipelinePanel)
        return;
    pipelinePanel.body.update('<div style="padding: 5px;"><i>loading...</i></div>');
    activeMenus = {};
    fileMap = {};
    actionDivs = [];

    var path = record.data.path;
    if (startsWith(path,"/"))
        path = path.substring(1);
    var requestid = actionsConnection.request({
        autoAbort:true,
        url:actionsURL + encodeURIComponent(path),
        method:'GET',
        disableCaching:false,
        success : function(response)
        {
            var o = eval('var $=' + response.responseText + ';$;');
            var actions = o.success ? o.actions : [];
            if (!actions || !actions.length)
                pipelinePanel.body.update('<div style="padding: 5px;"><i>no actions</i></div>');
            else
                renderPipelineActions(actions);
        }
    });
}


function renderPipelineActions(actions)
{
    var html = [];
    activeMenus = {};
    fileMap = {};
    actionDivs = [];

    // combine actions with same filesets action.links will be an array after this call
    actions = consolidateActions(actions);

    // Always show all the actions for the current folder, even when a file is selected
//    var all = {id:'showAll', style:{display:'none'}, children:['[', {tag:'a', onClick:'showAllActions()', href:'#', children:['show all']},']']};
//    html.push(all);
    
    for (var i=0; i < actions.length; i++)
    {
        var action = actions[i];
        var actionMarkup = {tag:'div', id:'actiondiv' + ++actionDivCounter, style:styleAction, children:[]};
        actionDivs.push(actionMarkup.id);

        // UNDONE: some actions depend on this this parameter, why not append it themselves?
        var fileInputNames = "";
        for (var f=0; f < action.files.length; f++)
            fileInputNames += "&fileInputNames=" + encodeURIComponent(action.files[f]);

        // BUTTONS
        for (var lg=0; lg < action.linkgroups.length; lg++)
        {
            var linkgroup = action.linkgroups[lg];
            var link = linkgroup.links;
            var a = {tag:'a', 'cls':'labkey-button', href:$h(link.href ? link.href + fileInputNames : '#action'), children:['<span>',$h(link.text),'</span>']};
            //var span = {tag:'span', 'cls':'labkey-button', children:[a]};
            if (link.items && link.items.length)
            {
                var menuid = 'actionmenu' + ++actionMenuCounter;
                a.children.push("&nbsp;&nbsp;&nbsp;");
                a.children.push({tag:'img', src:LABKEY.imagePath+'/button_arrow.gif', 'cls':'labkey-button-arrow'});
                var menu = toMenu(link);
                activeMenus[menuid] = new Ext.menu.Menu(menu);
                a.id = menuid;
                a.onClick = "showActionMenu(this)";
            }
            actionMarkup.children.push(a);
            actionMarkup.children.push('<br>');

            if (linkgroup.description && linkgroup.description.length > 0)
            {
                actionMarkup.children.push('<em>' + linkgroup.description + '</em>');
            }
        }

        // FILES
        actionMarkup.children.push('<ul style="margin-left:10px;">');
        for (var f=0 ; f<action.files.length ; f++)
        {
            var file = action.files[f];
            actionMarkup.children.push({tag:'li', html:$h(file)});
            if (fileMap[file])
                fileMap[file].push(actionMarkup.id);
            else
                fileMap[file] = [actionMarkup.id];
        }
        actionMarkup.children.push("</ul>");
        html.push(actionMarkup);
    }
    var pipelinePanel = Ext.ComponentMgr.get('pipelinePanel');
    pipelinePanel.body.update(Ext.DomHelper.markup(html));
}


function toMenu(link)
{
    var menu = Ext.apply({}, link);
    menu.cls = 'extContainer';
    var items = [];
    for (var i=0 ; i<link.items.length ; i++)
    {
        var item = link.items[i];
        if (item.text == '-')
            items.push('-');
        else
            items.push(item);
    }
    menu.items = items;
    return menu;
}

function consolidateActions(actions)
{
    var fileSets = {};
    var result = [];
    for (var i=0 ; i<actions.length ; i++)
    {
        var action = actions[i];
        var key = action.files.join(':');
        if (fileSets[key])
        {
            fileSets[key].linkgroups.push({ description: action.description, links: action.links });
        }
        else
        {
            fileSets[key] = {
                files: action.files,
                linkgroups: [{ description : action.description, links: action.links }]
            };
            result.push(fileSets[key]);
        }
    }
    return result;
}


function showActionMenu(el)
{
    el = Ext.get(el);
    //Ext.menu.MenuMgr.get(el.id).show(el, 'tl-bl?');
    activeMenus[el.id].show(el,'tl-bl?');
}


var styleAction = {display:'block', width:'', margin:'5px', padding:'3px', 'background-color':'#f8f8f8', border:'solid 1px #c0c0c0' }
var styleDimmedAction = {'background-color':'#f8f8f8', border:'solid 1px #c0c0c0'};
var styleSelectedAction = {'background-color':'#f8f8e0', border:'solid 1px #000000'};


function showAllActions()
{
    var i, el;
    for (i=0 ; i<actionDivs.length ; i++)
    {
        el = Ext.get(actionDivs[i]);
        dimAction(el);
    }
    if (Ext.get('showAll'))
        Ext.get('showAll').setStyle({display:'none'});
}

function highlightAction(el)
{
//    if (!('save' in el) || el.save.showing)
//        return;
    el.applyStyles(styleSelectedAction);
//    el.scale(Ext.ComponentMgr.get('pipelinePanel').getEl().getWidth(), el.save.height, {duration:.1, afterStyle:styleAction});
//    el.fadeIn({duration:.1});
//    el.save.showing = true;
}

function dimAction(el)
{
//    if (!('save' in el))
//        el.save = {showing:true, width:el.getWidth(), height:el.getHeight()};
//    if (!el.save.showing)
//        return;
    el.applyStyles(styleDimmedAction);
//    el.fadeOut({duration:.1});
//    el.scale(Ext.ComponentMgr.get('pipelinePanel').getEl().getWidth(), el.save.height, {duration:.1, afterStyle:styleDimmedAction});
//    el.save.showing = false;
}

function updateSelection(record)
{
    if (!record || !record.data.file)
    {
        showAllActions();
        return;
    }
    
    var i, el;
    var ids = fileMap[record.data.name] || [];
    var show = {};
    for (i=0 ; i<ids.length ; i++)
        show[ids[i]] = true;
    for (i=0 ; i<actionDivs.length ; i++)
    {
        el = Ext.get(actionDivs[i]);
        if (show[actionDivs[i]])
            highlightAction(el);
        else
            dimAction(el);
    }
//    if (Ext.get('showAll'))
//    {
//        if (actionDivs.length == 0 || ids.length == actionDivs.length)
//            Ext.get('showAll').setStyle({display:'none'});
//        else
//            Ext.get('showAll').setStyle({display:'block'});
//    }
}

Ext.state.Manager.setProvider(new Ext.state.CookieProvider());
var pipelineStateKey = window.location.pathname + "#pipelineDirectory";

var fileSystem = null;
var fileBrowser = null;

Ext.onReady(function()
{
    Ext.QuickTips.init();

    fileSystem = new LABKEY.WebdavFileSystem({
        baseUrl:<%=PageFlowUtil.jsString(rootPath)%>,
        rootName:<%=PageFlowUtil.jsString(rootName)%>});

    var dropAction = new Ext.Action({text: 'Upload multiple files (OLD)', scope:this, disabled:true, handler: function()
    {
        if (!fileBrowser.currentDirectory)
            return;
        var path = fileBrowser.currentDirectory.data.path;
        var dropUrl = <%=PageFlowUtil.jsString((new ActionURL("ftp","drop",c)).getEncodedLocalURIString() + "pipeline=")%> + encodeURIComponent(path);
        window.open(dropUrl, '_blank', 'height=600,width=1000,resizable=yes');
    }});

    fileBrowser = new LABKEY.FileBrowser({
        fileSystem:fileSystem
        ,statePrefix:'<%=context.getContainer().getId()%>#pipeline'
        ,helpEl:null
        ,showAddressBar:true
        ,showFolderTree:true
        ,showDetails:true
        ,allowChangeDirectory:true
        ,propertiesPanel:[{title:'Pipeline Actions', id:'pipelinePanel', autoScroll: true}]
        ,actions:{drop:dropAction}
        ,tbar:['parentFolder', 'download', 'deletePath', 'refresh', 'createDirectory', 'uploadTool']
    });

    fileBrowser.on(BROWSER_EVENTS.directorychange, updatePipelinePanel);
    fileBrowser.on(BROWSER_EVENTS.selectionchange, updateSelection);
    fileBrowser.on(BROWSER_EVENTS.directorychange, function(record)
    {
        if (record && this.fileSystem.canWrite(record))
            dropAction.enable();
        else
            dropAction.disable();
    });

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
    else
    {
        var resizer = new Ext.Resizable('files', {width:800, height:600, minWidth:640, minHeight:400});
        resizer.on("resize", function(o,width,height){ this.setWidth(width); this.setHeight(height); }.createDelegate(fileBrowser));
    }

    fileBrowser.start();
});
</script>

