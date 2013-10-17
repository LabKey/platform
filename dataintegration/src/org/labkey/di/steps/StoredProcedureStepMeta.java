package org.labkey.di.steps;

import org.apache.xmlbeans.XmlException;
import org.labkey.api.query.SchemaKey;
import org.labkey.etl.xml.ProcedureParameterType;
import org.labkey.etl.xml.SchemaProcedureType;
import org.labkey.etl.xml.TransformType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * User: tgaluhn
 * Date: 10/7/13
 */
public class StoredProcedureStepMeta extends StepMetaImpl
{
    private Map<String, String> xmlParamValues = new HashMap<>();
    private Set<String> overrideParams = new HashSet<>();
    private boolean useTransaction;


    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Stored procedure " + getTargetSchema().toString() + "." + getTargetQuery());
        if (null != getSourceSchema())
        {
            sb.append(" filter val from " + getSourceSchema().toString() + "." + getSourceQuery());
        }
        return sb.toString();
    }

    @Override
    protected void parseWorkOptions(TransformType transformXML) throws XmlException
    {
        parseSource(transformXML);
        if (getSourceSchema() == null)
            setUseSource(false);
        parseProcedure(transformXML.getProcedure());
    }

    private void parseProcedure(SchemaProcedureType procedure) throws XmlException
    {
        if (null != procedure)
        {
            setTargetSchema(SchemaKey.fromString(procedure.getSchemaName()));
            setTargetQuery(procedure.getProcedureName());
            useTransaction = procedure.isSetUseTransaction();

            for (ProcedureParameterType xmlParam: procedure.getParameterArray())
            {
                xmlParamValues.put(xmlParam.getName(), xmlParam.getValue()); // TODO: Handle dupes?
                if (xmlParam.getOverride())
                {
                    overrideParams.add(xmlParam.getName());
                }
            }
        }
        else throw new XmlException(INVALID_PROCEDURE);

    }

    public Map<String, String> getXmlParamValues()
    {
        return xmlParamValues;
    }

    public boolean isOverrideParam(String paramName)
    {
        return overrideParams.contains(paramName);
    }

    public boolean isUseTransaction()
    {
        return useTransaction;
    }
}
