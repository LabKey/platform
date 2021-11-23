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
<%@ page import="org.labkey.api.study.StudyService" %>
<%@ page import="org.labkey.api.study.publish.AbstractPublishStartAction" %>
<%@ page import="org.labkey.api.study.publish.PublishBean" %>
<%@ page import="org.labkey.api.study.publish.StudyPublishService" %>
<%@ page import="org.labkey.api.util.HelpTopic" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.util.Pair" %>
<%@ page import="org.labkey.api.util.StringUtilsLabKey" %>
<%@ page import="org.labkey.api.util.element.Input" %>
<%@ page import="org.labkey.api.util.element.Option" %>
<%@ page import="org.labkey.api.util.element.Select" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="java.util.Iterator" %>
<%@ page import="java.util.List" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%
    JspView<PublishBean> me = (JspView<PublishBean>) HttpView.currentView();
    PublishBean bean = me.getModelBean();
    boolean exceedsMaxRows = bean.getIds().size() > AbstractPublishStartAction.MAX_ROWS_TO_LINK;
    boolean unambiguous = !bean.isInsufficientPermissions() && !bean.isNullStudies() && bean.getStudies().size() == 1;
    Study firstStudy = null;
    Container firstStudyContainer = null;
    boolean autoLinkEnabled = bean.isAutoLinkEnabled();

    if (unambiguous)
    {
        Iterator<Container> studyIt = bean.getStudies().iterator();
        firstStudyContainer = studyIt.next();
        firstStudy = StudyService.get().getStudy(firstStudyContainer);
        if (firstStudy == null)
            unambiguous = false;
    }

    ActionURL postURL = bean.getSuccessURL();
    List<Pair<String, String>> parameters = postURL.getParameters();
    postURL.deleteParameters();

    Select.SelectBuilder options = new Select.SelectBuilder().name("targetStudy").label("Choose target study")
            .id("targetStudy")
            .layout(Input.Layout.HORIZONTAL)
            .formGroup(true);

    for (Study study : StudyPublishService.get().getValidPublishTargets(getUser(), InsertPermission.class))
    {
        String path = study.getContainer().getPath();
        options.addOption(new Option.OptionBuilder()
                .value(study.getContainer().getId())
                .label(path + " (" + study.getLabel() + ")")
                .selected(firstStudyContainer != null && firstStudyContainer.getPath().equals(path))
                .build());
    }
%>

<%-- Issue43119: <labkey:input type="checkbox"/> elements cause juddering and overlapping --%>
<%-- on this page without overriding styles --%>
<style>
    .control-label {
        width: 270px !important;
        padding-top: 0 !important;
    }

    .col-sm-9 {
        width: 400px !important;
    }

    .col-lg-10 {
        min-width: 400px !important;
        width: 40vw !important;
    }

    .form-control {
        width: inherit !important;
    }

    .form-control-static {
        width: 90vw  !important;
    }

    .form-group {
        display: flex !important;
    }

    #chooseStudyError {
        color: #a94442;
        margin-bottom: 15px;
        margin-left: 270px;
    }
