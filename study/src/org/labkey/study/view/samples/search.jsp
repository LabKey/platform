<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.Portal" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.study.samples.SampleSearchBean" %>
<%
/*
 * Copyright (c) 2011-2012 LabKey Corporation
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
<%@ page extends="org.labkey.api.jsp.JspBase" %>

<%
    JspView<SampleSearchBean> me = (JspView) HttpView.currentView();
    int webPartId = me.getModelBean().getWebPartId();
    String renderTarget = "labkey-specimen-search-"+ webPartId;
%>
<script type="text/javascript">
    LABKEY.requiresScript("study/redesignUtils.js", true);
    LABKEY.requiresExt4ClientAPI();
    LABKEY.requiresCss("ux/CheckCombo/CheckCombo.css");
    LABKEY.requiresScript("ux/CheckCombo/CheckCombo.js");
</script>
<style type="text/css">
    .labkey-specimen-search-toggle {
	display: block;
	margin: 5px 0 15px 2px;
	color: #ccc;
}

.labkey-specimen-search-toggle a {
	font-weight: normal;
}

.labkey-specimen-search-toggle a.youarehere {
	color: #000;
	font-weight: bold;
}

#labkey-specimen-search {

}

#labkey-vial-search {
	display: none;
}

td.labkey-padright {
	padding-right: 10px;
	white-space: nowrap;
}

td.labkey-specimen-search-button {
	padding: 10px 0;
}

.labkey-wp-footer {
	width: 98%;
	text-align: right;
	padding: 5px 5px 6px 5px;
	margin-top: 10px;
}

span.labkey-advanced-search {
	display: inline;
	padding: 2px 10px 0 0;
}

select {
	font-family: verdana, arial, helvetica, sans serif;
	font-size: 100%;
}
</style>
<script type="text/javascript">

Ext4.onReady(function(){
    var multi = new LABKEY.MultiRequest();
    var requestFailed = false;
    var errorMessages = [];
    var studyMetadata = null;

    multi.add(LABKEY.Query.selectRows, {schemaName:"study",
        queryName:"StudyProperties",
        success:function (result) {
            if (result.rows.length > 0)
            {
                studyMetadata = result.rows[0];
            }
            else
                errorMessages.push("<i>No study found in this folder</i>");
        },
        failure: function(result) {
            errorMessages.push("<i>Could not retrieve study information for this folder: " + result.exception);
        },
    columns:"*"});

    // Test query to verify that there's specimen data in this study:
    multi.add(LABKEY.Query.selectRows,
        {
            schemaName: 'study',
            queryName: 'SimpleSpecimen',
            maxRows: 1,
            success : function(data)
            {
                if (data.rows.length == 0)
                     errorMessages.push('<i>No specimens found.</i>');
            },
            failure: function(result) {
                errorMessages.push("<i>Could not retrieve specimen information for this folder: </i>" + result.exception);
            }
    });

    multi.send(function() {
        if (errorMessages.length > 0)
            Ext4.get('<%=renderTarget%>').update(errorMessages.join("<br>"));
        else
            Ext4.create('LABKEY.ext.SampleSearchPanel', {}).render('<%=renderTarget%>');
    });

    //TODO: move to JS file?
    Ext4.define('LABKEY.ext.SampleSearchPanel', {
        extend: 'Ext.form.Panel',
        LABEL_WIDTH: 150,
        MAX_COMBO_ITEMS: 200,
        initComponent: function(){
            Ext4.QuickTips.init();
            Ext4.apply(this, {
                border: false,
                bodyStyle: 'padding: 5px;',
                defaults: {
                    border: false
                },
                items: [{
                    xtype: 'radiogroup',
                    itemId: 'searchType',
                    fieldLabel: 'Search Type',
                    labelWidth: this.LABEL_WIDTH,
                    labelStyle: 'font-weight: bold;',
                    afterLabelTextTpl: '<a href="#" data-qtip="Vial group search returns a single row per subject, time point, and sample type.  These results may be easier to read and navigate, but lack vial-level detail"><span class="labkey-help-pop-up">?</span></a>',
                    width: 450,
                    items: [{
                        boxLabel: 'Individual Vials',
                        inputValue: 'individual',
                        name: 'groupType',
                        checked: true
                    },{
                        boxLabel: 'Grouped Vials',
                        inputValue: 'grouped',
                        name: 'groupType',
                        checked: false
                    }],
                    listeners: {
                        buffer: 50,
                        change: function(rg, r){
                            var form = rg.up('form');
                            var panel = form.down('#searchFields');

                            var guidOp = panel.down('#guidOpField');
                            if(guidOp){
                                var guidField = panel.down('#guidField');
                                guidOp.setVisible(r.groupType != 'grouped');
                                if(r.groupType == 'grouped'){
                                    guidOp.setVisible(false);
                                    guidOp.reset();
                                    guidField.reset();
                                }
                            }
                        }
                    }
                },{
                    itemId: 'searchFields',
                    //TODO: remove once Ext4.1 bug fixed
                    width: 400 + 20,
                    defaults: {
                        labelWidth: this.LABEL_WIDTH,
                        width: 400
                    },
                    items: [{
                        border: false,
                        html: 'Loading...'
                    }],
                    buttons: [{
                        text: 'Search',
                        handler: this.onSubmit
                    }]
                },{
                    xtype: 'container',
                    layout: 'hbox',
                    style: 'padding-top: 15px;',
                    defaults: {
                        style: 'margin-right: 5px;'
                    },
                    items: [{
                        html: 'Advanced Search:',
                        //width: this.LABEL_WIDTH,
                        border: false
                    },{
                        xtype: 'labkey-linkbutton',
                        text: 'Individual Vials',
                        linkCls: 'labkey-text-link',
                        href: LABKEY.ActionURL.buildURL('study-samples', 'showSearch', null, {showAdvanced: true, showVials: true})
                    },{
                        xtype: 'labkey-linkbutton',
                        text: 'Grouped Vials',
                        linkCls: 'labkey-text-link',
                        href: LABKEY.ActionURL.buildURL('study-samples', 'showSearch', null, {showAdvanced: true, showVials: false})
                    }]
                }]
            });

            this.callParent(arguments);

            this.preloadStores();
        },

        preloadStores: function(){
            var toCreate = this.getGroupSearchItems();
            this.pendingStores = toCreate.length;
            Ext4.each(toCreate, function(args){
                var store = this.createStore.apply(this, args);
                this.mon(store, 'load', this.onStoreLoad, this);
            }, this);
        },

        onStoreLoad: function(store){
            this.pendingStores--;

            if(this.pendingStores == 0){
                var val = this.down('#searchType').getValue().groupType;
                var panel = this.down('#searchFields');
                panel.removeAll();
                if(val == 'grouped'){
                    panel.add(this.getGroupedSearchCfg());
                }
                else {
                    panel.add(this.getIndividualSearchCfg());
                }
            }
        },

        getIndividualSearchCfg: function(){
            var cfg = [{
                xtype: 'labkey-operatorcombo',
                itemId: 'guidOpField',
                jsonType: 'string',
                mvEnabled: false,
                emptyText: 'Any Global Unique ID',
                includeHasAnyValue: true,
                initialValue: null,
                fieldLabel: 'Global Unique ID',
                listeners: {
                    scope: this,
                    change: function(field, val){
                        this.down('#guidField').setVisible(val);
                    }
                }
            },{
                xtype: 'textfield',
                itemId: 'guidField',
                filterParam: 'GlobalUniqueId',
                fieldLabel: '&nbsp;',
                labelSeparator: '',
                hidden: true
            }].concat(this.getGroupedSearchCfg());

            return cfg;
        },

        getGroupedSearchCfg: function(){
            var cfg = [];
            Ext4.each(this.getGroupSearchItems(), function(item){
                cfg.push(this.getComboCfg.apply(this, item));
            }, this);

            return cfg;
        },

        getGroupSearchItems: function(){
            return [
                [studyMetadata.SubjectNounSingular, 'study', studyMetadata.SubjectNounSingular, studyMetadata.SubjectColumnName, studyMetadata.SubjectColumnName, studyMetadata.SubjectColumnName, 'Any ' + studyMetadata.SubjectNounSingular, null],
                ['Visit', 'study', 'Visit', 'Visit/SequenceNumMin', 'Label', 'SequenceNumMin', 'Any Visit', null, 'DisplayOrder,Label'],
                ['Primary Type', 'study', 'SpecimenPrimaryType', 'PrimaryType/Description', 'Description', 'Description', 'Any Primary Type', null],
                ['Derivative Type', 'study', 'SpecimenDerivative', 'DerivativeType/Description', 'Description', 'Description', 'Any Derivative Type', null],
                ['Additive Type', 'study', 'SpecimenAdditive', 'AdditiveType/Description', 'Description', 'Description', 'Any Additive Type', null]
            ]
        },

        getComboCfg: function(label, schemaName, queryName, filterParam, displayColumn, valueColumn, defaultOptionText, defaultOptionValue, sort){
            var store = this.createStore.apply(this, arguments);
            if(store.getCount() != 0 && store.getCount() >= this.MAX_COMBO_ITEMS){
                return {
                    xtype: 'textfield',
                    itemId: queryName,
                    fieldLabel: label,
                    filterParam: displayColumn,
                    emptyText: defaultOptionText,
                    value: defaultOptionValue
                }
            }
            else {
                return {
                    xtype: 'checkcombo',
                    editable: false,
                    itemId: queryName,
                    multiSelect: true,
                    fieldLabel: label,
                    filterParam: filterParam,
                    displayField: 'displayValue',
                    valueField: valueColumn,
                    emptyText: defaultOptionText,
                    value: defaultOptionValue,
                    store: store,
                    createNewOnEnter: true,
                    addAllSelector: true
                }
            }
        },

        createStore: function(label, schemaName, queryName, filterParam, displayColumn, valueColumn, defaultOptionText, defaultOptionValue, sort){
            //only create stores once
            var storeId = ['specimen-search', schemaName, queryName, displayColumn, valueColumn].join('||');
            var store = Ext4.StoreMgr.get(storeId);
            if(!store){
                var columns = displayColumn;
                if(valueColumn != displayColumn){
                    columns += ','+valueColumn;
                }

                var storeCfg = {
                    type: 'labkey-store',
                    storeId: storeId,
                    schemaName: schemaName,
                    //queryName: queryName,
                    sql: 'select distinct(' + displayColumn + ') as ' + displayColumn + (displayColumn == valueColumn ? '' : ', ' + valueColumn) + ' from ' + schemaName + '.' + queryName + ' WHERE ' + displayColumn + ' IS NOT NULL AND ' + displayColumn + ' != \'\'',
                    columns: columns,
                    sort: sort || displayColumn,
                    autoLoad: true,
                    maxRows: this.MAX_COMBO_ITEMS,
                    metadata: {
                        displayValue: {
                            createIfDoesNotExist: true,
                            setValueOnLoad: true,
                            getInitialValue: function(val, rec, meta){
                                if(displayColumn == valueColumn)
                                    return rec.get(displayColumn);
                                else {
                                    return rec.get(displayColumn) + ' (' + rec.get(valueColumn) + ')';
                                }
                            }
                        }
                    }
                };

                //special case participant
                if(LABKEY.demoMode && queryName == studyMetadata.SubjectNounSingular){
                    storeCfg.listeners = {
                        load: function(store){
                            store.each(function(rec){
                                rec.set(displayColumn, LABKEY.id(valueColumn))
                            }, this);
                        },
                        scope: this
                    };
                }

                store = Ext4.create('LABKEY.ext4.Store', storeCfg);
            }

            return store;
        },

        onSubmit: function(btn){
            var form = btn.up('form')
            var panel = form.down('#searchFields');
            var vialSearch = form.down('#searchType').getValue().groupType != 'grouped';

            var paramBase = vialSearch ? "SpecimenDetail." : "SpecimenSummary.";
            var params = {
                showVials: vialSearch
            };

            panel.items.each(function(item){
                var op, val;
                if(item.filterParam){
                    //special case GUID:
                    if(item.itemId == 'guidField'){
                        op = panel.down('#guidOpField').getValue();
                        val = item.getValue();
                    }
                    else {
                        op = 'eq';
                        val = item.getValueAsArray();
                    }

                    if(Ext4.isArray(val)){
                        if(val.length > 1)
                            op = 'in';

                        var optimized = form.optimizeFilter(op, val, item);
                        if(optimized){
                            op = optimized[0];
                            val = optimized[1];
                        }

                        val = val.join(';');
                    }

                    var filterType = LABKEY.Filter.getFilterTypeForURLSuffix(op);
                    if(!Ext4.isEmpty(val) || (filterType && !filterType.isDataValueRequired())){
                        params[paramBase + item.filterParam + '~' + op] = val;
                    }
                }
            }, this);

            window.location = LABKEY.ActionURL.buildURL('study-samples', 'samples', null, params);
        },

        optimizeFilter: function(op, values, field){
            if(field && field.store){
                if(values.length > (field.store.getCount() / 2)){
                    op = LABKEY.Filter.getFilterTypeForURLSuffix(op).getOpposite().getURLSuffix();

                    var newValues = [];
                    field.store.each(function(rec){
                        var v = rec.get(field.displayField)
                        if(values.indexOf(v) == -1){
                            newValues.push(v);
                        }
                    }, this);
                    values = newValues;
                }
            }
            values = Ext4.unique(values);
            return [op, values];
        }
    });

});
</script>
<div id="<%=renderTarget%>"></div>
