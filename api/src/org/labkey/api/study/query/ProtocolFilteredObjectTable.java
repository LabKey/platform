/*
 * Copyright (c) 2009-2014 LabKey Corporation
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
package org.labkey.api.study.query;

import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.query.FilteredTable;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.study.assay.AssaySchema;
/*
 * User: brittp
 * Date: Mar 15, 2009
 * Time: 10:55:45 AM
 */

public class ProtocolFilteredObjectTable extends FilteredTable<AssaySchema>
{
    private String _protocolLsid;
    public ProtocolFilteredObjectTable(AssaySchema schema, String protocolLsid)
    {
        super(OntologyManager.getTinfoObject(), schema);
        wrapAllColumns(true);
        _protocolLsid = protocolLsid;
    }

    @Override @NotNull
    public SQLFragment getFromSQL(String alias)
    {
        SQLFragment fromSQL = new SQLFragment("(");
        fromSQL.append("SELECT o.*, d.RowID as DataID, r.RowID AS RunID FROM ").append(getFromTable()).append(" o\n")
                .append("\tJOIN exp.Object parent ON \n").append("\t\to.OwnerObjectId = parent.ObjectId \n")
                .append("\tJOIN exp.Data d ON \n").append("\t\tparent.ObjectURI = d.lsid \n")
                .append("\tJOIN exp.ExperimentRun r ON \n").append("\t\td.RunId = r.RowId AND \n").append("\t\tr.ProtocolLSID = ?");
        fromSQL.add(_protocolLsid);
        fromSQL.append(") ").append(alias);
        return fromSQL;
    }
}