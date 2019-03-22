/*
 * Copyright (c) 2015-2016 LabKey Corporation
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
package org.labkey.api.settings;

import org.labkey.api.data.Container;
import org.labkey.api.exp.property.Lookup;

import java.util.HashMap;
import java.util.Map;

public class ConceptURIProperties extends AbstractWriteableSettingsGroup
{
    private static final String GROUP_NAME = "ConceptURIMapping";

    private final Container _c;

    private ConceptURIProperties(Container c, boolean writable)
    {
        _c = c;
        if (writable)
            makeWriteable(c);
    }

    public static ConceptURIProperties getInstance(Container c, boolean writable)
    {
        return new ConceptURIProperties(c, writable);
    }

    @Override
    protected String getGroupName()
    {
        return GROUP_NAME;
    }

    @Override
    protected String getType()
    {
        return "concept URI lookup mappings";
    }

    public void clearLookup(String uri)
    {
        remove(uri);
    }

    public void setLookup(String uri, Lookup lookup)
    {
        storeStringValue(uri, lookup.toJSONString());
    }

    public Lookup getLookup(String uri)
    {
        String lookupStr = lookupStringValue(_c, uri, null);
        if (lookupStr == null)
            return null;

        Lookup lookup = new Lookup();
        lookup.fromJSONString(lookupStr);
        return lookup;
    }

    public void removeLookup(String uri)
    {
        remove(uri);
    }

    public static Map<String, Lookup> getMappings(Container c)
    {
        Map<String, Lookup> conceptURIMappings = new HashMap<>();

        ConceptURIProperties props = ConceptURIProperties.getInstance(c, false);
        for (Map.Entry<String, String> entry : props.getProperties(c).entrySet())
        {
            Lookup lookup = new Lookup();
            lookup.fromJSONString(entry.getValue());
            conceptURIMappings.put(entry.getKey(), lookup);
        }

        return conceptURIMappings;
    }

    public static void setLookup(Container c, String uri, Lookup lookup)
    {
        ConceptURIProperties props = ConceptURIProperties.getInstance(c, true);
        props.setLookup(uri, lookup);
        props.save();
    }

    public static Lookup getLookup(Container c, String uri)
    {
        ConceptURIProperties props = ConceptURIProperties.getInstance(c, false);
        return props.getLookup(uri);
    }

    public static void removeLookup(Container c, String uri)
    {
        ConceptURIProperties props = ConceptURIProperties.getInstance(c, true);
        props.removeLookup(uri);
        props.save();
    }
}
