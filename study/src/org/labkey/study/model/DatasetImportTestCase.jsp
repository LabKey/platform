<%@ page import="org.apache.commons.lang3.mutable.MutableInt" %>
<%@ page import="org.apache.logging.log4j.Logger" %>
<%@ page import="org.jetbrains.annotations.NotNull" %>
<%@ page import="org.jetbrains.annotations.Nullable" %>
<%@ page import="org.junit.Test" %>
<%@ page import="org.labkey.api.audit.AuditLogService" %>
<%@ page import="org.labkey.api.collections.CaseInsensitiveHashMap" %>
<%@ page import="org.labkey.api.data.ColumnInfo" %>
<%@ page import="org.labkey.api.data.CompareType" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.data.ContainerManager" %>
<%@ page import="org.labkey.api.data.ConvertHelper" %>
<%@ page import="org.labkey.api.data.DbScope" %>
<%@ page import="org.labkey.api.data.MvUtil" %>
<%@ page import="org.labkey.api.data.Results" %>
<%@ page import="org.labkey.api.data.SQLFragment" %>
<%@ page import="org.labkey.api.data.SimpleFilter" %>
<%@ page import="org.labkey.api.data.Sort" %>
<%@ page import="org.labkey.api.data.SqlSelector" %>
<%@ page import="org.labkey.api.data.TableInfo" %>
<%@ page import="org.labkey.api.data.TableSelector" %>
<%@ page import="org.labkey.api.dataiterator.DetailedAuditLogDataIterator" %>
<%@ page import="org.labkey.api.dataiterator.ListofMapsDataIterator" %>
<%@ page import="org.labkey.api.exp.Lsid" %>
<%@ page import="org.labkey.api.exp.OntologyManager" %>
<%@ page import="org.labkey.api.exp.PropertyType" %>
<%@ page import="org.labkey.api.exp.property.DefaultPropertyValidator" %>
<%@ page import="org.labkey.api.exp.property.Domain" %>
<%@ page import="org.labkey.api.exp.property.DomainProperty" %>
<%@ page import="org.labkey.api.exp.property.IPropertyValidator" %>
<%@ page import="org.labkey.api.exp.property.PropertyService" %>
<%@ page import="org.labkey.api.gwt.client.AuditBehaviorType" %>
<%@ page import="org.labkey.api.gwt.client.model.PropertyValidatorType" %>
<%@ page import="org.labkey.api.module.FolderTypeManager" %>
<%@ page import="org.labkey.api.qc.QCState" %>
<%@ page import="org.labkey.api.qc.QCStateManager" %>
<%@ page import="org.labkey.api.query.BatchValidationException" %>
<%@ page import="org.labkey.api.query.FieldKey" %>
<%@ page import="org.labkey.api.query.QueryService" %>
<%@ page import="org.labkey.api.query.QueryUpdateService" %>
<%@ page import="org.labkey.api.query.UserSchema" %>
<%@ page import="org.labkey.api.query.ValidationException" %>
<%@ page import="org.labkey.api.reader.DataLoader" %>
<%@ page import="org.labkey.api.reader.MapLoader" %>
<%@ page import="org.labkey.api.reports.model.ViewCategory" %>
<%@ page import="org.labkey.api.reports.model.ViewCategoryManager" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.study.Dataset" %>
<%@ page import="org.labkey.api.study.Study" %>
<%@ page import="org.labkey.api.study.TimepointType" %>
<%@ page import="org.labkey.api.util.DateUtil" %>
<%@ page import="org.labkey.api.util.GUID" %>
<%@ page import="org.labkey.api.util.JunitUtil" %>
<%@ page import="org.labkey.api.util.PageFlowUtil" %>
<%@ page import="org.labkey.api.util.TestContext" %>
<%@ page import="org.labkey.study.StudyFolderType" %>
<%@ page import="org.labkey.study.StudySchema" %>
<%@ page import="org.labkey.study.dataset.DatasetAuditProvider" %>
<%@ page import="org.labkey.study.model.DatasetDefinition" %>
<%@ page import="org.labkey.study.model.SecurityType" %>
<%@ page import="org.labkey.study.model.StudyImpl" %>
<%@ page import="org.labkey.study.model.StudyManager" %>
<%@ page import="org.labkey.study.model.VisitImpl" %>
<%@ page import="org.labkey.study.query.StudyQuerySchema" %>
<%@ page import="org.labkey.study.writer.DatasetDataWriter" %>
<%@ page import="java.sql.ResultSet" %>
<%@ page import="java.util.ArrayList" %>
<%@ page import="java.util.Arrays" %>
<%@ page import="java.util.Collection" %>
<%@ page import="java.util.Date" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.LinkedHashSet" %>
<%@ page import="java.util.List" %>
<%@ page import="java.util.Map" %>
<%@ page import="static org.junit.Assert.*" %>
<%@ page import="static org.labkey.study.model.StudyManager.TEST_LOGGER" %>
<%@ page import="static org.labkey.study.dataset.DatasetAuditProvider.DATASET_AUDIT_EVENT" %>
<%@ page extends="org.labkey.api.jsp.JspTest.DRT" %>


<%!
private final StudyManager _manager = StudyManager.getInstance();

private TestContext _context = null;
private StudyImpl _studyDateBased = null;
private StudyImpl _studyVisitBased = null;

