/*
 * Copyright (c) 2016 LabKey Corporation
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
package org.labkey.api.exp;

/**
 * Created by matthew on 6/13/2016.
 *
 * See DomainTemplateGroup and DomainTemplate (in Internal module)
 *
 * This class is used for persisting information about the usage of a template
 */

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.JdbcType;

import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;

/* class used for persisting info about a template */
public class TemplateInfo
{
    final String moduleName;
    final String templateGroupName;
    final String tableName;
    final double createdModuleVersion;

    public String getModuleName()
    {
        return moduleName;
    }

    public String getTemplateGroupName()
    {
        return templateGroupName;
    }

    public String getTableName()
    {
        return tableName;
    }

    public double getCreatedModuleVersion()
    {
        return createdModuleVersion;
    }

    public TemplateInfo(String module, String template, String table, Double moduleVersion)
    {
        this.moduleName = module;
        this.templateGroupName = template;
        this.tableName = table;
        this.createdModuleVersion = null == moduleVersion ? 0.0 : moduleVersion;
    }

    public String toJSON()
    {
        try
        {
            StringWriter sw = new StringWriter();
            JsonGenerator jsonGen = new JsonFactory().createGenerator(sw);
            jsonGen.setCodec(new ObjectMapper());
            jsonGen.writeObject(this);
            return sw.toString();
        }
        catch (IOException io)
        {
            throw new RuntimeException(io);
        }
    }

    public static TemplateInfo fromJson(String json)
    {
        if (StringUtils.isBlank(json))
            return new TemplateInfo(null, null, null, null);

        try
        {
            ObjectMapper om = new ObjectMapper();
            HashMap map = om.readValue(json, HashMap.class);
            Double createdModuleVersion = null;
            if (null != map.get("createdModuleVersion"))
                createdModuleVersion = (Double) JdbcType.DOUBLE.convert(map.get("createdModuleVersion"));
            TemplateInfo t1 = new TemplateInfo(
                    _toString(map.get("moduleName")),
                    _toString(map.get("templateGroupName")),
                    _toString(map.get("tableName")),
                    createdModuleVersion);
            return t1;
        }
        catch (IOException x)
        {
            throw new RuntimeException(x);
        }
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TemplateInfo that = (TemplateInfo) o;

        if (Double.compare(that.createdModuleVersion, createdModuleVersion) != 0) return false;
        if (moduleName != null ? !moduleName.equals(that.moduleName) : that.moduleName != null) return false;
        if (templateGroupName != null ? !templateGroupName.equals(that.templateGroupName) : that.templateGroupName != null)
            return false;
        return tableName != null ? tableName.equals(that.tableName) : that.tableName == null;
    }

    @Override
    public int hashCode()
    {
        int result;
        long temp;
        result = moduleName != null ? moduleName.hashCode() : 0;
        result = 31 * result + (templateGroupName != null ? templateGroupName.hashCode() : 0);
        result = 31 * result + (tableName != null ? tableName.hashCode() : 0);
        temp = Double.doubleToLongBits(createdModuleVersion);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }

    @Override
    public String toString()
    {
        return toJSON();
    }

    private static String _toString(Object o)
    {
        return null == o ? null : StringUtils.trimToNull(String.valueOf(o));
    }
}