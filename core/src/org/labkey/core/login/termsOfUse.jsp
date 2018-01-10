<%
/*
 * Copyright (c) 2006-2017 LabKey Corporation
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
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.RedirectException" %>
<%@ page import="org.labkey.core.login.LoginController" %>
<%@ page import="org.labkey.core.login.LoginController.AgreeToTermsBean" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("login.css");
    }
%>
<%
    HttpView<AgreeToTermsBean> me = (HttpView<AgreeToTermsBean>) HttpView.currentView();
    AgreeToTermsBean bean = me.getModelBean();

    URLHelper returnURL = bean.form.getReturnURLHelper(AppProps.getInstance().getHomePageActionURL());
    String termsHtml = bean.termsOfUseHTML;

    // Redirect immediately if terms are blank or null
    if (StringUtils.isBlank(termsHtml))
        throw new RedirectException(returnURL);

    String formURL = buildURL(LoginController.AgreeToTermsAction.class);
%>
<style type="text/css">
    .labkey-error {
        margin-top: 15px;
    }
</style>
<div class="auth-form">
    <labkey:errors />
    <form name="login" method="POST" action="<%=h(formURL)%>" accept-charset="UTF-8"><labkey:csrf/>
        <div class="auth-form-body">
            <div class="auth-header auth-item">Terms of Use</div>
            <div class="toucontent auth-item" ><%=text(bean.termsOfUseHTML)%></div>
            <div class="auth-item">
                <input type="checkbox" name="approvedTermsOfUse" id="approvedTermsOfUse" class="auth-item">
                <label for="approvedTermsOfUse">I agree to these terms</label>
            </div>
            <input type="hidden" name="termsOfUseType" id="termsOfUseType" value="<%= h(bean.form.getTermsOfUseType())%>">

            <div class="auth-item">
                <%= button("Agree").submit(true) %>
            </div>
        </div>

        <%=generateReturnUrlFormField(returnURL)%>

        <input type="hidden" id="urlhash" name="urlhash">
    </form>
</div>
<script type="text/javascript">
    <% // Provide support for persisting the url hash through a login redirect %>
    (function() { if (window && window.location && window.location.hash) { var h = document.getElementById('urlhash'); if (h) { h.value = window.location.hash; } } })();
</script>
