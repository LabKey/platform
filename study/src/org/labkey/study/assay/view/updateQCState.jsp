<%@ page import="org.labkey.api.util.element.Input" %>
<%@ page import="org.labkey.api.util.element.Select" %>
<%@ page import="org.labkey.api.util.element.TextArea" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.study.controllers.assay.AssayController" %>
<%@ page import="org.labkey.api.study.assay.AssayService" %>
<%@ page import="org.labkey.api.assay.AssayQCService" %>
<%@ page import="org.labkey.api.exp.api.ExpRun" %>
<%@ page import="org.labkey.api.exp.api.ExperimentService" %>
<%@ page import="org.labkey.api.qc.QCState" %>
<%
    /*
     * Copyright (c) 2018 LabKey Corporation
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
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>

<%
    AssayController.UpdateQCStateForm form = (AssayController.UpdateQCStateForm) HttpView.currentView().getModelBean();
    String currentState = null;

    if (form.getRuns() != null && form.getRuns().length == 1)
    {
        ExpRun run = ExperimentService.get().getExpRun(form.getRuns()[0]);
        if (run != null)
        {
            QCState state = AssayQCService.getProvider().getQCState(run.getProtocol(), run.getRowId());
            currentState = state != null ? state.getLabel() : null;
        }
    }
%>

<script type="application/javascript">

    (function($){

        setQCStates = function(data){

            if (data && data.rows){

                var qcSelect = $("select[id='stateInput']");

                qcSelect.empty().append($('<option>'));
                $.each(data.rows, function (i, row) {
                    qcSelect.append($('<option>', { value: row.RowId,  text: row.Label,  selected: false}));
                });

                // track dirty state for the page
                qcSelect.on('change', function (event, ds) {
                    LABKEY.setDirty(true);
                });
            }
        };

        $(document).ready(function () {

            // qc states
            LABKEY.Query.selectRows({
                schemaName  : 'core',
                queryName   : 'qcstate',
                scope       : this,
                columns     : 'rowid, label',
                success     : function(data){setQCStates(data);},
                failure     : function(){LABKEY.Utils.alert('Error', 'Unable to read the core.QCState table')}
            });

            // runs qcflag info
            LABKEY.Query.selectRows({
                schemaName  : 'core',
                queryName   : 'qcstate',
                scope       : this,
                columns     : 'rowid, label',
                success     : function(data){setQCStates(data);},
                failure     : function(){LABKEY.Utils.alert('Error', 'Unable to read the core.QCState table')}
            });
            window.onbeforeunload = LABKEY.beforeunload(LABKEY.isDirty());
        });

    })(jQuery);
</script>


<labkey:errors/>
<labkey:form method="POST" layout="horizontal" onsubmit="LABKEY.setSubmit(true);">

    <%
        if (currentState != null)
        {
    %>
    <labkey:input type="displayfield" label="Current State" value="<%=h(currentState)%>"/>
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

    <%= new TextArea.TextAreaBuilder().name("comment").id("commentInput").label("Comment")
            .layout(Input.Layout.HORIZONTAL)
            .value(form.getComment())
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
    <labkey:input type="hidden" name="returnUrl" value="<%=h(form.getReturnUrl())%>"/>
    <labkey:button text="update" submit="true"/>
</labkey:form>
