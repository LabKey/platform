/*
 * Copyright (c) 2012-2017 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.BeanViewForm;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.util.IdentifierString;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.externalSchema.TemplateSchemaType;
import org.labkey.query.persist.AbstractExternalSchemaDef;
import org.labkey.query.persist.QueryManager;
import org.springframework.validation.Errors;

/**
 * User: kevink
 * Date: 12/19/12
 */
public abstract class AbstractExternalSchemaForm<T extends AbstractExternalSchemaDef> extends BeanViewForm<T>
{
    public AbstractExternalSchemaForm(Class<T> clazz)
    {
        super(clazz, QueryManager.get().getTableInfoExternalSchema());
    }

    public void validate(Errors errors)
    {
        AbstractExternalSchemaDef bean = getBean();

        if (StringUtils.isBlank(bean.getUserSchemaName()))
        {
            errors.reject(SpringActionController.ERROR_MSG, "Must provide a userSchemaName parameter");
        }
        else
        {
            if (null != IdentifierString.validateIdentifierString(bean.getUserSchemaName()))
                errors.reject(SpringActionController.ERROR_MSG, "Schema name should only contain alphanumeric characters and underscores");

            if (bean.getUserSchemaName().length() > 50)
                errors.reject(SpringActionController.ERROR_MSG, "Schema name must not be longer than 50 characters");

            if (bean.getSchemaTemplate() == null)
            {
                if (bean.getSourceSchemaName() == null)
                    errors.reject(SpringActionController.ERROR_MSG, "Source schema name is required");

                if (bean.getTables() == null)
                    errors.reject(SpringActionController.ERROR_MSG, "Schema tables is required");

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
            else
            {
                Container templateContainer = getContainer();
                String dataSource = bean.getDataSource();
                if (dataSource != null)
                    templateContainer = ContainerManager.getForId(dataSource);

                TemplateSchemaType template = bean.lookupTemplate(templateContainer);
                if (template == null)
                    errors.reject(SpringActionController.ERROR_MSG, "Template '" + bean.getSchemaTemplate() + "' not found in container");

                // We allow overriding the template sourceSchemaName, tables, and metaData.
//            if (bean.getSourceSchemaName() != null)
//                errors.reject(SpringActionController.ERROR_MSG, "Source schema name not allowed when using schema template");
//
//            if (bean.getTables() != null)
//                errors.reject(SpringActionController.ERROR_MSG, "Tables are not allowed when using a schema template");
//
//            if (bean.getMetaData() != null)
//                errors.reject(SpringActionController.ERROR_MSG, "Metadata not allowed when using a schema template");
            }
        }
    }
}

