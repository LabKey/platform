/* Apply patches found for Ext 4.0.7 here */

// Define an override for RowModel to fix page jumping on first click
// LabKey Issue : 12940: Data Browser - First click on a row scrolls down slightly, doesn't trigger metadata popup
// Ext issue    : http://www.sencha.com/forum/showthread.php?142291-Grid-panel-jump-to-start-of-the-panel-on-click-of-any-row-in-IE.In-mozilla-it-is-fine&p=640415
// NOTE: Still not fixed as of Ext 4.0.7
Ext4.define('Ext.selection.RowModelFixed', {
    extend : 'Ext.selection.RowModel',
    alias  : 'selection.rowmodelfixed',

    onRowMouseDown: function(view, record, item, index, e) {
        this.selectWithEvent(record, e);
    }
});

// Allows the modal mask (e.g. on windows, messages, etc) to be seen when using sandbox
// Ext issue    : http://www.sencha.com/forum/showthread.php?145608-4.0.5-Window-modal-broken-in-sandbox
// NOTE: Still not fixed as of Ext 4.0.7
Ext4.override(Ext4.ZIndexManager, {
    _showModalMask: function(comp) {
        var zIndex = comp.el.getStyle('zIndex') - 4,
            maskTarget = comp.floatParent ? comp.floatParent.getTargetEl() : Ext4.get(comp.getEl().dom.parentNode),
            parentBox = maskTarget.getBox();

        if (Ext4.isSandboxed) {
            if (comp.isXType('loadmask')) {
                zIndex = zIndex + 3;
            }

            if (
                maskTarget.hasCls(Ext4.baseCSSPrefix + 'reset') &&
                maskTarget.is('div')
            ) {
                maskTargetParentTemp = Ext4.get(maskTarget.dom.parentNode)
                if (maskTargetParentTemp.is('body')) {
                    maskTarget = maskTargetParentTemp;
                    parentBox = maskTarget.getBox();
                }
            }
        }

        if (!this.mask) {
            this.mask = Ext4.getBody().createChild({
                cls: Ext4.baseCSSPrefix + 'mask'
            });
            this.mask.setVisibilityMode(Ext4.Element.DISPLAY);
            this.mask.on('click', this._onMaskClick, this);
        }

        if (maskTarget.dom === document.body) {
            parentBox.height = document.body.scrollHeight || maskTarget.getHeight();
        }

        maskTarget.addCls(Ext4.baseCSSPrefix + 'body-masked');
        this.mask.setBox(parentBox);
        this.mask.setStyle('zIndex', zIndex);
        this.mask.show();
    }
});