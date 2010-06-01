<%
/*
 * Copyright (c) 2009-2010 LabKey Corporation
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
<%@ page import="org.labkey.api.util.SessionAppender" %>
<%@ page import="org.apache.log4j.spi.LoggingEvent" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%    
    boolean loggingEnabled = SessionAppender.isLogging(request);
%>

<%--<form method=POST>
logging: <input name=logging type="checkbox" <%=loggingEnabled?"checked":""%> value="true"><%= PageFlowUtil.generateSubmitButton("submit")%>
</form>

<div class="extContainer" id="logDiv">
</div>
--%>
<script type="text/javascript">

var timeRenderer = Ext.util.Format.dateRenderer("H:m:s");
var timestampRenderer = function(l){ return timeRenderer(new Date(l)); }

Ext.onReady(function(){

    var eventRecord = Ext.data.Record.create([
        {name: 'eventId'},
        {name: 'level'},
        {name: 'timestamp'},
        {name: 'message'}
    ]);

    var eventReader = new Ext.data.JsonReader({
        idProperty: 'eventId',
        root: 'events',
        fields : eventRecord
    });
    
    var url = LABKEY.ActionURL.buildURL("admin", "getSessionLogEvents.api", "/", {eventId:0});

    var eventStore = new Ext.data.Store(
    {
        reader: eventReader,
        url : url
    });

/*
    var grid = new Ext.grid.GridPanel({
        width:800, height:400,
        store: eventStore,
        colModel: new Ext.grid.ColumnModel(
        {
                defaults: {
                    width: 120,
                    sortable: false
                },
                columns: [
                    {dataIndex: 'level'},
                    {dataIndex: 'timestamp', renderer:timestampRenderer},
                    {dataIndex: 'message', width:550}
                ]
        }),
    });
*/

    var listView = new Ext.list.ListView({
        store: eventStore,
        multiSelect: true,
        emptyText: 'No events',
        reserveScrollOffset: true,
        columns: [
        {
            header: 'Time',
            width: 0.15,
            dataIndex: 'timestamp',
            //renderer:timestampRenderer,
            tpl: '{timestamp:date("H:i:s")}',
            align: 'right'
        },{
            width: .6,
            header: 'Message',
            dataIndex: 'message',
            tpl: '{message:htmlEncode()}'
        }]
    });
    var vp = new Ext.Viewport({layout:'fit', ctCls:'extContainer', items:[listView]});

    var autoScrollToBottom = true;


    function scrollContainer()
    {
        return listView.getTemplateTarget().dom.parentNode;
    }

    function updateConsole()
    {
        var sc = scrollContainer();
        autoScrollToBottom = sc.scrollTop+sc.clientHeight == sc.scrollHeight;

        var eventId = 0;
        if (eventStore.getCount() > 0)
            eventId = eventStore.getAt(eventStore.getCount()-1).data.eventId;
        eventStore.load({params:{eventId:eventId}});
    }

    function onAdd()
    {
        if (autoScrollToBottom)
        {
            var sc = scrollContainer();
            var d = listView.getTemplateTarget().dom.lastChild.lastChild;
            Ext.fly(d).scrollIntoView(listView.getTemplateTarget().dom.parentNode, false);
        }
    }

    eventStore.on({add:onAdd, datachanged:onAdd});
    updateConsole();
    Ext.TaskMgr.start({ run:updateConsole, interval:1000 });
});
</script>