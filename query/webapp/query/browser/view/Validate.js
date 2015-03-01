/*
 * Copyright (c) 2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.query.browser.view.Validate', {
    extend: 'Ext.panel.Panel',

    bodyStyle: 'padding: 5px;',

    constructor : function(config) {
        this.callParent([config]);
        this.addEvents('queryclick', 'startvalidation', 'stopvalidation');
    },

    initComponent : function() {

        Ext4.apply(this, {
            schemaNames: [],
            queries: [],
            curSchemaIdx: 0,
            curQueryIdx: 0,
            stop: false
        });

        this.items = [{
            xtype: 'box',
            cls: 'lk-sb-instructions',
            html: 'This will validate that all queries in all schemas parse and execute without errors. This will not examine the data returned from the query.'
        },{
            xtype: 'panel',
            border: false,
            items: [
                this.getStartButton(),
                this.getStopButton()
            ]
        },{
            xtype: 'panel',
            layout: {type: 'hbox'},
            border: false,
            defaults: {border: false},
            items: [{
                xtype: 'checkbox',
                itemId: 'lk-vq-subfolders',
                cls: 'lk-vq-subfolders',
                listeners: {
                    afterrender: {
                        fn: function(cb) {
                            this.subfolderCB = cb;
                            this.on('startvalidation', function() { this.setDisabled(true); }, cb);
                            this.on('stopvalidation', function() { this.setDisabled(false); }, cb);
                        },
                        scope: this,
                        single: true
                    }
                }
            },{
                cls: 'lk-sb-instructions',
                html: '&nbsp;'
            },{
                xtype: 'box',
                cls: 'lk-sb-instructions',
                html: ' Validate subfolders'
            },{
                html: '&nbsp;',
                cls: 'lk-sb-instructions'
            },{
                itemId: 'lk-vq-systemqueries',
                cls: 'lk-vq-systemqueries',
                xtype: 'checkbox',
                listeners: {
                    afterrender: {
                        fn: function(cb) {
                            this.systemCB = cb;
                            this.on('startvalidation', function() { this.setDisabled(true); }, cb);
                            this.on('stopvalidation', function() { this.setDisabled(false); }, cb);
                        },
                        scope: this,
                        single: true
                    }
                }
            },{
                html: '&nbsp;',
                cls: 'lk-sb-instructions'
            },{
                xtype: 'box',
                html: ' Include system queries',
                cls: 'lk-sb-instructions'
            },{
                html: '&nbsp;',
                cls: 'lk-sb-instructions'
            },{
                // TODO: this option allows a more thorough validation of queries, metadata and custom views.  it is disabled until we cleanup more of the built-in queries
                xtype: 'checkbox',
                itemId: 'lk-vq-validatemetadata',
                cls: 'lk-vq-validatemetadata',
                listeners: {
                    afterrender: {
                        fn: function(cb) {
                            this.validateCB = cb;
                            this.on('startvalidation', function() { this.setDisabled(true); }, cb);
                            this.on('stopvalidation', function() { this.setDisabled(false); }, cb);
                        },
                        scope: this,
                        single: true
                    }
                }
            },{
                html: '&nbsp;',
                cls: 'lk-sb-instructions'
            },{
                xtype: 'box',
                html: ' Validate metadata and views',
                cls: 'lk-sb-instructions'
            }]
        },{
            xtype: 'panel',
            itemId: 'lk-vq-status-frame',
            cls: 'lk-vq-status-frame',
            hidden: true,
            bodyStyle: 'background: transparent;',
            border: false,
            items: [{
                itemId: 'lk-vq-status',
                cls: 'lk-vq-status',
                xtype: 'box'
            }]
        },{
            xtype: 'panel',
            itemId: 'lk-vq-errors',
            cls: 'lk-vq-errors-frame',
            hidden: true,
            border: false,
            listeners: {
                afterrender: {
                    fn: function(p) {
                        this.on('startvalidation', function() { this.removeAll() }, p);
                    },
                    scope: this,
                    single: true
                }
            }
        }];

        this.callParent();
    },

    addValidationError : function(schemaName, queryName, errorInfo) {
        var errors = this.getComponent('lk-vq-errors'),
                errorContainer = this.currentContainer;

        var config = {
            xtype: 'panel',
            cls: 'lk-vq-error',
            border: false,
            items: [{
                xtype: 'panel',
                cls: 'labkey-vq-error-name',
                border: false,
                items: [{
                    xtype: 'box',
                    cls: 'labkey-link lk-vq-error-name',
                    html: Ext4.htmlEncode(this.currentContainer) + ': ' + Ext4.htmlEncode(schemaName) + '.' + Ext4.htmlEncode(queryName),
                    listeners: {
                        afterrender: function(box) {
                            box.getEl().on('click', function() {
                                this.fireEvent('queryclick', schemaName, queryName, errorContainer);
                            }, this);
                        },
                        scope: this
                    }
                }]
            }]
        };

        if (errorInfo.errors) {
            var messages = [];
            var hasErrors = false;
            Ext4.each(errorInfo.errors, function(e){
                messages.push(e.msg);
            }, this);
            messages = Ext4.unique(messages);
            messages.sort();

            Ext4.each(messages, function(msg) {
                var cls = 'lk-vq-error-message';
                if (msg.match(/^INFO:/))
                    cls = 'lk-vq-info-message';
                if (msg.match(/^WARNING:/))
                    cls = 'lk-vq-warn-message';

                if (cls === 'lk-vq-error-message') {
                    hasErrors = true;
                }

                //config.children.push({
                config.items.push({
                    //tag: 'div',
                    xtype: 'box',
                    cls: cls,
                    html: Ext4.htmlEncode(msg)
                });
            }, this);

            if (!hasErrors) {
                this.numErrors--;
                this.numValid++;
            }
        }
        else {
            config.items.push({
                xtype: 'box',
                cls: 'lk-vq-error-message',
                html: Ext4.htmlEncode(errorInfo.exception)
            });
        }

        errors.add(config);

        if (errors.hidden) {
            errors.show();
        }
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
                cls: 'lk-sb-button',
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

    setStatusIcon : function(iconCls) {
        var status = this.getComponent('lk-vq-status-frame').getComponent('lk-vq-status');
        if (!status) {
            status = this.setStatus('');
        }
        if (this.curIconClass) {
            status.removeCls(this.curIconClass);
        }
        status.addCls(iconCls);
        this.curIconClass = iconCls;
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
    },

    validateQuery : function() {
        this.setStatus(this.getStatusPrefix() + ": Validating '" + this.getCurrentQueryLabel() + "'...");
        LABKEY.Query.validateQuery({
            schemaName: this.schemaNames[this.curSchemaIdx],
            queryName: this.queries[this.curQueryIdx].name,
            successCallback: this.onValidQuery,
            errorCallback: this.onValidationFailure,
            includeAllColumns: true,
            validateQueryMetadata: this.validateCB.checked,
            containerPath: this.currentContainer,
            scope: this
        });
    },

    validateSchema : function() {
        var schemaName = this.schemaNames[this.curSchemaIdx];
        if (!this.isValidSchemaName(schemaName)) {
            var status = 'FAILED: Unable to resolve invalid schema name: \'' + Ext4.htmlEncode(schemaName) + '\'';
            this.setStatusIcon('iconAjaxLoadingRed');
            this.numErrors++;
            this.addValidationError(schemaName, undefined, {exception : status});
            return;
        }
        this.setStatus(this.getStatusPrefix() + ": Validating queries in schema '" + Ext4.htmlEncode(schemaName) + "'...");
        LABKEY.Query.getQueries({
            schemaName: schemaName,
            successCallback: this.onQueries,
            includeColumns: false,
            includeUserQueries: true,
            includeSystemQueries: this.systemCB.checked,
            containerPath: this.currentContainer,
            scope: this
        });
        // Be sure to recurse into child schemas, if any
        LABKEY.Query.getSchemas({
            schemaName: schemaName,
            successCallback: function(schemasInfo) {
                this.onChildSchemas(schemasInfo, schemaName);
            },
            scope: this,
            apiVersion: 12.3,
            containerPath: this.currentContainer
        });
    }
});