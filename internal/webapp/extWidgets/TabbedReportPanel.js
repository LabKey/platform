/*
 * Copyright (c) 2010-2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.requiresExt4ClientAPI();
LABKEY.requiresScript('/extWidgets/QueryPanel.js');
LABKEY.requiresScript('/extWidgets/DetailsPanel.js');
LABKEY.requiresScript('/extWidgets/GraphPanel.js');

Ext4.namespace('LABKEY.ext');

Ext4.define('LABKEY.ext4.TabbedReportPanel', {
    extend: 'Ext.form.Panel',
    alias: 'widget.labkey-tabbedreportpanel',
    config: {
        jsReportNamespace: 'LABKEY.tabbedReports'
    },
    initComponent: function(){
        Ext4.apply(this, {
            bodyBorder: false
            ,bodyStyle: 'padding: 10px;'
            ,border: false
            ,reportTabs: {}
            ,defaults: {
                border: false
            }
            ,items: [{
                layout: 'column'
                ,defaults: {
                    border: false
                }
                ,items: [{
                    width: 500,
                    items: [{
                        xtype: 'radiogroup',
                        width: 300,
                        columns: 1,
                        itemId: 'filterType',
                        fieldLabel: 'Type of Search',
                        items: this.getFilterTypes(),
                        listeners: {
                            scope: this,
                            change: {fn: function(o, val){
                                this.processSubj();
                                if(!Ext4.isArray(val))
                                    this['render'+val.filterType]();
                            }, scope: this, buffer: 20}
                        }
                    },{
                        xtype: 'panel',
                        border: false,
                        bodyStyle: 'padding: 5px;',
                        defaults: {
                            border: false
                        },
                        itemId: 'filterPanel'
                    }]
                },{
                    width: 'auto',
                    itemId: 'idPanel',
                    defaults: {
                        border: false
                    }
                }]
            },{
                xtype: 'button'
                ,text: 'Refresh'
                ,handler: this.onSubmit
                ,forceRefresh: true
                ,itemId: 'submitBtn'
                ,type: 'submit'
                ,scope: this
                ,style:'margin-left:110px;'
            },{
                tag: 'span',
                style: 'padding: 10px'
            },{
                xtype: 'tabpanel',
                itemId: 'tabPanel',
                style: 'padding-bottom: 20px',
                bodyStyle: 'height: auto;',
                border: true //todo
            }]

        });

        this.callParent(arguments);

        this.createTabPanel();
        this.renderSingleSubject();
        this.restoreFromUrl();

        Ext4.create('Ext.util.KeyNav', Ext4.getBody(), {
            scope: this,
            enter: this.onSubmit
        });

    },
    getFilterTypes: function(){
        return [{
            xtype: 'radio',
            name: 'filterType',
            inputValue: 'SingleSubject',
            boxLabel: 'Single Subject',
            checked: true
        },{
            xtype: 'radio',
            name: 'filterType',
            inputValue: 'MultiSubject',
            boxLabel: 'Multiple Subjects'
        },{
            xtype: 'radio',
            name: 'filterType',
            inputValue: 'AllData',
            boxLabel: 'All Data'
        }];
    },
    renderAllData: function(){
        var target = this.down('#filterPanel');
        target.removeAll();
    },
    renderSingleSubject: function(){
        var target = this.down('#filterPanel');
        target.removeAll();

        target.add({
            xtype: 'panel',
            border: false,
            defaults: {
                border: false
            },
            items: [{
                xtype: 'textfield',
                fieldLabel: 'Subject(s)',
                width: 300,
                itemId: 'subjArea',
                value: (this.subjectArray && this.subjectArray.length ? this.subjectArray.join(';') : ''),
                keys: [{
                    key: Ext4.EventObject.ENTER,
                    handler: this.onSubmit,
                    scope: this
                }]
            }],
            keys: [{
                key: Ext4.EventObject.ENTER,
                handler: this.onSubmit,
                scope: this
            }]
        });

        target.doLayout();

    },
    renderMultiSubject: function(){
        var target = this.down('#filterPanel');
        target.removeAll();
        //target.add({width: 200, html: 'Enter Subject Id(s):<br><i>(Separated by commas, semicolons, space or line breaks)</i>'});

        var thePanel = target.add({
            xtype: 'form',
            layout: 'hbox'
        });

        thePanel.add({
            xtype: 'textarea',
            fieldLabel: 'Subject(s)',
            width: 250,
            height: 100,
            itemId: 'subjArea'
        });

        var subjButton = thePanel.add({
            bodyStyle:'padding-left: 10px;padding-right: 10px',
            width: 100,
            buttonAlign: 'center',
            border: false,
            defaults: {
                buttonAlign: 'center'
                ,scope: this
                ,bodyStyle:'align: center'
                ,xtype: 'button'
                ,minWidth: 85
                ,style: 'margin: 2px;'
            }
        });

        subjButton.add({
            text: '  Append -->'
            ,handler: this.processSubj
        });

        subjButton.add({
            text: '  Replace -->'
            ,handler: function(){
                this.subjectArray = [];
                this.processSubj()
            }
        });

        subjButton.add({
            text: ' Clear '
            ,handler: function(c){
                this.subjectArray = [];
                this.down('#idPanel').removeAll();
            }
        });

        target.doLayout();
    },

    restoreFromUrl: function(){
        if(document.location.hash){
            var token = document.location.hash.split('#');
            token = token[1].split('&');
            var doLoad;
            var reportName;
            for (var i=0;i<token.length;i++){
                var t = token[i].split(':');
                switch(t[0]){
                    case 'filterType':
                        this.down('#filterType').setValue({filterType: t[1]});
                        break;
                    case 'subjects':
                        if(this.down('#subjArea')){
                            this.down('#subjArea').setValue(t[1]);
                            this.processSubj();
                        }
                        break;
                    case 'showReport':
                        doLoad = true;
                        break;
                    case 'activeReport':
                        reportName = t[1];
                        break;
                }
            }

            if(doLoad && reportName){
                this.activeReport = this.down('#'+reportName);
                this.activeReport.ownerCt.setActiveTab(this.activeReport);
                this.down('tabpanel').setActiveTab(this.activeReport.ownerCt);
                this.onSubmit();
            }
        }
    },

    processSubj: function(){
        var type = this.down('#filterType').getValue().filterType;
        var subjArea = this.down('#subjArea');
        var idPanel = this.down('#idPanel');

        if(!subjArea){
            this.subjectArray = [];
            idPanel.removeAll();
            return;
        }

        //we clean up, combine, then split the subjectBox and subject inputs
        var subjectArray = subjArea.getValue();
        subjectArray = subjectArray.replace(/[\s,;]+/g, ';');
        subjectArray = subjectArray.replace(/(^;|;$)/g, '');
        subjectArray = subjectArray.toLowerCase();

        if(subjectArray)
            subjectArray = subjectArray.split(';');
        else
            subjectArray = new Array();

        if (type == 'MultiSubject' && this.subjectArray && this.subjectArray.length){
            subjectArray = subjectArray.concat(this.subjectArray);
        }

        if (subjectArray.length > 0){
            subjectArray = Ext4.unique(subjectArray);
            subjectArray.sort();
        }

        this.subjectArray = subjectArray;

        if(type == 'MultiSubject'){
            this.down('#subjArea').setValue('');
            this.makeSubjGrid();
        }
        else {
            this.down('#subjArea').setValue(subjectArray);
            this.down('#idPanel').removeAll();
        }
    },

    makeSubjGrid: function(){
        var target = this.down('#idPanel');
        target.removeAll();

        target.add({
            tag: 'div',
            html: 'Total IDs: '+this.subjectArray.length
        });

        var thePanel = target.add({
            xtype: 'panel'
            ,border: false
            ,layout: {
                type: 'table',
                columns: 4
            }
            ,defaults: {
                border: false
                ,style: 'margin: 2px;'
            }
        });
        
        Ext4.each(this.subjectArray, function(subj){
            thePanel.add({
                xtype: 'button'
                ,text: subj+' (X)'
                ,minWidth: 60
                ,subjectID: subj
                //,style: 'padding-right:0px;padding-left:0px'
                ,handler: function(button){
                    var subject = button.subjectID;

                    //we find the subjectArray
                    this.subjectArray.remove(subject);

                    //we rebuild the table
                    this.makeSubjGrid()
                }
                ,scope: this
            });
        }, this);

        target.add(thePanel);
        target.doLayout();
    },

    onSubmit: function(btn){
       if (!this.checkValid())
            return;

       if(btn)
            this.forceRefresh = btn.forceRefresh;

       if (!this.activeReport){
            var parent = this.down('tabpanel').items.first();
            this.activeReport = parent.items.first();

            this.down('#tabPanel').setActiveTab(parent);
            parent.setActiveTab(this.activeReport);
       }

       this.loadTab(this.activeReport);
    },

    //separated so subclasses can override as needed
    checkValid: function(){
       this.processSubj();
       var type = this.down('#filterType').getValue().filterType;

       switch (type){
       case 'AllData':
           break;
       default:
           if(!this.subjectArray.length){
                alert('Must Enter At Least 1 Subject Id');
                return 0;
           }
       }
       return 1;
    },

    displayReport: function(tab){
        this.addHeader(tab);

        if(tab.subjectArray.length){
            //we handle differently depending on whether we combine subjects
            if (!tab.combineSubj)
            {
                for (var i = 0; i < tab.subjectArray.length; i++)
                {
                    //first we make a new div for each subject to hold the report
                    var subject = [tab.subjectArray[i]];
                    this.renderReport(tab, subject);
                }
            }
            else
            {
                this.renderReport(tab, tab.subjectArray);
            }
        }
        else {          
            this.renderReport(tab);
        }


    },

    renderReport: function(tab, subject){
        switch (tab.rowData.get("reporttype"))
        {
            case 'query':
                this.loadQuery(tab, subject);
                break;
            case 'webpart':
                this.loadWebPart(tab, subject);
                break;
            case 'details':
                this.loadDetails(tab, subject);
                break;
            case 'report':
                this.loadReport(tab, subject);
                break;
            case 'js':
                this.loadJS(tab, subject);
                break;
            case 'lineGraph':
                this.loadLineGraph(tab, subject);
                break;
            default:
                LABKEY.Utils.onError({exception: 'Improper Report Type'});
        }
    },

    getFilterArray: function(tab, subject){
        var rowData = tab.rowData;
        var filterArray = {
            removable: [],
            nonRemovable: []
        };

        var subjectFieldName = rowData.get('subjectfieldname');
        if(tab.subjectArray && tab.subjectArray.length && subjectFieldName){
            filterArray.nonRemovable.push(LABKEY.Filter.create(subjectFieldName, subject.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF));
        }

        tab.filterArray = filterArray;
        return filterArray;
    },

    makeTitle: function(tab, subject){
        var title = [];

        if(subject && subject.length)
            title.push(subject.join("; "));

        return title.join(', ');
    },

    loadQuery: function(tab, subject, target){
        var filterArray = this.getFilterArray(tab, subject);
        var title = this.makeTitle(tab, subject);

        tab.removeAll();

        var queryConfig = {
            title: tab.rowData.get("reporttitle") + ": " + title,
            containerPath: tab.rowData.get("containerpath"),
            schemaName: tab.rowData.get("schemaname"),
            queryName: tab.rowData.get("queryname"),
            viewName: tab.rowData.get("viewname"),
            containerFilter: 'CurrentOrParentAndWorkbooks',
            allowChooseQuery: false,
            showInsertNewButton: false,
            showDeleteButton: false,
            showDetailsColumn: true,
            showUpdateColumn: false,
            showRecordSelectors: true,
            showReports: false,
            buttonBarPosition: 'both',
            timeout: 0,
            filters: filterArray.nonRemovable,
            removeableFilters: filterArray.removable,
            linkTarget: '_blank',
            //renderTo: targetId,
            failure: LABKEY.Utils.onError,
//            success: function(dataRegion, panel){
//                console.log(panel);
//            },
            scope: this
        };

        tab.add({
            xtype: 'labkey-querypanel',
            //style: 'padding-bottom: 10px;',
            autoLoadQuery: true,
            queryConfig: queryConfig
        });
    },

    loadReport: function(tab, subject, target){
        var filterArray = this.getFilterArray(tab, subject);
        filterArray = filterArray.nonRemovable.concat(filterArray.removable);

        target = target || tab.add({tag: 'span', html: 'Loading...', cls: 'loading-indicator'});
        var title = (subject ? subject.join("; ") : '');

        var queryConfig = {
            partName: 'Report',
            renderTo: target.id,
            containerPath: tab.rowData.get("containerpath"),
            partConfig: {
                title: tab.rowData.get("reporttitle") + ": " + title,
                schemaName: tab.rowData.get("schemaname"),
                reportId : tab.rowData.get("report"),
                'query.containerFilterName': 'CurrentOrParentAndWorkbooks',
                'query.queryName': tab.rowData.get("queryname"),
                'query.Id~in': subject.join(";")
            },
            filters: filterArray,
            failure: LABKEY.Utils.onError,
            scope: this
        };

        new LABKEY.WebPart(queryConfig).render();

    },

    loadJS: function(tab, subject, target){
        if(this.jsReportNamespace){
            if(typeof this.jsReportNamespace == 'string'){
                this.jsReportNamespace = eval(this.jsReportNamespace);
            }

            if(this.jsReportNamespace[tab.rowData.get('jsfunctionname')])
                this.jsReportNamespace[tab.rowData.get('jsfunctionname')].call(this, tab, subject, target);
        }
    },

    loadLineGraph: function(tab, subject, target){
        var filterArray = this.getFilterArray(tab, subject);
        filterArray = filterArray.nonRemovable.concat(filterArray.removable);

        var store = Ext4.create('LABKEY.ext4.Store', {
            schemaName: tab.rowData.get("schemaname"),
            queryName: tab.rowData.get("queryname"),
            containerFilter: 'CurrentOrParentAndWorkbooks',
            filterArray: filterArray,
            sort: (tab.rowData.get("subjectfieldname") ? tab.rowData.get("subjectfieldname") : null),
            listeners: {
                scope: this,
                load: renderGraph
            },
            failure: LABKEY.Utils.onError
        });

        if (tab.rowData.get("viewname")){
            store.viewName = tab.rowData.get("viewname")
        }

        store.load();

        function renderGraph(store){
            tab.add({
                xtype: 'labkey-graphpanel',
                store: store,
                title: (subject ? subject.join("; ") : ''),
                type: 'line',
                xField: 'Date',
                yField: 'ViralLoad'
            });
        }
    },

    loadGrid: function(tab, subject, target){
        var filterArray = this.getFilterArray(tab, subject);
        filterArray = filterArray.nonRemovable.concat(filterArray.removable);

        target = target || tab.add({tag: 'span', html: 'Loading...', cls: 'loading-indicator'});
        var title = (subject ? subject.join("; ") : '');

        var store = Ext4.create('LABKEY.ext4.Store', {
            schemaName: tab.rowData.get("schemaname"),
            queryName: tab.rowData.get("queryname"),
            filterArray: filterArray,
            sort: (tab.rowData.get("subjectfieldname") ? tab.rowData.get("subjectfieldname") : null),
            failure: LABKEY.Utils.onError
        });

        if (tab.rowData.get("viewname")){
            store.viewName = tab.rowData.get("viewname")
        }

        Ext4.create('LABKEY.ext4.GridPanel', {
            store: store
            ,title: tab.rowData.get("reporttitle") + ": " + title
            ,editable: false
        }).render(target);
    },

    loadWebPart: function(tab, subject, target){
        var filterArray = this.getFilterArray(tab, subject);
        filterArray = filterArray.nonRemovable.concat(filterArray.removable);

        target = target || tab.add({tag: 'span', html: 'Loading...', cls: 'loading-indicator'});
        var title = (subject ? subject.join("; ") : '');

        this.params = {};
        this.subject = subject;

        var WebPartRenderer = new LABKEY.WebPart({
            partName: tab.rowData.get("queryname"),
            title: tab.rowData.get("reporttitle") + ": " + title,
            renderTo: target,
            failure: LABKEY.Utils.onError,
            scope: this
        });
        WebPartRenderer.render(target);
    },

    loadDetails: function(tab, subject, target){
        var filterArray = this.getFilterArray(tab, subject);
        filterArray = filterArray.nonRemovable.concat(filterArray.removable);

        target = target || tab.add({
            xtype: 'panel'
        });
        var title = (subject ? subject.join("; ") : '');

        tab.doLayout();
        
        var config = {
            schemaName: tab.rowData.get("schemaname"),
            queryName: tab.rowData.get("queryname"),
            title: tab.rowData.get("reporttitle") + ":",
            titleField: 'Id',
            renderTo: target.body.id,
            filterArray: filterArray,
            multiToGrid: this.multiToGrid
        };

        if (tab.rowData.get("viewname")){
            config.viewName = tab.rowData.get("viewname");
        }

        Ext4.create('LABKEY.ext.DetailsPanel', config);

    },

    createTabPanel: function(){
        if(!this.reports || !this.reports.length)
            return;

        var tabPanel = this.down('#tabPanel');
        Ext4.each(this.reports, function(rec){
            var category = rec.get('category');
            //create top-level tab
            var queryString = 'panel[itemId="'+category+'"]';
            var subTab;
            if(!tabPanel.down(queryString)){
                subTab = tabPanel.add({
                    xtype: 'tabpanel',
                    itemId: category,
                    title: category
                });
            }

            subTab = subTab || tabPanel.down(queryString);
            var reportName = 'report' + rec.get('rowid');

            //create 2nd tier tab
            var lowerTab = subTab.down('#report'+reportName);
            if(!lowerTab){
                lowerTab = subTab.add({
                    //xtype: 'tab',
//                    layout: 'fit',
                    title: rec.get('reporttitle') || rec.get('queryname') || 'default',
                    itemId: reportName,
                    rowData: rec,
                    border: false,
                    bodyBorder: false,
                    subjectArray: [],
                    filterArray: {},
                    //tbar: {style: 'padding-left:10px'},
                    combineSubj: true
                });

//                if(this.activeReport==reportName){
//                    this.activeReport = lowerTab;
//                }
            }

        }, this);

        tabPanel.setActiveTab(0);
        tabPanel.getActiveTab().setActiveTab(0);

        tabPanel.items.each(function(tab){
            tab.getTabBar().on('change', function(bar, tab, card){
                this.onTabChange(card)
            }, this);
        }, this);

        //set the active tabs
//        if(this.activeReportName){
//            this.activeReport = tabPanel.down('#'+this.activeReportName);
//            tabPanel.setActiveTab(this.activeReport);
//            delete this.activeReportName;
//            if(this.doLoad){
//                this.onSubmit(this.activeReport);
//            }
//        }
//        else {
//        }

        if(this.down('#submitBtn'))
            this.down('#submitBtn').setDisabled(false);

    },
    onTabChange: function(tab){
        this.activeReport = tab;
        this.onSubmit();
    },
    loadTab: function(o){
        var filters = this.getFilters(o);
        var reload = 0;
        for (var i in filters){
            if(!o.queryFilters || filters[i]!==o.queryFilters[i])
                reload = 1;
        }
                
        //indicates tab already has up to date content
        if(reload == 0 && !this.forceRefresh){
//            console.log('no reload needed');
            return;
        }
        this.forceRefresh = null;

        o.queryFilters = filters;
        o.subjectArray = this.subjectArray;
        
        o.removeAll();

        this.displayReport(o);
        this.activeReport = o;
        o.doLayout();
    },
    getFilters: function(tab){
        var filters = {
            filterType : this.down('#filterType').getValue().filterType,
            showReport: 1,
            subjects: this.subjectArray.join(';'),
            combineSubj : tab.combineSubj,
            activeReport: tab.itemId
        };

        this.processSubj();
        var token = [];
        for (var i in filters){
            if(filters[i]){
                token.push(i+':'+filters[i]);
            }
        }
        Ext4.History.add(token.join('&'));

        return filters;
    },
    addHeader: function(tab, items){
//        var tb = tab.getTopToolbar();
//        tb.removeAll();
//
//        //cannot separate subjects if filtering by room
//        if(!this.subjArea){
//            return;
//        }
//
//        tb.add({
//                html: 'Combine Subjects:'
//            });
//        tb.add({
//            xtype: 'radiogroup',
//            ref: 'combine',
//            tab: tab,
//            style: 'padding-left:5px;padding-top:0px;padding-bottom:2px;',
////            defaults: {
////                style: 'padding-right:10px'
////                //,labelWidth: 100
////                //,labelStyle: 'padding:10px'
////            },
////            columns: 2,
////            boxMinWidth: 300,
//            width: 90,
//            listeners: {
//                scope: this,
//                change: function(o, s){
//                    var val = o.getValue();
//
//                    if(o.tab.combineSubj != val && o.tab.subjectArray.length!=1){
//                        o.tab.combineSubj = val.inputValue;
//                        this.loadTab(tab)
//                    }
//                }
//            },
//            items: [{
//                name: 'combine',
//                boxLabel: 'No',
//                inputValue: false,
////                autoWidth: true,
//                ref: 'combine',
//                checked: !tab.combineSubj
//            },{
//                name: 'combine',
//                boxLabel: 'Yes',
//                inputValue: true,
////                autoWidth: true,
//                ref: 'separate',
//                checked: tab.combineSubj
//            }]
//        },
//            '-'
//        );
//
//        if(items){
//            tb.add(items);
//        }
    }

});


