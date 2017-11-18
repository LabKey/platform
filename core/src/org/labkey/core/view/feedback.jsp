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
<%@ page import="org.labkey.core.CoreController" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("internal/jQuery");
    }
%>
<%
    CoreController.FeedbackForm form = (CoreController.FeedbackForm) HttpView.currentModel();
%>

<script type="text/javascript">

    function onLoginClick()
    {
        (function($){
            $.ajax({
                url: LABKEY.ActionURL.buildURL("login", "loginAPI.api"),
                method: 'POST',
                data: {
                    email: document.getElementById('email').value,
                    password: document.getElementById('password').value
                }
            }).success(function() {
                $('.login .notifications p.text-success').html('Sign-in Successful. Your feedback will be submitted.');
                $('.login .notifications p.text-info').hide();
                $('.login .notifications p.text-danger').hide();

                setTimeout(function(){
                    submitFeedback();
                    $('#login').modal('hide');
                },3000);

            }).error(function() {
                $('.login .notifications p.text-danger').html('Sign-in Failed.');
            });
        })(jQuery);

    }

    function onSubmitClick()
    {
        if (LABKEY.user.isGuest)
        {
            jQuery('#login').modal('show');
        }
        else
            submitFeedback();
    }

    function submitFeedback()
    {
        if (!validInput())
        {
            jQuery('#errorModal').modal('show');
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
                jQuery('#successModal').modal('show');
                setTimeout(function(){
                    window.location = LABKEY.contextPath;
                },5000);
            },
            failure: function()
            {
                var errorMsg = 'Submit feedback failed.';
                jQuery('.feedback-notifications p.text-danger').html(errorMsg);
            }
        });

    }

    function validInput()
    {
        if (!document.getElementById('feedbacktitle').value || !document.getElementById('feedbackdescription').value)
            return false;
        return true;
    }
</script>

<div id="feedbackInputScreen">
    <div class="feedback-notifications">
        <p class="text-danger"></p>
    </div>
    <p>As we continue work on updating the LabKey Server user experience, we appreciate feedback you can provide on what is great about the new interface and what needs more work.</p>
    <br>
    <div>
        <labkey:form layout="horizontal" id="feedbackform" method="POST" enctype="multipart/form-data" >
            <labkey:csrf/>

            <labkey:input type="hidden" name="quf_serverSessionId" value="<%= h(form.getServerSessionId()) %>" id="sessionId"/>
            <labkey:input name="quf_title" label="Title" placeholder="Title" id="feedbacktitle"/>
            <labkey:input name="quf_description" label="Description" type="textarea" id="feedbackdescription"
                          placeholder="Description"/>
            <labkey:input name="quf_attachment" label="Attachment" type="file" id="feedbackattachment"/>
            <button type="button" class="btn btn-default" onclick="onSubmitClick();">Submit</button>
        </labkey:form>
    </div>
</div>


<!-- Login Modal -->
<div class="modal fade login" id="login" tabindex="-1" role="dialog"
     aria-labelledby="LogIn" aria-hidden="true">
    <div class="modal-dialog">
        <div class="modal-content">

            <div class="modal-header">
                <button type="button" class="close"
                        data-dismiss="modal">
                    <span aria-hidden="true">&times;</span>
                    <span class="sr-only">Close</span>
                </button>
                <h4 class="modal-title" id="myModalLabel">
                    Sign In
                </h4>
            </div>

            <div class="modal-body">
                <div class="notifications">
                    <p class="text-danger"></p>
                    <p class="text-success"></p>
                    <p class="text-info">Please sign in to LabKey to submit your feedback.</p>
                </div>
                <form role="form">
                    <div class="form-group">
                        <label for="email">Email address</label>
                        <input type="email" class="form-control"
                               id="email" placeholder="Enter email"/>
                    </div>
                    <div class="form-group">
                        <label for="password">Password</label>
                        <input type="password" class="form-control"
                               id="password" placeholder="Password"/>
                    </div>
                    <br>
                    <button type="button" class="btn btn-default" onclick="onLoginClick();">Sign In</button>
                </form>
            </div>
        </div>
    </div>
</div>

<%--Success Modal--%>
<div id="successModal" class="modal fade" role="dialog">
    <div class="modal-dialog">
        <div class="modal-content">
            <div class="modal-header">
                <button type="button" class="close" data-dismiss="modal">&times;</button>
                <h4 class="modal-title">Success</h4>
            </div>
            <div class="modal-body text-success">
                <br>
                <p>Thank you! Your feedback has been received.</p>
                <br>
            </div>
        </div>

    </div>
</div>

<%--Error Modal--%>
<div id="errorModal" class="modal fade" role="dialog">
    <div class="modal-dialog">
        <div class="modal-content">
            <div class="modal-header">
                <button type="button" class="close" data-dismiss="modal">&times;</button>
                <h4 class="modal-title text-danger">Error</h4>
            </div>
            <div class="modal-body">
                <br>
                <p>Please enter feedback title and description.</p>
                <br>
            </div>
        </div>

    </div>
</div>