//        @BeforeClass
public void createStudy()
{
    _context = TestContext.get();
    Container junit = JunitUtil.getTestContainer();

    {
        String name = GUID.makeHash();
        Container c = ContainerManager.createContainer(junit, name);
        c.setFolderType(FolderTypeManager.get().getFolderType(StudyFolderType.NAME), _context.getUser());
        StudyImpl s = new StudyImpl(c, "Junit Study");
        s.setTimepointType(TimepointType.DATE);
        s.setStartDate(new Date(DateUtil.parseDateTime(c, "2001-01-01")));
        s.setSubjectColumnName("SubjectID");
        s.setSubjectNounPlural("Subjects");
        s.setSubjectNounSingular("Subject");
        s.setSecurityType(SecurityType.BASIC_WRITE);
        s.setStartDate(new Date(DateUtil.parseDateTime(c, "1 Jan 2000")));
        _studyDateBased = StudyManager.getInstance().createStudy(_context.getUser(), s);

        MvUtil.assignMvIndicators(c,
                new String[] {"X", "Y", "Z"},
                new String[] {"XXX", "YYY", "ZZZ"});
    }

    {
        String name = GUID.makeHash();
        Container c = ContainerManager.createContainer(junit, name);
        StudyImpl s = new StudyImpl(c, "Junit Study");
        s.setTimepointType(TimepointType.VISIT);
        s.setStartDate(new Date(DateUtil.parseDateTime(c, "2001-01-01")));
        s.setSubjectColumnName("SubjectID");
        s.setSubjectNounPlural("Subjects");
        s.setSubjectNounSingular("Subject");
        s.setSecurityType(SecurityType.BASIC_WRITE);
        _studyVisitBased = StudyManager.getInstance().createStudy(_context.getUser(), s);

        MvUtil.assignMvIndicators(c,
                new String[] {"X", "Y", "Z"},
                new String[] {"XXX", "YYY", "ZZZ"});
    }
}


private int counterDatasetId = 100;
private int counterRow = 0;

protected enum DatasetType
{
    NORMAL
            {
                @Override
                public void configureDataset(DatasetDefinition dd)
                {
                    dd.setKeyPropertyName("Measure");
                }
            },
    DEMOGRAPHIC
            {
                @Override
                public void configureDataset(DatasetDefinition dd)
                {
                    dd.setDemographicData(true);
                }
            },
    OPTIONAL_GUID
            {
                @Override
                public void configureDataset(DatasetDefinition dd)
                {
                    dd.setKeyPropertyName("GUID");
                    dd.setKeyManagementType(Dataset.KeyManagementType.GUID);
                }
            };

    public abstract void configureDataset(DatasetDefinition dd);
}

Dataset createDataset(Study study, String name, boolean demographic) throws Exception
{
    if (demographic)
        return createDataset(study, name, DatasetType.DEMOGRAPHIC);
    else
        return createDataset(study, name, DatasetType.NORMAL);
}


Dataset createDataset(Study study, String name, DatasetType type) throws Exception
{
    int id = counterDatasetId++;
    _manager.createDatasetDefinition(_context.getUser(), study.getContainer(), id);
    DatasetDefinition dd = _manager.getDatasetDefinition(study, id);
    dd = dd.createMutable();

    dd.setName(name);
    dd.setLabel(name);
    dd.setCategory("Category");

    type.configureDataset(dd);

    String domainURI = StudyManager.getInstance().getDomainURI(study.getContainer(), null, dd);
    dd.setTypeURI(domainURI);
    OntologyManager.ensureDomainDescriptor(domainURI, dd.getName(), study.getContainer());
    StudyManager.getInstance().updateDatasetDefinition(_context.getUser(), dd);

    // validator
    Lsid lsidValidator = DefaultPropertyValidator.createValidatorURI(PropertyValidatorType.Range);
    IPropertyValidator pvLessThan100 = PropertyService.get().createValidator(lsidValidator.toString());
    pvLessThan100.setName("lessThan100");
    pvLessThan100.setExpressionValue("~lte=100.0");

    // define columns
    Domain domain = dd.getDomain();

    DomainProperty measure = domain.addProperty();
    measure.setName("Measure");
    measure.setPropertyURI(domain.getTypeURI()+"#"+measure.getName());
    measure.setRangeURI(PropertyType.STRING.getTypeUri());
    measure.setRequired(true);

    if (type==DatasetType.OPTIONAL_GUID)
    {
        DomainProperty guid = domain.addProperty();
        guid.setName("GUID");
        guid.setPropertyURI(domain.getTypeURI()+"#"+guid.getName());
        guid.setRangeURI(PropertyType.STRING.getTypeUri());
        guid.setRequired(true);
    }

    DomainProperty value = domain.addProperty();
    value.setName("Value");
    value.setPropertyURI(domain.getTypeURI() + "#" + value.getName());
    value.setRangeURI(PropertyType.DOUBLE.getTypeUri());
    value.setMvEnabled(true);

    // Missing values and validators don't work together, so I need another column
    DomainProperty number = domain.addProperty();
    number.setName("Number");
    number.setPropertyURI(domain.getTypeURI() + "#" + number.getName());
    number.setRangeURI(PropertyType.DOUBLE.getTypeUri());
    number.addValidator(pvLessThan100);

    if (type.equals(DatasetType.DEMOGRAPHIC))
    {
        DomainProperty startDate = domain.addProperty();
        startDate.setName("StartDate");
        startDate.setPropertyURI(domain.getTypeURI() + "#" + startDate.getName());
        startDate.setRangeURI(PropertyType.DATE_TIME.getTypeUri());
    }
    // save
    domain.save(_context.getUser());

    return study.getDataset(id);
}

private static final double DELTA = 1E-8;


