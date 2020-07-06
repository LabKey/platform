<%
/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container"%>
<%@ page import="org.labkey.api.data.DataRegion" %>
<%@ page import="org.labkey.api.data.DataRegionSelection" %>
<%@ page import="org.labkey.api.security.permissions.InsertPermission" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.StudyUrls" %>
<%@ page import="org.labkey.api.study.assay.AssayPublishService" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.util.element.Input" %>
<%@ page import="org.labkey.api.util.element.Option" %>
<%@ page import="org.labkey.api.util.element.Select" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.study.assay.PublishStartAction" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.List" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<PublishStartAction.PublishBean> me = (JspView<PublishStartAction.PublishBean>) HttpView.currentView();
    PublishStartAction.PublishBean bean = me.getModelBean();
    boolean unambiguous = !bean.isInsufficientPermissions() && !bean.isNullStudies() && bean.getStudies().size() == 1;
    Study firstStudy = null;
    Container firstStudyContainer = null;

    if (unambiguous)
    {
        Iterator<Container> studyIt = bean.getStudies().iterator();
        firstStudyContainer = studyIt.next();
        firstStudy = StudyManager.getInstance().getStudy(firstStudyContainer);
        if (firstStudy == null)
            unambiguous = false;
    }

    ActionURL postURL = urlProvider(StudyUrls.class).getCopyToStudyConfirmURL(getContainer(), bean.getProtocol());
    List<Pair<String, String>> parameters = postURL.getParameters();
    postURL.deleteParameters();

    Select.SelectBuilder options = new Select.SelectBuilder().name("targetStudy").label("Choose target study")
            .id("targetStudy")
            .layout(Input.Layout.HORIZONTAL)
            .formGroup(true);

    for (Study study : AssayPublishService.get().getValidPublishTargets(getUser(), InsertPermission.class))
    {
        String path = study.getContainer().getPath();
        options.addOption(new Option.OptionBuilder()
                .value(study.getContainer().getId())
                .label(path + " (" + study.getLabel() + ")")
                .selected(firstStudyContainer != null && firstStudyContainer.getPath().equals(path))
                .build());
    }
%>
<script type="application/javascript">

    (function($){

        toggleStudies = function(){
            var studySelect = $("select[id='targetStudy']");
            var studySelectLabel = $("label[for='targetStudy']");
            var chooseStudy = $("input[id='chooseStudy']");

            if (chooseStudy.prop('checked')){
                studySelect.show();
                studySelectLabel.show();
            }
            else {
                studySelect.hide();
                studySelectLabel.hide();
            }
        };

        handleNext = function(){

            var autoCopy = $("input[id='autoCopy']");
            if (autoCopy.prop('checked')){

                var data = {};
                $('#copyForm').serializeArray().map(function(x){
                    if (!data[x.name]) {
                        data[x.name] = x.value;
                    } else {
                        if (!$.isArray(data[x.name])){
                            var prev = data[x.name];
                            data[x.name] = [prev];
                        }
                        data[x.name].push(x.value);
                    }
                });

                LABKEY.Ajax.request({
                    url : LABKEY.ActionURL.buildURL('publish', 'autoCopyRun.api', null),
                    method : 'POST',
                    jsonData : data,
                    success: LABKEY.Utils.getCallbackWrapper(function(response)
                    {
                        if (response.success)
                            window.location = response.returnUrl;
                    }),
                    failure: LABKEY.Utils.getCallbackWrapper(function(response) {
                        LABKEY.Utils.alert('Error', 'Unable to auto copy the assay data : ' + response.exception);
                    },
                    this, true)
                });

            }
            else {
                // submit the form to the standard confirm page
                $('#copyForm').submit();
            }
        };

        $(document).ready(function () {
            if (<%=unambiguous%>){
                toggleStudies();
            }
        });

    })(jQuery);
</script>

<%
    if (bean.getStudies().size() > 1)
    {
%>
<span class="labkey-error"><h4>WARNING: The selected runs were initially associated with different studies.</h4></span>
<%
    }
    if (bean.isInsufficientPermissions())
    {
%>
<span class="labkey-error"><h4>WARNING: You do not have permissions to copy to one or more of the selected run's associated studies.</h4></span>
<%
    }
%>

<labkey:form method="POST" id="copyForm" layout="horizontal" action="<%=h(postURL.getLocalURIString())%>">
    <%
        if (unambiguous)
        {
            HtmlString label = HtmlString.unsafe("All data is marked for copying to study <b>" + h(firstStudy.getLabel()) + "</b> in folder <b>" + h(firstStudy.getContainer().getPath()) + "</b>.");
    %>
    <labkey:input type="displayfield" value="<%=label%>"/>
    <labkey:input type="checkbox" label="Copy to a different study" id="chooseStudy" onChange="toggleStudies();"/>
    <%
        }
    %>

    <%= options %>
    <%
        if (!bean.getRunIds().isEmpty())
        {
    %>
    <labkey:input type="checkbox" label="Auto copy the selected run(s)" id="autoCopy" forceSmallContext="true"
                  contextContent="Selecting this checkbox will skip the confirmation step and run the copy to study process in a pipeline job. Run data missing valid subject IDs and timepoints will be ignored."/>
    <%
        }
    %>

    <labkey:button text="Next" onclick="handleNext();" submit="false"/>
    <labkey:button text="Cancel" href="<%=bean.getReturnURL()%>"/>

    <%
        for (Pair<String, String> parameter : parameters)
        {
    %>
    <input type="hidden" name="<%= h(parameter.getKey()) %>" value="<%= h(parameter.getValue()) %>">
    <%
        }
        // data row IDs
        for (Integer id : bean.getIds())
        {
    %>
    <input type="hidden" name="<%= h(DataRegion.SELECT_CHECKBOX_NAME) %>" value="<%= id %>">
    <%
        }
        // run IDs (if run selected)
        for (Integer id : bean.getRunIds())
        {
    %>
    <input type="hidden" name="runId" value="<%= id %>">
    <%
        }
    %>
    <input type="hidden" name="<%=ActionURL.Param.returnUrl%>" value="<%= h(bean.getReturnURL()) %>">
    <input type="hidden" name="containerFilterName" value="<%= h(bean.getContainerFilterName()) %>">
    <input type="hidden" name="<%= h(DataRegionSelection.DATA_REGION_SELECTION_KEY) %>" value="<%=h(bean.getDataRegionSelectionKey())%>">
</labkey:form>
