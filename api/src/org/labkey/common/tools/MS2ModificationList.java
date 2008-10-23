/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
package org.labkey.common.tools;

import java.util.*;

/**
 * User: adam
 * Date: May 8, 2006
 * Time: 2:17:53 PM
 */
public class MS2ModificationList extends ArrayList<MS2Modification>
{
    public MS2Modification get(String aa, double mass)
    {
        for (MS2Modification mod : this)
            if (aa.equals(mod.getAminoAcid()) && Math.abs(mass - mod.getMass()) < 0.01)
                return mod;

        return null;
    }


    private static final String _symbols = "'\"~#*@!$%&:0123456789";

    // Fills in unique modification symbols if they haven't been specified in the pepXML file.
    // This needs to be called explicitly after all modifications have been added.
    public void initializeSymbols()
    {
        Set<String> symbolSet = new LinkedHashSet<String>(30);

        for (int i=0; i<_symbols.length(); i++)
            symbolSet.add(_symbols.substring(i, i+1));

        // Eliminate all symbols that were specified in pepXML
        for (MS2Modification mod : this)
            if (mod.getVariable())
                symbolSet.remove(mod.getSymbol());

        Iterator<String> iter = symbolSet.iterator();

        // Assign symbols to all other variable modifications, in order specified above
        for (MS2Modification mod : this)
            if (mod.getVariable() && null == mod.getSymbol())
                mod.setSymbol(iter.next());
    }
}