</style>
<script type="application/javascript">

    (function($){

        var exceedsMaxRow = <%=exceedsMaxRows%>;
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

        // invoked when the auto link checkbox is changed, if the number of rows has been exceeded we only allow
        // the auto link route and show/hide the max rows error message.
        toggleAutoLink = function() {
            if (exceedsMaxRow) {
                let errorMsg = $("span[id='row-exceeded-msg']");
                if ($("input[id='autoLink']").prop('checked')){
                    errorMsg.hide();
                    $("a[id='next-btn']").removeClass('labkey-disabled-button');
                }
                else {
                    errorMsg.show();
                    $("a[id='next-btn']").addClass('labkey-disabled-button');
                }
            }
        };

        handleNext = function(){
            if ($("#autoLinkCategory").val() !== undefined && $("#autoLinkCategory").val().length > 200) {
                let errorMessageDiv = $('#chooseStudyError');
                errorMessageDiv.text("Linked Dataset Category name must be shorter than 200 characters.");
                return;
            }

            var autoLink = $("input[id='autoLink']");
            if (autoLink.prop('checked')){

                var data = {};
                $('#linkForm').serializeArray().map(function(x){
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
                    url : LABKEY.ActionURL.buildURL('publish', 'autoLinkRun.api', null),
                    method : 'POST',
                    jsonData : data,
                    success: LABKEY.Utils.getCallbackWrapper(function(response)
                    {
                        if (response.success)
                            window.location = response.returnUrl;
                    }),
                    failure: LABKEY.Utils.getCallbackWrapper(function(response) {
                        LABKEY.Utils.alert('Error', 'Unable to auto link the data : ' + response.exception);
                    },
                    this, true)
                });

            }
            else {
                // submit the form to the standard confirm page
                $('#linkForm').submit();
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
    if (exceedsMaxRows)
    {
%>
    <span class="labkey-error" id="row-exceeded-msg" <%=!bean.getBatchIds().isEmpty() ? HtmlString.unsafe("style = 'display:none;'") : HtmlString.unsafe("")%>><h4>
        The maximum number of rows (<%=AbstractPublishStartAction.MAX_ROWS_TO_LINK%>) that can be linked at one time has been exceeded. You can review this <%=new HelpTopic("publishAssayData#trouble").getSimpleLinkHtml("help topic")%> for more information on how to work around this limit.
    </h4></span>
<%
    }

    if (bean.getStudies().size() > 1)
    {
%>
<span class="labkey-error"><h4>WARNING: The selected <%=h(bean.getBatchNoun())%>s were initially associated with different studies.</h4></span>
<%
    }
    if (bean.isInsufficientPermissions())
    {
%>
<span class="labkey-error"><h4>WARNING: You do not have permissions to link to one or more of the selected <%=h(bean.getBatchNoun())%>'s associated studies.</h4></span>
<%
    }
%>

<div id="chooseStudyError"> </div>
<labkey:form method="POST" id="linkForm" layout="horizontal" action="<%=postURL%>">
    <%
        if (unambiguous)
        {
            HtmlString label = HtmlString.unsafe("All data is marked for linking to study <b>" + h(firstStudy.getLabel()) + "</b> in folder <b>" + h(firstStudy.getContainer().getPath()) + "</b>.");
    %>
    <labkey:input type="displayfield" value="<%=label%>"/>
    <labkey:input type="checkbox" label="Link to a different study" id="chooseStudy" onChange="toggleStudies();"/>
    <%
        }
    %>

    <%= options %>
    <%
        if (!bean.getBatchIds().isEmpty())
        {
            String autoLinkLabel = "Auto link data from " +
                    StringUtilsLabKey.pluralize(bean.getBatchIds().size(), bean.getBatchNoun()) + " (" +
                    StringUtilsLabKey.pluralize(bean.getIds().size(), "row") + ")";

            String autoLinkTip = "Selecting this checkbox will skip the confirmation step and run the link to study process in a pipeline job. Any " +
                    bean.getBatchNoun() + " data missing valid subject IDs and timepoints will be ignored.";
    %>
    <labkey:input type="checkbox" label="<%= autoLinkLabel %>" id="autoLink" forceSmallContext="true"
                  contextContent="<%= autoLinkTip %>" checked="<%=exceedsMaxRows%>" onChange="toggleAutoLink()"/>
    <%
        }
    %>

    <%
        if (!autoLinkEnabled)
        {
            String autoLinkCategoryTip = "Specify the desired category for the Dataset that will be created (or appended to) in the target study when rows are linked. " +
                    "If the category you specify does not exist, it will be created. If the Dataset already exists, this setting will not overwrite a previously assigned category. " +
                    "Leave blank to use the default category of \"Uncategorized\".";
    %>
    <labkey:input type="text" label="Specify Linked Dataset Category" className="form-control" name="autoLinkCategory" id="autoLinkCategory" contextContent="<%= autoLinkCategoryTip %>" forceSmallContext="true"/>
    <%
        }
    %>

    <labkey:button text="Next" onclick="handleNext();" id="next-btn" submit="false" enabled="<%=!exceedsMaxRows || !bean.getBatchIds().isEmpty()%>"/>
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
        for (Integer id : bean.getBatchIds())
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
