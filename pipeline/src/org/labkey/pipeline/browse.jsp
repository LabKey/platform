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
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
//FastDateFormat dateFormat = FastDateFormat.getInstance("EEE, dd MMM yyyy HH:mm:ss zzz", TimeZone.getTimeZone("GMT"), Locale.US);
FastDateFormat dateFormat = FastDateFormat.getInstance("EEE, dd MMM yyyy HH:mm:ss zzz");
%>
<%
    ViewContext context = HttpView.currentContext();
    PipelineController.BrowseWebPart me = (PipelineController.BrowseWebPart) HttpView.currentView();
    PipeRoot root = me.getPipeRoot();
    Container c = root.getContainer();

    // prefix is where we what the tree rooted
    // TODO: applet and fileBrowser could use more consistent configuration parameters
    String rootName = c.getName();
    String webdavPrefix = context.getContextPath() + "/" + WebdavService.getServletPath();
    String rootPath = webdavPrefix + c.getEncodedPath() + "@pipeline/";
%>
<script type="text/javascript" language="javascript">
    LABKEY.requiresExtJs(true);
    LABKEY.requiresScript("fileBrowser.js");
    LABKEY.requiresScript("applet.js",true);
    LABKEY.requiresScript("FileUploadField.js");
</script>

<div class="extContainer" style="padding:20px;">
<div id="files"/>
</div>

<div style="display:none;">
<div id="help">
    help helpy help.
</div>
</div>

<script type="text/javascript">
Ext.BLANK_IMAGE_URL = LABKEY.contextPath + "/_.gif";
var rootPath = <%=PageFlowUtil.jsString(rootPath)%>;
var actionsURL = <%=PageFlowUtil.jsString(new ActionURL(PipelineController.ActionsAction.class,c).getLocalURIString() + "path=")%>;

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
    pipelinePanel.body.update('<i>loading...</i>');
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
                pipelinePanel.body.update('<i>no actions</i>');
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

    var all = {id:'showAll', style:{display:'none'}, children:['[', {tag:'a', onClick:'showAllActions()', href:'#', children:['show all']},']']};
    html.push(all);
    
    for (var i=0 ; i<actions.length ; i++)
    {
        var action = actions[i];
        var actionMarkup = {tag:'div', id:'actiondiv' + ++actionDivCounter, style:styleAction, children:[]};
        actionDivs.push(actionMarkup.id);
        // BUTTONS
        for (var l=0 ; l<action.links.length ; l++)
        {
            var link = action.links[l];
            var a = {tag:'a', href:link.href || '#action', children:['<span>',$h(link.text),'</span>']};
            var span = {tag:'span', 'cls':'labkey-button', children:[a]};
            if (link.items && link.items.length)
            {
                var menuid = 'actionmenu' + ++actionMenuCounter;
                a.children.push("&nbsp;&nbsp;&nbsp;");
                a.children.push({tag:'img', src:LABKEY.imagePath+'/text_link_arrow.gif', 'cls':'labkey-button-arrow'});
                activeMenus[menuid] = new Ext.menu.Menu(Ext.apply(link, {cls:'extContainer'}));
                span.id = menuid;
                span.onClick = "showActionMenu(this)";
            }
            actionMarkup.children.push(span);
            actionMarkup.children.push('<br>');
        }
        // FILES
        actionMarkup.children.push('<ul style="margin-left:5px;">');
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
            fileSets[key].links.push(action.links);
        }
        else
        {
            fileSets[key] = {files:action.files, links:[action.links]};
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


var styleAction = {display:'block', width:'', margin:'5px', padding:'3px', 'background-color':'#f8f8e0', border:'solid 1px #c0c0c0' }
var styleHiddenAction = {display:'none'};
var styleSelectedAction = {display:'block'};


function showAllActions()
{
    var i, el;
    for (i=0 ; i<actionDivs.length ; i++)
    {
        el = Ext.get(actionDivs[i]);
        showAction(el);
    }
    if (Ext.get('showAll'))
        Ext.get('showAll').setStyle({display:'none'});
}

function showAction(el)
{
    if (!('save' in el) || el.save.showing)
        return;
    el.scale(Ext.ComponentMgr.get('pipelinePanel').getEl().getWidth(), el.save.height, {duration:.1, afterStyle:styleAction});
    //el.slideIn('r');
    el.fadeIn({duration:.1});
    el.save.showing = true;
}

function hideAction(el)
{
    if (!('save' in el))
        el.save = {showing:true, width:el.getWidth(), height:el.getHeight()};
    if (!el.save.showing)
        return;
    //el.slideOut('r');
    el.fadeOut({duration:.1});
    el.scale(el.getWidth(), 0, {duration:.1, afterStyle:styleHiddenAction});
    el.save.showing = false;
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
            showAction(el);
        else
            hideAction(el);
    }
    if (Ext.get('showAll'))
    {
        if (actionDivs.length == 0 || ids.length == actionDivs.length)
            Ext.get('showAll').setStyle({display:'none'});
        else
            Ext.get('showAll').setStyle({display:'block'});
    }
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

    var dropAction = new Ext.Action({text: 'Upload multiple files', scope:this, disabled:true, handler: function()
    {
        if (!fileBrowser.currentDirectory)
            return;
        var path = fileBrowser.currentDirectory.data.path;
        var dropUrl = <%=PageFlowUtil.jsString((new ActionURL("ftp","drop",c)).getEncodedLocalURIString() + "pipeline=")%> + encodeURIComponent(path);
        window.open(dropUrl, '_blank', 'height=600,width=1000,resizable=yes');
    }});

    fileBrowser = new LABKEY.FileBrowser({
        fileSystem:fileSystem
        ,statePrefix:window.location.pathname + '#pipeline'
        ,helpEl:null
        ,showAddressBar:true
        ,showFolderTree:true
        ,showDetails:true
        ,allowChangeDirectory:true
        ,propertiesPanel:[{title:'Pipeline Actions', id:'pipelinePanel'}]
        ,actions:{drop:dropAction}
        ,tbar:['download','deletePath','refresh','drop']
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

    var resizer = new Ext.Resizable('files', {width:800, height:600, minWidth:640, minHeight:400});
    resizer.on("resize", function(o,width,height){ this.setWidth(width); this.setHeight(height); }.createDelegate(fileBrowser));
    fileBrowser.start();
});
</script>

