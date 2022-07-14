Ext4.define('LABKEY.ext4.JupyterReportOptions', {

    getJupyterReportOptions : function(items, isScriptEditor) {
        if (this.reportConfig.jupyterOptions) {
            items.push({
                xtype: 'fieldset',
                title: 'Jupyter Report Options',
                collapsible: true,
                collapsed: false,
                padding: 3,
                hidden: this.readOnly || !isScriptEditor,
                items: [{
                    xtype: 'container',
                    layout: 'hbox',
                    defaults: {
                        margin : '0 0 8 0'
                    },
                    items: [{
                        xtype: 'filefield',
                        id: 'import-btn',
                        buttonOnly: true,
                        buttonText: 'Import',
                        clearOnSubmit: false, // allows form to be resubmitted in case of file overwrite
                        submitValue: false,
                        listeners: {
                            change: function (cmp) {
                                var file = cmp.fileInputEl.dom.files[0];
                                if (file)
                                    this.importFileIntoEditor(file);
                            },
                            scope: this
                        }
                    }, {
                        xtype: 'label',
                        margin: 5,
                        cls: 'x4-form-cb-label',
                        html: 'Import a Jupyter Report&nbsp;<span data-qtip="Load a .ipynb file into the editor."><i class="fa fa-question-circle-o"></i></span>'
                    }]
                }, {
                    xtype: 'container',
                    layout: 'hbox',
                    items: [{
                        xtype: 'button',
                        text: 'Export',
                        handler: function () {
                            this.exportFileFromEditor();
                        },
                        scope: this
                    }, {
                        xtype: 'label',
                        margin: 5,
                        cls: 'x4-form-cb-label',
                        html: 'Export a Jupyter Report&nbsp;<span data-qtip="Download the report from this editor."><i class="fa fa-question-circle-o"></i></span>'
                    }]
                }]
            });
        }
    },

    importFileIntoEditor : function(file) {
        var reader = new FileReader();
        reader.addEventListener('load', (event) => {
            this.codeMirror.setValue(reader.result);
        });
        reader.readAsText(file);
    },

    exportFileFromEditor : function() {
        var element = document.createElement('a');
        var text = this.codeMirror.getValue();
        var filename = this.reportConfig.reportName ? this.reportConfig.reportName : 'untitled';

        element.setAttribute('href', 'data:text/plain;charset=utf-8,' + encodeURIComponent(text));
        element.setAttribute('download', filename + '.ipynb');

        element.style.display = 'none';
        document.body.appendChild(element);
        element.click();

        // clean up the dom
        document.body.removeChild(element);
    },

    onNewReport : function() {
        if (this.reportConfig.jupyterOptions) {
            var win = new Ext4.Window({
                title: 'Jupyter Report',
                border: false,
                width : 400,
                closeAction:'destroy',
                modal: true,
                items: [{
                    xtype : 'label',
                    margin: 8,
                    html : 'Would you like to initialize this Jupyter Report (ipynb) from a file (recommended), or start with a blank report?'
                }],
                resizable: false,
                buttons: [{
                    xtype: 'filefield',
                    buttonOnly: true,
                    buttonText: 'Import from File',
                    listeners: {
                        change: function (cmp) {
                            var file = cmp.fileInputEl.dom.files[0];
                            if (file)
                                this.importFileIntoEditor(file);

                            new Ext4.util.DelayedTask(function(){
                                win.close();
                            }).delay(100);
                        },
                        scope: this
                    }
                },{
                    text: 'Start with blank Report',
                    handler: function(){win.close()}
                }]
            });

            win.show();
        }
    }
});