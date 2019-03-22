/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2008-2016 LabKey Corporation
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * <p/>
 */

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
                animateTarget: this.getEl(),
                align: "tl-bl",
                title: "Edit " + this.columnName || "Edit"
            });
        }

        this.popup.getComponent(0).setValue(this.getValue());
        this.popup.show();

        this.hide();
        this.suspendEvents();

        this.popup.getComponent(0).getEl().focus();

    },

    onSaveChanges : function() {
        this.hidePopup();
        this.setValue(this.popup.getComponent(0).getValue());
    },

    onCancel : function() {
        this.hidePopup();
    },

    hidePopup : function() {
        this.popup.hide();
        this.show();
        this.resumeEvents();
        this.getEl().focus();
    },

    onDestroy : function() {
        if(this.popup)
            this.popup.destroy();
        LABKEY.ext.LongTextField.superclass.onDestroy.call(this);
    }
});