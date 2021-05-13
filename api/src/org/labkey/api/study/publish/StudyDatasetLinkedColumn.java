package org.labkey.api.study.publish;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.security.User;
import org.labkey.api.study.Dataset;

import java.util.Map;

public class StudyDatasetLinkedColumn extends ExprColumn
{
    private final Dataset _dataset;
    private final User _user;
    private final String _rowIdName;

    public StudyDatasetLinkedColumn(TableInfo parent, String name, Dataset dataset, String rowIdName, User user)
    {
        super(parent, name, new SQLFragment("(CASE WHEN " + "StudyDataJoin$" + dataset.getContainer().getRowId() +
                "._key IS NOT NULL THEN " + dataset.getDatasetId() + " ELSE NULL END)"), JdbcType.INTEGER);

        _dataset = dataset;
        _rowIdName = rowIdName;
        _user = user;
    }

    public Container getStudyContainer()
    {
        return _dataset.getContainer();
    }

    public static String getDatasetIdAlias(Container studyContainer)
    {
        return "StudyDataJoin$" + studyContainer.getRowId();
    }

    public String getDatasetIdAlias()
    {
        return getDatasetIdAlias(getStudyContainer());
    }

    @Override
    public void declareJoins(String parentAlias, Map<String, SQLFragment> map)
    {
        super.declareJoins(parentAlias, map);
        SQLFragment joinSql = new SQLFragment();
        String datasetAlias = getDatasetIdAlias();
        Container studyContainer = getStudyContainer();
        TableInfo datasetTable = _dataset.getTableInfo(_user);

        joinSql.appendComment("<StudyDatasetColumn.join " + studyContainer.getPath() + ">", getSqlDialect());
        joinSql.append(" LEFT OUTER JOIN ").append(datasetTable.getFromSQL(datasetAlias)).append(" ON ");
        joinSql.append(datasetAlias).append("._key = CAST(").append(parentAlias).append(".").append(_rowIdName).append(" AS ");
        joinSql.append(getSqlDialect().getSqlTypeName(JdbcType.VARCHAR)).append("(200))");
        joinSql.appendComment("</StudyDatasetColumn.join>", getSqlDialect());
        map.put(datasetAlias, joinSql);
    }
}
