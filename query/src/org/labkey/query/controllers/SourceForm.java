/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.json.XML;
import org.labkey.api.action.HasBindParameters;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewForm;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;

import static org.labkey.api.action.BaseViewAction.springBindParameters;

public class SourceForm extends ViewForm implements HasBindParameters
{
    public String schemaName;
    public String queryName;
    public String ff_queryText;
    public String ff_metadataText;
    public QueryAction ff_redirect = QueryAction.sourceQuery;

    public SourceForm()
    {
    }

    public SourceForm(ViewContext context)
    {
        setViewContext(context);
    }

    public String getSchemaName()
    {
        return schemaName;
    }

    public SchemaKey getSchemaKey()
    {
        if (null == schemaName)
            return null;
        return SchemaKey.fromString(schemaName);
    }

    public void setSchemaName(String schemaName)
    {
        this.schemaName = schemaName;
    }

    public String getQueryName()
    {
        return queryName;
    }

    public void setQueryName(String queryName)
    {
        this.queryName = queryName;
    }

    public void setFf_queryText(String text)
    {
        ff_queryText = text;
    }

    public void setFf_metadataText(String text)
    {
        ff_metadataText = text;
    }
    public void setFf_redirect(String action)
    {
        ff_redirect = QueryAction.valueOf(action);
    }

    private static String DEFAULT_METADATA_TEXT =
            "<tables xmlns=\"http://labkey.org/data/xml\">\n" +
            "  <table tableName=\"%s\" tableDbType=\"NOT_IN_DB\">\n" +
            "    <columns>\n" +
            "    </columns>\n" +
            "  </table>\n" +
            "</tables>\n";

    public String getDefaultMetadataText()
    {
        return String.format(DEFAULT_METADATA_TEXT, XML.escape(getQueryName()));
    }

    @Override
    public @NotNull BindException bindParameters(PropertyValues pvs)
    {
        MutablePropertyValues mps = new MutablePropertyValues(pvs);
        // handle query.queryName
        if (pvs.contains("query.queryName"))
            mps.addPropertyValue("queryName",pvs.getPropertyValue("query.queryName").getValue());
        return springBindParameters(this, "form", mps);
    }
}
