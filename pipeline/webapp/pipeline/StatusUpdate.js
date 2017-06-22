/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @version 8.1
 * @license Copyright (c) 2008-2017 LabKey Corporation
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
Ext4.ns('LABKEY.pipeline');

/**
  * @description Status class for retrieving pipeline status data region.
  * @class Status class for retrieving pipeline status data region.
  * @constructor
  * @param {String} controller The controller in which the region should update itself.
  * @param {String} action The action in the specified controller in which the region should update itself.
  * @param {String} returnURL The URL to which operations (cancel, delete, etc requests) should return after they've been processed.
  */

LABKEY.pipeline.StatusUpdate = function(controller, action, returnURL)
{
    //private data
    var _controller = controller;
    var _action = action;
    var _dt = null;
    var _lastUpdate = "";
    var _iDelay = 0;
    var _delays = [15, 30, 60, 120, 240];

    var isSelectionModified = function()
    {
        // Disable refresh if the user has toggled any checkboxes
        return LABKEY.Utils.isDefined(LABKEY.DataRegions) &&
               LABKEY.Utils.isDefined(LABKEY.DataRegions["StatusFiles"]) &&
               LABKEY.DataRegions["StatusFiles"].selectionModified;
    };

    // private methods:
    var nextUpdate = function(iNext)
    {
        if (isSelectionModified())
        {
            return;
        }
        
        _iDelay = (iNext < _delays.length ? iNext : _delays.length - 1);
        var sec = _delays[_iDelay];
        setStatusFailure(_iDelay > 0, 'Waiting ' + sec + 's...');
        _dt.delay(sec * 1000);
    };

    var setStatusFailure = function(b, msg)
    {
        var el = Ext4.get('statusFailureDiv');
        if (el)
        {
            if (b)
                el.update('Failed to retrieve updated status. ' + msg);
            el.setDisplayed(b ? "" : "none");
        }
    };

    var update = function()
    {
        if (isSelectionModified())
        {
            return;
        }

        var url = LABKEY.ActionURL.buildURL(_controller, _action, null, {returnUrl: returnURL });
        if (document.location.search && document.location.search.length > 0)
        {
            // Strip off the leading ? on search
            url = url + "&" + document.location.search.substring(1);
        }

        LABKEY.Ajax.request({
            url: url,
            method: 'GET',
            success: onUpdateSuccess,
            failure: onUpdateFailure
        });
    };

    var onUpdateSuccess = function (response)
    {
        // LABKEY.disablePipelineRefresh is a secret value set through Selenium to make IE testing more reliable
        if (isSelectionModified() || LABKEY.disablePipelineRefresh)
        {
            return;
        }

        // get div to update
        var el = Ext4.get('statusRegionDiv');

        // fail if there were any problems
        if (el && response && response.responseText)
        {
            if (response.responseText.indexOf('StatusFiles') < 0)
            {
                setStatusFailure(true, 'Refresh this page.')
            }
            else
            {
                var newText = Ext4.util.Format.stripTags(Ext4.util.Format.stripScripts(response.responseText));
                if (_lastUpdate != newText)
                {
                    var dr = LABKEY.DataRegions["StatusFiles"];
                    if (dr)
                        dr.destroy();
                    el.update(response.responseText, true);
                    _lastUpdate = newText;
                }
                nextUpdate(0);
            }
        }
        else
        {
            onUpdateFailure(response);
        }
    };

    var onUpdateFailure = function(response)
    {
        // Don't show an error message if the request was canceled, as would happen if the user clicked on a link
        // while we were trying up refresh
        if (response.status != 0)
        {
            nextUpdate(_iDelay + 1);
        }
    };

    // public methods:
    /** @scope LABKEY.pipeline.StatusUpdate.prototype */
    return {
        start : function()
        {
            if (_dt == null)
                _dt = new Ext4.util.DelayedTask(update);
            nextUpdate(0);
        }
    }
};

