/*
 * Copyright (c) 2017-2019 LabKey Corporation
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
package org.labkey.api.util.element;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.element.Option.OptionBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class Select extends Input
{
    private final boolean _multiple;
    private final List<Option> _options;
    private final String _selected;

    private Select(SelectBuilder builder)
    {
        super(builder);
        _multiple = builder._multiple;
        _options = builder._options;
        _selected = builder._selected;
    }

    @Override
    public boolean isMultiple()
    {
        return _multiple;
    }

    public @NotNull List<Option> getOptions()
    {
        return _options;
    }

    @Override
    protected void doInput(StringBuilder sb)
    {
        sb.append("<select")
                .append(" name=\"").append(getName()).append("\"");

        if (StringUtils.isNotEmpty(getId()))
            sb.append(" id=\"").append(getId()).append("\"");
        if (StringUtils.isNotEmpty(getClassName()))
            sb.append(" class=\"").append(PageFlowUtil.filter(getClassName())).append("\"");
        if (StringUtils.isNotEmpty(getPlaceholder()))
            sb.append(" placeholder=\"").append(PageFlowUtil.filter(getPlaceholder())).append("\"");
        if (getSize() != null)
            sb.append(" size=\"").append(getSize()).append("\"");
        if (isMultiple())
            sb.append(" multiple");

        doStyles(sb);

        doInputEvents(sb);

        if (isDisabled())
            sb.append(" disabled");

        sb.append(">");

        doOptions(sb);

        sb.append("</select>");
    }

    private void doOptions(StringBuilder sb)
    {
        for (Option o : getOptions())
        {
            if (o != null)
            {
                boolean forceSelected = null != _selected && _selected.equals(o.getValue());
                sb.append(o.render(forceSelected));
            }
        }
    }

    public static class SelectBuilder extends InputBuilder<SelectBuilder>
    {
        private final List<Option> _options = new ArrayList<>();

        private boolean _multiple;
        // Alternative way to specify the selected option - useful when adding options as Strings or Objects
        private String _selected = null;

        public SelectBuilder()
        {
        }

        /**
         * Adds a single option, either a previously created Option object or a new Option where the key is set to the value.
         * @param option An Option, String, or Object
         * @return The SelectBuilder
         */
        public SelectBuilder addOption(Object option)
        {
            return addOptions(Stream.of(option));
        }

        /**
         * Adds a single Option built from an OptionBuilder, as a convenience.
         * @param  builder An OptionBuilder
         * @return The SelectBuilder
         */
        public SelectBuilder addOption(OptionBuilder builder)
        {
            return addOptions(Stream.of(builder.build()));
        }

        /**
         * Adds a single option with the specified label and value.
         * @param label The new option's label
         * @param value The new option's value
         * @return The SelectBuilder
         */
        public SelectBuilder addOption(String label, String value)
        {
            return addOption(new OptionBuilder(label, value));
        }

        /**
         * Adds multiple options to the &lt;select> elements by supplying a collection of objects. See {@link #addOptions(Stream)}
         * for more details.
         * @param options A collection of Options, OptionBuilders, Strings, or Objects
         * @return The SelectBuilder
         */
        public SelectBuilder addOptions(@NotNull Collection<?> options)
        {
            return addOptions(options.stream());
        }

        /**
         * Adds multiple options to the &lt;select> element by supplying a {@code Stream<Object>}. If the {@code Stream}
         * elements are {@code Option} objects then they are simply added. If the elements are {@code OptionBuilder}
         * objects then new {@code Option} objects are built and added. If the elements are {@code String} or
         * {@code Object} then new {@code Option} objects are created and added; in this case, the key and value are both
         * set to the String value of each object.
         *
         * @param options A {@code Stream} of Options, OptionBuilders, Strings, or Objects
         * @return This SelectBuilder
         */
        public SelectBuilder addOptions(@NotNull Stream<?> options)
        {
            options
                .filter(Objects::nonNull)
                .map(o -> {
                    if (o instanceof Option)
                    {
                        return (Option) o;
                    }
                    else if (o instanceof OptionBuilder)
                    {
                        return ((OptionBuilder) o).build();
                    }
                    else
                    {
                        String value = o.toString();
                        return new OptionBuilder(value, value).build();
                    }

                })
                .forEach(_options::add);

            return this;
        }

        public SelectBuilder addOptions(Map<?, String> options)
        {
            return addOptions(
                options.entrySet().stream()
                    .map(e->new OptionBuilder(e.getValue(), e.getKey()).build())
            );
        }

        public SelectBuilder multiple(boolean multiple)
        {
            _multiple = multiple;
            return this;
        }

        public SelectBuilder selected(@Nullable Object key)
        {
            _selected = null != key ? key.toString() : null;
            return this;
        }

        @Override
        public Select build()
        {
            return new Select(this);
        }
    }
}
