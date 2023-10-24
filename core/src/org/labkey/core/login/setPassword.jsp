<%
/*
 * Copyright (c) 2005-2017 Fred Hutchinson Cancer Research Center
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
<%@ page import="org.labkey.api.collections.NamedObject" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.security.PasswordRule" %>
<%@ page import="org.labkey.api.util.HtmlString" %>
<%@ page import="org.labkey.api.util.URLHelper" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page import="org.labkey.core.login.DbLoginManager" %>
<%@ page import="org.labkey.core.login.LoginController" %>
<%@ page import="org.labkey.core.login.LoginController.SetPasswordBean" %>
<%@ page import="org.labkey.core.portal.ProjectController.HomeAction" %>
<%@ page import="org.labkey.core.portal.ProjectController.StartAction" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("login.css");
    }
%>
<%
    SetPasswordBean bean = ((JspView<SetPasswordBean>)HttpView.currentView()).getModelBean();
    String errors = formatMissedErrorsStr("form");
    int gaugeWidth = 350;
    int gaugeHeight = 40;
    String firstPasswordId = null;
    PasswordRule rule = DbLoginManager.getPasswordRule();
%>
<style>
    .labkey-button {
        width: 100px;
    }
</style>
<labkey:form method="POST" id="setPasswordForm" action="<%=urlFor(bean.action)%>" layout="horizontal" className="auth-form">
    <% if (bean.title != null) { %>
        <div class="auth-header"><%=h(bean.title)%></div>
    <% } %>

    <% if (!errors.isEmpty()) { %>
        <%=text(errors)%>
    <% } %>

    <div class="auth-form-body">
    <% if (!bean.unrecoverableError) { %>
        <p><%=h(bean.message)%></p>

        <%
            for (NamedObject input : bean.nonPasswordInputs)
            { %>
            <label for="<%=h(input.getObject().toString())%>">
                <%=h(input.getName())%>
            </label>
            <input
                type="text"
                id="<%=h(input.getObject().toString())%>"
                name="<%=h(input.getObject().toString())%>"
                value="<%=h(input.getDefaultValue())%>"
                class="input-block"
            />
        <%  }

            for (NamedObject input : bean.passwordInputs) {
                boolean firstPassword = LoginController.PASSWORD1_TEXT_FIELD_NAME.equals(input.getObject());
                HtmlString contextContent = firstPassword ? rule.getSummaryRuleHtml() : null;
        %>
            <p>
                <%=h(contextContent)%>
            </p>
            <label for="<%=h(input.getObject().toString())%>">
                <%=h(input.getName())%>
            </label>
            <input
                type="password"
                id="<%=h(input.getObject().toString())%>"
                name="<%=h(input.getObject().toString())%>"
                class="input-block"
                autocomplete="off"
            />
        <%
                if (rule.shouldShowPasswordGuidance() && firstPassword)
                {
                    firstPasswordId = input.getObject().toString();

        %>
            <canvas id="strengthGuidance" width="<%=gaugeWidth%>" height="<%=gaugeHeight%>">
                Your browser does not support the HTML5 canvas element.
            </canvas>
        <%
                }
            }
        %>

        <div>
        <% if (null != bean.email) { %>
            <labkey:input type="hidden" name="email" value="<%=bean.email%>"/>
        <% }

        if (null != bean.form.getVerification()) { %>
            <labkey:input type="hidden" name="verification" value="<%=bean.form.getVerification()%>"/>
        <% }

        if (null != bean.form.getMessage()) { %>
            <labkey:input type="hidden" name="message" value="<%=bean.form.getMessage()%>"/>
        <% }

        if (bean.form.getSkipProfile()) { %>
            <labkey:input type="hidden" name="skipProfile" value="1"/>
        <% }

        if (null != bean.form.getReturnURLHelper()) { %>
            <%=generateReturnUrlFormField(bean.form)%>
        <% } %>
        </div>

        <div class="auth-item">
            <%= button(bean.buttonText).submit(true).name("set") %>
            <%=unsafe(bean.cancellable ? button("Cancel").href(bean.form.getReturnURLHelper() != null ? bean.form.getReturnURLHelper() : new ActionURL(HomeAction.class, getContainer())).toString() : "")%>
        </div>
    <% }
       else
       {
           Container c = getContainer().isRoot() ? ContainerManager.getHomeContainer() : getContainer();
           URLHelper homeURL = bean.form.getReturnURLHelper() != null ? bean.form.getReturnURLHelper() : new ActionURL(StartAction.class, c);
    %>
            <div class="auth-item">
                <%= unsafe(button("Home").href(homeURL).toString()) %>
            </div>
    <% } %>
    </div>
</labkey:form>
<script type="application/javascript" <%=getScriptNonce()%>>
    let renderBarFunction;

    LABKEY.Utils.onReady(function() {
        const canvas = document.getElementById("strengthGuidance");

        if (canvas)
        {
            // Stashing original width & height helps on resize
            const canvasWidth = canvas.width;
            const canvasHeight = canvas.height;
            render(canvas, canvasWidth, canvasHeight);
            window.addEventListener("resize", function() { render(canvas, canvasWidth, canvasHeight); });
        }
    });

    function render(canvas, originalWidth, originalHeight)
    {
        const ctx = canvas.getContext("2d");

        if (ctx) {
            const ratio = increaseResolution(canvas, ctx, originalWidth, originalHeight);
            const borderWidth = drawOutline(canvas, ctx, ratio);
            const maxBarWidth = canvas.width - 2 * borderWidth;
            const barHeight = canvas.height - 2 * borderWidth;
            const centerX = canvas.width / 2;
            const centerY = canvas.height / 2;

            ctx.lineWidth = 1;
            ctx.font = 12 * ratio + "pt Sans-Serif"
            ctx.textAlign = "center";
            ctx.textBaseLine = "middle";

            // testBaseLine = middle sets the text too high, IMO. Adjust by half the size of the vertical bound.
            const metrics = ctx.measureText("H");
            const textHeightFix = (metrics.fontBoundingBoxAscent - metrics.fontBoundingBoxDescent) / 2 - 1;

            const firstPassword = document.getElementById(<%=q(firstPasswordId)%>);

            // Remove existing event, if present (resize case)
            if (renderBarFunction)
                firstPassword.removeEventListener('input', renderBarFunction);

            renderBarFunction = function() {
                LABKEY.Ajax.request({
                    url: LABKEY.ActionURL.buildURL("login", "getPasswordScore.api"),
                    method: 'POST',
                    params: {
                        password: firstPassword.value,
                        email: <%=q(bean.email)%>
                    },
                    success: function (response)
                    {
                        const responseText = LABKEY.Utils.decode(response.responseText);
                        if (responseText)
                        {
                            // Clear everything inside the outline
                            ctx.clearRect(borderWidth, borderWidth, maxBarWidth, barHeight);

                            // Render bar
                            const percent = Math.min(responseText.score / 90, 0.99999);
                            const colorIndex = Math.floor(percent * 3);
                            ctx.fillStyle = ["red", "yellow", "green"][colorIndex];
                            const barWidth = percent * maxBarWidth;
                            ctx.fillRect(borderWidth, borderWidth, barWidth, barHeight);

                            // Render text
                            ctx.fillStyle = 2 === colorIndex ? "white" : "black";
                            const textIndex = Math.floor(percent * 6);
                            const text = ["Very Weak", "Very Weak", "Weak", "Weak", "Strong", "Very Strong"][textIndex];
                            ctx.fillText(text, centerX, centerY + textHeightFix);
                            canvas.text = "Password Strength: " + text;
                        }
                    }
                });
            };

            firstPassword.addEventListener('input', renderBarFunction);
            renderBarFunction(); // Proactive render otherwise box will be empty after resizing until next change
        }
    }

    function drawOutline(canvas, ctx, ratio) {
        ctx.lineWidth = 3 * ratio;
        ctx.strokeRect(0, 0, canvas.width, canvas.height);
        return ctx.lineWidth / 2; // IDK why the border seems to be drawn at half of lineWidth
    }

    // Modifies a canvas element's resolution to match the native monitor resolution. This results in much clearer text.
    // See https://stackoverflow.com/questions/15661339/how-do-i-fix-blurry-text-in-my-html5-canvas
    function increaseResolution(canvas, ctx, originalWidth, originalHeight)
    {
        const dpr = window.devicePixelRatio || 1;
        const bsr = ctx.webkitBackingStorePixelRatio ||
            ctx.mozBackingStorePixelRatio ||
            ctx.msBackingStorePixelRatio ||
            ctx.oBackingStorePixelRatio ||
            ctx.backingStorePixelRatio || 1;

        const ratio = dpr / bsr;

        canvas.width = originalWidth * ratio;
        canvas.height = originalHeight * ratio;
        canvas.style.width = originalWidth + "px";
        canvas.style.height = originalHeight + "px";

        // Uncomment if you want to draw using the original width + height coordinates instead new high resolution coordinates
        //ctx.setTransform(ratio, 0, 0, ratio, 0, 0);

        return ratio;
    }
</script>