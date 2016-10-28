<%
/*
 * Copyright (c) 2014-2016 LabKey Corporation
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

Ext.onReady(function(){

    var requestRecord = Ext.data.Record.create([
        {name: 'requestId'},
        {name: 'url'},
        {name: 'date'},
        {name: 'objects'}
    ]);

    var requestReader = new Ext.data.JsonReader({
        idProperty: 'requestId',
        root: 'requests',
        fields : requestRecord
    });

    var url = LABKEY.ActionURL.buildURL("admin", "getTrackedAllocations.api", "/");

    var requestStore = new Ext.data.Store(
    {
        reader: requestReader,
        url : url
    });

    var lastRequestId = 0;

    var listView = new Ext.list.ListView({
        store: requestStore,
        multiSelect: true,
        emptyText: 'No requests',
        reserveScrollOffset: true,
        columns: [
        {
            header: 'Id',
            width: 0.05,
            dataIndex: 'requestId',
            tpl: '{requestId}',
            align: 'right'
        },{
                header: 'Time',
                width: 0.05,
                dataIndex: 'date',
                tpl: '{date:date("H:i:s")}',
                align: 'right'
        },{
            width: 0.45,
            header: 'URL',
            dataIndex: 'url',
            tpl: '{url:htmlEncode()}'
        },{
            width: 0.45,
            header: 'Objects',
            dataIndex: 'objects',
            tpl: '<tpl for="objects">{count} @ {name:htmlEncode()}<br/></tpl>'
        }]
    });
    var panel = new Ext.Panel({
        layout:'fit',
        items:[listView],
        tbar:[{text:'Clear', handler:function(){if (requestStore) requestStore.removeAll();}}]
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

        if (requestStore.getCount() > 0)
            lastRequestId = requestStore.getAt(requestStore.getCount()-1).data.requestId;
        requestStore.load({add:true, params:{requestId:lastRequestId}});
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

    requestStore.on({add:onAdd, datachanged:onAdd});
    updateConsole();
    Ext.TaskMgr.start({ run:updateConsole, interval:5000 });
});
</script>