@Test
public void test() throws Throwable
{
    try
    {
        createStudy();
        _testImportDatasetData(_studyDateBased);
        _testDatasetUpdateService(_studyDateBased);
        _testDaysSinceStartCalculation(_studyDateBased);
        _testImportDemographicDatasetData(_studyDateBased);
        _testImportDemographicDatasetData(_studyVisitBased);
        _testImportDatasetData(_studyVisitBased);
        _testImportDatasetDataAllowImportGuid(_studyDateBased);
        _testDatasetTransformExport(_studyDateBased);
        testDatasetSubcategory();
// TODO VisitBased
//        _testDatasetUpdateService(_studyVisitBased);
        _testDatasetDetailedLogging(_studyDateBased);
    }
    catch (BatchValidationException x)
    {
        List<ValidationException> l = x.getRowErrors();
        if (null != l && l.size() > 0)
            throw l.get(0);
        throw x;
    }
    finally
    {
//        tearDown();
    }
}


private void _testDatasetUpdateService(StudyImpl study) throws Throwable
{
    StudyQuerySchema ss = StudyQuerySchema.createSchema(study, _context.getUser(), false);
    Dataset def = createDataset(study, "A", false);
    TableInfo tt = ss.getTable(def.getName());
    QueryUpdateService qus = tt.getUpdateService();
    BatchValidationException errors = new BatchValidationException();
    assertNotNull(qus);

    Date Jan1 = new Date(DateUtil.parseISODateTime("2011-01-01"));
    Date Feb1 = new Date(DateUtil.parseISODateTime("2011-02-01"));
    List<Map<String, Object>> rows = new ArrayList<>();

    // insert one row
    rows.clear(); errors.clear();
    rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++this.counterRow), "Value", 1.0));
    List<Map<String,Object>> ret = qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
    String msg = errors.getRowErrors().size() > 0 ? errors.getRowErrors().get(0).toString() : "no message";
    assertFalse(msg, errors.hasErrors());
    Map<String,Object> firstRowMap = ret.get(0);
    String lsidRet = (String)firstRowMap.get("lsid");
    assertNotNull(lsidRet);
    assertTrue("lsid should end with "+":101.A1.20110101.0000.Test"+counterRow + ".  Was: " + lsidRet, lsidRet.endsWith(":101.A1.20110101.0000.Test"+counterRow));

    String lsidFirstRow;

    try (ResultSet rs = new TableSelector(tt).getResultSet())
    {
        assertTrue(rs.next());
        lsidFirstRow = rs.getString("lsid");
        assertEquals(lsidFirstRow, lsidRet);
    }

    // duplicate row
    qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
    //study:Label: Only one row is allowed for each Subject/Visit/Measure Triple.  Duplicates were found in the database or imported data.; Duplicate: Subject = A1Date = Sat Jan 01 00:00:00 PST 2011, Measure = Test1
    assertTrue(errors.getRowErrors().get(0).getMessage().contains("Duplicates were found"));

    // different participant
    rows.clear(); errors.clear();
    rows.add(PageFlowUtil.mapInsensitive("SubjectId", "B2", "Date", Jan1, "Measure", "Test" + (counterRow), "Value", 2.0));
    qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
    assertFalse(errors.hasErrors());

    // different date
    rows.clear(); errors.clear();
    rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Feb1, "Measure", "Test" + (counterRow), "Value", "X"));
    qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
    assertFalse(errors.hasErrors());

    // different measure
    rows.clear(); errors.clear();
    rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", "X"));
    qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
    assertFalse(errors.hasErrors());

    // duplicates in batch
    rows.clear(); errors.clear();
    rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", 1.0));
    rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (counterRow), "Value", 1.0));
    qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
    //study:Label: Only one row is allowed for each Subject/Visit/Measure Triple.  Duplicates were found in the database or imported data.; Duplicate: Subject = A1Date = Sat Jan 01 00:00:00 PST 2011, Measure = Test3
    assertTrue(errors.getRowErrors().get(0).getMessage().contains("Duplicates were found in the database or imported data"));

    // missing participantid
    rows.clear(); errors.clear();
    rows.add(PageFlowUtil.mapInsensitive("SubjectId", null, "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", 1.0));
    qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
    //study:Label: All dataset rows must include a value for SubjectID
    msg = errors.getRowErrors().get(0).getMessage();
    assertTrue(msg.contains("required") || msg.contains("must include"));
    assertTrue(errors.getRowErrors().get(0).getMessage().contains("SubjectID"));

    // missing date
    rows.clear(); errors.clear();
    rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", null, "Measure", "Test" + (++counterRow), "Value", 1.0));
    qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
    //study:Label: Row 1 does not contain required field date.
    assertTrue(errors.getRowErrors().get(0).getMessage().toLowerCase().contains("date"));

    // missing required property field (Measure in map)
    rows.clear(); errors.clear();
    rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", null, "Value", 1.0));
    qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
    //study:Label: Row 1 does not contain required field Measure.
    assertTrue(errors.getRowErrors().get(0).getMessage().contains("required"));
    assertTrue(errors.getRowErrors().get(0).getMessage().contains("Measure"));

    // missing required property field (Measure not in map)
    rows.clear(); errors.clear();
    rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Value", 1.0));
    qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
    //study:Label: Row 1 does not contain required field Measure.
    assertTrue(errors.getRowErrors().get(0).getMessage().contains("does not contain required field"));
    assertTrue(errors.getRowErrors().get(0).getMessage().contains("Measure"));

    // legal MV indicator
    rows.clear(); errors.clear();
    rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", "X"));
    qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
    assertFalse(errors.hasErrors());

    // illegal MV indicator
    rows.clear(); errors.clear();
    rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", "N/A"));
    qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
    //study:Label: Value: Could not convert value 'N/A' (String) for Double field 'Value'
    assertTrue(errors.getRowErrors().get(0).getMessage().endsWith(ConvertHelper.getStandardConversionErrorMessage("N/A", "Value", Double.class)));

    // conversion test
    rows.clear(); errors.clear();
    rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", "100"));
    qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
    assertFalse(errors.hasErrors());


    // validation test
    rows.clear(); errors.clear();
    rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", 1, "Number", 101));
    qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
    //study:Label: Value '101.0' for field 'Number' is invalid.
    assertTrue(errors.getRowErrors().get(0).getMessage().contains("is invalid"));

    rows.clear(); errors.clear();
    rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (counterRow), "Value", 1, "Number", 99));
    qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
    assertFalse(errors.hasErrors());

    // QCStateLabel
    rows.clear(); errors.clear();
    rows.add(PageFlowUtil.mapInsensitive("QCStateLabel", "dirty", "SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", 1, "Number", 5));
    List<QCState> qcstates = QCStateManager.getInstance().getQCStates(study.getContainer());
    assertEquals(0, qcstates.size());
    qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
    assertFalse(errors.hasErrors());
    qcstates = QCStateManager.getInstance().getQCStates(study.getContainer());
    assertEquals(1, qcstates.size());
    assertEquals("dirty" , qcstates.get(0).getLabel());

    // let's try to update a row
    rows.clear(); errors.clear();
    assertTrue(firstRowMap.containsKey("Value"));
    CaseInsensitiveHashMap<Object> row = new CaseInsensitiveHashMap<>();
    row.putAll(firstRowMap);
    row.put("Value", 3.14159);
    // TODO why is Number==null OK on insert() but not update()?
    row.put("Number", 1.0);
    rows.add(row);
    List<Map<String, Object>> keys = new ArrayList<>();
    keys.add(PageFlowUtil.mapInsensitive("lsid", lsidFirstRow));
    ret = qus.updateRows(_context.getUser(), study.getContainer(), rows, keys, null, null);
    assert(ret.size() == 1);
}


