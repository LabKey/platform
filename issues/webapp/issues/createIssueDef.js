/*
 * Copyright (c) 2014-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */

IssueDefUtil = new function() {

    return {

        verifyIssueDefName : function(btnId){

            // get the form value
            var input = document.getElementsByName("quf_Label")[0];
            var name = input.value;
            if (name){

                Ext4.Ajax.request({
                    url     : LABKEY.ActionURL.buildURL("issues", "validateIssueDefName.api"),
                    scope   : this,
                    jsonData : {
                        issueDefName : name
                    },
                    success: function(response) {
                        var jsonResp = LABKEY.Utils.decode(response.responseText);
                        if (jsonResp){

                            Ext4.MessageBox.confirm('Create Issue List Definition?', jsonResp.message, function(btn){

                                if (btn === 'yes'){
                                    input.form.submit();
                                }
                            });

                        }
                    },
                    failure: function(response){
                        var jsonResp = LABKEY.Utils.decode(response.responseText);
                        if (jsonResp && jsonResp.errors)
                        {
                            var errorHTML = jsonResp.errors[0].message;
                            Ext4.Msg.alert('Error', errorHTML);
                        }
                    }
                });
            }
        }
    };
};
