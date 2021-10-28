
Ext4.namespace("LABKEY.experiment");

LABKEY.experiment.confirmDelete = function(dataRegionName, schemaName, queryName, selectionKey, nounSingular, nounPlural) {
    var loadingMsg = Ext4.Msg.show({
        title: "Retrieving data",
        msg: "Loading ..."
    });
    Ext4.Ajax.request({
        url: LABKEY.ActionURL.buildURL('experiment', "getMaterialOperationConfirmationData.api", LABKEY.containerPath, {
            dataRegionSelectionKey: selectionKey,
            sampleOperation: 'Delete',
        }),
        method: "GET",
        success: LABKEY.Utils.getCallbackWrapper(function(response) {
            loadingMsg.hide();
            if (response.success) {
                var numCanDelete = response.data.allowed.length;
                var numCannotDelete = response.data.notAllowed.length;
                var associatedDatasets = response.data.associatedDatasets;
                var associatedDatasetsLength = associatedDatasets.length;
                var canDeleteNoun = numCanDelete === 1 ? nounSingular : nounPlural;
                var cannotDeleteNoun = numCannotDelete === 1 ? nounSingular : nounPlural;
                var totalNum = numCanDelete + numCannotDelete;
                var totalNoun = totalNum === 1 ? nounSingular : nounPlural;
                var dependencyText = LABKEY.moduleContext.experiment && (LABKEY.moduleContext.experiment['experimental-sample-status'] === true) ?
                        "derived sample or assay data dependencies or status that prevents deletion"
                        : "derived sample or assay data dependencies";
                var text;
                if (totalNum === 0) {
                    text = "Either no " + nounPlural + " are selected for deletion or the selected " + nounPlural + " are no longer valid."
                }
                else if (numCannotDelete === 0)  {
                    text = totalNum === 1 ? "The selected "  : (totalNum === 2 ? "Both " : "All " + totalNum + " ");
                    text += totalNoun + " will be permanently deleted."
                }
                else if (numCanDelete === 0) {
                    if (totalNum === 1) {
                        text = "The " + totalNoun + " you've selected cannot be deleted because it has " + dependencyText;
                    } else {
                        text = (numCannotDelete === 2) ? "Neither of" : "None of";
                        text += " the " + totalNum + " " + totalNoun + " you've selected can be deleted";
                        text += " because they have " + dependencyText + ".";
                    }
                }
                else {
                    text = "You've selected " + totalNum + " " + totalNoun + " but only " + numCanDelete + " can be deleted.  ";
                    text += numCannotDelete + " " + cannotDeleteNoun + " cannot be deleted because ";
                    text += (numCannotDelete === 1 ? " it has ": " they have ") + dependencyText + "."
                }
                if (associatedDatasetsLength > 0) {
                    text += "<br/><br/> The selected row(s) will also be deleted from the linked dataset(s) in the following studies:";
                    text += "<ul>";
                    // Alphabetize by dataset name before displaying text rows
                    associatedDatasets.sort((dataset1, dataset2) => {
                        if(dataset1.name < dataset2.name) { return -1; }
                        if(dataset1.name > dataset2.name) { return 1; }
                        return 0;
                    }).forEach(dataset => {
                        text += `<li> <a href="${dataset.url}" target="_blank"> ${dataset.name} </a> </li>`
                    });
                    text += "</ul>";
                }

                if (numCannotDelete > 0) {
                    text += "&nbsp;(<a target='_blank' href='" + LABKEY.Utils.getHelpTopicHref('viewSampleSets#delete') + "'>more info</a>)";
                }
                if (numCanDelete > 0) {
                    if (associatedDatasetsLength === 0) {
                        text += "<br/><br/>";
                    }
                    text += " <b>Deletion cannot be undone.</b>  Do you want to proceed?";
                }

                Ext4.Msg.show({
                    title: numCanDelete > 0 ? "Permanently delete " + numCanDelete + " " + canDeleteNoun : "No " + nounPlural + " can be deleted",
                    msg: text,
                    icon: Ext4.window.MessageBox.QUESTION,
                    buttons: numCanDelete === 0 ? Ext4.Msg.CANCEL : Ext4.Msg.OKCANCEL,
                    buttonText: numCanDelete === 0 ?
                            {
                                cancel:  "Dismiss"
                            } :
                            {
                                ok: "Yes, Delete",
                                cancel: "Cancel"
                            },
                    fn: function(btn) {
                        if (btn === 'cancel') {
                            Ext4.Msg.hide();
                        }
                        else if (btn === 'ok') {
                            const canDelete = response.data.allowed;
                            Ext4.Ajax.request({
                                url: LABKEY.ActionURL.buildURL('query', 'deleteRows'),
                                method: 'POST',
                                jsonData: {
                                    schemaName: schemaName,
                                    queryName: queryName,
                                    rows: canDelete,
                                    apiVersion: 13.2
                                },
                                success: LABKEY.Utils.getCallbackWrapper(function(response)  {
                                    // clear the selection only for the rows that were deleted
                                    // TODO: support clearing selection in query-deleteRows.api using a selectionKey
                                    const ids = canDelete.map((row) => row.RowId);
                                    const dr = LABKEY.DataRegions[dataRegionName];
                                    if (dr) {
                                        dr.setSelected({
                                            ids,
                                            checked: false
                                        });
                                    }

                                    Ext4.Msg.hide();
                                    var responseMsg = Ext4.Msg.show({
                                        title: "Delete " + totalNoun,
                                        msg:  response.rowsAffected + " " + (response.rowsAffected === 1 ? nounSingular : nounPlural) + " deleted."
                                    });
                                    Ext4.defer(function() {
                                        responseMsg.hide();
                                        window.location.reload();
                                    }, 2500, responseMsg);

                                }),
                                failure: LABKEY.Utils.getCallbackWrapper(function(response) {
                                    console.error("There was a problem deleting " + nounPlural, response);
                                    Ext4.Msg.hide();
                                    Ext4.Msg.show({
                                        title: "Delete " + totalNoun,
                                        msg: "There was a problem deleting your " + totalNoun + ".",
                                        buttons: Ext4.Msg.OK
                                    });
                                })
                            });
                        }
                    }
                });
            }
            else {
                LABKEY.Utils.displayAjaxErrorResponse(response);
            }
        }),
        failure: function(response, opts) {
            loadingMsg.hide();
            LABKEY.Utils.displayAjaxErrorResponse(response, opts);
        }
    })
};