private void _testDatasetDetailedLogging(StudyImpl study) throws Throwable
{
    // first get last audit record
    UserSchema auditSchema = AuditLogService.get().createSchema(_context.getUser(), study.getContainer());
    TableInfo auditTable = auditSchema.getTable(DatasetAuditProvider.DATASET_AUDIT_EVENT);
    Integer RowId = new SqlSelector(auditSchema.getDbSchema(), new SQLFragment("select max(rowid) FROM ").append(auditTable.getFromSQL("_")))
            .getObject(Integer.class);
    int rowid = null==RowId ? 0 : RowId.intValue();

    StudyQuerySchema ss = StudyQuerySchema.createSchema(study, _context.getUser(), true);
    Dataset def = createDataset(study, "DL", true);
    TableInfo tt = ss.getTable(def.getName());
    QueryUpdateService qus = tt.getUpdateService();
    BatchValidationException errors = new BatchValidationException();
    assertNotNull(qus);

    Date Jan1 = new Date(DateUtil.parseISODateTime("2011-01-01"));
    Date Feb1 = new Date(DateUtil.parseISODateTime("2011-02-01"));
    List<Map<String, Object>> rows = new ArrayList<>();

    Map<Enum,Object> config = Map.of(DetailedAuditLogDataIterator.AuditConfigs.AuditBehavior, AuditBehaviorType.DETAILED);

    // INSERT
    rows.clear(); errors.clear();
    rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", "Initial", "Value", 1.0));
    List<Map<String,Object>> ret = qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, config, null);
    String msg = errors.getRowErrors().size() > 0 ? errors.getRowErrors().get(0).toString() : "no message";
    assertFalse(msg, errors.hasErrors());
    Map<String,Object> firstRowMap = ret.get(0);
    String lsidRet = (String)firstRowMap.get("lsid");
    assertNotNull(lsidRet);

    SimpleFilter f = new SimpleFilter(new FieldKey(null,"RowId"),rowid, CompareType.GT);
    List<DatasetAuditProvider.DatasetAuditEvent> events = AuditLogService.get().getAuditEvents(study.getContainer(),_context.getUser(),DATASET_AUDIT_EVENT,f,new Sort("-RowId"));
    assertFalse(events.isEmpty());
    assertNull(events.get(0).getOldRecordMap());
    assertNotNull(events.get(0).getNewRecordMap());
    Map<String,String> newRecordMap = new CaseInsensitiveHashMap<>(PageFlowUtil.mapFromQueryString(events.get(0).getNewRecordMap()));
    assertEquals(lsidRet, newRecordMap.get("lsid"));
    assertEquals("Initial", newRecordMap.get("Measure"));
    assertEquals("1.0", newRecordMap.get("Value"));

    // UPDATE
    rows.clear(); errors.clear();
    rows.add(PageFlowUtil.mapInsensitive("LSID", lsidRet, "Measure", "Updated", "Value", 2.0));
