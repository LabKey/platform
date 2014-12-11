/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.di.steps;

import org.apache.commons.lang3.StringUtils;
import org.apache.xmlbeans.XmlException;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.query.SchemaKey;
import org.labkey.di.VariableMap;
import org.labkey.di.pipeline.TransformManager;
import org.labkey.etl.xml.ProcedureParameterScopeType;
import org.labkey.etl.xml.ProcedureParameterType;
import org.labkey.etl.xml.SchemaProcedureType;
import org.labkey.etl.xml.TransformType;

import java.util.Map;

/**
 * User: tgaluhn
 * Date: 10/7/13
 */
public class StoredProcedureStepMeta extends StepMetaImpl
{
    private Map<String, ETLParameterInfo> xmlParamInfos = new CaseInsensitiveHashMap<>();
    private boolean useTransaction;

    final class ETLParameterInfo
    {
        private String value;
        private boolean override = false;
        private VariableMap.Scope scope = VariableMap.Scope.local;

        public String getValue()
        {
            return value;
        }

        public void setValue(String value)
        {
            this.value = value;
        }

        public boolean isOverride()
        {
            return override;
        }

        public void setOverride(boolean override)
        {
            this.override = override;
        }

        public VariableMap.Scope getScope()
        {
            return scope;
        }

        public void setScope(ProcedureParameterScopeType.Enum scope)
        {
            this.scope = VariableMap.Scope.valueOf(scope.toString());
        }
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Stored procedure ").append(getProcedureSchema().toString()).append(".").append(getProcedure());
        if (null != getSourceSchema())
        {
            sb.append(" filter val from ").append(getSourceSchema().toString()).append(".").append(getSourceQuery());
        }
        if (isUseTarget())
        {
            sb.append(" destination ").append(getFullTargetString());
        }
        return sb.toString();
    }

    @Override
    protected void parseWorkOptions(TransformType transformXML) throws XmlException
    {
        parseProcedure(transformXML.getProcedure());
        super.parseWorkOptions(transformXML);
    }

    private void parseProcedure(SchemaProcedureType procedure) throws XmlException
    {
        if (null != procedure)
        {
            setProcedureSchema(SchemaKey.fromString(procedure.getSchemaName()));
            setProcedure(procedure.getProcedureName());
            useTransaction = procedure.isSetUseTransaction();

            for (ProcedureParameterType xmlParam: procedure.getParameterArray())
            {
                String name = xmlParam.getName();
                if (StringUtils.startsWith(name, "@"))
                    name = StringUtils.substringAfter(name, "@");

                ETLParameterInfo parameterInfo = new ETLParameterInfo();
                parameterInfo.setValue(xmlParam.getValue());
                parameterInfo.setOverride(xmlParam.getOverride());
                parameterInfo.setScope(xmlParam.getScope());
                xmlParamInfos.put(name, parameterInfo); // TODO: Handle dupes?
            }
        }
        else throw new XmlException(TransformManager.INVALID_PROCEDURE);
    }

    public Map<String, ETLParameterInfo> getXmlParamInfos()
    {
        return xmlParamInfos;
    }

    public boolean isOverrideParam(String paramName)
    {
        return xmlParamInfos.containsKey(paramName) && xmlParamInfos.get(paramName).isOverride();
    }

    public boolean isGlobalParam(String paramName)
    {
        return xmlParamInfos.containsKey(paramName) && xmlParamInfos.get(paramName).getScope().equals(VariableMap.Scope.global);
    }

    public boolean isUseTransaction()
    {
        return useTransaction;
    }
}
