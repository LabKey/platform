/*
 * Copyright (c) 2016-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

IssueDefUtil = new function() {

    return {

        verifyIssueDefName : function(btnId){

            // get the form value
            var inputKind = document.getElementsByName("quf_Kind")[0];
            if (inputKind.value) {
                var input = document.getElementsByName("quf_Label")[0];
                var name = input.value;
                if (name) {

                    LABKEY.Ajax.request({
                        url: LABKEY.ActionURL.buildURL("issues", "validateIssueDefName.api"),
                        scope: this,
                        jsonData: {
                            issueDefName: name,
                            issueDefKind: inputKind.value
                        },
                        success: function (response) {
                            var jsonResp = LABKEY.Utils.decode(response.responseText);
                            if (jsonResp) {
                                if (jsonResp.success) {
                                    Ext4.MessageBox.confirm('Create Issue List Definition?', jsonResp.message, function (btn) {

                                        if (btn === 'yes') {
                                            input.form.submit();
                                        }
                                    });
                                }
                                else {
                                    var errorHTML = jsonResp.message;
                                    LABKEY.Utils.alert('Error', errorHTML);
                                }

                            }
                        },
                        failure: function (response) {
                            var jsonResp = LABKEY.Utils.decode(response.responseText);
                            if (jsonResp && jsonResp.errors) {
                                var errorHTML = jsonResp.errors[0].message;
                                LABKEY.Utils.alert('Error', errorHTML);
                            }
                        }
                    });
                }
            }
            else {
                LABKEY.Utils.alert('Error', "No IssueList Kind found. You may need to enable the Issues module.");
            }
        }
    };
};
