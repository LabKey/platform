/*
 * Copyright (c) 2009-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */


var _requestWin;
var _addToRequestButton;
var _vialGrid;
var _detailsPanel;
var _requestSelector;
var _requestStore;

var ID_TYPE = {
    RowId : "RowId",
    GlobalUniqueId : "GlobalUniqueId",
    SpecimenHash : "SpecimenHash"
};

function showRequestWindow(specOrVialIdArray, vialIdType)
{
    if(!_requestWin)
    {
        _addToRequestButton = new Ext.Button({
            text: "Add Vial to Request",
            handler: addToCurrentRequest,
            id: 'add-to-request-button'
        });

        _requestWin = new Ext.Window({
            contentEl: "specimen-request-div",
            title: "Request Vial",
            width: 700,
            height: 565,
            modal: true,
            resizable: false,
            closeAction: 'hide',
            listeners: {
                show : initRequestList
            },
            buttons: [
                _addToRequestButton,
                {text: "Remove Checked Vials", handler: removeSelectedVial},
                {text: "Manage Request", menu : {
                    cls: 'extContainer',
                    items: [
                            {text: "Request Details", handler: showRequestDetails},
                            {text: "Submit Request", handler: submitRequest},
                            {text: "Cancel Request", handler: cancelRequest}
                    ]
                }},
                {text: "Close", handler: cancelAddSpecimen}
            ]
        });

        _requestWin.on("show", function() {
            _requestWin.center();
        });
    }

    if (specOrVialIdArray && specOrVialIdArray.length > 0)
    {
        if (vialIdType == "GlobalUniqueId")
            _addToRequestButton.setText("Add " + specOrVialIdArray[0] + " to Request");
        else
            _addToRequestButton.setText("Add " + specOrVialIdArray.length +
                   " Vial" + (specOrVialIdArray.length > 1 ? "s" : "") + " to Request");
        _addToRequestButton.show();
    }
    else
        _addToRequestButton.hide();

    _requestWin.specOrVialIdArray = specOrVialIdArray;
    _requestWin.vialIdType = vialIdType;
    _requestWin.show();
}

function getSelectedRequestId()
{
    var requestId = _requestWin.selectedRequestId;
    if (!requestId)
        requestId = LABKEY.Utils.getCookie("selectedRequest");
    return requestId;
}

function setSelectedRequestId(requestId)
{
    _requestWin.selectedRequestId = requestId;
    if (requestId)
    {
        LABKEY.Utils.setCookie("selectedRequest", requestId, true);
    }
    else
    {
        LABKEY.Utils.deleteCookie("selectedRequest", true);
    }
}

function submitRequest()
{
    Ext.Msg.confirm("Submit request?", "Once a request is submitted, its specimen list may no longer be modified.  Continue?",
        function(button)
        {
            if (button == 'yes')
            {
                document.location = LABKEY.ActionURL.buildURL("study-samples", "submitRequest",
                        LABKEY.ActionURL.getContainer(), { id: getSelectedRequestId()});
            }
        });
}

function cancelSuccessful()
{
    initRequestList();
    Ext.Msg.hide();
}

function cancelRequest()
{
    Ext.Msg.confirm("Cancel request?", "Canceling will permanently delete this pending request.  Continue?",
        function(button)
        {
            if (button == 'yes')
            {
                Ext.Msg.wait("Canceling request...");
                LABKEY.Specimen.cancelRequest(cancelSuccessful, getSelectedRequestId());
            }
        });
}

function showRequestDetails()
{
    document.location = LABKEY.ActionURL.buildURL("study-samples", "manageRequest",
            LABKEY.ActionURL.getContainer(), { id: getSelectedRequestId()});
}

function requestAPISelection(request)
{
    if (!request)
    {
        Ext.Msg.alert("Request Not Found","This study does not contain a request ID " + _requestWin.manualSelection + ".");
        _requestWin.manualSelection = undefined;
        return;
    }
    var requestRecord = requestToRequestRecord(request);
    requestSelected(requestRecord);
}

function requestComboSelection(combo, record, index)
{
    requestSelected(record);
}

