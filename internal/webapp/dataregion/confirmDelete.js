
Ext4.namespace("LABKEY.dataregion");

LABKEY.dataregion.confirmDelete = function(
        dataRegionName,
        schemaName,
        queryName,
        controller,
        deleteConfirmationActionName,
        selectionKey,
        nounSingular,
        nounPlural,
        dependencyText,
        extraConfirmationContext = {},
        deleteUrl = LABKEY.ActionURL.buildURL('query', 'deleteRows'),
        rowParameterName = 'rows') {
    var loadingMsg = Ext4.Msg.show({
        title: "Retrieving data",
        msg: "Loading ..."
    });
    Ext4.Ajax.request({
        url: LABKEY.ActionURL.buildURL(controller, deleteConfirmationActionName, LABKEY.containerPath, Object.assign({}, {
            dataRegionSelectionKey: selectionKey,
        }, extraConfirmationContext)),
        method: "GET",
        success: LABKEY.Utils.getCallbackWrapper(function(response) {
            loadingMsg.hide();
            if (response.success) {
                var numCanDelete = response.data.allowed.length;
                var numCannotDelete = response.data.notAllowed.length;
                var associatedDatasets = response.data.associatedDatasets;
                var associatedDatasetsLength = associatedDatasets ? associatedDatasets.length : 0;
                var canDeleteNoun = numCanDelete === 1 ? nounSingular : nounPlural;
                var cannotDeleteNoun = numCannotDelete === 1 ? nounSingular : nounPlural;
                var totalNum = numCanDelete + numCannotDelete;
                var totalNoun = totalNum === 1 ? nounSingular : nounPlural;
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

                if (numCanDelete > 0) {
                    if (associatedDatasetsLength === 0) {
                        text += "<br/><br/>";
                    }
                    text += " <b>Deletion cannot be undone.</b>  Do you want to proceed?";
                }

                Ext4.Msg.show({
                    title: numCanDelete > 0 ? "Permanently delete " + numCanDelete + " " + canDeleteNoun : "No " + nounPlural + " can be deleted",
                    msg: text,
                    width: 450, // added to keep the text from being swallowed vertically
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
                                url: deleteUrl,
                                method: 'POST',
                                jsonData: {
                                    schemaName: schemaName,
                                    queryName: queryName,
                                    [rowParameterName]: canDelete,
                                    apiVersion: 13.2
                                },
                                success: LABKEY.Utils.getCallbackWrapper(function(response)  {
                                    // clear the selection only for the rows that were deleted
                                    const ids = canDelete.map((row) => row.RowId);
                                    const dr = LABKEY.DataRegions[dataRegionName];
                                    if (dr) {
                                        dr.setSelected({
                                            ids,
                                            checked: false
                                        });
                                    }

                                    Ext4.Msg.hide();
                                    var numDeleted = response.rowsAffected ? response.rowsAffected : ids.length;
                                    var responseMsg = Ext4.Msg.show({
                                        title: "Delete " + totalNoun,
                                        msg:  numDeleted + " " + (numDeleted === 1 ? nounSingular : nounPlural) + " deleted."
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
