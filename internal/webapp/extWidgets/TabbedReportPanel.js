/*
 * Copyright (c) 2010-2011 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

LABKEY.requiresExt4ClientAPI();

Ext4.namespace('LABKEY.ext');


Ext4.define('LABKEY.ext4.TabbedReportPanel', {
    extend: 'Ext.form.Panel',
    alias: 'widget.labkey-tabbedreportpanel',
    config: {
        jsReportNamespace: 'LABKEY.tabbedReports'
    },
    initComponent: function(){
        Ext4.apply(this, {
            autoHeight: true
            ,bodyBorder: false
            ,autoScroll: true
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
                        //style: 'padding: 5px;',
                        width: 300,
                        columns: 1,
                        autoHeight: true,
                        itemId: 'filterType',
                        fieldLabel: 'Type of Search',
                        items: this.getFilterTypes()
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
                    itemId: 'idPanel'
                }]
            },{
                xtype: 'button'
                ,text: 'Refresh'
                ,handler: this.onSubmit
                ,forceRefresh: true
                ,itemId: 'submitBtn'
                ,type: 'submit'
                ,scope: this
                ,style:'margin-left:200px;'
            },{
                tag: 'span',
                style: 'padding: 10px'
            },{
                xtype: 'tabpanel',
                itemId: 'tabPanel',
                autoHeight: true,
                activeTab: 0,
                cls: 'extContainer'
            }]

        });

        this.callParent(arguments);

        this.createTabPanel();
        this.renderSingleSubject();

        this.on('beforeRender', this.restoreUrl);
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
        }];
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
        var target = this.filterPanel;
        target.removeAll();
        target.add({width: 200, html: 'Enter Subject Id(s):<br><i>(Separated by commas, semicolons, space or line breaks)</i>'});

        var thePanel = target.add({xtype: 'panel'});

        thePanel.add({
            xtype: 'textarea',
            fieldLabel: 'Subject(s)',
            width:165,
            itemId: 'subjArea'
        });

        var subjButton = target.add({
            bodyStyle:'padding-left: 16px;padding-right: 16px',
            buttonAlign: 'center',
            defaults: {buttonAlign: 'center'}
        });

        subjButton.add({
            xtype: 'button'
            ,text: '  Append -->'
            ,minWidth: 85
            ,handler: this.processSubj
            ,scope: this
            //,style:'align: center'
            ,bodyStyle:'align: center'
            ,buttonAlign: 'center'
            //,cls: 'labkey-button'
        });

        subjButton.add({
            xtype: 'button'
            ,text: '  Replace -->'
            ,minWidth: 85
            ,handler: function(){
                this.subjectArray = [];
                this.processSubj()
            }
            ,scope: this
            ,bodyStyle:'align: center'
        });

        subjButton.add({
            xtype: 'button'
            ,text: ' Clear '
            ,minWidth: 85
            ,handler: function(c){
                this.subjectArray = [];
                this.down('#idPanel').removeAll();
            }
            ,scope: this
            ,bodyStyle:'align: center'
            ,buttonAlign: 'center'
        });

        target.doLayout();
    },

    restoreUrl: function(){
        if(document.location.hash){
            var token = document.location.hash.split('#');
            token = token[1].split('&');
            for (var i=0;i<token.length;i++){
                var t = token[i].split(':');
                switch(t[0]){
                    case 'inputType':
                        Ext4.each(this.down('#inputType').items, function(c){
                            c.checked = (c.inputValue == t[1]);
                        }, this);

                        this[t[1]]();
                        break;
                    case 'subjects':
                        if(this.down('#subjArea')){
                            this.down('#subjArea').setValue(t[1]);
                            //this.processSubj();
                        }
                        break;
                    case 'showReport':
                        this.doLoad = 1;
                        break;
                    case 'activeReport':
                        this.activeReport = t[1];
                        break;
                }
            }
        }
    },

    processSubj: function(){
        var type = this.down('#filterType');
        var subjArea = this.down('#subjArea');

        this.subjectArray = [];
        this.down('#idPanel').removeAll();

        if(!subjArea)
            return;

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

            //we display the result
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
            ,layout: 'table'
            ,layoutConfig: {
                columns: 4
            }
        });
        
        for (var i = 0; i < this.subjectArray.length; i++)
        {
            thePanel.add({
                xtype: 'button'
                ,text: this.subjectArray[i]+' (X)'
                ,subjectID: this.subjectArray[i]
                ,style: 'padding-right:0px;padding-left:0px'
                ,handler: function(button){
                    var subject = button.subjectID;

                    //we find the subjectArray
                    this.subjectArray.remove(subject);

                    //we rebuild the table
                    this.makeSubjGrid()
                }
                ,scope: this
            });

        }
        target.add(thePanel);
        target.doLayout();
    },

    onSubmit: function(b){
       if (!this.checkValid())
            return;

       if(b)
            this.forceRefresh = b.forceRefresh;

        if (!this.activeReport){
           this.activeReport = this.tabPanel['General']['abstract'];
           var parent = this.activeReport.ownerCt;
           this.tabPanel.activate(parent);
           parent.activate(this.activeReport);
       }
       else {
           this.loadTab(this.activeReport);    
       }

    },

    //separated so subclasses can override as needed
    checkValid: function(){
       this.processSubj();
       var type = this.down('#filterType').getValue();

       switch (type){
       case 'renderColony':
           break;
       default:
           if(!this.subjectArray.length){
                alert('Must Enter At Least 1 Animal ID');
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
            default:
                LABKEY.Utils.onError({message: 'Improper Report Type'});
        }
    },

    getFilterArray: function(tab, subject){
        var rowData = tab.rowData;
        var filterArray = {
            removable: [],
            nonRemovable: []
        };

        if(tab.subjectArray && tab.subjectArray.length){
            filterArray.nonRemovable.push(LABKEY.Filter.create('Id', subject.join(';'), LABKEY.Filter.Types.EQUALS_ONE_OF));
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
        var targetId = Ext4.id();
        target = tab.add({
            labout: 'fit',
            defaults: {
                border: false
            },
            items: [{
                tag: 'div',
                id : targetId
            }]
        });

        var title = this.makeTitle(tab, subject);
        var queryConfig = {
            title: tab.rowData.get("reporttitle") + ": " + title,
            containerPath: tab.rowData.get("containerpath"),
            schemaName: tab.rowData.get("schemaname"),
            queryName: tab.rowData.get("queryname"),
            viewName: tab.rowData.get("viewname"),
            allowChooseQuery: false,
            allowChooseView: true,
            showInsertNewButton: false,
            showDeleteButton: false,
            showDetailsColumn: true,
            showUpdateColumn: false,
            showRecordSelectors: true,
            showReports: false,
            tab: tab,
            frame: 'portal',            
            buttonBarPosition: 'top',
            timeout: 0,
            filters: filterArray.nonRemovable,
            removeableFilters: filterArray.removable,
            linkTarget: '_blank',
            renderTo: target.id,
            success: function(dataRegion){
                var target = this.down('#tabPanel');
                var width1 = Ext4.get('dataregion_'+dataRegion.id).getSize().width;
                var width2 = Ext4.get(target.id).getSize().width;

                if(width1 > width2){
                    target.setWidth(width1+50);
                }
                else {
                    target.setWidth('100%');
                }

                target.doLayout();
            },
            failure: LABKEY.Utils.onError,
            scope: this
        };

        tab.QWP = new LABKEY.QueryWebPart(queryConfig);
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

            if(this.jsReportNamespace[tab.rowData.get('queryname')])
                this.jsReportNamespace[tab.rowData.get('queryname')].call(this, tab, subject, target);
        }
    },

    loadGrid: function(tab, subject, target){
        var filterArray = this.getFilterArray(tab, subject);
        filterArray = filterArray.nonRemovable.concat(filterArray.removable);

        target = target || tab.add({tag: 'span', html: 'Loading...', cls: 'loading-indicator'});
        var title = (subject ? subject.join("; ") : '');

        var store = new LABKEY.ext.Store({
            schemaName: tab.rowData.get("schemaname"),
            queryName: tab.rowData.get("queryname"),
            filterArray: filterArray,
            sort: 'Id'
        });

        if (tab.rowData.get("viewname")){
            store.viewName = tab.rowData.get("viewname")
        }

        new LABKEY.ext.EditorGridPanel({
            store: store
            ,title: tab.rowData.get("reporttitle") + ": " + title
            ,width: 1000
            ,autoHeight: true
            ,editable: false
            ,stripeRows: true
            ,disableSelection: true
            //,successCallback: this.endMsg
            ,failure: LABKEY.Utils.onError
            ,scope: this
        }).render(target);

    },

    loadWebPart: function(tab, subject, target){
        var filterArray = this.getFilterArray(tab, subject);
        filterArray = filterArray.nonRemovable.concat(filterArray.removable);

        target = target || tab.add({tag: 'span', html: 'Loading...', cls: 'loading-indicator'});
        var title = (subject ? subject.join("; ") : '');

        this.params = {};
        this.subject = subject;
        this.params.rowData = rowData;

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

        target = target || tab.add({tag: 'span', html: 'Loading...', cls: 'loading-indicator'});
        var title = (subject ? subject.join("; ") : '');

        tab.doLayout();
        
        var config = {
            schemaName: tab.rowData.get("schemaname"),
            queryName: tab.rowData.get("queryname"),
            title: tab.rowData.get("reporttitle") + ":",
            titleField: 'Id',
            renderTo: target.id,
            filterArray: filterArray,
            multiToGrid: this.multiToGrid
        };

        if (tab.rowData.get("viewname")){
            config.viewName = tab.rowData.get("viewname");
        }

        new LABKEY.ext4.DetailsPanel(config);

    },

    createTabPanel: function(){
        if(!this.reports || !this.reports.length)
            return;

        Ext4.Array.each(this.reports, function(rec){
            var category = rec.get('category');

            //create top-level tab
            var tabPanel = this.down('#tabPanel');
            if(!tabPanel.down('#category')){
                tabPanel.add({
                    xtype: 'tabpanel',
                    autoHeight: true,
                    itemId: category,
                    title: category,
                    enableTabScroll: true,
                    listeners: {
                        scope: this,
                        activate: function(t){
                            if(t.activeTab){
                                this.activeReport = t.activeTab;
                                this.onSubmit();
                            }
                        }
                    }
                })
            }

            var subTab = tabPanel.down('#'+category);
            var reportName = rec.get('reportname');

            //create 2nd tier tab
            var lowerTab = subTab.down('#'+reportName);
            if(!lowerTab){
                lowerTab = subTab.add({
                    xtype: 'panel',
                    autoHeight: true,
                    height: 400,
                    title: rec.get('reporttitle') || rec.get('queryname'),
                    itemId: reportName,
                    rowData: rec,
                    bodyStyle:'padding:5px',
                    border: false,
                    autoScroll: true,
                    subjectArray: [],
                    filterArray: {},
                    tbar: {style: 'padding-left:10px'},
                    combineSubj: true,
                    listeners: {
                        scope: this,
                        activate: function(t){
                            this.activeReport = t;
                            this.onSubmit();
                        },
                        click: function(t){
                            console.log('click');
                            this.activeReport = t;
                            this.onSubmit();
                        }
                    }
                });

                if(this.activeReport==reportName){
                    this.activeReport = lowerTab;
                }

                this.reportTabs[rec.get('reportname')] = lowerTab;
            }

        }, this);

        if(this.activeReport){
            this.down('#tabPanel').setActiveTab(this.activeReport.ownerCt);
            this.activeReport.suspendEvents();
            this.activeReport.ownerCt.setActiveTab(this.activeReport);
            this.activeReport.resumeEvents();
            if(this.doLoad){
                this.onSubmit(this.activeReport);
            }
        }
        else{
            this.down('#tabPanel').setActiveTab(this.down('#tabPanel').down('#General'));
        }

//        if(this.submitBtn)
//            this.submitBtn.setDisabled(false);

    },
    loadTab: function(o){
        o.combineSubj = o.combineSubj;
        
        this.setFilters(o);

        var reload = 0;
        for (var i in this.filters){
            if(!o.filters || this.filters[i]!==o.filters[i]){
                reload = 1;
            }
        }
                
        //indicates tab already has up to date content
        if(reload == 0 && !this.forceRefresh){
            console.log('no reload needed');
            return;
        }
        this.forceRefresh = null;


        o.filters = this.filters;
        o.subjectArray = this.subjectArray;
        
        o.removeAll();

        this.displayReport(o);
        this.activeReport = o;
        o.doLayout();
        
    },
    setFilters: function(tab){
        this.filters = {
            filterType : this.down('#filterType').getValue().filterType,
            showReport: 1,
            subjects: this.subjectArray.join(';'),
            combineSubj : tab.combineSubj,
            activeReport: tab.rowData.get('reportname')
        };

        this.processSubj();
        var token = [];
        for (var i in this.filters){
            if(this.filters[i]){
                token.push(i+':'+this.filters[i]);
            }
        }
        Ext.History.add(token.join('&'));
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