function requestSelected(requestRecord)
{
    var requestData = requestRecord.data;
    setSelectedRequestId(requestData.requestId);

    if (!_detailsPanel)
    {
        var createdLabel = new Ext.form.Label({ html: Ext.util.Format.date(new Date(requestData.created), "Y-m-d H:i:s") });
        var createdByLabel = new Ext.form.Label({ html: requestData.createdBy });
        var destinationLabel = new Ext.form.Label({ html: requestData.destination });
        var statusLabel = new Ext.form.Label({ html: requestData.status });
        _detailsPanel = new Ext.Panel({
            title: "Request " + requestData.requestId,
            layout:'table',
            width: 680,
            autoHeight: true,
            renderTo: 'sample-request-details',
            defaults: {
                // applied to each contained panel
                bodyStyle:'padding:5px;border:0px'
            },
            layoutConfig: {
                // The total column count must be specified here
                columns: 2
            },
            items: [
                { html: 'Created:' }, createdLabel,
                { html: 'Created By:' }, createdByLabel,
                { html: 'Destination:' }, destinationLabel,
                { html: 'Request status:' }, statusLabel
            ],
            id: 'request-details-panel'
        });
        _detailsPanel.createdLabel = createdLabel;
        _detailsPanel.createdByLabel = createdByLabel;
        _detailsPanel.destinationLabel = destinationLabel;
        _detailsPanel.statusLabel = statusLabel;
    }
    else
    {
        _detailsPanel.setTitle('Request ' + requestData.requestId);
        _detailsPanel.createdLabel.setText(Ext.util.Format.date(new Date(requestData.created), "Y-m-d H:i:s"));
        _detailsPanel.createdByLabel.setText(requestData.createdBy);
        _detailsPanel.destinationLabel.setText(requestData.destination);
        _detailsPanel.statusLabel.setText(requestData.status);
    }

    var displayColumns = "GlobalUniqueId,ParticipantId,Visit,PrimaryType,AdditiveType,DerivativeType";

    var vialStore = new LABKEY.ext.Store({
        schemaName: 'study',
        queryName: 'SpecimenDetail',
        filterArray : [
            LABKEY.Filter.create('RowId', requestData.vialRowIds, LABKEY.Filter.Types.IN)
        ]
    });

    if (!_vialGrid)
    {
        _vialGrid = new LABKEY.ext.EditorGridPanel({
            store: vialStore,
            viewConfig: {
                forceFit: true,
                autoFill: true
            },
            width: 680,
            height: 330,
            autoHeight: false,
            title: 'Vials Currently in Request',
            editable: false,
            enableFilters: false,
            renderTo: 'request-vial-details',
            pageSize: 1000,
            bbar: [],
            tbar: []
        });

        _vialGrid.on('columnmodelcustomize', function(colModel)
        {
            // hide any columns we didn't explicitly ask for; these can be included
            // because fo sorting, filtering, etc.
            var displayColArray = displayColumns.split(',');
            var displayColObj = {};
            for (var displayColIndex = 0; displayColIndex < displayColArray.length; displayColIndex++)
                displayColObj[displayColArray[displayColIndex]] = true;
            for (var colIndex in colModel)
            {
                var col = colModel[colIndex];
                if (col.dataIndex && !displayColObj[col.dataIndex])
                    col.hidden = 'true';
            }
        });
    }
    else
    {
        _vialGrid.reconfigure(vialStore, _vialGrid.getColumnModel());
        vialStore.reload();
    }
}

function removeSelectedVialSuccessful(updatedRequest)
{
    var vialRowIds = getVialRowIds(updatedRequest.vials);
    var filter;
    if (vialRowIds && vialRowIds.length > 0)
    {
        filter = LABKEY.Filter.create("RowId", vialRowIds, LABKEY.Filter.Types.IN);
    }
    else
    {
        filter = LABKEY.Filter.create("RowId", null, LABKEY.Filter.Types.ISBLANK);
    }
    _vialGrid.getStore().filterArray = [filter];
    _vialGrid.getStore().reload();
    Ext.Msg.hide();
}

function removeSelectedVial()
{
    Ext.Msg.wait("Removing vials...");
    var selections = _vialGrid.getSelectionModel().getSelections();
    var vialIds = [];
    for (var recordIndex = 0; recordIndex < selections.length; recordIndex++)
    {
        var record = selections[recordIndex];
        var recordData = record.data;
        vialIds[vialIds.length] = recordData.GlobalUniqueId;
    }
    LABKEY.Specimen.removeVialsFromRequest(removeSelectedVialSuccessful, getSelectedRequestId(), vialIds, "GlobalUniqueId");
}

function failedAddCallback(exceptionObj, responseObj)
{
    Ext.Msg.hide();
    LABKEY.Utils.displayAjaxErrorResponse(responseObj, exceptionObj);
    cancelAddSpecimen();
}

function markVialRequested(span)
{
    span.innerHTML = "<a href=\"#\" onclick=\"showRequestWindow(undefined, _requestWin.vialIdType); return false;\">" +
                     "<img src=\"" + LABKEY.ActionURL.getContextPath() + "/_images/cart_added.png\"></a>";
}

