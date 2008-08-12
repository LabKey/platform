
Ext.namespace('LABKEY', 'LABKEY.ext');

LABKEY.ext.LongTextField = Ext.extend(Ext.form.TriggerField, {

    triggerClass : 'labkey-trigger-elipsis',
    
    initComponent : function() {
        LABKEY.ext.LongTextField.superclass.initComponent.call(this);
    },
    
    onTriggerClick : function() {
        //create the window if necessary
        if(!this.popup)
        {
            this.popup = new Ext.Window({
                closeAction: 'hide',
                height: this.height || 400,
                width: this.width || 300,
                layout: 'fit',
                items: [new Ext.form.TextArea()],
                buttons: [
                    {
                        text: 'Save Changes',
                        handler: this.onSaveChanges,
                        scope: this
                    },
                    {
                        text: 'Cancel',
                        handler: this.onCancel,
                        scope: this
                    }
                ],
                closable: true,
                modal: true,
                animateTarget: this,
                title: "Edit " + this.columnName || "Edit"
            });
        }

        this.suspendEvents();
        this.popup.getComponent(0).setValue(this.getValue());
        this.popup.show();
        this.popup.getComponent(0).getEl().focus();
    },

    onSaveChanges : function() {
        this.setValue(this.popup.getComponent(0).getValue());
        this.hidePopup();
    },

    onCancel : function() {
        this.hidePopup();
    },

    hidePopup : function() {
        this.popup.hide();
        this.resumeEvents();
        this.getEl().focus();
    },

    onDestroy : function() {
        if(this.popup)
            this.popup.destroy();
        LABKEY.ext.LongTextField.superclass.onDestroy.call(this);
    }
});