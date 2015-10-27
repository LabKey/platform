<%
/*
 * Copyright (c) 2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
%>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%@ page import="org.labkey.api.security.LoginUrls" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.saml.SamlController" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
        public LinkedHashSet<ClientDependency> getClientDependencies()
        {
            LinkedHashSet<ClientDependency> resources = new LinkedHashSet<>();
            resources.add(ClientDependency.fromPath("Ext4"));
//            resources.add(ClientDependency.fromPath("FileUploadField.js"));
            return resources;
        }
%>
<%
    JspView<SamlController.Config> me = (JspView<SamlController.Config>)HttpView.currentView();
    SamlController.Config bean = me.getModelBean();
%>
<labkey:form action="configure.post" id="configureSAML" method="post" enctype="multipart/form-data">
    <table>

        <%=formatMissedErrorsInTable("form", 2)%>

        <tr>
            <td class="labkey-form-label">Cert Upload Type  <%= PageFlowUtil.helpPopup("Cert Upload Type", "Select " +
                    "'Copy/Paste' to Copy & Paste the content of the " +
                    "X.509 certificate file. Select 'File' to upload a X.509 certificate file. " +
                    "A X.509 certificate file is typically a file with a .pem extension ")%></td>
            <td>
                <%--<input type="radio" name="certUploadType" value="paste" checked="checked">Cut/Paste--%>
            <input type="radio" name="certUploadType" value="paste" checked="checked" onchange="disableFileUpload()">Copy/Paste
                <input type="radio" name="certUploadType" value="file" onchange="enableFileUpload()">File
            </td>
        </tr>
        <tr id="file-field" style="display: none;">
            <td class="labkey-form-label">Upload Cert File</td>
            <td id="upload-field"></td>
        </tr>
        <tr id="certificate">
            <td class="labkey-form-label">Certificate</td>
            <td>
                <textarea id="textbox" rows=25 cols="120" style="width: 100%;" name="certData" wrap="on"><%=h(bean.getCertData())%></textarea>
            </td>
        </tr>
        <tr>
            <td class="labkey-form-label-nowrap">IdP SSO URL (Required)<%= PageFlowUtil.helpPopup("IdP SSO URL", "IdP SSO URL explanation here.")%></td>
            <td><input type="text" name="idPSsoUrl" size="105" value="<%=h(bean.getIdPSsoUrl())%>" onkeyup=Expand(this)></td>
        </tr>
        <tr>
            <td class="labkey-form-label-nowrap">Issuer URL (Optional)<%= PageFlowUtil.helpPopup("Issuer URL", "Issuer URL explanation here.")%></td>
            <td><input type="text" name="issuerUrl" size="105" value="<%=h(bean.getIssuerUrl())%>"></td>
        </tr>
        <tr>
            <td class="labkey-form-label-nowrap">Request Parameter (Optional)<%= PageFlowUtil.helpPopup("Request Parameter", "SAML provider specific Request Parameter Name.")%></td>
            <td><input type="text" name="requestParamName" size="105" value="<%=h(bean.getRequestParamName())%>"></td>
        </tr>
        <tr>
            <td class="labkey-form-label-nowrap">Response Parameter (Optional)<%= PageFlowUtil.helpPopup("Response Parameter", "SAML provider specific Response Parameter Name")%></td>
            <td><input type="text" name="responseParamName" size="105" value="<%=h(bean.getResponseParamName())%>"></td>
        </tr>
        <tr><td>&nbsp;</td></tr>
        <tr>
            <td colspan=2>
                <%= button("Save").submit(true) %>
                <%= button("Done").href(urlProvider(LoginUrls.class).getConfigureURL()) %>
            </td>
        </tr>
        <tr><td colspan="2">&nbsp;</td></tr>
    </table>
</labkey:form>

<script type="text/javascript">

    function Expand(obj){
        if (!obj.savesize)
            obj.savesize = obj.size;
        obj.size=Math.max(obj.savesize, obj.value.length);
    }

    function disableFileUpload() {
        Ext4.get('file-field').setDisplayed('none');
        Ext4.get('certificate').setDisplayed('table-row');
        Ext4.getCmp('upload-run-field').disable();
    }

    function enableFileUpload() {
        Ext4.get('file-field').setDisplayed('table-row');
        Ext4.get('certificate').setDisplayed('none');
        Ext4.getCmp('upload-run-field').enable();
    }

    function init() {
        var fileField = Ext4.create('Ext4.form.field.File', {
            buttonText: 'Upload X.509 Cert File',
            id: 'upload-run-field',
            name: 'file',
            buttonOnly: true,
            disabled    : true,
            scope       : this,
            listeners   : {
                scope : this,
                change : function(cmp, value){

                    var form = new Ext4.form.BasicForm(formPanel);

                    var processResponse = function(form, action) {
                        var fileContents = Ext4.decode(action.response.responseText);
                        document.forms['configureSAML'].elements['certData'].value = fileContents;
                    }

                    if(form.isValid()) {
                        form.submit({
                            url: LABKEY.ActionURL.buildURL('saml', 'parseCert'),
                            success: processResponse,
                            failure: processResponse
                        });
                    }
                }
            }
        });

        var formPanel = Ext4.create('Ext.form.Panel', {
            renderTo: 'upload-field',
            border: false,
            bodyStyle: 'background: transparent',
            width: 200,
            height: 27,
            items: [{xtype: 'hidden', name: 'X-LABKEY-CSRF', value: LABKEY.CSRF}, fileField]
        });
    }
    Ext4.onReady(init);

</script>