function succesfulAddCallback(request)
{
    // update spans to show that the vial has been requested.
    var idx, span, check;
    var dataRegionName = _requestWin.vialIdType == "SpecimenHash" ? "SpecimenSummary" : "SpecimenDetail";
    LABKEY.DataRegions[dataRegionName].selectNone();

    var idMap = {};
    for (idx = 0; idx < _requestWin.specOrVialIdArray.length; idx++)
        idMap[_requestWin.specOrVialIdArray[idx]] = true;

    // for each vial currently in the request, see if it was added in this last addition:
    for (idx = 0; idx < request.vials.length; idx++)
    {
        var vial = request.vials[idx];
        var id;
        if (_requestWin.vialIdType == "RowId")
            id = vial.rowId;
        else if (_requestWin.vialIdType == "GlobalUniqueId")
            id = vial.globalUniqueId;
        else if (_requestWin.vialIdType == "SpecimenHash")
            id = vial.specimenHash;

        // if this vial was added in this request, update status and checkbox:
        if (idMap[id])
        {
            var inputId = (_requestWin.vialIdType == "SpecimenHash" ? vial.specimenHash : vial.globalUniqueId);
            span = document.getElementById(inputId);
            if (span)
                markVialRequested(span);
            check = document.getElementById("check_" + inputId);
            if (check)
                check.disabled = true;
        }
    }
    Ext.Msg.alert("Success", "Vial(s) successfully added to request " + request.requestId);
    cancelAddSpecimen();
}

function addToCurrentRequest()
{
    var specOrVialIdArray = _requestWin.specOrVialIdArray;
    var requestId = getSelectedRequestId();
    var vialIdType = _requestWin.vialIdType;
    if (!specOrVialIdArray || !specOrVialIdArray.length > 0 || !requestId)
    {
        alert("Error: request and/or vial have not been selected.");
        return;
    }
    Ext.Msg.wait("Adding vial(s)...");
    if (vialIdType != "SpecimenHash")
        LABKEY.Specimen.addVialsToRequest(succesfulAddCallback, requestId, specOrVialIdArray, vialIdType, failedAddCallback);
    else
        LABKEY.Specimen.getProvidingLocations(verifyProvidingLocations, specOrVialIdArray, failedAddCallback);
}

function verifyProvidingLocations(locations)
{
    var requestId = getSelectedRequestId();
    var specOrVialIdArray = _requestWin.specOrVialIdArray;
    if (locations == null || locations.length <= 1)
        LABKEY.Specimen.addSamplesToRequest(succesfulAddCallback, requestId, specOrVialIdArray, undefined, failedAddCallback);
    else
    {
        var locationArray = [];
        for (var i = 0; i < locations.length; i++)
            locationArray[i] = [locations[i].rowId, locations[i].label];
        var locationSelect = new Ext.form.ComboBox({
            width: 300,
            store: locationArray,
            editable: false,
            blankText: 'Select a location...',
            selectOnFocus: true,
            triggerAction: 'all',
            disableKeyFilter: true,
            border: false
        });

        var selectPanel  = new Ext.Panel({
            style: { padding:'10px', backgroundColor: 'FFFFFF' },
            border: false,
            bodyBorder: false,
            items: [
                {
                    html: "Vials of this specimen are available from multiple providing locations.  Please select your preferred providing location:<p>",
                    border: false
                },
                locationSelect
            ]
        });

        Ext.Msg.hide();
        var selectLocationWin = new Ext.Window({
            items: selectPanel,
            title: "Multiple Providing Locations",
            width: 400,
            autoHeight: true,
            modal: true,
            resizable: false,
            bbar: [
               {
                   text: "Select",
                   handler: function() {
                       if (!locationSelect.getValue())
                       {
                           Ext.Msg.alert("Location Required", "Please select a providing location.");
                           return;
                       }
                       Ext.Msg.wait("Adding vial(s)...");
                       LABKEY.Specimen.addSamplesToRequest(succesfulAddCallback, requestId,
                               specOrVialIdArray, locationSelect.getValue(), failedAddCallback);
                       selectLocationWin.close();
                   }
               },
                {
                   text: "Cancel",
                   handler: function() {
                       selectLocationWin.close();
                   }
               }
            ]});
        selectLocationWin.show();
    }
}


var REQUEST_RECORD_CONSTRUCTOR = Ext.data.Record.create([
    {name: 'comments'},
    {name: 'created'},
    {name: 'createdBy'},
    {name: 'destination'},
    {name: 'requestId'},
    {name: 'status'}
]);

function getVialRowIds(vialArray)
{
    var ids = "";
    for (var i = 0; i < vialArray.length; i ++)
    {
        if (i > 0)
            ids += ";";
        ids += vialArray[i].rowId;
    }
    return ids;
}

