<%
/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
<%@ page import="org.labkey.api.admin.AdminUrls" %>
<%@ page import="org.labkey.api.data.PropertyManager" %>
<%@ page import="org.labkey.api.study.MasterPatientIndexService" %>
<%@ page import="org.labkey.api.util.HelpTopic" %>
<%@ page import="org.labkey.api.util.element.Input" %>
<%@ page import="org.labkey.api.util.element.Option" %>
<%@ page import="org.labkey.api.util.element.Select" %>
<%@ page import="org.labkey.study.controllers.StudyController" %>
<%@ page import="java.util.Collection" %>
<%@ page extends="org.labkey.api.jsp.FormPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    PropertyManager.PropertyMap map = PropertyManager.getNormalStore().getProperties(StudyController.MasterPatientProviderSettings.CATEGORY);
    String type = map.get(StudyController.MasterPatientProviderSettings.TYPE);
    String docLink = new HelpTopic("empi").getHelpTopicHref();

    MasterPatientIndexService.ServerSettings settings = null;
    if (type != null)
    {
        MasterPatientIndexService svc = MasterPatientIndexService.getProvider(type);
        if (svc != null )
            settings = MasterPatientIndexService.getProvider(type).getServerSettings();
    }

    Collection<MasterPatientIndexService> services = MasterPatientIndexService.getProviders();
    Select.SelectBuilder options = new Select.SelectBuilder().name("type").label("Type")
            .layout(Input.Layout.HORIZONTAL)
            .formGroup(true)
            .addOption(new Option.OptionBuilder().build());

    for (MasterPatientIndexService svc : services)
    {
        options.addOption(new Option.OptionBuilder().value(svc.getName())
                .label(svc.getName())
                .selected(svc.getName().equals(type))
                .build());
    }
%>

<script type="application/javascript">

    (function($){
        testConnection = function(){

            var params = {
                type : $("select[name='type']").val(),
                url : $("input[name='url']").val(),
                username : $("input[name='username']").val(),
                password : $("input[name='password']").val()
            };

            console.log(params);

            LABKEY.Ajax.request({
                url: LABKEY.ActionURL.buildURL("study", "testMasterPatientProvider.api"),
                method: "POST",
                jsonData : params,
                success: LABKEY.Utils.getCallbackWrapper(function(response)
                {
                    if (response.success)
                        LABKEY.Utils.alert("Connection succeeded", response.message);
                    else if (response.message){
                        LABKEY.Utils.alert("Connection failed", response.message);
                    }
                }),
                failure: LABKEY.Utils.getCallbackWrapper(function(response)
                {
                    LABKEY.Utils.alert("Connection failed", response.exception);
                })
            });
        };

        $(document).ready(function () {
            window.onbeforeunload = LABKEY.beforeunload(LABKEY.isDirty());
        });

    })(jQuery);
</script>

<labkey:errors/>
<%  if (services.isEmpty()) {
%>
    <div class="alert alert-info">
        <h1 class="fa fa-star-o"> Premium Feature</h1>
        <h3>Enterprise Master Patient Index integration is a premium feature and is not available with your current edition of LabKey Server.</h3>
        <hr>
        <p>Premium edition subscribers have the ability to integrate with an Enterprise Master Patient Index, using EMPI IDs to
            create an authoritative connection between LabKey-housed data and a patient's master index record.</p>
        <p><a class="alert-link" href="<%=h(docLink)%>" target="_blank" rel="noopener noreferrer">Learn more <i class="fa fa-external-link"></i></a></p>
        <p>In addition to this feature, premium editions of LabKey Server provide professional support and advanced functionality to help teams maximize the value of the platform.</p>
        <br>
        <p><a class="alert-link" href="https://www.labkey.com/platform/go-premium/" target="_blank" rel="noopener noreferrer">Learn more about premium editions <i class="fa fa-external-link"></i></a></p>
    </div>
<%  }
    else
    {
%>
    <labkey:form method="post" layout="horizontal" onsubmit="LABKEY.setSubmit(true);">
        <%= options %>

        <labkey:input type="text" label="Server URL *" name="url" value="<%=settings != null ? settings.getUrl() : null%>" size="50" isRequired="true" onChange="LABKEY.setDirty(true);"/>
        <labkey:input type="text" label="User *" name="username" value="<%=settings != null ? settings.getUsername() : null%>"
                      isRequired="true" contextContent="Provide a valid user name for logging onto the Master Patient Index server" forceSmallContext="true" onChange="LABKEY.setDirty(true);"/>
        <labkey:input type="password" label="Password *" name="password"
                      isRequired="true" contextContent="Provide the password for the user name" forceSmallContext="true" onChange="LABKEY.setDirty(true);"/>

        <labkey:button text="save" submit="true"/>
        <labkey:button text="cancel" href="<%=urlProvider(AdminUrls.class).getAdminConsoleURL()%>" onclick="LABKEY.setSubmit(true);"/>
        <labkey:button text="test connection" submit="false" onclick="testConnection();"/>
    </labkey:form>
<%  }
%>
