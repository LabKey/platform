<%
/*
 * Copyright (c) 2010 LabKey Corporation
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
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.issue.IssuesController" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<IssuesController.SearchResultsView> me = (JspView<IssuesController.SearchResultsView>) HttpView.currentView();
    IssuesController.SearchResultsView form = me.getModelBean();
    String img = me.getViewContext().getContextPath() + "/_.gif";
    String q = StringUtils.trimToEmpty(form._query);
%>

<div id='searchRenderDiv'>
</div>

<script type="text/javascript">
var wait = null;
var request = null;                                                
var template = new Ext.XTemplate(
    '<tpl for=".">',
    '<a href="{url:htmlEncode}">{title:htmlEncode}</a><br>',
      '<div style="margin-left:10px;">{summary:htmlEncode}<br>[<a href="#" onclick="preview({issueid})" style="color:green;">preview</a>]</div>',
    '<br></tpl>'
);
template.compile();
var task = new Ext.util.DelayedTask(search);


function q_onchange(qField,e)
{
    var keycode = e.keyCode;
    var q = qField.getValue();
    if (request)
        Ext.Ajax.abort(request);
    request = null;
    if (!q)
    {
        task.cancel();
        return;
    }
    var delay = 300;
    if (!keycode || keycode == e.TAB || keycode == e.ENTER)
        delay = 1;
    else if (keycode == e.BACKSPACE || keycode == e.DELETE)
        delay = 600;
    task.delay(delay, null, null, [q]);
}


function search_onclick()
{
    search(Ext.get('q').getValue());
}


var _lastQ = null;

function search(q)
{
    if (request)
        Ext.Ajax.abort(request);
    request = null;

    q = q ? q.trim() : '';
    if (!q || q == _lastQ)
        return;
    _lastQ = q;

    Ext.get('resultsDiv').update(Ext.Updater.defaults.indicatorText);

    var url = LABKEY.ActionURL.buildURL("search","json",LABKEY.container.path);
    var params = {
        q:q,
        includeSubfolders:0,
        category:'issue',
        container: LABKEY.container.id,
        offset:0,
        limit:1000 };
    request = Ext.Ajax.request({
        url:url,
        success:loadResults,
        failure:failedSearch,
        params:params
    });
}


function setMessage(msg)
{
    var e = Ext.get('message');
    if (e) e.update(msg);
}


var cacheHits = {};

function loadResults(response, options)
{
    var target = Ext.get('resultsDiv');

    cacheHits = {};
    var json={};
    var i;

    try
    {
        json = eval("("+response.responseText+")");
    }
    catch (x)
    {
    }
    if (!json || !json.success || !json.hits || !json.hits.length)
        return noResults();

    var hits = json.hits;
    for (i=0 ; i<hits.length ; i++)
    {
        var hit = hits[i];
        var title = hit.title;
        hit.issueid = parseInt(title.substr(0,title.indexOf(':')+1).trim());
//        hit.summary = hit.summary.substring(0,200);
        cacheHits[hit.issueid] = hit;
    }
    template.overwrite(target,hits);

    var elems = Ext.DomQuery.select("TD[class=_preview]");
    for (i=0 ; i<elems.length ; i++)
    {
        var elem = elems[i];
        Ext.fly(elem).hover(
            function(){Ext.fly(elem).applyStyles({border:'1px solid gray'});},
            function(){Ext.fly(elem).applyStyles({border:''});}
        );
    }
}


function failedSearch(response, options)
{
    // UNDONE: inspect response
    noResults();
}


function noResults()
{
    var target = Ext.get('resultsDiv');
    target.update('<i>No results</i>');
}



var usePreviewWindow = true;
var _previewWindow = null;
var _previewPanel = null;
var _previewIssue = null;

function preview(issueid)
{
    _previewIssue = cacheHits[issueid];
    if (!_previewIssue)
        return;
    var url = _previewIssue.url + "&_print=1";

    if (!_previewPanel)
    {
        if (null == _previewWindow && usePreviewWindow)
        {
            _previewWindow = new Ext.Window({width:600, height:600, autoScroll:true, closeAction:'hide'});
            _previewWindow.render(Ext.getBody());
            _previewPanel = _previewWindow;
        }
        else
        {
            _previewPanel = previewPanel;
        }
        _previewPanel.getUpdater().on("update",setTitleLink);
    }

    if (_previewPanel)
        _previewPanel.load({url:url});
    if (_previewWindow)
        _previewWindow.show();
        _previewWindow.show();
}


function setTitleLink(el)
{
    var selector = "TD.labkey-body-panel TD.labkey-wp-title";
    var td = Ext.DomQuery.selectNode(selector,el.dom);
    if (!td) return;
    if (!_previewIssue) return;                
    td = Ext.fly(td);
    var html = td.dom.innerHTML;
    td.update('<a href="' + Ext.util.Format.htmlEncode(_previewIssue.url) + '">' + html + '</a>');
}


function startWait()
{
    document.body.style.cursor = 'wait';
    setMessage('Searching. . .');
}


function endWait()
{
    document.body.style.cursor = '';
    setMessage('');
}

var searchPanel, resultsPanel, previewPanel;
var q;

Ext.onReady(function()
{
    Ext.Ajax.on('beforerequest', startWait);
    Ext.Ajax.on('requestcomplete', endWait);
    Ext.Ajax.on('requestexception', endWait);

    searchPanel = new Ext.Panel({
        height:30,
        borders:false,
        items:[{xtype:'textfield', id:'q', name:'q', height:26, width:800, borders:false, enableKeyEvents:true, value:<%=PageFlowUtil.jsString(q)%>}]
    });
    searchPanel.on("resize",function(p,x,y){Ext.getCmp('q').setSize(x,y);});

    resultsPanel = new Ext.Panel({id:'resultsPanel', autoScroll:true, html:'<div id="resultsDiv">'});

    previewPanel = new Ext.Panel({id:'previewPanel', autoScroll:true});

    var items = [
            { region:'north', split:false, layout:'fit', items:searchPanel, height:30 },
            { region:'center', split:true, layout:'fit', items:resultsPanel }];
    if (!usePreviewWindow)
        items.push({ region:'south', split:true, layout:'fit', items:previewPanel, height:250, style:{background:'#cccccc'}});

    var topPanel = new Ext.Panel({
        layout:'border',
        height:900, width:800,
        items:items
    });

    topPanel.render('searchRenderDiv');

    var resizeWindow = function(w,h)
    {
        if (!topPanel.rendered || !topPanel.el)
            return;
        var xy = topPanel.el.getXY();
        var width = Math.max(400,(Math.min(800,w-xy[0]-20)));
        var height = Math.max(400,h-xy[1]-20);
        topPanel.setSize(width,height);
        topPanel.doLayout();
        resultsPanel.setSize(width,undefined);
        var results = Ext.get("resultsDiv");
        if (results)
            Ext.fly(results).applyStyles({width:width-30});
    };
    var delay = new Ext.util.DelayedTask();
    Ext.EventManager.onWindowResize(function(w,h){delay.delay(100,function(){resizeWindow(w,h)});});
    Ext.EventManager.fireWindowResize();

    q = Ext.getCmp('q');
    q.on('keypress',q_onchange);
    q.on('keydown',q_onchange);
    q.on('keyup',q_onchange);
    q.on('change',q_onchange);

    search(<%=PageFlowUtil.jsString(q)%>);
});

</script>