function requestToRequestRecord(request)
{
    return new REQUEST_RECORD_CONSTRUCTOR({
        comments: request.comments,
        created: request.created,
        createdBy: request.createdBy.displayName,
        destination: request.destination.label,
        requestId: request.requestId,
        status: request.status,
        vialRowIds: getVialRowIds(request.vials)
    }, request.requestId);
}

function populateRequestList(requests)
{
    if (!requests || requests.length == 0)
    {
        Ext.Msg.confirm("Create new request?", "You have no open requests.  Would you like to create a new request that will include this vial?",
            function(button)
            {
                if (button == 'yes')
                    createRequest();
                else if (button == 'no')
                    cancelAddSpecimen();
            });
        return;
    }

    var requestRecords = [];
    for (var requestIndex = 0; requestIndex < requests.length; requestIndex++)
    {
        var request = requests[requestIndex];
        requestRecords[requestRecords.length] = requestToRequestRecord(request);
    }

    if (_requestSelector)
    {
        _requestStore.removeAll();
        _requestStore.add(requestRecords);
    }
    else
    {
        _requestStore = new Ext.data.Store();
        _requestStore.add(requestRecords);
        _requestSelector = new Ext.form.ComboBox({
            store: _requestStore,
            editable: true,
            width: 100,
       //     fieldLabel: 'Open Requests:',
            loadingText: 'Loading...',
            displayField: 'requestId',
            mode: 'local',
            allowBlank: false,
            typeAhead: true,
            forceSelection: false,
            selectOnFocus: true,
            triggerAction: 'all',
            renderTo: 'sample-request-list',
            listeners: {
                select: this.requestComboSelection,
                specialkey: function(field, event)
                {
                    if (event.getKey() == Ext.EventObject.ENTER)
                    {
                        var value = field.getValue();
                        var reg_isinteger = new RegExp('[0-9]+');
                        if( reg_isinteger.test(value))
                        {
                            if (_requestStore.getById(value))
                            {
                                // the user has typed an ID that was already in the list:
                                _requestSelector.setValue(value);
                                _requestSelector.fireEvent('select', _requestSelector, _requestStore.getById(value));
                            }
                            else
                            {
                                _requestWin.manualSelection = value;
                                LABKEY.Specimen.getRequest(requestAPISelection, value);
                            }
                        }
                        else
                            Ext.Msg.alert("Integer ID Required", "Request IDs are integer values.");
                    }
                }
            }
        });
    }

    // since our set of available requests may have changed, we need to make sure
    // that our previously selected request (if any) is still active:
    var selectedRequest = getSelectedRequestId();
    if (selectedRequest)
    {
        var found = false;
        for (var i = 0; i < requests.length && !found; i++)
            found = (requests[i].requestId == selectedRequest);
        if (!found && selectedRequest != _requestWin.manualSelection)
        {
            setSelectedRequestId(undefined);
            selectedRequest = undefined;
        }
    }

    var defaultValue = selectedRequest ? selectedRequest :  requests[0].requestId;
    if (defaultValue == _requestWin.manualSelection)
    {
        // the user was last using a request that is not part of the default list.  We'll need to select that record now,
        // since it isn't part of our current resultset.
        LABKEY.Specimen.getRequest(requestAPISelection, _requestWin.manualSelection);
    }
    else
    {
        _requestSelector.setValue(defaultValue);
        _requestSelector.fireEvent('select', _requestSelector, _requestStore.getById(defaultValue));
    }
}

function createRequest()
{
    // delete any stored request.  when we return to this page, we want to see the newly created request,
    // which will be at the top of the list:
    setSelectedRequestId(undefined);
    if (_requestWin.specOrVialIdArray)
        document.location = CREATE_REQUEST_BASE_LINK + '&sampleIds=' + _requestWin.specOrVialIdArray.toString();
    else
        document.location = CREATE_REQUEST_BASE_LINK;
}

function initRequestList()
{
    LABKEY.Specimen.getOpenRequests(populateRequestList);
}

function cancelAddSpecimen()
{
    _requestWin.specOrVialIdArray = undefined;
    _requestWin.isVial = undefined;
    _requestWin.hide();
}

function updateStatusSpan(spanId)
{
    var span = document.getElementById(spanId);
    span.innerHTML = spanId;
}

function requestByGlobalUniqueId(globalUniqueId)
{
    showRequestWindow([globalUniqueId], "GlobalUniqueId");
    return false;
}

function requestByHash(hash)
{
    showRequestWindow([hash], "SpecimenHash");
    return false;
}
