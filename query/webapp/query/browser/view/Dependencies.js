/*
 * Copyright (c) 2020 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.query.browser.view.Dependencies', {
    extend: 'Ext.panel.Panel',

    bodyStyle: 'padding: 5px;',

    constructor : function(config) {
        this.analysisPath = '/';
        this.callParent([config]);
        this.addEvents('dependencychanged');
    },

    initComponent : function() {
        this.enableBubble('dependencychanged');
        this.dependencyCache = LABKEY.query.browser.cache.QueryDependencies;
        this.items = [{
            xtype: 'box',
            cls: 'lk-cf-instructions',
            width: '75%',
            html: 'This will allow an administrator to perform a query dependency analysis across folders. The user has ' +
                    'the option to analyze at the site wide level which will include all folders on the server or at the project level. ' +
                    'which will include the current project and all sub folders.'
        },{
            xtype: 'form',
            border: false,
            padding: '5',
            defaults: {
                border: false,
                labelWidth: 200
            },
            items: [{
                xtype: 'radio',
                fieldLabel: 'Site Level',
                checked: true,
                name: 'depth',
                scope: this,
                handler: function (cmp, checked) {
                    if (checked)
                        this.analysisPath = '/';
                }
            },{
                xtype: 'radio',
                fieldLabel: 'Current Project Level',
                name: 'depth',
                scope: this,
                handler: function (cmp, checked) {
                    if (checked)
                        this.analysisPath = LABKEY.project.path;
                }
            }],
            buttonAlign : 'left',
            buttons : [
                {text : 'Start Analysis', handler : this.startAnalysis, scope : this}
            ]
        },{
            xtype: 'box',
            padding: 10,
            id: 'lk-dependency-progress-bar'
        }];
        this.callParent();
    },

    startAnalysis : function() {
        // display a progress bar (even if it renders under the mask)
        let pb = Ext4.create('Ext.ProgressBar', {
            renderTo: 'lk-dependency-progress-bar',
            width: 500
        });
        Ext4.TaskManager.start({
            interval: 250,
            delay: 1000,
            scope: this,
            run: function(){
                let info = this.dependencyCache.getProgress();
                pb.updateProgress(info.progress, info.currentContainer, true);
            }
        });

        function loadSuccessHandler() {
            pb.destroy();
            Ext4.TaskManager.stopAll();
            this.parent.getEl().unmask();
            Ext4.Msg.alert('Cross Folder Dependencies', 'The query analysis has completed successfully',
                    function () {
                        this.fireEvent('dependencychanged');
                    },
                    this);
        }

        // clear the cache and re-load using the configured path
        this.parent.getEl().mask();
        this.dependencyCache.clear();
        this.dependencyCache.load(this.analysisPath, loadSuccessHandler, this.onLoadError, this);
    },

    onLoadError : function() {
        Ext4.Msg.alert('Cross Folder Dependencies', 'The query analysis failed, check the console log');
    }
});