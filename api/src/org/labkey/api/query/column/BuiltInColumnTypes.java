package org.labkey.api.query.column;

import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.query.QueryService;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

/**
 * These standard columns are not module specific, and need to be recognized by SchemaTableInfo * /
 */
public enum BuiltInColumnTypes
{
    Container(When.insert, JdbcType.GUID, BuiltInColumnTypes.CONTAINERID_CONCEPT_URI, null),
    CreatedBy(When.insert, JdbcType.INTEGER, BuiltInColumnTypes.CREATEDBY_CONCEPT_URI, "Created By"),
    Created(When.insert, JdbcType.TIMESTAMP, BuiltInColumnTypes.CREATED_CONCEPT_URI, "Created"),
    ModifiedBy(When.both, JdbcType.INTEGER, BuiltInColumnTypes.MODIFIEDBY_CONCEPT_URI, "Modified By"),
    Modified(When.both, JdbcType.TIMESTAMP, BuiltInColumnTypes.MODIFIED_CONCEPT_URI, "Modified"),
    EntityId(When.insert, JdbcType.GUID, null, null);

    public enum When
    {
        insert,
        update,
        both
    }

    public final When when;
    public final JdbcType type;
    public final String label;
    public final String conceptURI;

    BuiltInColumnTypes(When when, JdbcType type, String conceptURI, String label)
    {
        this.when = when;
        this.type = type;
        this.label = label;
        this.conceptURI = conceptURI;
    }

    public boolean matches(ColumnInfo c)
    {
        return c.getJdbcType()==type && name().equalsIgnoreCase(c.getName());
    }

    static final Map<String, BuiltInColumnTypes> names;
    static
    {
        CaseInsensitiveHashMap<BuiltInColumnTypes> map = new CaseInsensitiveHashMap<>();
        Arrays.stream(BuiltInColumnTypes.values()).forEach(t -> map.put(t.name(), t));
        map.put("Folder", BuiltInColumnTypes.Container);
        names = Collections.unmodifiableMap(map);
    }

    public static BuiltInColumnTypes findBuiltInType(ColumnInfo col)
    {
        BuiltInColumnTypes type = names.get(col.getName());
        return null != type && type.matches(col) ? type : null;
    }

    public static void registerStandardColumnTransformers()
    {
        QueryService.get().registerColumnInfoTransformer(new UserIdColumnInfoTransformer());
        QueryService.get().registerColumnInfoTransformer(CREATEDBY_CONCEPT_URI, new UserIdColumnInfoTransformer());
        QueryService.get().registerColumnInfoTransformer(MODIFIEDBY_CONCEPT_URI, new UserIdColumnInfoTransformer());
        QueryService.get().registerColumnInfoTransformer(new ContainerIdColumnInfoTransformer());
    }

    public static final String CONTAINERID_CONCEPT_URI = "http://www.labkey.org/types#containerId";         // JdcType.GUID
    public static final String USERID_CONCEPT_URI      = "http://www.labkey.org/types#userId";              // JdbcType.INTEGER
    public static final String CREATEDBY_CONCEPT_URI   = "http://www.labkey.org/types#createdByUserId";    // JdbcType.INTEGER
    public static final String MODIFIEDBY_CONCEPT_URI  = "http://www.labkey.org/types#modifiedByUserId";     // JdbcType.INTEGER
    public static final String CREATED_CONCEPT_URI     = "http://www.labkey.org/types#createdTimestamp";    // JdbcType.TIMESTAMP
    public static final String MODIFIED_CONCEPT_URI    = "http://www.labkey.org/types#modifiedTimestamp";   // JdbcType.TIMESTAMP

    /*
     * EXPOBJECTID_CONCEPT_URI should only be applied to objectid from exp.object table (or columns that copy that value).
     * This type should not be applied by user (e.g. xml override) to other columns, it is used by
     * only used by experiment table method expObjectId() to implement LabKey SQL lineage functionality.
     */
    public static final String EXPOBJECTID_CONCEPT_URI    = "http://www.labkey.org/types#experimentObjectId";   // JbcType.INTEGER
}


/* For reference: places to look for other ConceptURI
    // study
    public static final String PARTICIPANT_CONCEPT_URI = "http://cpas.labkey.com/Study#ParticipantId";
    public static final String VISIT_CONCEPT_URI = "http://cpas.labkey.com/Study#VisitId";
    public static final String SPECIMEN_CONCEPT_URI = "http://cpas.labkey.com/Study#SpecimenId";
    // laboratory service
    static public final String ASSAYRESULT_CONCEPT_URI = "http://cpas.labkey.com/laboratory#assayResult";
    static public final String ASSAYRAWRESULT_CONCEPT_URI = "http://cpas.labkey.com/laboratory#assayRawResult";
    static public final String SAMPLEDATE_CONCEPT_URI = "http://cpas.labkey.com/laboratory#sampleDate";
    static public final String BIRTHDATE_CONCEPT_URI = "http://cpas.labkey.com/laboratory#birthDate";
    static public final String DEATHDATE_CONCEPT_URI = "http://cpas.labkey.com/laboratory#deathDate";
    // ontology
    String conceptCodeConceptURI = "http://www.labkey.org/types#conceptCode";
    // ExpRunItemTableImpl
    public static final String ALIAS_CONCEPT_URI = "http://www.labkey.org/exp/xml#alias";
    // PropertiesDisplayColumn
    public static final String CONCEPT_URI = "http://www.labkey.org/types#properties";
    // ProeprtyType, sometimes used as conceptURI (e.g. expFlag)
    expMultiLine("http://www.w3.org/2001/XMLSchema#multiLine", true, "Multi-Line Text", "String", "string"),
    xsdString("http://www.w3.org/2001/XMLSchema#string", true, "Text (String)", "String", "string"),
    xsdBoolean("http://www.w3.org/2001/XMLSchema#boolean", false, "Boolean", null, "boolean"),
    xsdInt("http://www.w3.org/2001/XMLSchema#int", true, "Integer", null, "int"),
    xsdDouble("http://www.w3.org/2001/XMLSchema#double", true, "Number (Double)", "Double", "float"),
    xsdDateTime("http://www.w3.org/2001/XMLSchema#dateTime", true, "DateTime", null, "date"),
    xsdDate("http://www.w3.org/2001/XMLSchema#date", true, "Date", null, "date"),
    xsdTime("http://www.w3.org/2001/XMLSchema#time", true, "Time", null, "date"),
    expFileLink("http://cpas.fhcrc.org/exp/xml#fileLink", false, "File"),
    expAttachment("http://www.labkey.org/exp/xml#attachment", false, "Attachment"),
    expFlag("http://www.labkey.org/exp/xml#flag", false, "Flag (String)"),
    xsdFloat("http://www.w3.org/2001/XMLSchema#float", true, "Number (Float)", "Float", "float"),
    xsdDecimal("http://www.w3.org/2001/XMLSchema#decimal", true, "Number (Decimal)", "Decimal", "float"),
    xsdLong("http://www.w3.org/2001/XMLSchema#long", true, "Long Integer", "Long", "int"),
    xsdBinary("http://www.w3.org/2001/XMLSchema#binary", false, "Byte Buffer", "Buffer", "string");
*/
