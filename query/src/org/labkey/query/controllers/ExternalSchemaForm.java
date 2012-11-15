/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.BeanViewForm;

import org.labkey.api.util.IdentifierString;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.data.xml.TablesDocument;
import org.labkey.query.persist.ExternalSchemaDef;
import org.labkey.query.persist.QueryManager;
import org.springframework.validation.Errors;

public class ExternalSchemaForm extends BeanViewForm<ExternalSchemaDef>
{
    public ExternalSchemaForm()
    {
        super(ExternalSchemaDef.class, QueryManager.get().getTableInfoExternalSchema());
    }

    public void validate(Errors errors)
    {
        ExternalSchemaDef bean = getBean();
        if (null != IdentifierString.validateIdentifierString(bean.getUserSchemaName()))
            errors.reject(SpringActionController.ERROR_MSG, "Schema name should only contain alphanumeric characters and underscores");

        String metaData = bean.getMetaData();

        if (null != metaData)
        {
            try
            {
                XmlOptions options = XmlBeansUtil.getDefaultParseOptions();
                TablesDocument doc = TablesDocument.Factory.parse(metaData, options);
                XmlBeansUtil.validateXmlDocument(doc);
            }
            catch (XmlException e)
            {
                errors.reject("metaData", e.getMessage());    // TODO: Place error message above meta data box
            }
            catch (XmlValidationException e)
            {
                errors.reject(SpringActionController.ERROR_MSG, e.getDetails());
            }
        }
    }
}
