
Ext4.namespace("LABKEY.experiment");

LABKEY.experiment.confirmDelete = function(schemaName, queryName, selectionKey, nounSingular, nounPlural) {
    var loadingMsg = Ext4.Msg.show({
        title: "Retrieving data",
        msg: "Loading ..."
    });
    Ext4.Ajax.request({
        url: LABKEY.ActionURL.buildURL('experiment', "getMaterialDeleteConfirmationData.api", LABKEY.containerPath, {
            dataRegionSelectionKey: selectionKey
        }),
        method: "GET",
        success: LABKEY.Utils.getCallbackWrapper(function(response) {
            loadingMsg.hide();
            if (response.success) {
                var numCanDelete = response.data.canDelete.length;
                var numCannotDelete = response.data.cannotDelete.length;
                var canDeleteNoun = numCanDelete === 1 ? nounSingular : nounPlural;
                var cannotDeleteNoun = numCannotDelete === 1 ? nounSingular : nounPlural;
                var totalNum = numCanDelete + numCannotDelete;
                var totalNoun = totalNum === 1 ? nounSingular : nounPlural;
                var dependencyText = "derived sample or assay data dependencies";
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
                if (numCannotDelete > 0) {
                    text += "&nbsp;(<a target='_blank' href='" + LABKEY.Utils.getHelpTopicHref('viewSampleSets#delete') + "'>more info</a>)";
                }
                if (numCanDelete > 0) {
                    text += " <br/><br/><b>Deletion cannot be undone.</b>  Do you want to proceed?";
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
                            Ext4.Ajax.request({
                                url: LABKEY.ActionURL.buildURL('query', 'deleteRows'),
                                method: 'POST',
                                jsonData: {
                                    schemaName: schemaName,
                                    queryName: queryName,
                                    rows: response.data.canDelete,
                                    apiVersion: 13.2
                                },
                                success: LABKEY.Utils.getCallbackWrapper(function(response)  {
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