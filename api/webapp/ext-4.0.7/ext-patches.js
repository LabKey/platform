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