//        rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", "Updated", "Value", 2.0));
    qus.updateRows(_context.getUser(), study.getContainer(), rows, null, config, null);

    events = AuditLogService.get().getAuditEvents(study.getContainer(),_context.getUser(),DATASET_AUDIT_EVENT,f,new Sort("-RowId"));
    assertFalse(events.isEmpty());
    assertNotNull(events.get(0).getOldRecordMap());
    Map<String,String> oldRecordMap = new CaseInsensitiveHashMap<>(PageFlowUtil.mapFromQueryString(events.get(0).getOldRecordMap()));
    assertFalse(oldRecordMap.containsKey("lsid"));
    assertEquals("Initial", newRecordMap.get("Measure"));
    assertEquals("1.0", newRecordMap.get("Value"));
    assertEquals(2, oldRecordMap.size());
    assertNotNull(events.get(0).getNewRecordMap());
    newRecordMap = new CaseInsensitiveHashMap<>(PageFlowUtil.mapFromQueryString(events.get(0).getNewRecordMap()));
    assertFalse(newRecordMap.containsKey("lsid"));
    assertEquals("Updated",newRecordMap.get("Measure"));
    assertEquals("2.0", newRecordMap.get("Value"));
    assertEquals(2, newRecordMap.size());

    // MERGE
    // and since merge is a different code path...
    rows.clear(); errors.clear();
    rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Jan1, "Measure", "Merged", "Value", 3.0));
    int count = qus.mergeRows(_context.getUser(), study.getContainer(), new ListofMapsDataIterator(null,rows), errors, config, null);
    assertEquals(1, count);

    events = AuditLogService.get().getAuditEvents(study.getContainer(),_context.getUser(),DATASET_AUDIT_EVENT,f,new Sort("-RowId"));
    assertFalse(events.isEmpty());
    assertNotNull(events.get(0).getOldRecordMap());
    oldRecordMap = new CaseInsensitiveHashMap<>(PageFlowUtil.mapFromQueryString(events.get(0).getOldRecordMap()));
    assertFalse(oldRecordMap.containsKey("lsid"));
    assertEquals("Updated", newRecordMap.get("Measure"));
    assertEquals("2.0", newRecordMap.get("Value"));
    assertEquals(2, oldRecordMap.size());
    assertNotNull(events.get(0).getNewRecordMap());
    newRecordMap = new CaseInsensitiveHashMap<>(PageFlowUtil.mapFromQueryString(events.get(0).getNewRecordMap()));
    assertFalse(newRecordMap.containsKey("lsid"));
    assertEquals("Merged",newRecordMap.get("Measure"));
    assertEquals("3.0", newRecordMap.get("Value"));
    assertEquals(2, newRecordMap.size());
}


