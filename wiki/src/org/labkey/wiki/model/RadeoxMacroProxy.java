/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

package org.labkey.wiki.model;

import org.radeox.macro.BaseMacro;
import org.radeox.macro.parameter.MacroParameter;
import org.labkey.api.wiki.MacroProvider;
import org.labkey.api.view.HttpView;

import java.io.Writer;
import java.io.IOException;
import java.util.*;

/**
 * User: Mark Igra
 * Date: Jun 26, 2006
 * Time: 10:06:16 PM
 */
public class RadeoxMacroProxy extends BaseMacro
{
    MacroProvider provider;
    String name;

    public RadeoxMacroProxy(String name, MacroProvider provider)
    {
        this.name = name;
        this.provider = provider;
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public String getDescription()
    {
        return "See " + getName() + " macro documentation for description of this macro.";
    }

    @Override
    public String[] getParamDescription()
    {
        return new String[] {getDescription()};
    }

    @Override
    public void execute(Writer writer, MacroParameter params) throws IllegalArgumentException, IOException
    {
        HttpView view = provider.getView(params.get(0), new MacroParameterProxy(params), HttpView.currentContext());

        try
        {
            view.include(view, writer);
        }
        catch (IOException x)
        {
            throw x;
        }
        catch (IllegalArgumentException x)
        {
            throw x;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Turn wacky Radeox MacroParameter into just a map. Radeox map has
     * twice as many elements in it as necessary. Each element is indexed by
     * stringified numeric "0", "1", "2" indexes, plus strings for =. Here params
     * 0 and 1 are stripped (they are the provider and sub-macro names) and all
     * other parameters must be named.
     *
     * We do not (yet) support custom module macros with content...
     */
    private static class MacroParameterProxy implements Map<String, String>
    {
        private Map baseMap;

        private MacroParameterProxy(MacroParameter params)
        {
            this.baseMap = params.getParams();
        }

        public int size()
        {
            return (baseMap.size() - 2) / 2;
        }

        public boolean isEmpty()
        {
            return baseMap.size() == 2;
        }

        public boolean containsKey(Object key)
        {
            return !isNumericKey((String) key) && baseMap.containsKey(key);
        }

        public boolean containsValue(Object value)
        {
            return baseMap.containsValue(value);
        }

        public String get(Object key)
        {
            if (isNumericKey((String) key))
                return null;

            return (String) baseMap.get(key);
        }

        public String put(String key, String value)
        {
            throw new UnsupportedOperationException();
        }

        public String remove(Object key)
        {
            throw new UnsupportedOperationException();
        }

        public void putAll(Map<? extends String, ? extends String> t)
        {
            throw new UnsupportedOperationException();
        }

        public void clear()
        {
            throw new UnsupportedOperationException();
        }

        public Set<String> keySet()
        {
            HashSet<String> keys = new HashSet<>();

            Set<String> baseKeys = (Set<String>) baseMap.keySet();
            for (String key : baseKeys)
                if (!isNumericKey(key))
                    keys.add(key);

            return keys;
        }

        public Collection<String> values()
        {
            ArrayList<String> values = new ArrayList<>();
            Set<String> baseKeys = (Set<String>) baseMap.keySet();

            for (String key : baseKeys)
                if (!isNumericKey(key))
                    values.add((String) baseMap.get(key));

            return values;
        }

        public Set<Entry<String, String>> entrySet()
        {
            Set<Entry<String, String>> values = new HashSet<>();
            Set<Entry<String, String>> baseEntries = (Set<Entry<String, String>>) baseMap.entrySet();

            for (Entry<String, String> entry : baseEntries)
                if (!isNumericKey(entry.getKey()))
                    values.add(entry);

            return values;
        }

        private boolean isNumericKey(String str)
        {
            int length = str.length();
            //Assume no more than 99 numeric keys
            return length <= 2 && Character.isDigit(str.charAt(0)) && (length == 1 || Character.isDigit(str.charAt(1)));
        }
    }
}
