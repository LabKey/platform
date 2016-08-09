
Ext4.define('LABKEY.vis.ChartWizardWindow', {
    extend: 'Ext.window.Window',
    cls: 'chart-wizard-dialog',
    header: false,
    resizable: false,
    closeAction: 'hide',
    panelToMask: null,
    listeners: {
        show: function()
        {
            if (this.panelToMask)
                this.panelToMask.getEl().mask();
        },
        hide: function()
        {
            if (this.panelToMask)
                this.panelToMask.getEl().unmask();
        }
    }
});

Ext4.define('LABKEY.vis.ChartWizardPanel', {
    extend: 'Ext.panel.Panel',
    cls: 'chart-wizard-panel',
    layout: 'border',
    border: false,
    mainTitle: '',
    bottomButtons: [],
    getTitlePanel : function()
    {
        if (!this.titlePanel)
        {
            this.titlePanel = Ext4.create('Ext.panel.Panel', {
                region: 'north',
                cls: 'region-panel title-panel',
                hidden: this.mainTitle == null,
                border: false,
                html: this.mainTitle
            });
        }

        return this.titlePanel;
    },

    setMainTitle : function(title)
    {
        this.mainTitle = title;
        this.getTitlePanel().update(this.mainTitle);
    },

    getButtonBar : function()
    {
        if (!this.buttonBar)
        {
            this.buttonBar = Ext4.create('Ext.toolbar.Toolbar', {
                region: 'south',
                cls: 'region-panel button-bar',
                border: false,
                ui: 'footer',
                defaults: {width: 65},
                items: this.bottomButtons
            });
        }

        return this.buttonBar;
    }
});
