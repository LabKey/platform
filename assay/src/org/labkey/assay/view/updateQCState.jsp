<%
/*
 * Copyright (c) 2019 LabKey Corporation
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
<%@ page import="org.labkey.api.assay.AssayQCService" %>
<%@ page import="org.labkey.api.exp.api.ExpRun" %>
<%@ page import="org.labkey.api.exp.api.ExperimentService" %>
<%@ page import="org.labkey.api.qc.DataState" %>
<%@ page import="org.labkey.api.util.element.Input" %>
<%@ page import="org.labkey.api.util.element.Select" %>
<%@ page import="org.labkey.api.util.element.TextArea" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.assay.AssayController.UpdateQCStateForm" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    UpdateQCStateForm form = (UpdateQCStateForm) HttpView.currentView().getModelBean();
    String currentState = null;
    String protocolContainerPath = null;
    boolean requireComment = false;

    if (form.getRuns().size() == 1)
    {
        // for single row selections, display the current QC state
        ExpRun run = ExperimentService.get().getExpRun(form.getRuns().stream().findFirst().get());
        if (run != null)
        {
            DataState state = AssayQCService.getProvider().getQCState(run.getProtocol(), run.getRowId());
            currentState = state != null ? state.getLabel() : null;
        }
    }

    // get the protocol container to determine the folder where QC states are defined
    ExpRun expRun = ExperimentService.get().getExpRun(form.getRuns().stream().findFirst().get());
    if (expRun != null)
    {
        protocolContainerPath = expRun.getProtocol().getContainer().getPath();
        requireComment = AssayQCService.getProvider().isRequireCommentOnQCStateChange(expRun.getProtocol().getContainer());
    }
%>

<script type="application/javascript">

    (function($){

        let setQCStates = function(data){

            if (data && data.rows){

                let qcSelect = $("select[id='stateInput']");
                qcSelect.empty().append($('<option>'));
                $.each(data.rows, function (i, row) {
                    qcSelect.append($('<option>', { value: row.RowId,  text: row.Label,  selected: false}));
                });

                // track dirty state for the page
                qcSelect.on('change', function (event) {
                    enableUpdateBtn(qcSelect.val());
                    LABKEY.setDirty(true);
                });
            }
        };

        let enableUpdateBtn = function(enable){
            if (enable)
                LABKEY.Utils.removeClass(document.querySelector('#update-btn'), 'labkey-disabled-button');
            else
                LABKEY.Utils.addClass(document.querySelector('#update-btn'), 'labkey-disabled-button');
        };

        saveState = function(){

            let form = document.querySelector('#qc_form');
            if (form){
                const formData = new FormData(form);
                const missingComment = <%=requireComment%> && formData.get('comment').trim().length === 0;

                if (missingComment) {
                    LABKEY.Utils.alert('Error', 'A comment is required when changing a QC State for the selected run(s).')
                    return;
                }

                LABKEY.Ajax.request({
                    method  : 'POST',
                    url     : LABKEY.ActionURL.buildURL("assay", "updateQCState.api"),
                    form    : new FormData(form),
                    success : LABKEY.Utils.getCallbackWrapper(function(response)
                    {
                        if (response.success) {
                            window.onbeforeunload = null;
                            window.location = <%=q(form.getReturnUrl())%>;
                        }
                    }),
                    failure : LABKEY.Utils.displayAjaxErrorResponse
                });
            }
        };

        $(document).ready(function () {

            // disable update button
            enableUpdateBtn(false);

            // qc states
            LABKEY.Query.selectRows({
                schemaName  : 'core',
                queryName   : 'qcstate',
                containerPath : <%=q(protocolContainerPath)%>,
                scope       : this,
                columns     : 'rowid, label',
                success     : function(data){setQCStates(data);},
                failure     : function(){LABKEY.Utils.alert('Error', 'Unable to read the core.qcState table')}
            });
            window.onbeforeunload = LABKEY.beforeunload(LABKEY.isDirty());
        });

    })(jQuery);
</script>


<labkey:errors/>
<labkey:form method="POST" layout="horizontal" onsubmit="LABKEY.setSubmit(true);" id="qc_form">

    <%
        if (currentState != null)
        {
    %>
    <labkey:input type="displayfield" label="Current State" value="<%=currentState%>"/>
    <%
        }
    %>

    <%= new Select.SelectBuilder().name("state").id("stateInput").label("New State *")
            .layout(Input.Layout.HORIZONTAL)
            .required(true)
            .contextContent("The QC State to assign to the selected run(s)")
            .forceSmallContext(true)
            .formGroup(true)
    %>

    <%
        String commentLabel = requireComment ? "Comment *" : "Comment";
        String commentHelpTip = requireComment ? "A comment is required when changing a QC State for the selected run(s)." : null;
    %>
    <%= new TextArea.TextAreaBuilder().name("comment").id("commentInput").label(commentLabel)
            .layout(Input.Layout.HORIZONTAL)
            .value(form.getComment())
            .required(requireComment)
            .contextContent(commentHelpTip)
            .forceSmallContext(true)
            .formGroup(true)
            .columns(80)
            .rows(5)
    %>

    <%
        for (int run : form.getRuns())
        {
    %>
        <labkey:input type="hidden" name="runs" value="<%=run%>"/>
    <%
        }
    %>
    <%= generateReturnUrlFormField(form) %>
    <labkey:button text="update" submit="false" onclick="saveState();" id="update-btn"/>
    <labkey:button text="cancel" href="<%=form.getReturnActionURL()%>" onclick="LABKEY.setSubmit(true);"/>
</labkey:form>
