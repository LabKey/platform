/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.experiment.samples;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;

import java.util.Map;

import static org.labkey.api.audit.AuditHandler.DELTA_PROVIDED_DATA_PREFIX;
import static org.labkey.api.audit.AuditHandler.PROVIDED_DATA_PREFIX;
import static org.labkey.api.audit.AuditHandler.USER_PROVIDED_DATA_PREFIX;
import static org.labkey.api.audit.SampleTimelineAuditEvent.SAMPLE_TIMELINE_EVENT_TYPE;
import static org.labkey.api.exp.query.ExpMaterialTable.Column.StoredAmount;

/** GH Issue 1640: flags metadata that has a provided amount recorded before the large-import fix, which may be wrong. */
public class SampleTimelineMetadataDisplayColumn extends DataColumn
{
    private static final String LEGACY_PROVIDED_AMOUNT_KEY = PROVIDED_DATA_PREFIX + StoredAmount.label();
    private static final String WARNING_STYLE = "background-color:#fcf8e3";
    private static final String WARNING_TITLE = "Provided Amount May Be Inaccurate";
    private static final String WARNING_MESSAGE = "This provided amount was recorded before a fix for insert or update operations of more than 1,000 rows and may be inaccurate. The stored sample amount is not affected.";

    public SampleTimelineMetadataDisplayColumn(ColumnInfo col)
    {
        super(col);
    }

    static boolean containsLegacyProvidedAmount(@Nullable Object metadata)
    {
        return metadata instanceof String str
                && AbstractAuditTypeProvider.decodeFromDataMap(str).containsKey(LEGACY_PROVIDED_AMOUNT_KEY);
    }

    private boolean hasLegacyProvidedAmount(RenderContext ctx)
    {
        return containsLegacyProvidedAmount(getValue(ctx));
    }

    @Override
    public @NotNull String getCssStyle(RenderContext ctx)
    {
        String style = super.getCssStyle(ctx);
        if (!hasLegacyProvidedAmount(ctx))
            return style;
        return StringUtils.isEmpty(style) ? WARNING_STYLE : style + ";" + WARNING_STYLE;
    }

    @Override
    protected String getHoverContent(RenderContext ctx)
    {
        return hasLegacyProvidedAmount(ctx) ? WARNING_MESSAGE : super.getHoverContent(ctx);
    }

    @Override
    protected String getHoverTitle(RenderContext ctx)
    {
        return hasLegacyProvidedAmount(ctx) ? WARNING_TITLE : super.getHoverTitle(ctx);
    }

    public static class TestCase extends Assert
    {
        private static String metadata(String key, String value)
        {
            return AbstractAuditTypeProvider.encodeForDataMap(Map.of(key, value, SAMPLE_TIMELINE_EVENT_TYPE, "INSERT"));
        }

        @Test
        public void testLegacyProvidedAmountIsFlagged()
        {
            assertTrue(containsLegacyProvidedAmount(metadata(PROVIDED_DATA_PREFIX + StoredAmount.label(), "2 L")));
        }

        @Test
        public void testOtherMetadataIsNotFlagged()
        {
            assertFalse(containsLegacyProvidedAmount(metadata(USER_PROVIDED_DATA_PREFIX + StoredAmount.label(), "2 L")));
            assertFalse(containsLegacyProvidedAmount(metadata(DELTA_PROVIDED_DATA_PREFIX + StoredAmount.label(), "-1 mL")));
            assertFalse(containsLegacyProvidedAmount(AbstractAuditTypeProvider.encodeForDataMap(Map.of(SAMPLE_TIMELINE_EVENT_TYPE, "INSERT"))));
            assertFalse(containsLegacyProvidedAmount(""));
            assertFalse(containsLegacyProvidedAmount(null));
        }
    }
}
