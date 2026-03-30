package org.labkey.api.util;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;

public class TextAreaBuilder extends InputBuilder<TextAreaBuilder>
{
    private Integer _columns;
    private Integer _rows;

    public TextAreaBuilder columns(Integer columns)
    {
        _columns = columns;
        return this;
    }

    public TextAreaBuilder rows(Integer rows)
    {
        _rows = rows;
        return this;
    }

    @Override
    public TextArea build()
    {
        return new TextArea(this);
    }

    public static class TextArea extends Input
    {
        private final int _columns;
        private final int _rows;

        private TextArea(TextAreaBuilder builder)
        {
            super(builder);
            _columns = builder._columns == null ? -1 : builder._columns;
            _rows = builder._rows == null ? -1 : builder._rows;
        }

        public int getColumns()
        {
            return _columns;
        }

        public int getRows()
        {
            return _rows;
        }

        @Override
        protected void doInput(Appendable sb) throws IOException
        {
            var id = generateId("textarea");
            sb.append("<textarea id=\"").append(h(id)).append("\" name=\"").append(hname(getName())).append("\"");

            if (getColumns() != -1)
                sb.append(" cols=\"").append(h(getColumns())).append("\"");
            if (getRows() != -1)
                sb.append(" rows=\"").append(h(getRows())).append("\"");

            if (StringUtils.isNotEmpty(getId()))
                sb.append(" id=\"").append(h(getId())).append("\"");
            if (StringUtils.isNotEmpty(getClassName()))
                sb.append(" class=\"").append(h(getClassName())).append("\"");
            if (StringUtils.isNotEmpty(getPlaceholder()))
                sb.append(" placeholder=\"").append(h(getPlaceholder())).append("\"");

            doInputEvents(id);

            if (isDisabled())
                sb.append(" disabled");

            sb.append(">");

            if (!HtmlString.isEmpty(getValue()))
                sb.append(h(getValue()));

            sb.append("</textarea>");
        }
    }
}
