/*
 * Copyright (c) 2013-2017 LabKey Corporation
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
package org.labkey.api.assay.dilution.query;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.dilution.DilutionAssayProvider;
import org.labkey.api.assay.dilution.DilutionDataHandler;
import org.labkey.api.assay.dilution.SampleInfoMethod;
import org.labkey.api.data.Container;
import org.labkey.api.data.EnumTableInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.statistics.StatsService;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.security.User;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayProviderSchema;
import org.labkey.api.study.assay.AssayService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * User: klum
 * Date: 5/8/13
 */
public class DilutionProviderSchema extends AssayProviderSchema
{
    public static final String SAMPLE_PREPARATION_METHOD_TABLE_NAME = "SamplePreparationMethod";
    public static final String CURVE_FIT_METHOD_TABLE_NAME = "CurveFitMethod";
    public static final String RUN_ID_COLUMN_NAME = "RunId";

    private final String _schemaName;

    public DilutionProviderSchema(User user, Container container, AssayProvider provider, String schemaName, @Nullable Container targetStudy, boolean hidden)
    {
        super(user, container, provider, targetStudy);
        _schemaName = schemaName;
        _hidden = hidden;
    }

    public Set<String> getTableNames()
    {
        return getTableNames(false);
    }

    public Set<String> getVisibleTableNames()
    {
        return getTableNames(true);
    }

    protected Set<String> getTableNames(boolean visible)
    {
        Set<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        names.add(SAMPLE_PREPARATION_METHOD_TABLE_NAME);
        names.add(CURVE_FIT_METHOD_TABLE_NAME);

        return names;
    }

    public TableInfo createTable(String name)
    {
        if (SAMPLE_PREPARATION_METHOD_TABLE_NAME.equalsIgnoreCase(name))
        {
            EnumTableInfo<SampleInfoMethod> result = new EnumTableInfo<>(SampleInfoMethod.class, this,
                    "List of possible sample preparation methods for the " + getProvider().getResourceName() + " assay.", false);
            result.setPublicSchemaName(_schemaName);
            result.setPublicName(SAMPLE_PREPARATION_METHOD_TABLE_NAME);
            return result;
        }
        if (CURVE_FIT_METHOD_TABLE_NAME.equalsIgnoreCase(name))
        {
            EnumTableInfo<StatsService.CurveFitType> result = new EnumTableInfo<>(StatsService.CurveFitType.class, this, StatsService.CurveFitType::getLabel, false, "List of possible curve fitting methods for the " + getProvider().getResourceName() + " assay.");
            result.setPublicSchemaName(_schemaName);
            result.setPublicName(CURVE_FIT_METHOD_TABLE_NAME);
            return result;
        }
        return super.createTable(name);
    }

    private static final String[] _fixedRunDataProps = {DilutionDataHandler.DILUTION_INPUT_MATERIAL_DATA_PROPERTY, DilutionDataHandler.FIT_ERROR_PROPERTY, DilutionDataHandler.WELLGROUP_NAME_PROPERTY};
    private static final String[] _curveFitSuffixes = {"", DilutionDataHandler.PL4_SUFFIX, DilutionDataHandler.PL5_SUFFIX, DilutionDataHandler.POLY_SUFFIX};
    private static final String[] _aucPrefixes = {DilutionDataHandler.AUC_PREFIX, DilutionDataHandler.pAUC_PREFIX};
    private static final String[] _oorSuffixes = {"", DilutionDataHandler.OOR_SUFFIX};

    public static List<PropertyDescriptor> getExistingDataProperties(ExpProtocol protocol, Set<Double> cutoffValues)
    {
        DilutionAssayProvider provider = (DilutionAssayProvider)AssayService.get().getProvider(protocol);
        DilutionDataHandler dataHandler = provider.getDataHandler();
        List<PropertyDescriptor> propertyDescriptors = new ArrayList<>();
        Map<Integer, String> cutoffFormats = new HashMap<>();
        Container container = protocol.getContainer();
        for (String fixedProp : _fixedRunDataProps)
            propertyDescriptors.add(dataHandler.getPropertyDescriptor(container, protocol, fixedProp, cutoffFormats));

        for (String prefix : _aucPrefixes)
            for (String suffix : _curveFitSuffixes)
                propertyDescriptors.add(dataHandler.getPropertyDescriptor(container, protocol, prefix + suffix, cutoffFormats));

        for (Double cutoffValue : cutoffValues)
            for (String oorSuffix : _oorSuffixes)
            {
                propertyDescriptors.add(dataHandler.getPropertyDescriptor(container, protocol, DilutionDataHandler.POINT_IC_PREFIX + cutoffValue.intValue() + oorSuffix, cutoffFormats));
                for (String suffix : _curveFitSuffixes)
                    propertyDescriptors.add(dataHandler.getPropertyDescriptor(container, protocol, DilutionDataHandler.CURVE_IC_PREFIX + cutoffValue.intValue() + suffix + oorSuffix, cutoffFormats));
            }

        return propertyDescriptors;
    }
}
