<%
/*
 * Copyright (c) 2012 LabKey Corporation
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
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.json.JSONObject" %>

<%
    JspView<Portal.WebPart> me = (JspView) HttpView.currentView();
    int webPartId = me.getModelBean().getRowId();
    JSONObject jsonProps = new JSONObject(me.getModelBean().getPropertyMap());
    String renderTarget = "history-" + webPartId + "-" + me.getModelBean().getIndex();
%>

<div id='<%=renderTarget%>'></div>

<script type="text/javascript">

    LABKEY.requiresExt4ClientAPI(true);

</script>
<script type="text/javascript">

Ext4.onReady(function(){
    var config = '<%=jsonProps%>';
    config = Ext4.decode(config);

    Ext4.apply(config, {
        pageSize: 30,
        duration: 7   //max days to show
    });

    var store = Ext4.create('LABKEY.ext4.Store', {
        schemaName: 'auditLog',
        queryName: 'LabAuditEvents',
        columns: 'date,createdby,comment,containerId',
        sort: '-Date',
        groupField: 'Date',
        groupDir: 'DESC',
        pageSize: config.pageSize,
        getGroupString: function(rec){
            var string;
            var recDate = rec.get('Date');
            var now = new Date();
            if(now.getDate() == recDate.getDate() && now.getFullYear() == recDate.getFullYear())
                string = 'Today';
            else
                string =  rec.get('Date').format('l, F d, Y');
            return string;
        },
        filterArray: [
            LABKEY.Filter.create('date', '-'+config.duration+'d', LABKEY.Filter.Types.DATE_GREATER_THAN_OR_EQUAL)
        ],
        metadata: {
            Date: {
                format: 'h:m A',
                columnConfig: {
                    width: 75
                }
            },
            CreatedBy: {
                //NOTE: this is to reformat entries with a null user to be blank, instead of [0]
                buildDisplayString: function(config){
                    return config.displayValue == '[0]' ? "" : config.displayValue;
                },
                columnConfig: {
                    width: 100
                }
            },
            ContainerId: {
                columnConfig: {
                    width: 200
                }
            }
        },
        listeners: {
            scope: this,
            load: function(s){
                onStoreLoad(s);
            }
        },
        autoLoad: {pageSize: config.pageSize}
    });

function onStoreLoad(store){
    var renderTarget = Ext4.get('<%=renderTarget%>');

    var panel = Ext4.create('Ext.panel.Panel', {
        border: false,
        //layout: 'fit',
        items: [{
            border: false,
            layout: 'hbox',
            align: 'center',
            style: 'padding-bottom:10px;',
            defaults: {
                xtype: 'labkey-linkbutton',
                style: 'padding-right: 20px;',
                linkCls: 'labkey-text-link'
            },
            items: [{
                text: 'New Experiment',
                handler: function(btn){
                    if(LABKEY.container && LABKEY.container.isWorkbook)
                        window.location = LABKEY.ActionURL.buildURL('project', 'begin');
                    else
                        Ext4.create('LABKEY.ext.ImportWizardWin', {
                            canAddToExistingExperiment: false,
                            controller: 'project',
                            action: 'begin',
                            title: 'Create Experiment'
                        }).show();
                },
                renderData: {
                    icon: LABKEY.ActionURL.getContextPath() + '/study/tools/study_overview.png'
                }
            },{
                text: 'Import Data',
                renderData: {
                    icon: LABKEY.ActionURL.getContextPath() + '/study/tools/study_overview.png'
                },
                menu: {
                    showSeparator: false,
                    itemId: 'importDataMenu',
                    items: ['Loading...']
                }
            },{
                text: 'Import Samples',
                renderData: {
                    icon: LABKEY.ActionURL.getContextPath() + '/study/tools/specimen_report.png'
                },
                menu: {
                    showSeparator: false,
                    itemId: 'importSamplesMenu',
                    items: ['Loading...']
                }
            }]
        },{
            xtype: 'labkey-gridpanel',
            store: store,
            disableSelection: true,
            forceFit: true,
            hideHeaders: true,
            width: renderTarget.getWidth(),
            bodyStyle: 'border: 0;',
            border: false,
            frame: false,
//            scroll   : 'vertical',
            multiSelect: false,
            viewConfig : {
                stripRows : true,
                emptyText : 'No Events'
            },
            features  : [Ext4.create('Ext4.grid.feature.Grouping', {
                groupHeaderTpl : '&nbsp;{name}' // &nbsp; allows '+/-' to show up
            })]
        }],
        renderTo: renderTarget.id
    });

    Ext4.EventManager.onWindowResize(function(){
        panel.setWidth(renderTarget.getWidth());
    }, this);

    LABKEY.Query.selectRows({
        containerPath: null,
        scope: this,
        schemaName: 'exp',
        queryName: 'SampleSets',
        success: function(data){
            var menu = panel.down('#importSamplesMenu');
            menu.removeAll();

            if(data && data.rows && data.rows.length){
                Ext4.each(data.rows, function(row){
                    menu.add({
                        text: row.Name,
                        controller: 'experiment',
                        importAction: 'showUploadMaterials',
                        urlParams: {name: row.Name, importMoreSamples: true},
                        handler: function(btn){
                            window.location = LABKEY.ActionURL.buildURL(btn.controller, btn.importAction, null, btn.urlParams)
                        }
                    });
                }, this);
            }
            else {
                menu.add({
                    text: 'No Sample Sets Defined'
                })
            }
        },
        failure: LABKEY.Utils.onError
    });

    LABKEY.Assay.getAll({
        containerPath: null,
        scope: this,
        success: function(results){
            var menu = panel.down('#importDataMenu');
            menu.removeAll();
            if(results && results.length){
                results = results.sort(function(a,b){
                    if(a.name < b.name) return -1;
                    else if(a.name > b.name) return 1;
                    else return 0;
                });

                Ext4.each(results, function(i){
                    menu.add({
                        text: i.name,
                        assayId: i.id,
                        urlParams: {rowId: i.id, srcURL: LABKEY.ActionURL.buildURL('project', 'begin')},
                        importAction: i.importAction || 'moduleAssayUpload',
                        importController: i.importController || 'assay',
                        handler: function(btn){
                            if(LABKEY.container && LABKEY.container.isWorkbook)
                                window.location = LABKEY.ActionURL.buildURL(btn.importController, btn.importAction, null, {rowId: btn.assayId});
                            else
                                Ext4.create('LABKEY.ext.ImportWizardWin', {
                                    controller: btn.importController,
                                    action: btn.importAction,
                                    urlParams: btn.urlParams
                                }).show();
                        }
                    })
                }, this);
            }
            else {
                menu.add({
                    text: 'No Assays Defined'
                })
            }
        },
        failure: LABKEY.Utils.onError
    });

}
});
</script>