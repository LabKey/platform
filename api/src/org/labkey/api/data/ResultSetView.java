/*
 * Copyright (c) 2017-2026 LabKey Corporation
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
package org.labkey.api.data;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.DOM.Renderable;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.LinkBuilder;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.WebPartView;
import org.labkey.api.writer.HtmlWriter;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.stream.IntStream;

import static org.labkey.api.util.DOM.TABLE;
import static org.labkey.api.util.DOM.TD;
import static org.labkey.api.util.DOM.TR;
import static org.labkey.api.util.DOM.cl;

public class ResultSetView extends WebPartView<Object>
{
    private final ResultSet _rs;
    private final int _linkColumn;
    private final String _unencodedLink;

    public ResultSetView(ResultSet rs, String title)
    {
        this(rs, title, null, null);
    }

    public ResultSetView(ResultSet rs, String title, @Nullable String linkColumnName, @Nullable ActionURL linkUrl)
    {
        super(title);
        _rs = rs;
        try
        {
            _linkColumn = linkColumnName != null ? rs.findColumn(linkColumnName) : 0;
        }
        catch (SQLException e)
        {
            throw new RuntimeException(e);
        }
        _unencodedLink = linkUrl != null ? linkUrl.toString() : null;
    }

    @Override
    protected void renderView(Object model, HtmlWriter out) throws Exception
    {
        TABLE(
            cl("labkey-data-region-legacy", "labkey-show-borders"),
            (Renderable) ret -> {
                try
                {
                    ResultSetMetaData md = _rs.getMetaData();
                    int columnCount = md.getColumnCount();

                    TR(
                        IntStream.rangeClosed(1, columnCount).mapToObj(i -> {
                            try
                            {
                                return TD(
                                    cl("labkey-column-header"),
                                    md.getColumnLabel(i)
                                );
                            }
                            catch (SQLException e)
                            {
                                throw new RuntimeSQLException(e);
                            }
                        })
                    ).appendTo(out);

                    long rowCount = 0;

                    while (_rs.next())
                    {
                        TR(
                            cl(rowCount % 2 == 0, "labkey-row", "labkey-alternate-row"),
                            (Renderable) ret2 -> {
                                try
                                {
                                    for (int i = 1; i <= columnCount; i++)
                                    {
                                        Object val = _rs.getObject(i);

                                        boolean createLink = null != _unencodedLink && _linkColumn == i && null != val && shouldLink(_rs);
                                        Renderable value = null == val ? HtmlString.NBSP : HtmlString.of(val);

                                        TD(
                                            createLink ? LinkBuilder.simpleLink(value).href(_unencodedLink + val) : value
                                        ).appendTo(out);
                                    }
                                }
                                catch (SQLException e)
                                {
                                    throw new RuntimeSQLException(e);
                                }
                                return ret2;
                            }
                        ).appendTo(out);

                        rowCount++;
                    }
                }
                catch (SQLException e)
                {
                    throw new RuntimeSQLException(e);
                }
                finally
                {
                    ResultSetUtil.close(_rs);
                }

                return ret;
            }
        ).appendTo(out);
    }

    protected boolean shouldLink(ResultSet rs) throws SQLException
    {
        return true;
    }
}
