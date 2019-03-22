/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @license Copyright (c) 2009-2016 LabKey Corporation
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
(function($) {

    var onElemChange = function() {
        this._isDirty = true;
    };

    /**
     * Constructs a LABKEY.Form object. This object may be used to track the dirty state of an HTML form
     * and warn the user about unsaved changes if the user leaves the page before saving.
     * @class LABKEY.Form A utility class for tracking HTML form dirty state and warning the user about unsaved changes.
     * @constructor
     * @param {Object} config A configuration ojbect containing the following properties:
     * @param {Element} config.formElement A reference to the HTML form element you want to track.
     * @param {String} [config.warningMessage] A warning message to display if the user
     * attempts to leave the page while the form is still dirty. If not supplied, a default message is used.
     * @param {Boolean} [config.showWarningMessage] Set to false to stop this from displaying the warning message
     * when the user attempts to leave the page while the form is still dirty. If you only want to use this class
     * to track the dirty state only and not to warn on unload, set this to false.
     * @example
     &lt;form id='myform' action="..." method="POST"&gt;
     &lt;input type="text" name="Example" size="10"/&gt;
     &lt;input type="submit" value="Save"/&gt;
     &lt;button onclick="window.location.back();"&gt;Cancel&lt;/button&gt;
     &lt;/form&gt;

     &lt;script type="text/javascript"&gt;
     var _form;
     LABKEY.Utils.onReady(function(){
        //create a new LABKEY.Form to track the dirty state of the form above.
        //if the user tries to navigate away while the form is dirty, it will warn the user
        //and provide an option to stay on the page.
        _form = new LABKEY.Form({
            formElement: 'myform',
            warningMessage: 'This is a custom warning message' //omit to get standard warning message
        });
    });
     &lt;/script&gt;
     */
    LABKEY.Form = function(config) {

        if (!config || !config.formElement)
        {
            throw "Invalid config passed to LABKEY.Form constructor! Your config object should have a property named formElement which is the id of your form.";
        }

        var me = this;
        var formEl = $('#' + config.formElement);
        this.warningMessage = config.warningMessage || "Your changes have not yet been saved.";
        this._isDirty = false;

        //register for onchange events on all input elements
        formEl.find(':input').change(function() {
            onElemChange.call(me);
        });

        formEl.submit(function() { me.setClean(); });

        if (config.showWarningMessage !== false)
        {
            $(window).bind('beforeunload', function(e) {
                if (me.isDirty())
                {
                    e.returnMessage = me.warningMessage;
                    return me.warningMessage;
                }
            });
        }
    };

    /**
     * Returns true if the form is currently dirty, false otherwise.
     */
    LABKEY.Form.prototype.isDirty = function() {
        return this._isDirty;
    };

    /**
     * Sets the form's dirty state to clean
     */
    LABKEY.Form.prototype.setClean = function() {
        this._isDirty = false;
    };

    /**
     * Sets the form's dirty state to dirty
     */
    LABKEY.Form.prototype.setDirty = function() {
        this._isDirty = true;
    };

})(jQuery);
