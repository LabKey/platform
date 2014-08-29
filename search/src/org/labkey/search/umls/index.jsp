<%
/*
 * Copyright (c) 2010-2014 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.*" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.search.umls.UmlsController" %>
<%@ page import="org.labkey.api.util.PollingUtil" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
JspView<UmlsController.IndexAction> me = (JspView<UmlsController.IndexAction>) HttpView.currentView();
UmlsController.IndexAction form = me.getModelBean();
PollingUtil.PollKey pollKey = form._key;
String pollURL = null==pollKey ? null : pollKey.getUrl();

%>
<labkey:errors></labkey:errors>
<labkey:form action="index.post" style='display:<%=null==pollURL?"block":"none"%>' method="POST">
    <input name="path" value="<%=h(request.getParameter("path"))%>"><input type="submit" name="START" value="START"></labkey:form>
<div style="display:<%=null==pollURL?"none":"block"%>"><span id="status"></span></div>
<script type="text/javascript">
var url = <%=PageFlowUtil.jsString(pollURL)%>;
var task=null;
var req=null;
var stopped=false;
var numberFormat = function(n) {return '' + n;}; //Ext.util.Format.number(n,"0,000");};
function stop()
{
    Ext.TaskMgr.stop(task);
    Ext.Ajax.abort(req);
    stopped=true;
}
function updateCount(response,options)
{
    var o = eval('(' + response.responseText + ')');
    var status = '';
    if ('count' in o)
    {
        if ('done' in o && o.done)
        {
            stop();
            status = "DONE: " + o.count;
        }
        else
        {
            if ('status' in o && o.status)
                status = o.status + ' ';
            if ('estimate' in o && o.estimate>0)
            {
                status += numberFormat(o.count) + ' ' + Math.round(100.0*o.count/o.estimate) + '%';
            }
            else
            {
                status += numberFormat(o.count);
            }
        }
        Ext.get('status').update(status);
    }
}
function requestCount()
{
    req = Ext.Ajax.request({
       url: url,
       success: updateCount,
       failure: function(response,options)
       {
           if (stopped) return;
           stop();
           alert(response.statusText + "\n\n" + response.responseText);
       }
    });
}
Ext.onReady(function(){
    if (<%=null==pollURL?"false":"true"%>)
    task = Ext.TaskMgr.start({ run: requestCount, interval: 500 });
});
</script>
