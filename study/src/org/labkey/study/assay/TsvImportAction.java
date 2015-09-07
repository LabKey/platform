/*
 * Copyright (c) 2011-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.study.assay;

import org.labkey.api.security.RequiresPermission;
import org.labkey.api.study.actions.ImportAction;
import org.labkey.api.study.permissions.DesignAssayPermission;
import org.springframework.web.servlet.ModelAndView;

import java.util.Map;

/**
 * User: klum
 * Date: Dec 30, 2010
 * Time: 12:58:09 PM
 */
@RequiresPermission(DesignAssayPermission.class)
public class TsvImportAction extends ImportAction
{
    @Override
    protected ModelAndView createGWTView(Map<String, String> properties)
    {
        properties.put("showInferredColumns", "true");

        return super.createGWTView(properties);
    }
}
