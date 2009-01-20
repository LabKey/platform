/**
 * @fileOverview
 * @author <a href="https://www.labkey.org">LabKey Software</a> (<a href="mailto:info@labkey.com">info@labkey.com</a>)
 * @version 8.1
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

LABKEY.Assay.BatchState = {
    "new": "Batch has not been saved",
    saved : "Batch has been created and saved, but has not been validated",
    errors : "Batch has been saved, but has validation errors",
    complete : "Batch has been validated and saved"
};

LABKEY.Assay.Batch = function (assayId, batchId) {

    /**
     * Read-only. The assay id.  Provided by the server.
     */
    this.assayId = assayId;
    /**
     * Read-only. The batch id.  When creating a new batch, a new batch id
     * will be provided by the server after the first save.
     * @type {Integer}
     */
    this.batchId = batchId;

    /**
     * Mutible. When saving an exising Batch, the properties of the batch
     * will be merged by the server.  When creating a new batch, the server
     * will provide some values such as batch.id and batch.createdBy.
     * @type {LABKEY.Exp.RunGroup}
     */
    this.batch = null;

    this.addEvents(
            "beforeload",
            "load",
            "loadexception",
            "beforesave",
            "save",
            "saveexception"
    );
};

Ext.extend(LABKEY.Assay.Batch, Ext.util.Observable, {
    isLoading : function () {
        return !!this.transId;
    },

    abort : function () {
        if (this.isLoading()) {
            Ext.Ajax.abort(this.transId);
        }
    },

    load : function (callback) {
        if (!this.assayId || !this.batchId) {
            Ext.Msg.alert("Programmer Error", "You cannot load a batch without an assayId and batchId");
            return;
        }
        if (this.loaded || this.isLoading())
            return;
        if (this.fireEvent("beforeload", this, callback) !== false) {
            this.loadData(callback);
        } else {
            // if the load is cancelled, make sure we notify we are done
            if (typeof callback == "function") {
                callback(this);
            }
        }
    },

    loadData : function (callback) {
        this.transId = Ext.Ajax.request({
            url: LABKEY.ActionURL.buildURL("assay", "getAssayBatch", LABKEY.ActionURL.getContainer()),
            method: 'POST',
            success: this.handleLoadResponse,
            failure: this.handleLoadFailure,
            scope: this,
            argument: {callback: callback},
            jsonData : {
                assayId: this.assayId,
                batchId: this.batchId
            },
            headers : {
                'Content-Type' : 'application/json'
            }
        });
    },

    handleLoadResponse : function (response) {
        this.transId = false;
        var a = response.argument;
        this.processLoadResponse(response, a.callback);
        this.fireEvent("load", this, response);
    },

    processLoadResponse : function (response, callback) {
        var json = response.responseText;
        try {
            this._unpackData(json);
            if (typeof callback == "function") {
                callback(this);
            }
        }
        catch (e) {
            this.handleLoadFailure(response);
        }
    },

    _unpackData : function (json) {
        var o = eval("(" + json + ")");
        if (o)
        {
            this.assayId = o.assayId;
            this.batch = new LABKEY.Exp.RunGroup(o.batch);
            this.batchId = this.batch.id;
            this.loaded = true;
        }
    },

    handleLoadFailure : function (response) {
        this.transId = false;
        var a = response.argument;
        this.fireEvent("loadexception", this, response);
        if (typeof a.callback == "function") {
            a.callback(this);
        }
    },

    save : function (callback) {
        if (this.isLoading())
            return;
        if (this.fireEvent("beforesave", this, callback) !== false) {
            this.saveData(callback);
        } else {
            // if the load is cancelled, make sure we notify we are done
            if (typeof callback == "function") {
                callback(this);
            }
        }
    },

    saveData : function (callback) {
        this.transId = Ext.Ajax.request({
            url: LABKEY.ActionURL.buildURL("assay", "saveAssayBatch", LABKEY.ActionURL.getContainer()),
            method: 'POST',
            jsonData: {
                assayId: this.assayId,
                batch: this.batch
            },
            success: this.handleSaveResponse,
            failure: this.handleSaveFailure,
            scope: this,
            argument: {callback: callback},
            headers: {
                'Content-Type' : 'application/json'
            }
        });
    },

    handleSaveResponse : function (response) {
        this.transId = false;
        var a = response.argument;
        this.processLoadResponse(response, a.callback);
        this.fireEvent("save", this, response);
    },

    processSaveResponse : function (response, callback) {
        var json = response.responseText;
        try {
            this._unpackData(json);
            if (typeof callback == "function") {
                callback(this);
            }
        }
        catch (e) {
            this.handleSaveFailure(response);
        }
    },

    handleSaveFailure : function (response) {
        this.transId = false;
        var a = response.argument;
        this.fireEvent("saveexception", this, response);
        if (typeof a.callback == "function") {
            a.callback(this);
        }
    }

});

