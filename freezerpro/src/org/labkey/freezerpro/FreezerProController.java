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
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.Encryption;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.study.StudyUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.freezerpro.export.FreezerProExport;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

public class FreezerProController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(FreezerProController.class);
    public static final String FREEZER_PRO_PROPERTIES = "FreezerProConfigurationSettings";

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

                if (map.containsKey(FreezerProConfig.Options.url.name()))
                    form.setBaseServerUrl(map.get(FreezerProConfig.Options.url.name()));
                if (map.containsKey(FreezerProConfig.Options.user.name()))
                    form.setUsername(map.get(FreezerProConfig.Options.user.name()));
                if (map.containsKey(FreezerProConfig.Options.password.name()))
                    form.setPassword(map.get(FreezerProConfig.Options.password.name()));
                if (map.containsKey(FreezerProConfig.Options.enableReload.name()))
                    form.setEnableReload(Boolean.parseBoolean(map.get(FreezerProConfig.Options.enableReload.name())));
                if (map.containsKey(FreezerProConfig.Options.reloadInterval.name()))
                    form.setReloadInterval(Integer.parseInt(map.get(FreezerProConfig.Options.reloadInterval.name())));

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
        public void validateForm(FreezerProConfig form, Errors errors)
        {
            try
            {
                FreezerProExport export = new FreezerProExport(form, null, null);
                export.testConnection();
            }
            catch (ValidationException e)
            {
                errors.reject(ERROR_MSG, "Unable to connect with the specified configuration. The following error was returned : " + e.getMessage());
            }
        }

        @Override
        public ApiResponse execute(FreezerProConfig form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            PropertyManager.PropertyMap map = PropertyManager.getEncryptedStore().getWritableProperties(getContainer(), FREEZER_PRO_PROPERTIES, true);

            map.put(FreezerProConfig.Options.url.name(), form.getBaseServerUrl());
            map.put(FreezerProConfig.Options.user.name(), form.getUsername());
            map.put(FreezerProConfig.Options.password.name(), form.getPassword());
            map.put(FreezerProConfig.Options.enableReload.name(), String.valueOf(form.isEnableReload()));
            map.put(FreezerProConfig.Options.reloadInterval.name(), String.valueOf(form.getReloadInterval()));

            PropertyManager.getEncryptedStore().saveProperties(map);

            response.put("success", true);
            response.put("returnUrl", PageFlowUtil.urlProvider(StudyUrls.class).getManageStudyURL(getContainer()).getLocalURIString());

            return response;
        }
    }
}