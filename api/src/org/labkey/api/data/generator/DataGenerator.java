package org.labkey.api.data.generator;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.exp.query.SamplesSchema;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DuplicateKeyException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryUpdateServiceException;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.User;

import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class DataGenerator
{
    protected Container _container;
    protected User _user;

    public DataGenerator(Container container, User user)
    {
        _container = container;
        _user = user;
    }
    record FieldPrefix(String uri, String namePrefix) { }

    private static final List<FieldPrefix> fieldPrefixes = new ArrayList<>();
    static {
        fieldPrefixes.add(new FieldPrefix("string", "TextField"));
        fieldPrefixes.add(new FieldPrefix("int", "IntField"));
        fieldPrefixes.add(new FieldPrefix("float", "FloatField"));
        fieldPrefixes.add(new FieldPrefix("date", "DateField"));
    }

    public ExpSampleType generateSampleType(String sampleTypeName, @Nullable String namingPattern, int numFields, Logger log) throws ExperimentException, SQLException
    {
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        props.add(new GWTPropertyDescriptor("Name", "string"));
        addDomainProperties(props, numFields);

        SampleTypeService service = SampleTypeService.get();
        log.info(String.format("Creating Sample Type '%s' with %d fields", sampleTypeName, numFields));
        return service.createSampleType(_container, _user, sampleTypeName,
                "Generated sample type", props, List.of(), namingPattern);
    }

    public void generateSamples(ExpSampleType sampleType, int numSamples) throws SQLException, BatchValidationException, QueryUpdateServiceException, DuplicateKeyException
    {
        UserSchema schema = QueryService.get().getUserSchema(_user, _container, SchemaKey.fromParts(SamplesSchema.SCHEMA_NAME));
        generateExpData(numSamples, schema, sampleType.getName(), sampleType.getDomain());
    }

    private List<Map<String, Object>> createRows(int numRows, Domain domain)
    {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 1; i <= numRows; i++)
        {
            Map<String, Object> row = new CaseInsensitiveHashMap<>();
            List<? extends DomainProperty> properties = domain.getProperties();
            for (int p = 0; p < properties.size(); p++)
            {
                DomainProperty property = properties.get(p);
                int dataNum = p + (i % 15);
                Object value = switch (property.getRangeURI())
                        {
                            case "string" -> "Text " + dataNum;
                            case "int" -> dataNum;
                            case "float" -> dataNum * 1.5;
                            case "date" -> randomDate();
                            default -> null;
                        };
                row.put(property.getName(), value);
            }
            rows.add(row);
        }
        return rows;
    }

   public ExpDataClass generateDataClass(String dataClassName, @Nullable String namingPattern, int numFields, Logger log, @Nullable String category) throws ExperimentException, SQLException
    {
        List<GWTPropertyDescriptor> props = new ArrayList<>();
        addDomainProperties(props, numFields);

        ExperimentService service = ExperimentService.get();

        log.info(String.format("Creating Data Class '%s' with %d fields", dataClassName, numFields));
        return service.createDataClass(_container, _user, dataClassName, "Custom data class with " + numFields + " fields",
                    props, List.of(), null,
                    namingPattern, null, category);
    }

    public void generateDataClassObjects(ExpDataClass dataClass, int numObjects) throws SQLException, BatchValidationException, QueryUpdateServiceException, DuplicateKeyException
    {
        UserSchema schema = QueryService.get().getUserSchema(_user, _container, ExpSchema.SCHEMA_EXP_DATA);
        generateExpData(numObjects, schema, dataClass.getName(), dataClass.getDomain());
    }


    private void generateExpData(int numRows, UserSchema schema, String name, Domain domain) throws DuplicateKeyException, BatchValidationException, QueryUpdateServiceException, SQLException
    {
        TableInfo table = schema.getTable(name);
        QueryUpdateService svc = table.getUpdateService();

        List<Map<String, Object>> rows = createRows(numRows, domain);
        BatchValidationException errors = new BatchValidationException();
        svc.insertRows(_user, _container, rows, errors, null, null);
        if (errors.hasErrors())
            throw errors;
    }

    private void addDomainProperties(List<GWTPropertyDescriptor> props, int numFields)
    {
        for (int i = 0; i < numFields; i++)
        {
            int suffix = i / fieldPrefixes.size() + 1;
            var fieldPrefix = fieldPrefixes.get(i % fieldPrefixes.size());
            props.add(new GWTPropertyDescriptor(fieldPrefix.namePrefix() + "_" + suffix, fieldPrefix.uri()));
        }
    }

    public static String randomDate()
    {
        var startDate = new Date(112 /* 2012 */, Calendar.JANUARY, 1);
        var endDate = new Date();

        var random = new Date(ThreadLocalRandom.current().nextLong(startDate.getTime(), endDate.getTime()));
        return new SimpleDateFormat("dd-MMM-yy").format(random);
    }

    public static String randomDouble(int min, int max)
    {
        double random = Math.random() < 0.5 ? ((1-Math.random()) * (max-min) + min) : (Math.random() * (max-min) + min);
        return String.format("%.2f", random);
    }

    public static <T> T randomIndex(T[] array)
    {
        return array[randomInt(0, array.length)];
    }

    public static int randomInt(int min, int max)
    {
        // The maximum is exclusive and the minimum is inclusive
        return (int) Math.round(Math.floor(Math.random() * (max - min) + min));
    }

}
