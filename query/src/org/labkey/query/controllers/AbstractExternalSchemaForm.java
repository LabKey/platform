package org.labkey.query.controllers;

import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.BeanViewForm;
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
        if (null != IdentifierString.validateIdentifierString(bean.getUserSchemaName()))
            errors.reject(SpringActionController.ERROR_MSG, "Schema name should only contain alphanumeric characters and underscores");

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
            TemplateSchemaType template = bean.lookupTemplate(getContainer());
            if (template == null)
                errors.reject(SpringActionController.ERROR_MSG, "Template '" + bean.getSchemaTemplate() + "' not found in container");

            if (bean.getSourceSchemaName() != null)
                errors.reject(SpringActionController.ERROR_MSG, "Source schema name not allowed when using schema template");

            if (bean.getTables() != null)
                errors.reject(SpringActionController.ERROR_MSG, "Tables are not allowed when using a schema template");

            if (bean.getMetaData() != null)
                errors.reject(SpringActionController.ERROR_MSG, "Metadata not allowed when using a schema template");
        }
    }
}

