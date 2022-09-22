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

import org.apache.commons.lang3.StringUtils;
import org.json.old.JSONArray;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AliasInsertHelper
{
    public static void handleInsertUpdate(Container container, User user, String lsid, TableInfo aliasMapTable, Object value)
    {
        // parse the alias value into separate names and alias rowIds
        Set<String> aliasNames = new HashSet<>();
        Set<Integer> aliasIds = new HashSet<>();
        parseValue(value, aliasNames, aliasIds);

        aliasNames.forEach(s -> {
            if ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\"")))
                throw new IllegalArgumentException("Alias values must not be surrounded by quote characters: " + s);
        });

        // ensure the alias exist collect the alias' rowId
        aliasIds.addAll(ensureAliases(user, aliasNames));

        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("LSID"), lsid);
        Table.delete(aliasMapTable, filter);
        insertMapEntries(container, user, aliasMapTable, aliasIds, lsid);
    }

    private static void parseValue(Object value, Set<String> aliasNames, Set<Integer> aliasIds)
    {
        if (value == null)
            return;

        if (value instanceof String[])
        {
            String[] aa = (String[]) value;
            for (String a : aa)
                parseValue(a, aliasNames, aliasIds); // recurse
        }
        else if (value instanceof JSONArray)
        {
            // LABKEY.Query.updateRows passes a JSONArray of individual alias names.
            for (Object o : ((JSONArray)value).toArray())
                parseValue(o.toString(), aliasNames, aliasIds); // recurse
        }
        else if (value instanceof Collection)
        {
            // Generic query insert form and Excel/tsv import passes an ArrayList with a single String element containing the comma-separated list of values: "abc,def"
            // LABKEY.Query.insertRows passes an ArrayList containing individual alias names.
            for (Object o : (Collection)value)
                parseValue(o, aliasNames, aliasIds); // recurse
        }
        else if (value instanceof String)
        {
            // Parse the single string element value submitted by the generic query insert and the tsv import forms.
            for (String s : splitAliases((String)value))
            {
                aliasNames.add(s);
            }
        }
        else if (value instanceof Integer)
        {
            aliasIds.add((Integer)value);
        }
        else
        {
            throw new IllegalArgumentException("Unsupported value for column 'Alias': " + value);
        }
    }

    public static Set<String> splitAliases(String aliases)
    {
        if (aliases == null)
            return Collections.emptySet();

        aliases = aliases.trim();
        if (aliases.length() == 0)
            return Collections.emptySet();

        // If the user entered a string formatted as a JSONArray, parse it now.
        if (aliases.startsWith("[") && aliases.endsWith("]"))
        {
            Set<String> parts = new HashSet<>();
            JSONArray a = new JSONArray(aliases);
            for (Object o : a.toArray())
            {
                if (o == null)
                    continue;
                String s = StringUtils.trim(String.valueOf(o));
                if (s.length() == 0)
                    continue;

                parts.add(s);
            }

            return parts;
        }

        if (aliases.contains(","))
        {
            // The user entered a comma-separated list of values
            return Arrays.stream(aliases.split(","))
                    .map(String::trim)
                    .filter(StringUtils::isNotEmpty)
                    .collect(Collectors.toSet());
        }
        else
        {
            return Collections.singleton(aliases);
        }
    }

    private static void insertMapEntries(Container container, User user, TableInfo aliasMapTable, Set<Integer> aliasIds, String lsid)
    {
        for (Integer aliasId : aliasIds)
        {
            Map<String, Object> row = CaseInsensitiveHashMap.of(
                    "container", container.getEntityId(),
                    "alias", aliasId,
                    "lsid", lsid
            );
            Table.insert(user, aliasMapTable, row);
        }
    }

    private static Collection<Integer> ensureAliases(User user, Set<String> aliasNames)
    {
        return ExperimentServiceImpl.get().ensureAliases(user, aliasNames);
    }

    public static Collection<String> getAliases(String lsid)
    {
        SQLFragment sql = new SQLFragment("SELECT AL.name FROM ").append(ExperimentService.get().getTinfoAlias(), "AL")
                .append(" JOIN ").append(ExperimentService.get().getTinfoMaterialAliasMap(), "MM")
                .append(" ON AL.rowId = MM.alias")
                .append(" WHERE MM.lsid = ?")
                .add(lsid);

        return new SqlSelector(ExperimentService.get().getSchema(), sql).getCollection(String.class);
    }
}
