/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.experiment.api;

import org.apache.commons.collections4.MultiValuedMap;
import org.labkey.api.data.AbstractTableInfo;
import org.labkey.api.data.CounterDefinition;
import org.labkey.api.data.TableCustomizer;
import org.labkey.api.data.TableInfo;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Java customizer for adding a CounterDefinition to a query
 *
 * Example metadata customizer
 * <pre>
 * <tables xmlns="http://labkey.org/data/xml">
 *   <table tableName="MySampSet" tableDbType="NOT_IN_DB">
 *     <javaCustomizer class="org.labkey.experiment.api.CountOfUniqueValueTableCustomizer">                 // Name of this customizer
 *         <properties>
 *             <property name="counterName">SampleCounter</property>                                        // Name of counter (required)
 *             <property name="counterType">org.labkey.api.data.UniqueValueCounterDefinition</property>     // Name of implementation of CounterDefinition (required)
 *
 *             <property name="pairedColumn">vessel</property>                                              // Zero or more pairedColumns (whose values are used to uniquify the DB sequence)
 * 	     	   <property name="pairedColumn">one</property>
 *             <property name="pairedColumn">two</property>
 *
 *             <property name="attachedColumn">intthree</property>                                          // One or more attached columns
 *         </properties>
 *     </javaCustomizer>
 *
 *   </table>
 * </tables>
 * </pre>
 */
public class CountOfUniqueValueTableCustomizer implements TableCustomizer
{
    private static final Logger _log = LogManager.getLogger(CountOfUniqueValueTableCustomizer.class);

    private final CounterDefinition _counterDefinition;

    public CountOfUniqueValueTableCustomizer(MultiValuedMap<String, Object> props)
    {
        Collection<Object> counterNames = props.get("counterName");
        String counterName = (counterNames.size() == 1 && counterNames.iterator().next() instanceof String) ?
                (String)counterNames.iterator().next() : null;

        Collection<Object> counterTypes = props.get("counterType");
        String counterType = (counterTypes.size() == 1 && counterTypes.iterator().next() instanceof String) ?
                (String)counterTypes.iterator().next() : null;

        List<String> pairedColumnNames = props.get("pairedColumn")
                .stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(Collectors.toList());


        Set<String> attachedColumnNames = props.get("attachedColumn")
                .stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .collect(Collectors.toSet());

        boolean valid = true;
        if (null == counterName)
        {
            _log.warn("Error in counter definition: CounterName required ");
            valid = false;
        }
        if (null == counterType)
        {
            _log.warn("Error in counter definition '" + counterName + "': CounterType required");
            valid = false;
        }

        if (!Collections.disjoint(pairedColumnNames, attachedColumnNames))
        {
            _log.warn("Error in counter definition '" + counterName + "': column included as both a paired and an attached column");
            valid = false;
        }

        CounterDefinition counterDefinition = null;
        if (valid)
        {
            try
            {
                Object objCounterDef = Class.forName(counterType).getDeclaredConstructor(String.class, List.class, Set.class).newInstance(counterName, pairedColumnNames, attachedColumnNames);
                if (objCounterDef instanceof CounterDefinition)
                    counterDefinition = (CounterDefinition)objCounterDef;
            }
            catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e)
            {
                _log.warn("Error instantiating counter '" + counterName + "' of type " + counterType + ": " + e.getMessage(), e);
            }
        }
        _counterDefinition = counterDefinition;
    }

    @Override
    public void customize(TableInfo tableInfo)
    {
        if (null != _counterDefinition && tableInfo instanceof AbstractTableInfo)
        {
            ((AbstractTableInfo)tableInfo).addCounterDefinition(_counterDefinition);
        }
    }

}
