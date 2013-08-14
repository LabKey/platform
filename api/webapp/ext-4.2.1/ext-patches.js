/* Apply patches found for Ext 4.2.1 here */

// Set USE_NATIVE_JSON so Ext.decode and Ext.encode use JSON.parse and JSON.stringify instead of eval
Ext4.USE_NATIVE_JSON = true;

// set the default ajax timeout from 30's to 5 minutes
Ext4.Ajax.timeout = 5 * 60 * 1000;

// set csrf value for all requests
Ext4.Ajax.defaultHeaders = {'X-LABKEY-CSRF': LABKEY.CSRF};

/**
 * @Override
 * This is an override of the Ext4.2.1 field template, which was performed in order to support a help popup next to the field label (ie. '?')
 * Set the field's helpPopup property to use this.  This should be the default Ext4.2.1 template with 2 lines added.  This is a heavy handed way to
 * inject this.  It would have been nicer to hook into afterLabelTextTpl; however, this template's data is populated through a slightly different
 * path.  This is probably prone to breaking during Ext version upgrades; however, it is generally not difficult to fix.
 */
Ext4.override(Ext4.form.field.Base, {
    labelableRenderTpl: [
        '<tr role="presentation" id="{id}-inputRow" <tpl if="inFormLayout">id="{id}"</tpl> class="{inputRowCls}">',

        '<tpl if="labelOnLeft">',
        '<td role="presentation" id="{id}-labelCell" style="{labelCellStyle}" {labelCellAttrs}>',
        '{beforeLabelTpl}',
        '<label id="{id}-labelEl" {labelAttrTpl}<tpl if="inputId"> for="{inputId}"</tpl> class="{labelCls}"',
        '<tpl if="labelStyle"> style="{labelStyle}"</tpl>',

        ' unselectable="on"',
        '>',
        '{beforeLabelTextTpl}',
        '<tpl if="fieldLabel">{fieldLabel}{labelSeparator}</tpl>',
        //NOTE: this line added.  this is sub-optimal, but afterLabelTpl uses a different path to populate tpl values
        '<tpl if="helpPopup"><a href="#" data-qtip="{helpPopup}"><span class="labkey-help-pop-up">?</span></a></tpl>',
        '{afterLabelTextTpl}',
        '</label>',
        '{afterLabelTpl}',
        '</td>',
        '</tpl>',

        '<td role="presentation" class="{baseBodyCls} {fieldBodyCls} {extraFieldBodyCls}" id="{id}-bodyEl" colspan="{bodyColspan}" role="presentation">',
        '{beforeBodyEl}',

        '<tpl if="labelAlign==\'top\'">',
        '{beforeLabelTpl}',
        '<div role="presentation" id="{id}-labelCell" style="{labelCellStyle}">',
        '<label id="{id}-labelEl" {labelAttrTpl}<tpl if="inputId"> for="{inputId}"</tpl> class="{labelCls}"',
        '<tpl if="labelStyle"> style="{labelStyle}"</tpl>',

        ' unselectable="on"',
        '>',
        '{beforeLabelTextTpl}',
        '<tpl if="fieldLabel">{fieldLabel}{labelSeparator}</tpl>',
        '{afterLabelTextTpl}',
        '</label>',
        '</div>',
        '{afterLabelTpl}',
        '</tpl>',

        '{beforeSubTpl}',
        '{[values.$comp.getSubTplMarkup(values)]}',
        '{afterSubTpl}',

        '<tpl if="msgTarget===\'side\'">',
        '{afterBodyEl}',
        '</td>',
        '<td role="presentation" id="{id}-sideErrorCell" vAlign="{[values.labelAlign===\'top\' && !values.hideLabel ? \'bottom\' : \'middle\']}" style="{[values.autoFitErrors ? \'display:none\' : \'\']}" width="{errorIconWidth}">',
        '<div role="presentation" id="{id}-errorEl" class="{errorMsgCls}" style="display:none"></div>',
        '</td>',
        '<tpl elseif="msgTarget==\'under\'">',
        '<div role="presentation" id="{id}-errorEl" class="{errorMsgClass}" colspan="2" style="display:none"></div>',
        '{afterBodyEl}',
        '</td>',
        '</tpl>',
        '</tr>',
        {
            disableFormats: true
        }
    ],
    getLabelableRenderData: function() {
        var data = this.callOverridden();

        //support a tooltip
        data.helpPopup = this.helpPopup;

        return data;
    }
    //this would be a more surgical approach, except renderData doesnt seem to be applied right
    //afterLabelTextTpl: ['{%this.helpPopup%}<tpl if="helpPopup"><a href="#" data-qtip="{helpPopup}"><span class="labkey-help-pop-up">?</span></a></tpl>']
});

/**
 * @Override
 * This was added for the same purpose as the override of Ext4.form.field.Base directly above.  It allows the field to supply a helpPopup
 * config property, which creates a tooltip next to the field label.  The override of FieldContainer is required for nested fields
 * (like CheckboxGroup or RadioGroup) to support the same option as regular fields.
 */
Ext4.override(Ext4.form.FieldContainer, {
    labelableRenderTpl: Ext4.form.field.Base.prototype.labelableRenderTpl,
    getLabelableRenderData: Ext4.form.field.Base.prototype.getLabelableRenderData
});

/**
 * @Override
 * This is an override of the Ext 4.1 selection model for checkboxes due to how easy it was to accidentally uncheck a large set of rows.
 * The fix is to use a DOM Element higher up in the structure making the 'clickable' area for the checkbox larger.
 * The reason for this pertains to issue 15193 linked below.
 * https://www.labkey.org/issues/home/Developer/issues/details.view?issueId=15193
 */
//Ext4.override(Ext4.selection.CheckboxModel, {
//
//    onRowMouseDown: function(view, record, item, index, e) {
//        view.el.focus();
//        var me = this,
//                checker = e.getTarget('.' + Ext4.baseCSSPrefix + 'grid-cell-inner'),
//                mode;
//
//        if (!me.allowRightMouseSelection(e)) {
//            return;
//        }
//
//        // checkOnly set, but we didn't click on a checker.
//        if (me.checkOnly && !checker) {
//            return;
//        }
//
//        if (checker) {
//            mode = me.getSelectionMode();
//            // dont change the mode if its single otherwise
//            // we would get multiple selection
//            if (mode !== 'SINGLE') {
//                me.setSelectionMode('SIMPLE');
//            }
//            me.selectWithEvent(record, e);
//            me.setSelectionMode(mode);
//        } else {
//            me.selectWithEvent(record, e);
//        }
//    }
//});

/**
 * @Override
 * Sencha Issue: http://www.sencha.com/forum/showthread.php?269116-Ext-4.2.1.883-Sandbox-getRowStyleTableEl
 */
Ext4.override(Ext4.view.Table, {
    getRowStyleTableEl : function(item) {
        return this.el.down('table.' + Ext4.baseCSSPrefix  + 'grid-table');
    }
});