private void _testImportDatasetDataAllowImportGuid(Study study) throws Throwable
{
    int sequenceNum = 0;

    StudyQuerySchema ss = StudyQuerySchema.createSchema((StudyImpl) study, _context.getUser(), false);
    Dataset def = createDataset(study, "GU", DatasetType.OPTIONAL_GUID);
    TableInfo tt = def.getTableInfo(_context.getUser());

    Date Jan1 = new Date(DateUtil.parseISODateTime("2011-01-01"));
    List<Map<String, Object>> rows = new ArrayList<>();

    String guid = "GUUUUID";
    Map map = PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", 1.0, "SequenceNum", sequenceNum++, "GUID", guid);
    importRowVerifyGuid(def, map, tt, TEST_LOGGER);

    // duplicate row
    // Issue 12985
    rows.add(map);
    importRows(def, rows, "duplicate key", "All rows must have unique SubjectID/Date/GUID values.");

    //study:Label: Only one row is allowed for each Subject/Visit/Measure Triple.  Duplicates were found in the database or imported data.; Duplicate: Subject = A1Date = Sat Jan 01 00:00:00 PST 2011, Measure = Test1
//                assertTrue(-1 != errors.get(0).indexOf("duplicate key value violates unique constraint"));

    //same participant, guid, different sequenceNum
    importRowVerifyGuid(def, PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", 1.0, "SequenceNum", sequenceNum++, "GUID", guid), tt, TEST_LOGGER);

    //  same GUID,sequenceNum, different different participant
    importRowVerifyGuid(def, PageFlowUtil.map("SubjectId", "B2", "Date", Jan1, "Measure", "Test"+(counterRow), "Value", 2.0, "SequenceNum", sequenceNum, "GUID", guid), tt, TEST_LOGGER);

    //same subject, sequenceNum, GUID not provided
    importRowVerifyGuid(def, PageFlowUtil.map("SubjectId", "B2", "Date", Jan1, "Measure", "Test"+(counterRow), "Value", 2.0, "SequenceNum", sequenceNum), tt, TEST_LOGGER);

    //repeat:  should still work
    importRowVerifyGuid(def, PageFlowUtil.map("SubjectId", "B2", "Date", Jan1, "Measure", "Test"+(counterRow), "Value", 2.0, "SequenceNum", sequenceNum), tt, TEST_LOGGER);
}

private void _testImportDatasetData(Study study) throws Throwable
{
    int sequenceNum = 0;

    StudyQuerySchema ss = StudyQuerySchema.createSchema((StudyImpl) study, _context.getUser(), false);
    Dataset def = createDataset(study, "B", false);
    TableInfo tt = def.getTableInfo(_context.getUser());

    Date Jan1 = new Date(DateUtil.parseISODateTime("2011-01-01"));
    Date Feb1 = new Date(DateUtil.parseISODateTime("2011-02-01"));
    List<Map<String, Object>> rows = new ArrayList<>();

    // insert one row
    rows.clear();
    rows.add((Map)PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counterRow), "Value", 1.0, "SequenceNum", sequenceNum++));
    importRows(def, rows);

    try (Results results = new TableSelector(tt).getResults())
    {
        assertTrue("Didn't find imported row", results.next());
        assertFalse("Found unexpected second row", results.next());
    }

    // duplicate row
    // study:Label: Only one row is allowed for each Subject/Visit/Measure Triple.  Duplicates were found in the database or imported data.; Duplicate: Subject = A1Date = Sat Jan 01 00:00:00 PST 2011, Measure = Test1
    importRows(def, rows, "Duplicates were found");

    // different participant
    Map map2 = PageFlowUtil.map("SubjectId", "B2", "Date", Jan1, "Measure", "Test"+(counterRow), "Value", 2.0, "SequenceNum", sequenceNum++);
    importRow(def, map2);
    importRow(def, PageFlowUtil.map("SubjectId", "B2", "Date", Jan1, "Measure", "Test"+(counterRow), "Value", 2.0, "SequenceNum", sequenceNum++));

    // different date
    importRow(def, PageFlowUtil.map("SubjectId", "A1", "Date", Feb1, "Measure", "Test"+(counterRow), "Value", "X", "SequenceNum", sequenceNum++));

    // different measure
    importRow(def, PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test" + (++counterRow), "Value", "X", "SequenceNum", sequenceNum++));

    // duplicates in batch
    rows.clear();
    rows.add((Map)PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counterRow), "Value", 1.0, "SequenceNum", sequenceNum));
    rows.add((Map)PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(counterRow), "Value", 1.0, "SequenceNum", sequenceNum++));
    //study:Label: Only one row is allowed for each Subject/Visit/Measure Triple.  Duplicates were found in the database or imported data.; Duplicate: Subject = A1Date = Sat Jan 01 00:00:00 PST 2011, Measure = Test3
    importRows(def, rows, "Duplicates were found in the database or imported data");

    // missing participantid
    importRow(def, PageFlowUtil.map("SubjectId", null, "Date", Jan1, "Measure", "Test"+(++counterRow), "Value", 1.0, "SequenceNum", sequenceNum++), "required", "SubjectID");

    // missing date
    if (study==_studyDateBased) //irrelevant for sequential visits
        importRow(def, PageFlowUtil.map("SubjectId", "A1", "Date", null, "Measure", "Test"+(++counterRow), "Value", 1.0, "SequenceNum", sequenceNum++), "required", "date");

    // missing required property field
    importRow(def, PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", null, "Value", 1.0, "SequenceNum", sequenceNum++), "required", "Measure");

    // legal MV indicator
    importRow(def, PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counterRow), "Value", "X", "SequenceNum", sequenceNum++));

    // count rows with "X"
    final MutableInt Xcount = new MutableInt(0);
    new TableSelector(tt).forEach(rs -> {
        if ("X".equals(rs.getString("ValueMVIndicator")))
            Xcount.increment();
    });

    // legal MV indicator
    importRow(def, PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counterRow), "Value", null, "ValueMVIndicator", "X", "SequenceNum", sequenceNum++));

    // should have two rows with "X"
    final MutableInt XcountAgain = new MutableInt(0);
    new TableSelector(tt).forEach(rs -> {
        if ("X".equals(rs.getString("ValueMVIndicator")))
            XcountAgain.increment();
    });
    assertEquals(Xcount.intValue() + 1, XcountAgain.intValue());

    // illegal MV indicator
    importRow(def, PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counterRow), "Value", "N/A", "SequenceNum", sequenceNum++), "Value");

    // conversion test
    importRow(def, PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counterRow), "Value", "100", "SequenceNum", sequenceNum++));

    // validation test
    importRow(def, PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(++counterRow), "Value", 1, "Number", 101, "SequenceNum", sequenceNum++), "is invalid");

    importRow(def, PageFlowUtil.map("SubjectId", "A1", "Date", Jan1, "Measure", "Test"+(counterRow), "Value", 1, "Number", 99, "SequenceNum", sequenceNum++));
}

private void importRowVerifyKey(Dataset def, Map map, TableInfo tt, String key, @NotNull Logger logger, String... expectedErrors)  throws Exception
{
    importRow(def, map, logger, expectedErrors);
    String expectedKey = (String) map.get(key);

    try (ResultSet rs = new TableSelector(tt).getResultSet())
    {
        rs.last();
        assertEquals(expectedKey, rs.getString(key));
    }
}

private void importRowVerifyGuid(Dataset def, Map map, TableInfo tt, @NotNull Logger logger, String... expectedErrors)  throws Exception
{
    if (map.containsKey("GUID"))
        importRowVerifyKey(def, map, tt, "GUID", logger, expectedErrors);
    else
    {
        importRow(def, map, expectedErrors);

        try (ResultSet rs = new TableSelector(tt).getResultSet())
        {
            rs.last();
            String actualKey = rs.getString("GUID");
            assertTrue("No GUID generated when null GUID provided", actualKey.length() > 0);
        }
    }
}

private void importRows(Dataset def, List<Map<String, Object>> rows, String... expectedErrors) throws Exception
{
    importRows(def, rows, null, expectedErrors);
}

private void importRows(Dataset def, List<Map<String, Object>> rows, @Nullable Logger logger, String... expectedErrors) throws Exception
{
    BatchValidationException errors = new BatchValidationException();

    DataLoader dl = new MapLoader(rows);
    Map<String, String> columnMap = new CaseInsensitiveHashMap<>();

    StudyManager.getInstance().importDatasetData(
            _context.getUser(),
            (DatasetDefinition) def, dl, columnMap,
            errors, DatasetDefinition.CheckForDuplicates.sourceAndDestination, null, QueryUpdateService.InsertOption.IMPORT, logger, false, null);

    if (expectedErrors == null)
    {
        if (errors.hasErrors())
            throw errors;
    }
    else
    {
        for(String expectedError : expectedErrors)
            assertTrue("Expected to find '" + expectedError + "' in error message: " + errors.getMessage(),
                    errors.getMessage().contains(expectedError));
    }
}

