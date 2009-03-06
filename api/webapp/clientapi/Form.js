/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @version 9.1
 * @license Copyright (c) 2009 LabKey Corporation
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

/**
 * @namespace LabKey Form Helper class.
 * This class provides several methods for working with HTML forms
 */
LABKEY.Form = function(config) {
    //if the config doesn't have a property named formElement
    //try treating it as a form element
    if(!config.formElement)
        config = {formElement: Ext.get(config)};

    if(!config.formElement)
        throw "Invalid config passed to LABKEY.Form constructor! Your config object should have a property named formElement which is the id of your form.";
    
    //save the form element
    this.formElement = Ext.get(config.formElement);
    this.warningMessage = config.warningMessage || "Your changes have not yet been saved. Choose Cancel to stay on the page and save your changes.";
    this._isDirty = false;

    //register for onchange events on all input elements
    var elems = this.formElement.dom.elements;
    for(var idx = 0; idx < elems.length; ++idx)
    {
        Ext.EventManager.on(elems[idx], "change", this.onElemChange, this);
    }

    Ext.EventManager.on(this.formElement, "submit", function(){
        this.setClean();
    }, this);

    if(false !== config.trackDirty)
    {
        Ext.EventManager.on(window, "beforeunload", function(evt){
            if(this.isDirty())
                evt.browserEvent.returnValue = this.warningMessage;
        }, this);
    }
};

LABKEY.Form.prototype.isDirty = function() {
    return this._isDirty;
};

LABKEY.Form.prototype.setClean = function() {
    this._isDirty = false;
};

LABKEY.Form.prototype.setDirty = function() {
    this._isDirty = true;
};

LABKEY.Form.prototype.onElemChange = function() {
    this._isDirty = true;
};
