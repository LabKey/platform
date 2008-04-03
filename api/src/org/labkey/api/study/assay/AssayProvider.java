package org.labkey.api.study.assay;

import org.labkey.api.exp.*;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRunTable;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.security.User;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.study.PlateTemplate;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.common.util.Pair;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.io.File;

/**
 * User: brittp
 * Date: Jul 11, 2007
 * Time: 9:59:49 AM
 */
public interface AssayProvider extends Handler<ExpProtocol>
{
    PropertyDescriptor[] getUploadSetColumns(ExpProtocol protocol);

    PropertyDescriptor[] getRunPropertyColumns(ExpProtocol protocol);

    PropertyDescriptor[] getRunInputPropertyColumns(ExpProtocol protocol);

    PropertyDescriptor[] getRunDataColumns(ExpProtocol protocol);

    ExpRun saveExperimentRun(AssayRunUploadContext context) throws ExperimentException;

    List<AssayDataCollector> getDataCollectors(Map<String, File> uploadedFiles);

    String getName();

    ExpProtocol createAssayDefinition(User user, Container container, String name, String description, int sampleCount) throws ExperimentException, SQLException;

    List<Domain> createDefaultDomains(Container c, User user);

    HttpView getDataDescriptionView(AssayRunUploadForm form);

    Container getAssociatedStudyContainer(ExpProtocol protocol, Object dataId);

    ActionURL getUploadWizardURL(Container container, ExpProtocol protocol);

    TableInfo createDataTable(QuerySchema schema, String alias, ExpProtocol protocol);

    ExpRunTable createRunTable(QuerySchema schema, String alias, ExpProtocol protocol);

    FieldKey getParticipantIDFieldKey();

    FieldKey getVisitIDFieldKey(Container targetStudy);

    FieldKey getRunIdFieldKeyFromDataRow();

    FieldKey getDataRowIdFieldKey();

    FieldKey getSpecimenIDFieldKey();

    ActionURL publish(User user, ExpProtocol protocol, Container study, Set<AssayPublishKey> dataKeys, List<String> errors);

    boolean canPublish();

    List<ParticipantVisitResolverType> getParticipantVisitResolverTypes();

    void setPlateTemplate(Container container, ExpProtocol protocol, PlateTemplate template);

    PlateTemplate getPlateTemplate(Container container, ExpProtocol protocol);

    boolean isPlateBased();

    List<Domain> getDomains(ExpProtocol protocol);

    Pair<ExpProtocol, List<Domain>> getAssayTemplate(User user, Container targetContainer);

    Pair<ExpProtocol, List<Domain>> getAssayTemplate(User user, Container targetContainer, ExpProtocol toCopy);

    boolean isFileLinkPropertyAllowed(ExpProtocol protocol, Domain domain);

    boolean isRequiredDomainProperty(Domain domain, String propertyName);

    boolean allowUpload(User user, Container container, ExpProtocol protocol);

    HttpView getDisallowedUploadMessageView(User user, Container container, ExpProtocol protocol);

    QueryView createRunDataView(ViewContext context, ExpProtocol protocol);

    QueryView createRunView(ViewContext context, ExpProtocol protocol);

    String getRunListTableName(ExpProtocol protocol);

    String getRunDataTableName(ExpProtocol protocol);

    void deleteProtocol(ExpProtocol protocol, User user) throws ExperimentException;
}
