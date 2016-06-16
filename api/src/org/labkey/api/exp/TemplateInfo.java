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
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.Converter;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.JdbcType;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;

/* class used for persisting info about a template */
public class TemplateInfo
{
    final String moduleName;
    final String templateGroupName;
    final String tableName;
    final BigDecimal createdModuleVersion;

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

    public BigDecimal getCreatedModuleVersion()
    {
        return createdModuleVersion;
    }

    public TemplateInfo(String module, String template, String table, BigDecimal moduleVersion)
    {
        this.moduleName = module;
        this.templateGroupName = template;
        this.tableName = table;
        this.createdModuleVersion = moduleVersion;

    }

    public TemplateInfo(String module, String template, String table, double moduleVersion)
    {
        this(module, template, table, new BigDecimal(BigInteger.valueOf((long) (10000 * moduleVersion)), 4));
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
            return new TemplateInfo(null,null,null,new BigDecimal(0.0));

        try
        {
            ObjectMapper om = new ObjectMapper();
            HashMap map = om.readValue(json, HashMap.class);
            BigDecimal createdModuleVersion = null;
            if (null != map.get("createdModuleVersion"))
                createdModuleVersion = _toDecimal(map.get("createdModuleVersion"));
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

        if (moduleName != null ? !moduleName.equals(that.moduleName) : that.moduleName != null) return false;
        if (templateGroupName != null ? !templateGroupName.equals(that.templateGroupName) : that.templateGroupName != null)
            return false;
        if (tableName != null ? !tableName.equals(that.tableName) : that.tableName != null) return false;
        return createdModuleVersion != null ? createdModuleVersion.equals(that.createdModuleVersion) : that.createdModuleVersion == null;

    }

    @Override
    public int hashCode()
    {
        int result = moduleName != null ? moduleName.hashCode() : 0;
        result = 31 * result + (templateGroupName != null ? templateGroupName.hashCode() : 0);
        result = 31 * result + (tableName != null ? tableName.hashCode() : 0);
        result = 31 * result + (createdModuleVersion != null ? createdModuleVersion.hashCode() : 0);
        return result;
    }

    @Override
    public String toString()
    {
        return toJSON();
    }

    private static BigDecimal _toDecimal(Object o)
    {
        return ((BigDecimal) JdbcType.DECIMAL.convert(o)).setScale(4,BigDecimal.ROUND_DOWN);
    }

    private static String _toString(Object o)
    {
        return null == o ? null : StringUtils.trimToNull(String.valueOf(o));
    }

//    static
//    {
//        ConvertUtils.register((type, value) -> {
//            if (null==value || type != TemplateInfo.class)
//                return null;
//            if (value instanceof TemplateInfo)
//                return value;
//            return TemplateInfo.fromJson(String.valueOf(value));
//        }, TemplateInfo.class);
//    }
}