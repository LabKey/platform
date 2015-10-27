/*
 * Copyright (c) 2015 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
package org.labkey.saml;

import com.onelogin.saml.Certificate;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.AllowedDuringUpgrade;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.AuthenticationProviderConfigAuditTypeProvider;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.security.cert.CertificateException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: tgaluhn
 * Date: 1/19/2015
 */
public class SamlController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(SamlController.class);

    public SamlController() throws Exception
    {
        super();
        setActionResolver(_actionResolver);
    }

    public static ActionURL getValidateURL()
    {
        return new ActionURL(ValidateAction.class, ContainerManager.getRoot());
    }

    public static ActionURL getConfigureURL()
    {
        return new ActionURL(ConfigureAction.class, ContainerManager.getRoot());
    }

    @RequiresNoPermission
    @AllowedDuringUpgrade
    public class ValidateAction extends AuthenticationManager.BaseSsoValidateAction<ReturnUrlForm>
    {

        @NotNull
        @Override
        public String getProviderName()
        {
            return SamlProvider.NAME;
        }

        @Nullable
        @Override
        public ValidEmail validateAuthentication(ReturnUrlForm form, BindException errors) throws Exception
        {
            if (!AppProps.getInstance().isExperimentalFeatureEnabled(SamlModule.EXPERIMENTAL_SAML_SERVICE_PROVIDER))
                throw new IllegalStateException();

            String email = SamlManager.getUserFromSamlResponse(getViewContext().getRequest());
            if (StringUtils.isNotBlank(email))
                return new ValidEmail(email);
            else
                return null;
        }
    }

    @AdminConsoleAction
    @CSRF
    public class ConfigureAction extends FormViewAction<Config>
    {

        @Override
        public void validateCommand(Config config, Errors errors)
        {
            if(StringUtils.isBlank(config.getCertData()))
                errors.reject(ERROR_MSG, "X.509 Certificate File cannot be blank.");
            if(StringUtils.isBlank(config.getIdPSsoUrl()))
                errors.reject(ERROR_MSG, "IdP SSO URL cannot be blank.");

            Certificate cert = new Certificate();
            try
            {
                cert.loadCertificate(config.getParsedCertData(config.getCertData())); //check to see if its a valid certificate
            }
            catch (CertificateException e)
            {
                errors.reject(ERROR_MSG, "Invalid X.509 Certificate.");
                throw new IllegalArgumentException("Invalid X.509 Certificate.", e);
            }
        }

        @Override
        public ModelAndView getView(Config configForm, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/saml/configureSAML.jsp", configForm, errors);
        }

        @Override
        public boolean handlePost(Config config, BindException errors) throws Exception
        {
            List<String> dirtyProps = new ArrayList<>();

            if (!config.getCertData().equalsIgnoreCase(SamlManager.getCertificate()))
                dirtyProps.add(SamlManager.Key.Certificate.toString());
            if (!config.getIdPSsoUrl().equalsIgnoreCase(SamlManager.getIdPSsoUrl()))
                dirtyProps.add(SamlManager.Key.IdPSsoUrl.toString());
            if (config.getIssuerUrl() != null && !config.getIssuerUrl().equalsIgnoreCase(SamlManager.getIssuerUrl()))
                dirtyProps.add(SamlManager.Key.IssuerUrl.toString());
            if (config.getRequestParamName() != null && !config.getRequestParamName().equalsIgnoreCase(SamlManager.getSamlRequestParamName()))
                dirtyProps.add(SamlManager.Key.SamlRequestParamName.toString());
            if (config.getResponseParamName() != null && !config.getResponseParamName().equalsIgnoreCase(SamlManager.getSamlResponseParamName()))
                dirtyProps.add(SamlManager.Key.SamlResponseParamName.toString());

            if (!dirtyProps.isEmpty())
            {
                SamlManager.saveCertificate(config.getParsedCertData(config.getCertData()));
                SamlManager.saveProperties(config);
                StringBuilder sb = new StringBuilder();
                for (String prop : dirtyProps)
                {
                    if (sb.length() > 0)
                        sb.append(", ");
                    sb.append(prop);
                }
                AuthenticationProviderConfigAuditTypeProvider.AuthProviderConfigAuditEvent event = new AuthenticationProviderConfigAuditTypeProvider.AuthProviderConfigAuditEvent(
                        ContainerManager.getRoot().getId(), SamlProvider.NAME + " provider configuration was changed.");
                event.setChanges(sb.toString());
                AuditLogService.get().addEvent(getUser(), event);
            }

            return true;
        }

        @Override
        public URLHelper getSuccessURL(Config config)
        {
            return null;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    public static class Config extends ReturnUrlForm
    {
        private String certData = SamlManager.getCertificate();//get X.509 Certificate - required
        private String idPSsoUrl = SamlManager.getIdPSsoUrl();//get IdP SSO Url - required
        private String issuerUrl = SamlManager.getIssuerUrl(); //get Issuer Url - optional
        private String requestParamName = SamlManager.getSamlRequestParamName();//Saml Provider Specific Request Param - optional
        private String responseParamName = SamlManager.getSamlResponseParamName();//Saml Provider Specific Response Param - optional

        @SuppressWarnings("UnusedDeclaration")
        public void setCertData(String cert)
        {
            certData = cert;
        }

        public String getCertData()
        {
            String certDataWithHeaders = getCertDataWithHeaders(certData);

            if(certDataWithHeaders != null)
                return certDataWithHeaders;

            return certData;
        }

        public String getIdPSsoUrl()
        {
            return idPSsoUrl;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setIdPSsoUrl(String idPSsoUrl)
        {
            this.idPSsoUrl = idPSsoUrl;
        }

        public String getIssuerUrl()
        {
            return issuerUrl;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setIssuerUrl(String issuerUrl)
        {
            this.issuerUrl = issuerUrl;
        }

        public String getRequestParamName()
        {
            return requestParamName;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setRequestParamName(String requestParamName)
        {
            this.requestParamName = requestParamName;
        }

        public String getResponseParamName()
        {
            return responseParamName;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setResponseParamName(String responseParamName)
        {
            this.responseParamName = responseParamName;
        }

        public String getParsedCertData(String cert)
        {
            String newCertStr = new String (cert);
            String replacedStr = newCertStr.replace("-----BEGIN CERTIFICATE-----", "");
            replacedStr = replacedStr.replace("-----END CERTIFICATE-----", "");
            return replacedStr.trim();
        }

        private String getCertDataWithHeaders(String cert)
        {
            StringBuffer str = new StringBuffer();
            if(StringUtils.isNotEmpty(cert) && !cert.contains("-----BEGIN CERTIFICATE-----"))
            {
                str.append("-----BEGIN CERTIFICATE-----\n");
                str.append(cert);
                str.append("\n-----END CERTIFICATE-----");
                return str.toString();
            }
            return null;
        }
    }

    @CSRF
    @AdminConsoleAction
    public class ParseCertAction extends ApiAction<ParseCertForm>
    {
        @Override
        public Object execute(ParseCertForm form, BindException errors) throws Exception
        {

            Map<String, MultipartFile> files = Collections.emptyMap();

            final HttpServletRequest request = getViewContext().getRequest();
            if (request instanceof MultipartHttpServletRequest)
                files = (Map<String, MultipartFile>) ((MultipartHttpServletRequest) request).getFileMap();

            MultipartFile certFile = files.get("file");
            byte[] certBytes = null;
            if (certFile != null)
                certBytes = certFile.getBytes();

            return new String(certBytes, StringUtilsLabKey.DEFAULT_CHARSET);
        }
    }

    public static class ParseCertForm
    {
        String format = ".pem";

        public String getFormat()
        {
            return format;
        }

        public void setFormat(String format)
        {
            this.format = format;
        }

    }
}
