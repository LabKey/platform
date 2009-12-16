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
    PipelineController.SearchWebPart me = (PipelineController.SearchWebPart) HttpView.currentView();
    PipeRoot root = me.getPipeRoot();
    Container c = null == root ? me.getContainer() : root.getContainer();

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

<div class="extContainer" style="<%=me.getAutoResize() ? "padding-left:10px;" : ""%>">
<div id="files"></div>
</div>

<div style="display:none;">
<div id="help">
    help helpy help.
</div>
</div>

<script type="text/javascript">
Ext.BLANK_IMAGE_URL = LABKEY.contextPath + "/_.gif";
var rootPath = <%=PageFlowUtil.jsString(rootPath)%>;
var searchURL = <%=PageFlowUtil.jsString(new ActionURL("search","search",c).getLocalURIString())%>;
// <a href="/labkey/_webdav/home/source/@pipeline/modules/search/src/org/labkey/search/model/LuceneSearch.java">
var sourcePattern = /<a href="\/labkey\/_webdav\/home\/source\/.*pipeline([~"]*)">/g;
sourcePattern.compile();

var $c = function(a,b) {return fileSystem.concatPaths(a,b);};
var $h = Ext.util.Format.htmlEncode;

var fileSystem = null;
var fileBrowser = null;
var autoResize = <%= me.getAutoResize() ? "true" : "false" %>;
var searchForm = null;
var searchResult = null;


var previewWindow = null;

function getPreviewWindow()
{
    if (!previewWindow)
    {
        previewWindow = new Ext.Window({
            width:800, height:600,
            closeAction : 'hide',
            layout : 'fit',
            items : [{xtype:'panel', autoScroll:true, id:'previewPanel'}]
        });
    }
    return previewWindow;
}

function previewText(path)
{
    var wnd = getPreviewWindow();

    var url = $c(fileSystem.baseUrl, encodeURI(path));
    Ext.Ajax.request({
        url: url,
        params: { contentDisposition:'inline' },
        success: function(resp){
            wnd.show();
            var text = resp.responseText;
            wnd.getComponent('previewPanel').body.update("<pre>" + $h(text) + "</pre>");
            previewWindow.show();
        }
    });
}

function preview(path)
{
    var s = path.lastIndexOf('/');
    var dir = path.substring(0,s);
    fileSystem.listFiles(dir,
        function(){
            var record = fileSystem.recordFromCache(path);
            if (record)
            {
                if (record.data.contentType.substring(0,5) == "text/")
                    previewText(path);
                else
                    alert(record.data.contentType);
            }
        }
    );
//    var url = fileSystem.baseUrl + encodeURI(path);
//    window.open(url + '?contentDisposition=inline', "sourcePreview");
}

function openFile(path)
{
    var s = path.lastIndexOf('/');
    var dir = path.substring(0,s);
    fileBrowser.changeDirectory(dir);
    preview(path);
}

           
function displaySearchResult(resp)
{
    var prefix = unescape(fileSystem.baseUrl);
//    var prefix = "/labkey/_webdav/home/source/@pipeline/";
    var text = resp.responseText;
    var split = text.split('<a href="');
    var matches = {};
    for (var i=1 ; i<split.length ; i++)
    {
        var q = split[i].indexOf('"');
        if (-1 == q)
            continue;
        var a = decodeURI(split[i].substring(0,q));
        if (a.substring(0,prefix.length) == prefix)
        {
            var path = a.substring(prefix.length-1);
            matches[path] = path;
        }
    }
    var html = [];
    for (var path in matches)
    {
        var s = path.lastIndexOf('/');
        var dir = path.substring(0,s);
        var file = path.substring(s+1);
        html.push("<div onclick=\"openFile('" + $h(path) + "')\">");
        html.push($h(file));
        html.push("</div>");
    }
    searchResult.update(html.join(''));
}


function submitSearch()
{
//    var form = searchForm.getForm();
//    var query = form.getValues()['query'];
    var query = searchForm.getComponent('query').getValue();
    if (!query)
        return;
//  console.debug(query);
    Ext.Ajax.request({
       url: searchURL,
       success: displaySearchResult,
       params: { _print:'1', query:query }
    });
}

Ext.onReady(function()
{
    Ext.QuickTips.init();

    fileSystem = new LABKEY.WebdavFileSystem({
        baseUrl:<%=PageFlowUtil.jsString(rootPath)%>,
        rootName:<%=PageFlowUtil.jsString(rootName)%>});

    fileBrowser = new LABKEY.FileBrowser({
        fileSystem:fileSystem
        ,statePrefix:'<%=context.getContainer().getId()%>#pipeline'
        ,helpEl:null
        ,showAddressBar:true
        ,showFolderTree:true
        ,showDetails:true
        ,allowChangeDirectory:true
        ,propertiesPanel:[{title:'Search', id:'searchPanel', autoScroll: true}]
        ,tbar:['parentFolder', 'download', 'deletePath', 'refresh', 'createDirectory', 'uploadTool']
    });
    fileBrowser.on(BROWSER_EVENTS.doubleclick, function(record){preview(record.data.path);})

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

    var searchPanel = Ext.getCmp('searchPanel');
    var body = searchPanel.body;

    searchForm  = new Ext.Panel({
        defaultType:'textfield',
        autoHeight:true,
        items:[{name:'query', fieldLabel:null, editable:true, height:20, id:'query'}], 
        buttons:[{text:'search', handler:submitSearch}]
    });
    searchForm.render(searchPanel.body);
    searchResult = Ext.DomHelper.insertAfter(searchForm.el, "<div id='searchResult'></div>", true);

    <% String url = new ActionURL("search","search",c).addParameter("_print","1").getLocalURIString(); %>
//    searchPanel.update("<iframe id='searchFrame' width=100% height=100% src='<%=h(url)%>'/></iframe>");
});
</script>