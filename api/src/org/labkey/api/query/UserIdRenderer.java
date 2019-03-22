/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

package org.labkey.api.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;

import java.util.Objects;

/**
 * Renders a column that contains values that point to core.UsersData.UserId rows, handling Guests and other special
 * users intelligently.
 */
public class UserIdRenderer extends DataColumn
{
    static public boolean isGuestUserId(Object value)
    {
        return Objects.equals(value, 0);
    }

    static public class GuestAsBlank extends DataColumn
    {
        public GuestAsBlank(ColumnInfo column)
        {
            super(column);
        }

        @Override @NotNull
        public String getFormattedValue(RenderContext ctx)
        {
            if (isGuestUserId(getBoundColumn().getValue(ctx)))
                return "&nbsp;";
            return super.getFormattedValue(ctx);
        }
    }

    public UserIdRenderer(ColumnInfo column)
    {
        super(column);
    }

    @Override @NotNull
    public String getFormattedValue(RenderContext ctx)
    {
        if (isGuestUserId(getBoundColumn().getValue(ctx)))
        {
            return "Guest";
        }
        return super.getFormattedValue(ctx);
    }
}
