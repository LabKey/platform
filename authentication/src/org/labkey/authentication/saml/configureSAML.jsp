<%
/*
 * Copyright (c) 2015 LabKey Corporation
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
<%@ page import="org.labkey.api.security.LoginUrls" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.view.template.ClientDependency" %>
<%@ page import="org.labkey.authentication.saml.SamlController" %>
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
<labkey:form action="configure.post" id="configureSAML" method="post">
    <table>

        <%=formatMissedErrorsInTable("form", 2)%>

        <tr>
            <%--<td class="labkey-form-label-nowrap">X.509 Certificate (Required)<%= PageFlowUtil.helpPopup("X.509 Certificate",--%>
                    <%--"This is typically stored in a file with a .pem extension. It should contain your BASE64 encoded " +--%>
                            <%--"X.509 certificate in a PKCS#8 file format. If you open the file in a text editor, it should " +--%>
                            <%--"contain:-----BEGIN CERTIFICATE-----.")%></td>--%>
            <%--<td><input type="file" size="70" name="certFile" value="<%=h(bean.getCertFile())%>"></td>--%>
            <%--<td><textarea id="certTextArea" name="certificate" rows="25" cols="120" value="<%=h(bean.getCertificate())%>" onkeyup=Expand(this)></textarea></td>--%>
            <td class="labkey-form-label">Cert Upload Type</td>
            <td>
                <%--<input type="radio" name="certUploadType" value="paste" checked="checked">Cut/Paste--%>
            <input type="radio" name="certUploadType" value="paste" checked="checked" onchange="disableFileUpload()">Cut/Paste
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
                <textarea id="textbox" rows=25 cols="120" style="width: 100%;" name="data" wrap="off"><%=h(bean.getCertificate())%></textarea>
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
                <%= button("Cancel").href(urlProvider(LoginUrls.class).getConfigureURL()) %>
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
        Ext4.create('Ext4.form.field.File', {
            buttonText: 'Upload X.509 Cert File',
            id: 'upload-run-field',
            width: 175,
            renderTo: 'upload-field',
            buttonOnly: true,
            disabled    : true,
            scope       : this,
            listeners   : {
                scope : this,
                change : function(cmp, value){
                    console.log("cmp", cmp);
                    console.log("value", value);
//                this.southPanel.getComponent('specimenUploadButton').enable();
                    var form = new Ext4.form.BasicForm(Ext4.get('configureSAML'), {
                        url: LABKEY.ActionURL.buildURL('saml', 'parseCert'),
                        fileUpload: true
                    });

                    var processResponse = function(form, action)
                    {
                        var fileContents = Ext4.decode(action.response.responseText);
                    }

                    if(form.isValid()) {
                        form.submit({
                            success: function(fp, o) {
                                Ext4.Msg.alert('Success', 'Your Certificate "' + o.result.file + '" has been uploaded.');
                                processResponse;
                            },
                            failure: function(fp, o) {
                                Ext4.Msg.alert('Failed', 'Failed to upload file.');
                            }
                        });
                    }
             }
            }
        });
    }
    Ext4.onReady(init);

</script>

