package org.labkey.study.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.security.User;
import org.labkey.api.study.StudyService;
import org.labkey.study.StudySchema;
import org.labkey.study.model.StudyImpl;

public class VisualizationVisitTagTable extends VirtualTable
{
    private final StudyImpl _study;
    private final User _user;
    private final boolean _useProtocolDay;
    private final String _visitTagName;
    private final String _dayString;

    public VisualizationVisitTagTable(StudyImpl study, User user, String visitTagName, boolean useProtocolDay, String interval)
    {
        super(StudySchema.getInstance().getSchema(), "VizVisitTag");
        _study = study;
        _user = user;

        addColumn(new ExprColumn(this, StudyService.get().getSubjectColumnName(_study.getContainer()),
                  new SQLFragment(ExprColumn.STR_TABLE_ALIAS + "." + "ParticipantId"), JdbcType.VARCHAR));

        _visitTagName = visitTagName;
        _useProtocolDay = useProtocolDay;

        if (_useProtocolDay)
            _dayString = "ProtocolDay";
        else
            _dayString = "Day";

        SQLFragment dateCalc;
        if (getSqlDialect().isSqlServer())
        {
            dateCalc = new SQLFragment("DATEADD(dd, " + _dayString + ", StartDate)" );
        }
        else
        {
            dateCalc = new SQLFragment("(StartDate + interval '1' day * " + _dayString + ")");
        }

        addColumn(new ExprColumn(this, "StartDate", dateCalc, JdbcType.DATE));
    }

    @NotNull
    @Override
    public SQLFragment getFromSQL(String alias)
    {
        String innerAlias = alias + "_SP";
        String joinString;
        if (_useProtocolDay)
        {
            joinString = "\nJOIN study.Visit ON " + innerAlias + ".VisitId = study.Visit.RowId AND " +
                         innerAlias + ".VisitId IS NOT NULL";
        }
        else
        {
            joinString = "\nJOIN study.ParticipantVisit ON " + innerAlias + ".VisitId = study.ParticipantVisit.VisitRowId AND " +
                         innerAlias + ".ParticipantId = study.ParticipantVisit.ParticipantId";
        }

        Container container = _study.getContainer();
        SQLFragment from = new SQLFragment("(SELECT StartDate, VisitId, " + _dayString + ", " + innerAlias + ".ParticipantId " + " FROM (SELECT ParticipantId, ");
        from.append("COALESCE(CohortVisitTag.VisitId, NoCohortTag.VisitId) As VisitId, CurrentCohortId FROM study.Participant\n")
            .append("LEFT OUTER JOIN study.VisitTagMap CohortVisitTag ON study.Participant.CurrentCohortId = CohortVisitTag.CohortID AND CohortVisitTag.VisitTag=")
            .append("'" + _visitTagName + "'\n")
            .append("AND CohortVisitTag.Container = ?");
        from.add(container);
        from.append("\nLEFT OUTER JOIN study.VisitTagMap NoCohortTag ON NoCohortTag.VisitTag =")
            .append("'" + _visitTagName + "'\n")
            .append("AND NoCohortTag.Container = ?");
        from.add(container);
        from.append("\nWHERE study.Participant.Container = ?) ").append(innerAlias);
        from.add(container);
        from.append(joinString)
            .append("\nJOIN study.study ON study.study.Container = ?");
        from.add(container);
        from.append(") ").append(alias);
        return from;
    }
}
