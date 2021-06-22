package org.labkey.api.exp.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.EnumTableInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.UserSchema;

import java.util.Arrays;
import java.util.EnumSet;

public class WorkflowTaskStatusEnumTableInfo extends EnumTableInfo
{
    public static final String WORKFLOW_TASK_STATUS_TABLE_NAME = "WorkflowTaskStatus";
    public static final String WORKFLOW_JOB_PRIORITY_TABLE_NAME = "WorkflowJobPriority";

    public enum WorkflowJobPriority
    {
        Low,
        Medium,
        High,
        Urgent;
    }

    public enum WorkflowStatusType
    {
        Initial,
        Active,
        Final;
    }

    public enum WorkflowTaskStatus
    {
        PENDING("Pending", WorkflowStatusType.Initial),
        IN_PROGRESS("In Progress", WorkflowStatusType.Active),
        COMPLETE("Complete", WorkflowStatusType.Final);

        private String _label;
        private WorkflowStatusType _type;

        WorkflowTaskStatus(String label, WorkflowStatusType type)
        {
            _label = label;
            _type = type;
        }

        public WorkflowStatusType getStatusType()
        {
            return _type;
        }

        @Override
        public String toString()
        {
            return _label;
        }

        @Nullable
        public static WorkflowTaskStatusEnumTableInfo.WorkflowTaskStatus fromLabel(String label)
        {
            return Arrays.stream(values()).filter(status -> status.toString().equals(label)).findFirst().orElse(null);
        }

        @Nullable
        public static WorkflowTaskStatusEnumTableInfo.WorkflowTaskStatus fromStatusType(WorkflowStatusType type)
        {
            return Arrays.stream(values()).filter(status -> status._type == type).findFirst().orElse(null);
        }

        public static int getOrdinalFromStatusType(WorkflowStatusType type)
        {
            WorkflowTaskStatus status = fromStatusType(type);
            return status != null ? status.ordinal() : -1;
        }

    }

    public interface TaskStatusTypeGetter<EnumType>
    {
        WorkflowStatusType getType(EnumType e);
    }

    protected final TaskStatusTypeGetter<WorkflowTaskStatus> _typeGetter = WorkflowTaskStatus::getStatusType;

    public WorkflowTaskStatusEnumTableInfo(UserSchema schema, String description)
    {
        super(WorkflowTaskStatus.class, schema, description, true);

        ExprColumn ordinalColumn = new ExprColumn(this, "StatusType", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".StatusType"), JdbcType.VARCHAR);
        ordinalColumn.setHidden(true);
        addColumn(ordinalColumn);
    }

    @Override
    public @NotNull SQLFragment getFromSQL()
    {
        SQLFragment sql = new SQLFragment();
        String separator = "";
        EnumSet<WorkflowTaskStatus> enumSet = EnumSet.allOf(_enum);
        for (WorkflowTaskStatus e : enumSet)
        {
            sql.append(separator);
            separator = " UNION ";
            sql.append("SELECT ? AS VALUE, ? AS RowId, ? As Ordinal, ? As StatusType");
            sql.add(_valueGetter.getValue(e));
            sql.add(_rowIdGetter.getRowId(e));
            sql.add(e.ordinal());
            sql.add(_typeGetter.getType(e));
        }
        return sql;
    }


}