private void importRow(Dataset def, Map map, String... expectedErrors)  throws Exception
{
    importRow(def, map, null, expectedErrors);
}

private void importRow(Dataset def, Map map, @Nullable Logger logger, String... expectedErrors)  throws Exception
{
    importRows(def, Arrays.asList(map), logger, expectedErrors);
}

private void _testImportDemographicDatasetData(Study study) throws Throwable
{
    StudyQuerySchema ss = StudyQuerySchema.createSchema((StudyImpl) study, _context.getUser(), false);
    Dataset def = createDataset(study, "Dem", true);
    TableInfo tt = def.getTableInfo(_context.getUser());

    Date Feb1 = new Date(DateUtil.parseISODateTime("2011-02-01"));
    List rows = new ArrayList();

    TimepointType time = study.getTimepointType();

    if (time == TimepointType.VISIT)
    {
        // insert one row w/visit
        importRow(def, PageFlowUtil.map("SubjectId", "A1", "SequenceNum", 1.0, "Measure", "Test"+(++counterRow), "Value", 1.0));

        try (ResultSet rs = new TableSelector(tt).getResultSet())
        {
            assertTrue(rs.next());
            assertEquals(1.0, rs.getDouble("SequenceNum"), DELTA);
        }

        // insert one row w/o visit
        rows.clear();
        rows.add(PageFlowUtil.map("SubjectId", "A2", "Measure", "Test"+(++counterRow), "Value", 1.0));
        importRows(def, rows);

        try (ResultSet rs = new TableSelector(tt).getResultSet())
        {
            assertTrue(rs.next());
            if ("A2".equals(rs.getString("SubjectId")))
                assertEquals(VisitImpl.DEMOGRAPHICS_VISIT, rs.getDouble("SequenceNum"), DELTA);
            assertTrue(rs.next());
            if ("A2".equals(rs.getString("SubjectId")))
                assertEquals(VisitImpl.DEMOGRAPHICS_VISIT, rs.getDouble("SequenceNum"), DELTA);
        }
    }
    else
    {
        // insert one row w/ date
        rows.clear();
        rows.add(PageFlowUtil.map("SubjectId", "A1", "Date", Feb1, "Measure", "Test"+(++counterRow), "Value", 1.0));
        importRows(def, rows);

        try (ResultSet rs = new TableSelector(tt).getResultSet())
        {
            assertTrue(rs.next());
            assertEquals(Feb1, new java.util.Date(rs.getTimestamp("date").getTime()));
        }

        Map map = PageFlowUtil.map("SubjectId", "A2", "Measure", "Test"+(++counterRow), "Value", 1.0);
        importRow(def, map);

        try (ResultSet rs = new TableSelector(tt).getResultSet())
        {
            assertTrue(rs.next());
            if ("A2".equals(rs.getString("SubjectId")))
                assertEquals(study.getStartDate(), new java.util.Date(rs.getTimestamp("date").getTime()));
            assertTrue(rs.next());
            if ("A2".equals(rs.getString("SubjectId")))
                assertEquals(study.getStartDate(), new java.util.Date(rs.getTimestamp("date").getTime()));
        }
    }
}

/**
 * Test calculation of timepoints for date based studies. Regression test for issue : 25987
 */
private void _testDaysSinceStartCalculation(Study study) throws Throwable
{
    StudyQuerySchema ss = StudyQuerySchema.createSchema((StudyImpl) study, _context.getUser(), false);
    Dataset dem = createDataset(study, "Dem", true);
    Dataset ds = createDataset(study, "DS", false);

    TableInfo tableInfo = ss.getTable(ds.getName());
    QueryUpdateService qus = tableInfo.getUpdateService();

    Date Feb1 = new Date(DateUtil.parseISODateTime("2016-02-01"));
    List rows = new ArrayList();
    List<String> errors = new ArrayList<>();

    TimepointType time = study.getTimepointType();

    Map<String, Integer> validValues = new HashMap<>();
    validValues.put("A1", 0);
    validValues.put("A2", 1);

    if (time == TimepointType.DATE)
    {
        // insert rows with a startDate
        rows.clear();
        errors.clear();
        rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", Feb1, "Measure", "Test"+(++counterRow), "Value", 1.0, "StartDate", Feb1));
        rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A2", "Date", Feb1, "Measure", "Test"+(++counterRow), "Value", 1.0, "StartDate", Feb1));
        importRows(dem, rows);

        // insert rows with a datetime
        rows.clear();
        BatchValidationException qusErrors = new BatchValidationException();
        rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A1", "Date", "2016-02-01 13:00", "Measure", "Test"+(++counterRow), "Value", 1.0));
        rows.add(PageFlowUtil.mapInsensitive("SubjectId", "A2", "Date", "2016-02-02 10:00", "Measure", "Test"+(++counterRow), "Value", 1.0));

        List<Map<String,Object>> ret = qus.insertRows(_context.getUser(), study.getContainer(), rows, qusErrors, null, null);
        String msg = qusErrors.getRowErrors().size() > 0 ? qusErrors.getRowErrors().get(0).toString() : "no message";
        assertFalse(msg, qusErrors.hasErrors());

        try (ResultSet rs = new TableSelector(tableInfo).getResultSet())
        {
            while (rs.next())
            {
                assertEquals((int)validValues.get(rs.getString("SubjectId")), rs.getInt("Day"));
            }
        }

        // clean up
        try (DbScope.Transaction transaction = StudySchema.getInstance().getScope().ensureTransaction())
        {
            dem.delete(_context.getUser());
            ds.delete(_context.getUser());
            transaction.commit();
        }
    }
}

