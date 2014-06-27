/*
 * Copyright (c) 2012-2014 LabKey Corporation
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

package org.labkey.query.controllers;

import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.Container;
import org.labkey.data.xml.externalSchema.TemplateSchemaType;
import org.labkey.query.persist.LinkedSchemaDef;
import org.springframework.validation.Errors;

public class LinkedSchemaForm extends AbstractExternalSchemaForm<LinkedSchemaDef>
{
    public LinkedSchemaForm()
    {
        super(LinkedSchemaDef.class);
    }

    @Override
    public void validate(Errors errors)
    {
        super.validate(errors);

        if (!errors.hasErrors())
        {
            LinkedSchemaDef bean = getBean();
            Container sourceContainer = bean.lookupSourceContainer();
            Container targetContainer = bean.lookupContainer();

            TemplateSchemaType template = bean.lookupTemplate(sourceContainer);

            String sourceSchemaName = bean.getSourceSchemaName();
            if (sourceSchemaName == null && template != null)
                sourceSchemaName = template.getSourceSchemaName();

            if (sourceSchemaName != null && sourceSchemaName.length() > 50)
                errors.reject(SpringActionController.ERROR_MSG, "Source schema name must not be longer than 50 characters");

            // Disallow recursive linked schema
            if ((targetContainer == null || targetContainer == sourceContainer) && bean.getUserSchemaName().equals(sourceSchemaName))
            {
                errors.reject(SpringActionController.ERROR_MSG, "Recursive linked schema definition is disallowed");
            }
        }
    }
}
