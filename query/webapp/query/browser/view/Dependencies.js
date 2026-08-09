/*
 * Copyright (c) 2020-2026 LabKey Corporation
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

        this.errorTpl = new Ext4.XTemplate(
                '<div class="labkey-error">',
                '<h3>Errors during analysis</h3>',
                '<tpl for=".">',
                    '<div style="margin-top: 0.5em;">',
                        '<div>{containerPath:htmlEncode}</div>',
                        '<tpl for="messages">',
                            '<div style="margin-left: 1em;">{.:htmlEncode}</div>',
                        '</tpl>',
                    '</div>',
                '</tpl>',
                '</div>'
        );
    },

    initComponent : function() {
        this.enableBubble('dependencychanged');
        this.dependencyCache = LABKEY.query.browser.cache.QueryDependencies;

        // the query browser can be opened at the site root, where there is no current project to analyze
        this.projectPath = LABKEY.project ? LABKEY.project.path : undefined;
        this.analysisPath = this.projectPath || '/';

        const loading = '<i class="fa fa-spinner fa-pulse"></i>';

        this.items = [{
            xtype: 'box',
            cls: 'lk-cf-instructions',
            width: '75%',
            html: 'By default, the Dependency Report shown when you view a query or table in the schema browser covers ' +
                    'only the current folder. This analysis proactively loads references in other folders as well, ' +
                    'so that dependencies from elsewhere on the server are included.'
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
                itemId: 'lk-cfd-project-scope',
                fieldLabel: this.projectPath ? 'Current project, ' + Ext4.htmlEncode(this.projectPath) : 'Current project',
                boxLabel: loading,
                checked: !!this.projectPath,
                disabled: !this.projectPath,
                name: 'depth',
                scope: this,
                handler: function (cmp, checked) {
                    if (checked)
                        this.analysisPath = this.projectPath;
                }
            },{
                xtype: 'radio',
                itemId: 'lk-cfd-site-scope',
                fieldLabel: 'Site-wide',
                boxLabel: loading,
                checked: !this.projectPath,
                name: 'depth',
                scope: this,
                handler: function (cmp, checked) {
                    if (checked)
                        this.analysisPath = '/';
                }
            },{
                xtype: 'box',
                itemId: 'lk-cfd-scope-status',
                padding: '5 0 0 0',
                html: loading + ' Counting the folders each option would analyze. This can take a moment on a large site.'
            }],
            buttonAlign : 'left',
            buttons : [
                {text : 'Start Analysis', itemId: 'lk-cfd-start', disabled: true, handler : this.startAnalysis, scope : this}
            ]
        },{
            xtype: 'box',
            itemId: 'lk-cfd-error',
            padding: 10,
            hidden: true
        }];
        this.callParent();

        this.on('afterrender', this.loadScopeCounts, this);
    },

    // resolve the folder counts up front so the cost of each option is visible before an analysis is started
    loadScopeCounts : function() {
        this.dependencyCache.loadContainerCounts(function(scopes) {
            // the tab is closable, so it may be gone by the time the container tree comes back
            if (this.isDestroyed)
                return;

            this.setScopeCount('#lk-cfd-site-scope', scopes['/']);
            this.setScopeCount('#lk-cfd-project-scope', this.projectPath ? scopes[this.projectPath] : undefined);
            this.down('#lk-cfd-scope-status').hide();
            this.down('#lk-cfd-start').enable();
        }, function() {
            if (this.isDestroyed)
                return;

            this.setScopeCount('#lk-cfd-site-scope', undefined);
            this.setScopeCount('#lk-cfd-project-scope', undefined);
            this.down('#lk-cfd-scope-status').update('<span class="labkey-error">Unable to determine how many folders each option would analyze.</span>');
        }, this);
    },

    setScopeCount : function(itemId, containers) {
        let radio = this.down(itemId);
        if (radio) {
            let label = '';
            if (containers) {
                label = containers.length === 1 ? '1 folder' : containers.length.toLocaleString() + ' folders';
            }
            radio.setBoxLabel(label);
        }
    },

    startAnalysis : function() {
        // resolved by loadScopeCounts(), so the analysis doesn't walk the container tree a second time
        let containers = this.dependencyCache.getScopeContainers(this.analysisPath);

        this.analysisRunning = true;
        this.down('#lk-cfd-start').disable();
        this.hideError();

        this.progressBar = Ext4.create('Ext.ProgressBar', {width: 500});

        // modal, both to keep Stop Analysis reachable and because QueryDependencies is a singleton that a query
        // details page would otherwise start a second, competing analysis on
        this.progressWindow = Ext4.create('Ext.window.Window', {
            title: 'Analyzing Query Dependencies',
            modal: true,
            closable: false,
            draggable: false,
            bodyPadding: 10,
            items: [this.progressBar],
            buttons: [{text: 'Stop Analysis', handler: this.stopAnalysis, scope: this}]
        });
        this.progressWindow.show();

        this.progressTask = Ext4.TaskManager.start({
            interval: 250,
            delay: 1000,
            scope: this,
            run: function(){
                let info = this.dependencyCache.getProgress();
                if (this.progressBar)
                    this.progressBar.updateProgress(info.progress, info.currentContainer, true);
            }
        });

        function loadSuccessHandler(json, resp, opts) {
            this.endAnalysis();
            Ext4.Msg.alert('Cross Folder Dependencies', 'The query analysis has completed successfully',
                    function () {
                        this.fireEvent('dependencychanged');
                    },
                    this);
        }

        function loadFailureHandler(json, resp, opts) {
            this.endAnalysis();
            this.showErrors(resp, opts);
        }

        // clear the cache and re-load using the configured path
        this.dependencyCache.clear();
        this.dependencyCache.load(this.analysisPath, loadSuccessHandler, loadFailureHandler, this, containers);
    },

    stopAnalysis : function() {
        if (!this.analysisRunning)
            return;

        this.dependencyCache.cancel();
        this.endAnalysis();

        // the partial graph isn't usable, so drop it and let a later analysis rebuild it
        this.dependencyCache.clear();
        Ext4.Msg.alert('Cross Folder Dependencies', 'The query analysis was stopped.');
    },

    endAnalysis : function() {
        this.analysisRunning = false;

        if (this.progressTask) {
            Ext4.TaskManager.stop(this.progressTask);
            this.progressTask = undefined;
        }
        if (this.progressWindow) {
            let win = this.progressWindow;
            this.progressWindow = undefined;
            this.progressBar = undefined;

            // destroys the progress bar along with it
            win.close();
        }
        this.down('#lk-cfd-start').enable();
    },

    /**
     * Lists every container whose analysis failed. resp/opts describe the failure that ended the analysis and are only
     * used when it failed before any container was requested, such as when the container list itself couldn't be loaded.
     */
    showErrors : function(resp, opts) {
        let errors = this.dependencyCache.getErrors();
        if (errors.length === 0)
            errors = [{containerPath: this.analysisPath, response: resp, options: opts}];

        let byContainer = {};
        let grouped = [];
        Ext4.each(errors, function(error) {
            let messages = byContainer[error.containerPath];
            if (!messages) {
                messages = byContainer[error.containerPath] = [];
                grouped.push({containerPath: error.containerPath, messages: messages});
            }
            messages.push(this.getErrorMessage(error.response, error.options));
        }, this);

        let box = this.down('#lk-cfd-error');
        box.update(this.errorTpl.apply(grouped));
        box.show();
    },

    getErrorMessage : function(response, opts) {
        // getErrorMessageFromResponse() reads responseURL off the response unconditionally
        if (!response)
            return 'Unknown error';

        // a JSON body that carries no exception (a bare success:false, say) leaves nothing to show
        return this.getErrorMessageFromResponse(response, opts).exception || 'Unknown error';
    },

    hideError : function() {
        let box = this.down('#lk-cfd-error');
        box.update('');
        box.hide();
    },

    getErrorMessageFromResponse : function (response, opts){
        if (response && response.getResponseHeader && response.getResponseHeader('Content-Type')
                && response.getResponseHeader('Content-Type').indexOf('application/json') >= 0){
            try {
                var error = LABKEY.Utils.decode(response.responseText);
                error["url"] = response.responseURL;

                return error;
            }
            catch (error){
                //we still want to proceed even if we cannot decode the JSON
            }
        }
        return {
            exception: LABKEY.Utils.getMsgFromError(response, opts),
            url: response.responseURL
        };
    }
});