<%
/*
 * Copyright (c) 2009-2016 LabKey Corporation
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
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext3");
    }
%>
<script type="text/javascript">

var timeRenderer = Ext.util.Format.dateRenderer("H:m:s");
var timestampRenderer = function(l){ return timeRenderer(new Date(l)); };

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
    
    var url = LABKEY.ActionURL.buildURL("admin", "getSessionLogEvents.api", "/");

    var eventStore = new Ext.data.Store(
    {
        reader: eventReader,
        url : url
    });

    var lastEventId = 0;

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
            tpl: '{timestamp:date("H:i:s")}',
            align: 'right'
        },{
            width: 0.85,
            header: 'Message',
            dataIndex: 'message',
            tpl: '<pre>{message:htmlEncode()}</pre>'
        }]
    });
    var panel = new Ext.Panel({
        layout:'fit',
        items:[listView],
        tbar:[{text:'Clear', handler:function(){if (eventStore) eventStore.removeAll();}}]
    });
    var vp = new Ext.Viewport({layout:'fit', ctCls:'extContainer', items:[panel]});

    var autoScrollToBottom = true;


    function scrollContainer()
    {
        return listView.getTemplateTarget().dom.parentNode;
    }

    function updateConsole()
    {
        var sc = scrollContainer();
        autoScrollToBottom = sc.scrollTop+sc.clientHeight == sc.scrollHeight;

        if (eventStore.getCount() > 0)
            lastEventId = eventStore.getAt(eventStore.getCount()-1).data.eventId;
        eventStore.load({add:true, params:{eventId:lastEventId}});
    }

    function onAdd()
    {
        if (autoScrollToBottom)
        {
            var sc = scrollContainer();
            var d = listView.getTemplateTarget().dom.lastChild.lastChild;
            var el;
            if (d && (el = Ext.fly(d)))
                el.scrollIntoView(listView.getTemplateTarget().dom.parentNode, false);
        }
    }

    eventStore.on({add:onAdd, datachanged:onAdd});
    updateConsole();
    Ext.TaskMgr.start({ run:updateConsole, interval:1000 });
});
</script>
