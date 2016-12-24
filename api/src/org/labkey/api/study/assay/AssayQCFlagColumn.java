/*
 * Copyright (c) 2011-2016 LabKey Corporation
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
package org.labkey.api.study.assay;

import org.apache.commons.beanutils.ConvertUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.DisplayColumnFactory;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.template.ClientDependency;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * User: jeckels
 * Date: Dec 12, 2011
 */
public class AssayQCFlagColumn extends ExprColumn
{
    private String _schemaName;
    private boolean _editable;

    public AssayQCFlagColumn(TableInfo parent, String schemaName, boolean editable)
    {
        super(parent, "QCFlags", createSQLFragment(parent.getSqlDialect(), "FlagType"), JdbcType.VARCHAR);
        setLabel("QC Flags");
        _schemaName = schemaName;
        _editable = editable;
    }

    @Override
    public DisplayColumnFactory getDisplayColumnFactory()
    {
        return new DisplayColumnFactory()
        {
            @Override
            public DisplayColumn createRenderer(ColumnInfo colInfo)
            {
                return new DataColumn(colInfo)
                {
                    @NotNull
                    @Override
                    public Set<ClientDependency> getClientDependencies()
                    {
                        return new LinkedHashSet<>(Arrays.asList(
                            ClientDependency.fromPath("clientapi/ext3"),
                            ClientDependency.fromPath("Experiment/QCFlagToggleWindow.js")
                        ));
                    }

                    @Override
                    public void renderGridCellContents(RenderContext ctx, Writer out) throws IOException
                    {
                        if (null != getValue(ctx))
                        {
                            String[] values = ((String)getValue(ctx)).split(",");
                            Boolean[] enabled = parseBooleans(values, ctx.get(getEnabledFieldKey(), String.class));
                            Integer runId = ctx.get(getRunRowIdFieldKey(), Integer.class);

                            // add onclick handler to call the QCFlag toggle window creation function
                            // users with update perm will be able to change enabled state and edit comment, others will only be able to read flag details
                            out.write("<a onclick=\"showQCFlagToggleWindow('" + _schemaName + "', " + runId + "," + _editable + ");\">");
                            out.write(getCollapsedQCFlagOutput(values, enabled));
                            out.write("</a>");
                        }
                        else
                        {
                            out.write("&nbsp;");
                        }
                    }

                    @Override
                    public void addQueryFieldKeys(Set<FieldKey> keys)
                    {
                        keys.add(getEnabledFieldKey());
                        keys.add(getRunRowIdFieldKey());
                    }

                    private FieldKey getEnabledFieldKey()
                    {
                        return new FieldKey(getBoundColumn().getFieldKey().getParent(), "QCFlagsEnabled");
                    }

                    private FieldKey getRunRowIdFieldKey()
                    {
                        return new FieldKey(getBoundColumn().getFieldKey().getParent(), "RowId");
                    }
                };
            }
        };
    }

    private Boolean[] parseBooleans(String[] values, String s)
    {
        Boolean[] enabled = new Boolean[values.length];
        if (s != null)
        {
            String[] enabledSplit = s.split(",");
            if (enabledSplit.length != values.length)
            {
                throw new IllegalStateException("Expected to get the same number of values for the FlagType and Enabled columns, but got " + values.length + " and " + enabledSplit.length);
            }
            for (int i = 0; i < enabledSplit.length; i++)
            {
                // The standard Boolean converter doesn't understand "f" or "t", which is what Postgres returns
                if ("f".equalsIgnoreCase(enabledSplit[i]))
                {
                    enabled[i] = false;
                }
                else if ("t".equalsIgnoreCase(enabledSplit[i]))
                {
                    enabled[i] = true;
                }
                else
                {
                    enabled[i] = (Boolean) ConvertUtils.convert(enabledSplit[i], Boolean.class);
                }
            }
        }
        return enabled;
    }

