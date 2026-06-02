/*
 * Copyright (c) 2020-2026 LabKey Corporation
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
package org.labkey.api.view;

import org.labkey.api.util.Pair;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.labkey.api.view.NavTree.MENU_SEPARATOR;

public class DeveloperMenuNavTrees
{
    /** Groupings for menu items, ordered based on their desired sequence (roughly in terms of usage) */
    public enum Section
    {
        query,
        tools,
        monitoring,
        referenceDocs,
        misc
    }

    private final List<Pair<Section, NavTree>> _items = new ArrayList<>();

    public void add(Section section, NavTree navTree)
    {
        _items.add(new Pair<>(section, navTree));
    }

    public List<NavTree> toNavTrees()
    {
        // First sort
        _items.sort(Comparator.comparing((Pair<Section, NavTree> o) -> o.first).thenComparing(o -> o.second.getText()));

        // Then assemble a list divided by section
        List<NavTree> result = new ArrayList<>();
        Section lastSection = null;
        for (Pair<Section, NavTree> item : _items)
        {
            if (lastSection != null && item.first != lastSection)
            {
                result.add(MENU_SEPARATOR);
            }
            lastSection = item.first;
            result.add(item.second);
        }
        return result;
    }
}
