<%
/*
 * Copyright (c) 2008 LabKey Corporation
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
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.api.view.ViewContext" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.pipeline.PipelineController" %>
<%@ page import="org.labkey.api.view.JspView" %>
<%@ page import="org.labkey.api.pipeline.PipelineService" %>
<%@ page import="java.security.cert.X509Certificate" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.pipeline.PipelineJobService" %>
<%@ page import="org.labkey.api.settings.AppProps" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    JspView<PipelineController.SetupBean> thisView = (JspView<PipelineController.SetupBean>) HttpView.currentView();
    PipelineController.SetupBean bean = thisView.getModelBean();

    if (bean.getConfirmMessage() != null)
    { %>
            <p class="labkey-message"><%= PageFlowUtil.filter(bean.getConfirmMessage()) %></p>
    <% }
%>

<labkey:errors />

<script type="text/javascript">
function toggleGlobusVisible()
{
    var newDisplay;
    if (document.getElementById('keyFileRow').style.display == 'none')
    {
        newDisplay = '';
    }
    else
    {
        newDisplay = 'none';
    }
    document.getElementById('keyPasswordRow').style.display = newDisplay;
    document.getElementById('keyFileRow').style.display = newDisplay;
    document.getElementById('certFileRow').style.display = newDisplay;
}
</script>
<form enctype="multipart/form-data" method="POST" action="setup.post">
    <table>
        <tr>
            <td class="labkey-form-label">Pipeline root directory:</td>
            <td><input type="text" name="path" size="70" value="<%= PageFlowUtil.filter(bean.getStrValue()) %>"></td>
        </tr><%
        if (PipelineService.get().isEnterprisePipeline() &&
                PipelineJobService.get().getGlobusClientProperties() != null)
        {
            boolean showConfig = true;
            if (bean.getGlobusKeyPair() != null)
            {
                showConfig = false; %>
                <tr>
                    <td class="labkey-form-label">Upload new Globus SSL configuration<labkey:helpPopup title="Use existing config">Check this box if you would like to replace the existing Globus configuration for this pipeline root.</labkey:helpPopup>:</td>
                    <td><input type="checkbox" name="uploadNewGlobusKeys" onclick="toggleGlobusVisible()">
                        <%
                            X509Certificate[] certs = bean.getGlobusKeyPair().getCertificates();
                            if (certs != null && certs.length > 0)
                            { %>
                                <br/>Current configuration expires <%= DateUtil.formatDate(certs[0].getNotAfter())%><%
                                if (certs[0].getSubjectX500Principal() != null)
                                {
                                    %>; Issued to <%= PageFlowUtil.filter(certs[0].getSubjectX500Principal().getName()) %>
                            <%  }
                            } %>
                    </td>
                </tr>
            <% }
            else
            {
                %><input type="hidden" name="uploadNewGlobusKeys" value="true" /><%
            } %>
            <tr id="keyFileRow" style="display: <%= showConfig ? "" : "none" %>">
                <td class="labkey-form-label">Globus SSL private key<labkey:helpPopup title="Globus SSL private key"><p>This is typically stored in a file with a .pem extension. It should be in a BASE64 encoded PKCS#8 file format, and may be encrypted.</p><p>If you open the file in a text editor, the first line should be:</p><pre>-----BEGIN RSA PRIVATE KEY-----</pre></labkey:helpPopup>:</td>
                <td><input type="file" size="70" name="keyFile"></td>
            </tr>
            <tr id="keyPasswordRow" style="display: <%= showConfig ? "" : "none" %>">
                <td class="labkey-form-label">Private key password<labkey:helpPopup title="Private key password">If your private key has been encrypted, you must specify the password so that it can be decrypted.</labkey:helpPopup>:</td>
                <td><input type="text" size="20" name="keyPassword" value=""></td>
            </tr>
            <tr id="certFileRow" style="display: <%= showConfig ? "" : "none" %>">
                <td class="labkey-form-label">Globus SSL certificate<labkey:helpPopup title="Globus SSL certificate"><p>This is typically stored in a file with a .pem extension. It should contain your BASE64 encoded X.509 certificatein a PKCS#8 file format.</p><p>If you open the file in a text editor, it should contain:</p><pre>-----BEGIN CERTIFICATE-----</pre></labkey:helpPopup>:</td>
                <td><input type="file" size="70" name="certFile"></td>
            </tr><%
        }

        if (AppProps.getInstance().isPerlPipelineEnabled())
        { %>
            <tr>
                <td class="labkey-form-label">Use Perl pipeline<labkey:helpPopup title="Use Perl pipeline">Check this box to override the X! Tandem and Mascot pipelines with Perl file scanning versions.  This requires extra setup of the file scanning service for each new root.</labkey:helpPopup>:</td>
                <td><input type="checkbox" name="perlPipeline" <%=bean.isPerlPipeline() ? " checked" : ""%>"></td>
            </tr><%
        }
        %>
        <tr><td colspan="3" style="font-size: 4px">&nbsp;</td></tr>
        <tr>
            <td colspan="2"><labkey:button text="Set"/></td>
        </tr>
    </table>
</form>
