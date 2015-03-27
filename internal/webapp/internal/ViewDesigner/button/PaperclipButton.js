
Ext4.define('LABKEY.internal.ViewDesigner.button.PaperclipButton', {

    extend: 'Ext.button.Button',

    alias: 'widget.paperclip-button',

    iconCls: 'labkey-paperclip',
    iconAlign: 'top',
    enableToggle: true,

    initComponent : function () {
        this.addEvents('blur');
        this.callParent();
    },

    afterRender : function () {
        this.callParent();
        this.updateToolTip();
    },

    // Called by ComponentDataView.renderItem when indexedProperty is false
    // When the record.urlParameter is true, the button is not pressed.
    setValue : function (value) {
        this.toggle(!value, true);
        this.updateToolTip();
    },

    // Called by ComponentDataView.renderItem when indexedProperty is false
    // We need to invert the value so the record.urlParameter is true when the button is not pressed.
    getValue : function () {
        return !this.pressed;
    },

    // 'blur' event needed by ComponentDataView to set the value after changing
    toggleHandler : function (btn, state) {
        this.fireEvent('blur', this);
        this.updateToolTip();
    },

    // Called by ComponentDataView.renderItem when indexedProperty is true
    setRecord : function (filterRecord, clauseIndex) {
        if (clauseIndex !== undefined)
        {
            this.record = filterRecord;
            this.clauseIndex = clauseIndex;

            var value = this.getRecordValue();
            this.setValue(value);
            this.on('toggle', function (f, pressed) {
                this.setRecordValue(!pressed);
            }, this);
        }
    },

    getRecordValue : function () {
        return this.record.get("items")[this.clauseIndex].urlParameter;
    },

    setRecordValue : function (value) {
        this.record.get("items")[this.clauseIndex].urlParameter = value;
    },

    getToolTipText : function ()
    {
        if (this.pressed) {
            return "This " + this.itemType + " will be saved with the view";
        }
        else {
            return "This " + this.itemType + " will NOT be saved as part of the view";
        }
    },

    updateToolTip : function () {
        var el = this.btnEl;
        var msg = this.getToolTipText();
        el.set({title: msg});
    }
});