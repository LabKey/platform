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
        this.addEvents('startvalidation', 'stopvalidation');
    },

    initComponent : function() {
        this.dependencyCache = LABKEY.query.browser.cache.QueryDependencies;
        this.items = [{
            xtype: 'box',
            cls: 'lk-sb-instructions',
            html: 'This will allow an administrator to perform a query dependency analysis across folders. The user has ' +
                    'the option to analyze at the site wide level which will include all folders on the server or at the project level. ' +
                    'which will include the project and all sub folders.'
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
                {text : 'Start Analysis', handler : this.startAnalysis, scope : this},
                {text : 'Stop Analysis', handler: this.stopAnalysis, scope : this}
            ]
        }];

        this.callParent();
    },

    startAnalysis : function() {

        console.log('start analysis with container path: ' + this.analysisPath);
        this.browser.getEl().mask();
        this.dependencyCache.clear();
        this.dependencyCache.load(this.analysisPath, function(){this.browser.getEl().unmask();}, function(){}, this);

    },

    stopAnalysis : function() {
        console.log('stop analysis');
    },

    advance : function() {
        if (this.stop) {
            this.onFinish();
        }
        else {
            this.curQueryIdx++;
            if (this.curQueryIdx >= this.queries.length) {
                // move to next schema
                this.curQueryIdx = 0;
                this.curSchemaIdx++;

                if (this.curSchemaIdx >= this.schemaNames.length) { // all done
                    this.onFinish();
                }
                else {
                    this.validateSchema();
                }
            }
            else {
                this.validateQuery();
            }
        }
    },

    getStartButton : function() {
        if (!this.startButton) {
            this.startButton = Ext4.create('Ext.button.Button', {
                text: 'Start Validation',
                //cls: 'lk-sb-button',
                handler: this.initContainerList,
                listeners: {
                    afterrender: {
                        fn: function(b) {
                            this.on('startvalidation', function() { this.setDisabled(true); }, b);
                            this.on('stopvalidation', function() { this.setDisabled(false); this.focus(); }, b);
                        },
                        scope: this,
                        single: true
                    }
                },
                scope: this
            });
        }

        return this.startButton;
    },

    getStopButton : function() {
        if (!this.stopButton) {
            this.stopButton = Ext4.create('Ext.button.Button', {
                text: 'Stop Validation',
                cls: 'lk-sb-button',
                disabled: true,
                handler: this.stopValidation,
                listeners: {
                    afterrender: {
                        fn: function(b) {
                            this.on('startvalidation', function() { this.setDisabled(false); this.focus(); }, b);
                            this.on('stopvalidation', function() { this.setDisabled(true); }, b);
                        },
                        scope: this,
                        single: true
                    }
                },
                scope: this
            });
        }

        return this.stopButton;
    },

    getCurrentQueryLabel : function() {
        return Ext4.htmlEncode(this.schemaNames[this.curSchemaIdx]) + "." + Ext4.htmlEncode(this.queries[this.curQueryIdx].name);
    },

    getStatusPrefix : function() {
        return Ext4.htmlEncode(this.currentContainer) + " (" + this.currentContainerNumber + "/" + this.containerCount + ")";
    },

    initEvents : function() {
        this.callParent();
        this.ownerCt.on('beforeremove', function() {
            if (this.validating) {
                Ext4.Msg.alert('Validation in Process', 'Please stop the validation process before closing this tab.');
                return false;
            }
        }, this);
    },

    initContainerList : function() {
        var containerList = [];
        if (this.subfolderCB.checked) {
            LABKEY.Security.getContainers({
                includeSubfolders: true,
                success: function(containersInfo) {
                    this.recurseContainers(containersInfo, containerList);
                    this.startValidation(containerList);
                },
                scope: this
            });
        }
        else {
            containerList[0] = LABKEY.ActionURL.getContainer();
            this.startValidation(containerList);
        }
    },

    isValidSchemaName : function(schemaName) {
        return Ext4.isDefined(schemaName) && !Ext4.isEmpty(schemaName);
    },

    onChildSchemas : function(schemasInfo, schemaName) {
        // Add child schemas to the list
        Ext4.iterate(schemasInfo, function(childSchemaName, info) {
            var fqn = info.fullyQualifiedName;
            if (!this.isValidSchemaName(fqn)) {
                var status = 'FAILED: Unable to resolve qualified schema name: \'' + Ext4.htmlEncode(fqn) + '\' of child schema \'' + Ext4.htmlEncode(childSchemaName) + '\'';
                this.setStatusIcon('iconAjaxLoadingRed');
                this.numErrors++;
                this.addValidationError(schemaName, childSchemaName, {exception : status});
                return false;
            }
            this.schemaNames.push(fqn);
        }, this);
    },

    onFinish : function() {
        var msg = (this.stop ? "Validation stopped by user." : "Finished Validation.");
        msg += " " + this.numValid + (1 === this.numValid ? " query was valid." : " queries were valid.");
        msg += " " + this.numErrors + (1 === this.numErrors ? " query" : " queries") + " failed validation.";
        this.setStatus(msg, (this.numErrors > 0 ? 'lk-vq-status-error' : 'lk-vq-status-all-ok'));
        this.setStatusIcon(this.numErrors > 0 ? 'iconWarning' : 'iconCheck');

        if (!this.stop && this.remainingContainers && this.remainingContainers.length > 0) {
            this.startValidation(this.remainingContainers);
        }
        else {
            this.stopValidation();
        }
    },

    onQueries : function(queriesInfo) {
        this.queries = queriesInfo.queries;
        this.curQueryIdx = 0;
        if (this.queries && this.queries.length > 0) {
            this.validateQuery();
        }
        else {
            this.advance();
        }
    },

    onSchemas : function(schemasInfo) {
        this.schemaNames = schemasInfo.schemas;
        this.curSchemaIdx = 0;
        this.validateSchema();
    },

    onValidationFailure : function(errorInfo) {
        this.numErrors++;
        //add to errors list
        this.setStatus(this.getStatusPrefix() + ": Validating '" + this.getCurrentQueryLabel() + "'...FAILED: " + errorInfo.exception);
        this.setStatusIcon('iconAjaxLoadingRed');
        this.addValidationError(this.schemaNames[this.curSchemaIdx], this.queries[this.curQueryIdx].name, errorInfo);
        this.advance();
    },

    onValidQuery : function() {
        this.numValid++;
        this.setStatus(this.getStatusPrefix()  + ": Validating '" + this.getCurrentQueryLabel() + "'...OK");
        this.advance();
    },

    recurseContainers : function(containersInfo, containerArray) {
        if (LABKEY.Security.hasEffectivePermission(containersInfo.effectivePermissions, LABKEY.Security.effectivePermissions.read)) {
            containerArray[containerArray.length] = containersInfo.path;
        }
        Ext4.each(containersInfo.children, function(child) {
            this.recurseContainers(child, containerArray);
        }, this);
    },

    setStatus : function(msg, cls, resetCls) {
        var frame = this.getComponent('lk-vq-status-frame'),
                status = frame.getComponent('lk-vq-status');

        status.update(msg);

        if (true === resetCls) {
            frame.cls = 'lk-vq-status-frame';
        }

        if (cls) {
            if (this.curStatusClass) {
                frame.removeCls(this.curStatusClass);
            }
            frame.addCls(cls);
            this.curStatusClass = cls;
        }

        if (frame.hidden) {
            frame.show();
        }

        return status;
    },

    startValidation : function(containerList) {
        // Set things up the first time through:
        if (!this.currentContainer) {
            this.fireEvent('startvalidation');
            this.numErrors = 0;
            this.numValid = 0;
            this.containerCount = containerList.length;
            this.stop = false;
        }

        if (!Ext4.isEmpty(containerList)) {
            this.currentContainer = containerList[0];
            containerList.splice(0,1);
            this.currentContainerNumber = this.containerCount - containerList.length;
            this.remainingContainers = containerList;
            LABKEY.Query.getSchemas({
                successCallback: this.onSchemas,
                scope: this,
                containerPath: this.currentContainer
            });
            this.setStatus('Validating queries in ' + Ext4.htmlEncode(this.currentContainer) + '...', null, true);
            this.setStatusIcon('iconAjaxLoadingGreen');
            this.validating = true;
        }
        else {
            Ext4.Msg.alert('Error', 'No containers provided to validate.');
        }
    },

    stopValidation : function() {
        this.stop = true;
        this.currentContainer = undefined;
        this.validating = false;
        this.fireEvent('stopvalidation');
    }
});