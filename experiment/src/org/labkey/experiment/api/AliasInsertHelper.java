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

import org.json.JSONArray;
import org.junit.Test;
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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.labkey.api.util.StringUtilsLabKey.unquoteString;

public class AliasInsertHelper
{
    public static void handleInsertUpdate(Container container, User user, String lsid, TableInfo aliasMapTable, Object value)
    {
        // parse the alias value into separate names and alias rowIds
        Set<String> aliasNames = new HashSet<>();
        Set<Integer> aliasIds = new HashSet<>();
        parseValue(value, aliasNames, aliasIds);

        // ensure the alias exist collect the alias' rowId
        aliasIds.addAll(ensureAliases(user, aliasNames));

        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("LSID"), lsid);
        Table.delete(aliasMapTable, filter);
        insertMapEntries(container, user, aliasMapTable, aliasIds, lsid);
    }

    private static void cleanValueAndAdd(String input, Set<String> aliasNames)
    {
        if (input == null)
            return;

        input = input.trim();
        if (input.isEmpty())
            return;

        input = unquoteString(input).trim();

        if (!input.isEmpty())
            aliasNames.add(input);
    }

    private static void parseItemValue(Object value, Set<String> aliasNames, Set<Integer> aliasIds)
    {
        if (value instanceof String s)
            cleanValueAndAdd(s, aliasNames);
        else if (value instanceof Integer i)
            aliasIds.add(i);
        else
            throw new IllegalArgumentException("Unsupported item value for column 'Alias': " + value);
    }

    private static void parseValue(Object value, Set<String> aliasNames, Set<Integer> aliasIds)
    {
        if (value == null)
            return;

        if (value instanceof String[] aa)
        {
            for (String a : aa)
                cleanValueAndAdd(a, aliasNames);
        }
        else if (value instanceof JSONArray array)
        {
            // LABKEY.Query.updateRows passes a JSONArray of individual alias names.
            for (Object o : array.toList())
                parseItemValue(o, aliasNames, aliasIds);
        }
        else if (value instanceof Collection<?> col)
        {
            if (col.size() == 1)
            {
                // Generic query insert form and Excel/tsv import passes an ArrayList with a single String element
                // containing the comma-separated list of values: "abc,def"
                // LABKEY.Query.insertRows passes an ArrayList containing individual alias names.
                parseValue(col.iterator().next(), aliasNames, aliasIds);
            }
            else
            {
                for (Object o : col)
                    parseItemValue(o, aliasNames, aliasIds);
            }
        }
        else if (value instanceof String s)
        {
            // Parse the single string element value submitted by the generic query insert and the tsv import forms.
            aliasNames.addAll(splitAliases(s));
        }
        else if (value instanceof Integer i)
        {
            aliasIds.add(i);
        }
        else
        {
            throw new IllegalArgumentException("Unsupported value for column 'Alias': " + value);
        }
    }

    private static Set<String> splitAliases(String aliases)
    {
        if (aliases == null)
            return Collections.emptySet();

        aliases = aliases.trim();
        if (aliases.isEmpty())
            return Collections.emptySet();

        // Split around a comma only if there are no double quotes or if there is an even number of double quotes ahead
        // of the comma. From: https://www.baeldung.com/java-split-string-commas#1-string-split-method
        String[] tokens = aliases.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);

        Set<String> aliasNames = new HashSet<>();
        for (String token : tokens)
            cleanValueAndAdd(token, aliasNames);

        return aliasNames;
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

    public static class TestCase
    {
        private Set<String> parseValueWrapper(Object value)
        {
            Set<String> names = new HashSet<>();
            parseValue(value, names, new HashSet<>());
            return names;
        }

        @Test
        public void testParseValueString()
        {
            assertEquals("string", Set.of("bob"), parseValueWrapper("bob"));
            assertEquals("string with commas", Set.of("bob", "mike"), parseValueWrapper("bob,mike"));
            assertEquals("quoted string with commas", Set.of("bob,mike"), parseValueWrapper("\"bob,mike\""));
            assertEquals("string with semicolon", Set.of("bob;mike"), parseValueWrapper("bob;mike"));
            assertEquals("quoted string with semicolon", Set.of("bob;mike"), parseValueWrapper("\"bob;mike\""));
            assertEquals("json array-like string", Set.of("[bob", "mike]"), parseValueWrapper("[bob, mike]"));
            assertEquals("grouped quoted string with commas", Set.of("bob", "mike"), parseValueWrapper("\"bob\", \"mike\""));
            assertEquals("grouped quoted string with commas inside", Set.of("bo,b", "m,ike"), parseValueWrapper("\"bo,b \", \" m,ike\""));
            assertEquals("json array-like prefix partial string", Set.of("[bob"), parseValueWrapper("[bob,"));
            assertEquals("json array-like suffix partial string", Set.of("bob]"), parseValueWrapper(", bob]"));
            assertEquals("json array-like string empty", Set.of("[]"), parseValueWrapper("[]"));
            assertEquals("unbalanced quotes", Set.of("b\"ob", "mi,ke", "steve"), parseValueWrapper("b\"ob,\"mi,ke\",\"steve\""));
        }

        @Test
        public void testParseValueList()
        {
            assertEquals("list with string", Set.of("bob"), parseValueWrapper(List.of("bob")));
            assertEquals("list with single item", Set.of("bob,mike", "steve"), parseValueWrapper(List.of("\"bob,mike\", steve")));
            assertEquals("list with multiple items", Set.of("\"bob\",mike", "steve"), parseValueWrapper(List.of("\"bob\",mike", "steve")));
        }

        @Test
        public void testParseValueStringArray()
        {
            assertEquals("array with string", Set.of("bob"), parseValueWrapper(new String[] { "bob" }));
            assertEquals("array with single item", Set.of("\"bob,mike\", steve"), parseValueWrapper(new String[] { "\"bob,mike\", steve" }));
            assertEquals("list with multiple items", Set.of("\"bob\",mike", "steve"), parseValueWrapper(new String[] { "\"bob\",mike", "steve", "", "\"\"" }));
        }

        @Test
        public void testParseValueJSONArray()
        {
            assertEquals("empty JSONArray", Collections.emptySet(), parseValueWrapper(new JSONArray()));
            assertEquals("JSONArray with single item", Set.of("bob"), parseValueWrapper(new JSONArray("[bob]")));
            assertEquals("JSONArray with multiple items", Set.of("bob", "mike"), parseValueWrapper(new JSONArray("[bob, mike]")));
            assertEquals("JSONArray with multiple items and commas", Set.of("bob", "mike", "ste,ve"), parseValueWrapper(new JSONArray("[bob, mike, \"ste,ve\"]")));
        }
    }
}
