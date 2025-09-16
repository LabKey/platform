package org.labkey.experiment;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.SampleTimelineAuditEvent;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;
import org.labkey.api.util.HtmlString;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.template.WarningProvider;
import org.labkey.api.view.template.Warnings;

public class ExperimentWarningProvider implements WarningProvider
{
    private Long _actualRecordCount;

    @Override
    public void addDynamicWarnings(@NotNull Warnings warnings, @Nullable ViewContext context, boolean showAllWarnings)
    {
        PropertyManager.WritablePropertyMap props = PropertyManager.getWritableProperties(ExperimentModule.AMOUNT_AND_UNIT_UPGRADE_PROP, false);
        if (props == null || props.isEmpty())
            return;
        String expectedCount = props.get(ExperimentModule.AUDIT_COUNT_PROP);
        String transactionIdStr = props.get(ExperimentModule.TRANSACTION_ID_PROP);
        if (StringUtils.isEmpty(expectedCount) || StringUtils.isEmpty(transactionIdStr))
            return;

        if (_actualRecordCount == null)
        {
            UserSchema auditLogSchema = AuditLogService.getAuditLogSchema(User.getAdminServiceUser(), ContainerManager.getRoot());
            if (auditLogSchema == null)
                return;
            ContainerFilter cf = ContainerFilter.Type.AllFolders.create(ContainerManager.getRoot(), User.getAdminServiceUser());
            TableInfo timelineTable = auditLogSchema.getTable(SampleTimelineAuditEvent.EVENT_TYPE, cf);
            if (timelineTable == null)
                return;
            SQLFragment sql = new SQLFragment("SELECT COUNT(*) from ").append(timelineTable)
                    .append(" WHERE transactionId = ?").add(Long.valueOf(transactionIdStr));
            SqlSelector selector = new SqlSelector(auditLogSchema.getDbSchema().getScope(), sql);
            _actualRecordCount = selector.getObject(Long.class);
        }

        if (Long.valueOf(expectedCount).equals(_actualRecordCount))
        {
            try (var ignored = SpringActionController.ignoreSqlUpdates())
            {
                props.delete();
            }
            _actualRecordCount = null;
        }
        else
        {
            String upgradeMessage = "The number of audit logs created during the upgrade of the Experiment Module is not as expected. Expected "
                    + expectedCount + " but got " + _actualRecordCount + ". The upgrade succeeded but not all audit logs were created, likely due to a premature server shutdown." +
                    " It is recommended that you restore the DB from backup and rerun the upgrade or contact your account manager.";
            warnings.add(HtmlString.of(upgradeMessage));
        }

    }
}
