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
