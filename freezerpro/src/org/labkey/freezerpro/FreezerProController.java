/*
 * Copyright (c) 2013 LabKey Corporation
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

package org.labkey.freezerpro;

import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.security.Encryption;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.SpecimenTransform;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

public class FreezerProController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(FreezerProController.class);
    private static final String FREEZER_PRO_PROPERTIES = "FreezerProConfigurationSettings";

    public FreezerProController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class ConfigureAction extends SimpleViewAction<FreezerProConfig>
    {
        @Override
        public ModelAndView getView(FreezerProConfig form, BindException errors) throws Exception
        {
            if (Encryption.isMasterEncryptionPassPhraseSpecified())
            {
                Map<String, String> map = PropertyManager.getEncryptedStore().getProperties(getContainer(), FREEZER_PRO_PROPERTIES);

                if (map.containsKey("url"))
                    form.setBaseServerUrl(map.get("url"));
                if (map.containsKey("user"))
                    form.setUsername(map.get("user"));
                if (map.containsKey("password"))
                    form.setPassword(map.get("password"));
                if (map.containsKey("enableReload"))
                    form.setEnableReload(Boolean.parseBoolean(map.get("enableReload")));
                if (map.containsKey("reloadInterval"))
                    form.setReloadInterval(Integer.parseInt(map.get("reloadInterval")));

                return new JspView<>("/org/labkey/freezerpro/view/configure.jsp", form, errors);
            }
            else
            {
                return new HtmlView("<span class='labkey-error'>Unable to save or retrieve configuration information, MasterEncryptionKey has not been specified in labkey.xml.</span>");
            }
        }

       @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("FreezerPro Configuration");
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class SaveFreezerProConfig extends ApiAction<FreezerProConfig>
    {
        @Override
        public ApiResponse execute(FreezerProConfig form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            PropertyManager.PropertyMap map = PropertyManager.getEncryptedStore().getWritableProperties(getContainer(), FREEZER_PRO_PROPERTIES, true);

            map.put("url", form.getBaseServerUrl());
            map.put("user", form.getUsername());
            map.put("password", form.getPassword());
            map.put("enableReload", String.valueOf(form.isEnableReload()));
            map.put("reloadInterval", String.valueOf(form.getReloadInterval()));

            PropertyManager.getEncryptedStore().saveProperties(map);

            response.put("success", true);
            response.put("returnUrl", PageFlowUtil.urlProvider(StudyUrls.class).getManageStudyURL(getContainer()).getLocalURIString());

            return response;
        }
    }

    public static class FreezerProConfig implements SpecimenTransform.ExternalImportConfig
    {
        private String _baseServerUrl;
        private String _username;
        private String _password;
        private int _reloadInterval;
        private boolean _enableReload;
        private boolean _importUserFields;

        public String getBaseServerUrl()
        {
            return _baseServerUrl;
        }

        public void setBaseServerUrl(String baseServerUrl)
        {
            _baseServerUrl = baseServerUrl;
        }

        public String getUsername()
        {
            return _username;
        }

        public void setUsername(String username)
        {
            _username = username;
        }

        public String getPassword()
        {
            return _password;
        }

        public void setPassword(String password)
        {
            _password = password;
        }

        public int getReloadInterval()
        {
            return _reloadInterval;
        }

        public void setReloadInterval(int reloadInterval)
        {
            _reloadInterval = reloadInterval;
        }

        public boolean isEnableReload()
        {
            return _enableReload;
        }

        public void setEnableReload(boolean enableReload)
        {
            _enableReload = enableReload;
        }

        public boolean isImportUserFields()
        {
            return _importUserFields;
        }

        public void setImportUserFields(boolean importUserFields)
        {
            _importUserFields = importUserFields;
        }
    }
}