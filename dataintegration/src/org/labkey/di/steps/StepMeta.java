/*
 * Copyright (c) 2013-2016 LabKey Corporation
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

import org.apache.xmlbeans.XmlException;
import org.labkey.api.data.ParameterDescription;
import org.labkey.api.di.columnTransform.ColumnTransform;
import org.labkey.api.query.SchemaKey;
import org.labkey.etl.xml.TransformType;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: matthew
 * Date: 4/22/13
 * Time: 11:58 AM
 */
public interface StepMeta
{
    StepProvider getProvider();

    void setProvider(StepProvider provider);

    String getDescription();

    void setDescription(String description);

    String getId();

    void setId(String id);

    SchemaKey getSourceSchema();

    String getSourceQuery();

    SchemaKey getTargetSchema();

    String getTargetQuery();

    void parseConfig(TransformType transformXML) throws XmlException;

    boolean isUseSource();

    boolean isUseTarget();

    boolean isUseTargetTransaction();

    int getBatchSize();

    boolean isGating();

    boolean isSaveState();

    Map<String, List<ColumnTransform>> getColumnTransforms();

    Map<ParameterDescription, Object> getConstants();

    void putConstants(Map<ParameterDescription, Object> constants);

    void setEtlName(String etlName);

    Set<String> getAlternateKeys();
}
