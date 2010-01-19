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
<div id="searchDiv">
    
</div>

<script>
var searchField;
var store;

Ext.onReady(function(){
    searchField = new Ext.form.TextField({id:'searchField', fieldLabel:'Search for', width:400});
    var form = new Ext.form.FormPanel({
        border : false,
        items:[searchField]
    });
    searchField.on("change",function(){search(searchField.getValue());});
    form.render('searchDiv');

    store = new Ext.data.JsonStore({
        url: 'json.view', root:'hits', fields:['id','title','summary','url']
    });
    store.on("load",load);
    var model = new Ext.grid.ColumnModel({
        defaults: {width:190, renderer:Ext.util.Format.htmlEncode},
        columns:[{id:'id', width:20},{id:'title', header:'Title'},{id:'summary', header:'Summary'},{id:'url',header:'URL'}]
    });
    var grid = new Ext.grid.GridPanel({
        store: store,
        colModel : model,
        width: 600,
        height: 400
    });
    grid.render('searchDiv');
});

function search(q)
{
    if (!q)
        return;
    store.reload({params:{q:q}});
}

function load(store,records,options)
{
    console.dir(store.reader.jsonData);
    console.dir(options);
}

</script>