private void  _testDatasetTransformExport(Study study) throws Throwable
{
    // create a dataset
    StudyQuerySchema ss = StudyQuerySchema.createSchema((StudyImpl) study, _context.getUser(), false);
    Dataset def = createDataset(study, "DS", false);
    TableInfo datasetTI = ss.getTable(def.getName());
    QueryUpdateService qus = datasetTI.getUpdateService();
    BatchValidationException errors = new BatchValidationException();
    assertNotNull(qus);

    // insert one row
    List rows = new ArrayList();
    Date jan1 = new Date(DateUtil.parseISODateTime("2012-01-01"));
    rows.add(PageFlowUtil.mapInsensitive("SubjectId", "DS1", "Date", jan1, "Measure", "Test" + (++this.counterRow), "Value", 0.0));
    List<Map<String,Object>> ret = qus.insertRows(_context.getUser(), study.getContainer(), rows, errors, null, null);
    assertFalse(errors.hasErrors());
    Map<String,Object> firstRowMap = ret.get(0);

    // Ensure alternateIds are generated for all participants
    StudyManager.getInstance().generateNeededAlternateParticipantIds(study, _context.getUser());

    // query the study.participant table to verify that the dateoffset and alternateID were generated for the ptid row inserted into the dataset
    TableInfo participantTableInfo = StudySchema.getInstance().getTableInfoParticipant();
    List<ColumnInfo> cols = new ArrayList<>();
    cols.add(participantTableInfo.getColumn("participantid"));
    cols.add(participantTableInfo.getColumn("dateoffset"));
    cols.add(participantTableInfo.getColumn("alternateid"));
    SimpleFilter filter = new SimpleFilter();
    filter.addCondition(participantTableInfo.getColumn("participantid"), "DS1");
    filter.addCondition(participantTableInfo.getColumn("container"), study.getContainer());

    String alternateId = null;

    try (ResultSet rs = QueryService.get().select(participantTableInfo, cols, SimpleFilter.createContainerFilter(study.getContainer()), null))
    {
        // store the ptid date offset and alternate ID for verification later
        int dateOffset = -1;
        while (rs.next())
        {
            if ("DS1".equals(rs.getString("participantid")))
            {
                dateOffset = rs.getInt("dateoffset");
                alternateId = rs.getString("alternateid");
                break;
            }
        }
        assertTrue("Date offset expected to be between 1 and 365", dateOffset > 0 && dateOffset < 366);
        assertNotNull(alternateId);
    }

    // test "exporting" the dataset data using the date shited values and alternate IDs
    Collection<ColumnInfo> datasetCols = new LinkedHashSet<>(datasetTI.getColumns());
    DatasetDataWriter.createDateShiftColumns(datasetTI, datasetCols, study.getContainer());
    DatasetDataWriter.createAlternateIdColumns(datasetTI, datasetCols, study.getContainer());

    try (ResultSet rs = QueryService.get().select(datasetTI, datasetCols, null, null))
    {
        // verify values from the transformed dataset
        assertTrue(rs.next());
        assertNotNull(rs.getString("SubjectId"));
        assertNotEquals("DS1", rs.getString("SubjectId"));
        assertEquals(alternateId, rs.getString("SubjectID"));
        assertTrue(rs.getDate("Date").before((Date) firstRowMap.get("Date")));
        // TODO: calculate the date offset and verify that it matches the firstRowMap.get("Date") value
        assertFalse(rs.next());
    }
}

// TODO
@Test
public void _testAutoIncrement()
{

}


// TODO
@Test
public void _testGuid()
{

}

/**
 * Regression test for 24107
 */
public void testDatasetSubcategory() throws Exception
{
    Container c = _studyDateBased.getContainer();
    User user = _context.getUser();
    Dataset dataset = createDataset(_studyDateBased, "DatasetWithSubcategory", true);
    int datasetId = dataset.getDatasetId();
    ViewCategoryManager mgr = ViewCategoryManager.getInstance();

    ViewCategory category = mgr.ensureViewCategory(c, user, "category");
    ViewCategory subCategory = mgr.ensureViewCategory(c, user, "category", "category");

    DatasetDefinition dd = _manager.getDatasetDefinition(_studyDateBased, dataset.getDatasetId());
    dd = dd.createMutable();

    dd.setCategoryId(subCategory.getRowId());
    dd.save(user);

    DatasetDefinition ds = _studyDateBased.getDataset(datasetId);
    assertEquals((int) ds.getCategoryId(), subCategory.getRowId());

    // clean up
    try (DbScope.Transaction transaction = StudySchema.getInstance().getScope().ensureTransaction())
    {
        dataset.delete(_context.getUser());
        transaction.commit();
    }
}

public void tearDown()
{
    if (null != _studyDateBased)
    {
        // Help track down #34735
//                assertFalse(DbScope.getLabKeyScope().isTransactionActive());
        assertTrue(ContainerManager.delete(_studyDateBased.getContainer(), _context.getUser()));
    }
    if (null != _studyVisitBased)
    {
        // Help track down #34735
//                assertFalse(DbScope.getLabKeyScope().isTransactionActive());
        assertTrue(ContainerManager.delete(_studyVisitBased.getContainer(), _context.getUser()));
    }
}
%>
