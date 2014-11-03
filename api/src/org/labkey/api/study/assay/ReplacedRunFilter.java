package org.labkey.api.study.assay;

/*
 * Copyright (c) 2012-2014 LabKey Corporation
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

import org.junit.Assert;
import org.apache.commons.beanutils.ConvertUtils;
import org.jmock.Expectations;
import org.jmock.Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Test;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Special filter to show assay data that has or hasn't been replaced with a more recent copy of the data.
 *
 * User: jeckels
 * Date: 8/20/12
 */
public class ReplacedRunFilter
{
    private static ReplacedRunFilter DEFAULT_FILTER = new ReplacedRunFilter(Type.CURRENT_ONLY);

    public enum Type
    {
        CURRENT_ONLY("Current only")
        {
            @Override
            public void addCondition(SimpleFilter filter, FieldKey fieldKey)
            {
                filter.addCondition(fieldKey, false, CompareType.EQUAL);
            }
        },
        ALL("All")
        {
            @Override
            public void addCondition(SimpleFilter filter, FieldKey fieldKey)
            {
                filter.addCondition(fieldKey, null, CompareType.NONBLANK);
            }
        },
        REPLACED_ONLY("Replaced only")
        {
            @Override
            public void addCondition(SimpleFilter filter, FieldKey fieldKey)
            {
                filter.addCondition(fieldKey, true, CompareType.EQUAL);
            }
        };

        private String _title;

        Type(String title)
        {
            _title = title;
        }

        public String getTitle()
        {
            return _title;
        }

        public abstract void addCondition(SimpleFilter filter, FieldKey fieldKey);

        public void addToURL(ActionURL url, String dataRegionName, FieldKey fieldKey)
        {
            URLHelper helper = new URLHelper(false);
            SimpleFilter filter = new SimpleFilter();
            addCondition(filter, fieldKey);
            filter.applyToURL(helper, dataRegionName);
            assert helper.getParameters().size() == 1;

            // Remove any existing URL filters on the Replaced column
            String paramName = helper.getParameters().get(0).getKey();
            String prefix = paramName.split(SimpleFilter.SEPARATOR_CHAR)[0];
            for (Pair<String, String> entry : url.getParameters())
            {
                if (entry.getKey().startsWith(prefix))
                {
                    url.deleteParameter(entry.getKey());
                }
            }

            for (Pair<String, String> param : helper.getParameters())
            {
                url.replaceParameter(param.getKey(), param.getValue());
            }
        }
    }

    private Type _type;

    public ReplacedRunFilter(Type type)
    {
        if (type == null)
            throw new IllegalArgumentException("Replaced run filter type must not be null");

        _type = type;
    }

    public Type getType()
    {
        return _type;
    }

    public static ReplacedRunFilter getFromURL(QueryView view, FieldKey fieldKey)
    {
        ActionURL url = view.getViewContext().cloneActionURL();
        CustomView customView = view.getSettings().getCustomView(view.getViewContext(), view.getQueryDef());

        if (customView != null)
        {
            customView.applyFilterAndSortToURL(url, view.getDataRegionName());
        }

        for (Type type : Type.values())
        {
            SimpleFilter typeFilter = new SimpleFilter();

            type.addCondition(typeFilter, fieldKey);
            URLHelper helper = new URLHelper(false);
            typeFilter.applyToURL(helper, view.getDataRegionName());
            List<Pair<String, String>> parameters = helper.getParameters();
            assert parameters.size() == 1;
            String paramName = parameters.get(0).getKey();
            String paramValue = parameters.get(0).getValue();

            if (url.getParameterMap().containsKey(paramName))
            {
                Boolean b1 = (Boolean)ConvertUtils.convert(paramValue, Boolean.class);
                Boolean b2 = (Boolean)ConvertUtils.convert(url.getParameter(paramName), Boolean.class);
                if (Objects.equals(b1, b2))
                {
                    return new ReplacedRunFilter(type);
                }
            }
        }

        // Default to current only
        return DEFAULT_FILTER;
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ReplacedRunFilter that = (ReplacedRunFilter) o;

        if (_type != that._type) return false;

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = _type.hashCode();
        return result;
    }