    /**
     * Collapse an array of values to the unique combinations of value and enabled state to display in QC Flag column
     * @param values String[] of values for QC Flags for a given run (ex. AUC, AUC, EC50, HMFI)
     * @param enabled Boolean[] of values for QC Flags for a given run indicating which are enabled (ex. t, f, t, t)
     * @return String with the collapsed grid cell contents to be written to the QC Flag column
     */
    public static String getCollapsedQCFlagOutput(String[] values, Boolean[] enabled)
    {
        if (values.length == 0 || values.length != enabled.length)
            return "";

        StringBuilder sb = new StringBuilder();
        // Keep track of which ones we've already rendered so we can eliminate dupes
        Set<Pair<String, Boolean>> alreadyRendered = new HashSet<>();
        String separator = "";
        for (int i = 0; i < values.length; i++)
        {
            if (alreadyRendered.add(new Pair<>(values[i], enabled[i])))
            {
                sb.append(separator);
                // Disabled flags are rendered with strike-through
                if (enabled[i] != null && !enabled[i].booleanValue())
                {
                    sb.append("<span style=\"text-decoration: line-through;\">");
                }
                sb.append(PageFlowUtil.filter(values[i]));
                if (enabled[i] != null && !enabled[i].booleanValue())
                {
                    sb.append("</span>");
                }
                separator = ", ";
            }
        }
        return sb.toString();
    }

    public static SQLFragment createSQLFragment(SqlDialect sqlDialect, String selectColumn)
    {
        SQLFragment innerSQL = new SQLFragment(" ");
        innerSQL.append("SELECT qcf." + selectColumn + " FROM ");
        innerSQL.append(ExperimentService.get().getTinfoAssayQCFlag(), "qcf");
        innerSQL.append(" WHERE qcf.RunId = " + STR_TABLE_ALIAS + ".RowId ORDER BY qcf.FlagType, qcf.Enabled, qcf.RowId");

        return sqlDialect.getSelectConcat(innerSQL, ",");
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testCollapseQCFlags() throws Exception
        {
            String expected;

            // single flag, enabled
            expected = "AUC";
            assertEquals("QC Flags not collapsed as expected", expected, getCollapsedQCFlagOutput(new String[]{"AUC"}, new Boolean[]{true}));
            // single flag, disabled
            expected = disabledFlag("AUC");
            assertEquals("QC Flags not collapsed as expected", expected, getCollapsedQCFlagOutput(new String[]{"AUC"}, new Boolean[]{false}));
            // multiple enabled flags, unique
            expected = "AUC, EC50, HMFI";
            assertEquals("QC Flags not collapsed as expected", expected, getCollapsedQCFlagOutput(new String[]{"AUC", "EC50", "HMFI"}, new Boolean[]{true, true, true}));
            // multiple enabled flags, duplicates
            expected = "AUC, HMFI";
            assertEquals("QC Flags not collapsed as expected", expected, getCollapsedQCFlagOutput(new String[]{"AUC", "AUC", "HMFI"}, new Boolean[]{true, true, true}));
            // multiple disabled flags, unique
            expected = disabledFlag("AUC") + ", " + disabledFlag("EC50");
            assertEquals("QC Flags not collapsed as expected", expected, getCollapsedQCFlagOutput(new String[]{"AUC", "EC50"}, new Boolean[]{false, false}));
            // multiple disabled flags, duplicates
            expected = disabledFlag("AUC") + ", " + disabledFlag("EC50");
            assertEquals("QC Flags not collapsed as expected", expected, getCollapsedQCFlagOutput(new String[]{"AUC", "EC50", "EC50"}, new Boolean[]{false, false, false}));
            // enabled and disabled flags, unique
            expected = "AUC, " + disabledFlag("EC50");
            assertEquals("QC Flags not collapsed as expected", expected, getCollapsedQCFlagOutput(new String[]{"AUC", "EC50"}, new Boolean[]{true, false}));
            // enabled and disabled flags, duplicates
            expected = "AUC, " + disabledFlag("AUC") + ", EC50, " + disabledFlag("EC50");
            assertEquals("QC Flags not collapsed as expected", expected, getCollapsedQCFlagOutput(new String[]{"AUC", "AUC", "AUC", "EC50", "EC50", "EC50"}, new Boolean[]{true, true, false, true, false, false}));
            // empty list of flags
            assertEquals("QC Flags not collapsed as expected", "", getCollapsedQCFlagOutput(new String[]{}, new Boolean[]{}));
            // unequal array length
            assertEquals("QC Flags not collapsed as expected", "", getCollapsedQCFlagOutput(new String[]{"AUC"}, new Boolean[]{true, true}));
        }

        private String disabledFlag(String value)
        {
            return "<span style=\"text-decoration: line-through;\">" + value + "</span>";
        }
    }
}
