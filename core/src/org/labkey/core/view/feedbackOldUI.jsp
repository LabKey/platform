<%
    /*
     * Copyright (c) 2017 LabKey Corporation
     *
     * Licensed under the Apache License, Version 2.0 (the "License");
     * you may not use this file except in compliance with the License.
     * You may obtain a copy of the License at
     *
     *     http://www.apache.org/licenses/LICENSE-2.0
     *
     * Unless required by applicable law or agreed to in writing, software
     * distributed under the License is distributed on an "AS IS" BASIS,
     * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     * See the License for the specific language governing permissions and
     * limitations under the License.
     */
%>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("clientapi/ext4");
    }
%>

<style type="text/css">
    label {
        display: inline-block;
        width: 10%;
        text-align: left;
        min-width: 100px;
    }
    input, textarea {
        width: 60%;
    }
    textarea {
        width: 60%;
        height: 80px;
    }
</style>

<script type="text/javascript">

    var getForm, getLoginWin, loginForm, loginWin;
    Ext4.onReady(function ()
    {
        getForm = function() {
            if (!loginForm) {
                loginForm = Ext4.create('Ext.form.Panel', {
                    id: 'loginForm',
                    bodyPadding: 5,
                    border: false,
                    flex: 1,
                    items: [{
                        xtype: 'textfield',
                        name: 'email',
                        fieldLabel: 'Email',
                        allowBlank: false,
                        vType: 'email',
                        width: 400
                    },{
                        xtype: 'textfield',
                        name: 'password',
                        fieldLabel: 'Password',
                        allowBlank: false,
                        inputType: 'password',
                        width: 400
                    }]
                });
            }
            return loginForm;
        };

        getLoginWin = function()
        {
            if (!loginWin) {
                loginWin = Ext4.create('Ext.window.Window', {
                    border: false,
                    modal: true,
                    resizable: false,
                    draggable: false,
                    title: 'Sign-In',
                    layout: {
                        type: 'fit'
                    },
                    items: [getForm()],
                    buttons: [,{
                        text: 'Sign-In',
                        handler: onLoginClick
                    }],
                    width: 450,
                    height: 150,
                    closeAction: 'hide'
                });
            }
            return loginWin;
        }

    });
    function onLoginClick()
    {
        var form = getForm().getForm();

        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('login', 'loginAPI.api'),
            method: 'POST',
            params: {
                email: form.findField('email').getValue(),
                password: form.findField('password').getValue()
            },
            success: function()
            {
                loginWin.hide();
                Ext4.Msg.show({
                    title: 'Success',
                    msg: 'Sign-in Successful. Do you want to submit your feedback now?',
                    buttons: Ext4.Msg.OK+Ext4.Msg.YES,
                    minWidth: 400,
                    buttonText: {
                        ok: 'No',
                        yes: 'Yes'
                    },
                    fn: function(id) {
                        if (id === 'yes') {
                            submitFeedback();
                        }
                    }
                });
            },
            failure: function()
            {
                Ext4.Msg.show({
                    title:'Error',
                    msg: 'Sign in failed.',
                    icon: Ext4.MessageBox.ERROR
                });
            }
        });

    }

    function onSubmitClick()
    {
        if (LABKEY.user.isGuest)
        {
            getLoginWin().show();
        }
        else
            submitFeedback();
    }

    function submitFeedback()
    {
        if (!validInput())
        {
            Ext4.Msg.show({
                title:'Error',
                msg: 'Please enter feedback title and description.',
                icon: Ext4.MessageBox.ERROR
            });
            return;
        }
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL('query', 'insertQueryRow.api', LABKEY.container.path + '/FeedbackList', {
                'schemaName': 'lists',
                'query.queryName': 'Feedback'
            }),
            method: 'POST',
            form: new FormData(document.getElementById('feedbackform')),
            success: function()
            {
                Ext4.Msg.show({
                    title:'Success',
                    msg: 'Thank you! Your feedback has been received.'
                });
                setTimeout(function(){
                    window.location = LABKEY.contextPath;
                },5000);
            },
            failure: function()
            {
                Ext4.Msg.show({
                    title:'Error',
                    msg: 'Submit feedback failed.',
                    icon: Ext4.MessageBox.ERROR
                });
            }
        });

    }

    function validInput()
    {
        return !(!document.getElementById('feedbacktitle').value || !document.getElementById('feedbackdescription').value);
    }

</script>

<div id="feedbackInputScreen">
    <div class="feedback-notifications">
        <p class="text-danger"></p>
    </div>
    <p>As we continue work on updating the LabKey Server user experience, we appreciate feedback you can provide on what is great about the new interface and what needs more work.</p>
    <br>
    <labkey:form id="feedbackform" method="POST" enctype="multipart/form-data">
        <labkey:csrf/>
        <labkey:input name="quf_title" label="Title" placeholder="Title" id="feedbacktitle"/><br>
        <labkey:input name="quf_description" label="Description" type="textarea" id="feedbackdescription"
                      placeholder="Description"/><br>
        <labkey:input name="quf_attachment" label="Attachment" type="file" id="feedbackattachment"/><br>
        <button type="button" class="btn btn-default" onclick="onSubmitClick();">Submit</button>
    </labkey:form>
</div>