    public void addFilterCondition(SimpleFilter filter, FieldKey fieldKey)
    {
        _type.addCondition(filter, fieldKey);
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testGenerateFilters()
        {
            Mockery context = new Mockery();
            context.setImposteriser(ClassImposteriser.INSTANCE);
            final SimpleFilter simpleFilter = context.mock(SimpleFilter.class);
            final FieldKey fieldKey = FieldKey.fromParts("Test");
            context.checking(new Expectations()
            {{
                oneOf(simpleFilter).addCondition(fieldKey, null, CompareType.NONBLANK);
                oneOf(simpleFilter).addCondition(fieldKey, true, CompareType.EQUAL);
                oneOf(simpleFilter).addCondition(fieldKey, false, CompareType.EQUAL);
            }});
            new ReplacedRunFilter(Type.ALL).addFilterCondition(simpleFilter, fieldKey);
            new ReplacedRunFilter(Type.REPLACED_ONLY).addFilterCondition(simpleFilter, fieldKey);
            new ReplacedRunFilter(Type.CURRENT_ONLY).addFilterCondition(simpleFilter, fieldKey);
        }

        @Test
        public void testAddToURL()
        {
            Mockery context = new Mockery();
            context.setImposteriser(ClassImposteriser.INSTANCE);
            final ActionURL url = context.mock(ActionURL.class);
            context.checking(new Expectations()
            {{
                allowing(url).getParameters();
                will(returnValue(Collections.emptyList()));
                oneOf(url).replaceParameter("DataRegionName.SomeColumn~isnonblank", null);
                oneOf(url).replaceParameter("DataRegionName.SomeColumn~eq", "false");
                oneOf(url).replaceParameter("DataRegionName.SomeColumn~eq", "true");
            }});
            FieldKey fieldKey = FieldKey.fromParts("SomeColumn");
            Type.ALL.addToURL(url, "DataRegionName", fieldKey);
            Type.CURRENT_ONLY.addToURL(url, "DataRegionName", fieldKey);
            Type.REPLACED_ONLY.addToURL(url, "DataRegionName", fieldKey);
        }

        @Test
        public void testParseURL() throws URISyntaxException
        {
            ReplacedRunFilter filter = ReplacedRunFilter.getFromURL(createMockView("AssayId Data.Run/Replaced~isnonblank", ""), FieldKey.fromParts("Run", "Replaced"));
            assertEquals(Type.ALL, filter.getType());

            filter = ReplacedRunFilter.getFromURL(createMockView("AssayId Data.Run/Replaced~eq", "false"), FieldKey.fromParts("Run", "Replaced"));
            assertEquals(Type.CURRENT_ONLY, filter.getType());

            filter = ReplacedRunFilter.getFromURL(createMockView("AssayId Data.Run/Replaced~eq", "0"), FieldKey.fromParts("Run", "Replaced"));
            assertEquals(Type.CURRENT_ONLY, filter.getType());

            filter = ReplacedRunFilter.getFromURL(createMockView("AssayId Data.Run/Replaced~eq", "1"), FieldKey.fromParts("Run", "Replaced"));
            assertEquals(Type.REPLACED_ONLY, filter.getType());

            filter = ReplacedRunFilter.getFromURL(createMockView("AssayId Data.Run/Replaced~eq", "true"), FieldKey.fromParts("Run", "Replaced"));
            assertEquals(Type.REPLACED_ONLY, filter.getType());

            filter = ReplacedRunFilter.getFromURL(createMockView("irrelevantParam", "true"), FieldKey.fromParts("Run", "Replaced"));
            assertEquals(Type.CURRENT_ONLY, filter.getType());

            filter = ReplacedRunFilter.getFromURL(createMockView("AssayId Data.Run/Replaced~eq", ""), FieldKey.fromParts("Run", "Replaced"));
            assertEquals(Type.CURRENT_ONLY, filter.getType());

        }

        private QueryView createMockView(final String paramName, final String paramValue)
        {
            Mockery context = new Mockery();
            context.setImposteriser(ClassImposteriser.INSTANCE);
            final QueryView view = context.mock(QueryView.class);
            final ViewContext viewContext = context.mock(ViewContext.class);
            final QuerySettings settings = context.mock(QuerySettings.class);
            context.checking(new Expectations()
            {{
                allowing(view).getDataRegionName();
                will(returnValue("AssayId Data"));
                allowing(view).getViewContext();
                will(returnValue(viewContext));
                allowing(viewContext).cloneActionURL();
                ActionURL actionURL = new ActionURL(false);
                will(returnValue(actionURL));
                actionURL.addParameter(paramName, paramValue);
                allowing(view).getSettings();
                will(returnValue(settings));
                allowing(settings);
                allowing(view);
            }});
            return view;
        }
    }
}
