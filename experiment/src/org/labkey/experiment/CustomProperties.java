package org.labkey.experiment;

import org.labkey.api.data.Container;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.view.ViewContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Created by adam on 9/4/2016.
 */
public class CustomProperties
{
    public static void iterate(Container c, Collection<ObjectProperty> properties, Map<String, CustomPropertyRenderer> rendererMap, PropertyHandler handler)
    {
        List<List<ObjectProperty>> stack = new ArrayList<>();
        stack.add(new ArrayList<>(properties));
        List<Integer> indices = new ArrayList<>();
        indices.add(0);

        while (!stack.isEmpty())
        {
            List<ObjectProperty> values = stack.get(stack.size() - 1);
            int currentIndex = indices.get(indices.size() - 1);
            indices.set(indices.size() - 1, currentIndex + 1);

            if (currentIndex == values.size())
            {
                stack.remove(stack.size() - 1);
                indices.remove(indices.size() - 1);
            }
            else
            {
                ObjectProperty value = values.get(currentIndex);
                CustomPropertyRenderer renderer = rendererMap.get(value.getPropertyURI());
                if (renderer.shouldRender(value, values))
                {
                    handler.handle(stack.size() - 1, renderer.getDescription(value, values), renderer.getValue(value, values, c));
                }
                if (value.retrieveChildProperties().size() > 0)
                {
                    stack.add(new ArrayList<>(value.retrieveChildProperties().values()));
                    indices.add(0);
                }
            }
        }
    }

    public interface PropertyHandler
    {
        void handle(int indent, String description, String value);
    }
}
