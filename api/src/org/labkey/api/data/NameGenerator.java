/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
package org.labkey.api.data;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.exp.Identifiable;
import org.labkey.api.exp.LsidManager;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataClass;
import org.labkey.api.exp.api.ExpLineage;
import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpRunItem;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.SampleTypeService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.RuntimeValidationException;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.GUID;
import org.labkey.api.util.JunitUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.StringExpressionFactory.AbstractStringExpression.NullValueBehavior;
import org.labkey.api.util.StringExpressionFactory.FieldKeyStringExpression;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.SubstitutionFormat;
import org.labkey.api.util.Tuple3;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.Time;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.labkey.api.exp.api.ExpRunItem.INPUT_PARENT;
import static org.labkey.api.exp.api.ExpRunItem.PARENT_IMPORT_ALIAS_MAP_PROP;
import static org.labkey.api.util.SubstitutionFormat.dailySampleCount;
import static org.labkey.api.util.SubstitutionFormat.monthlySampleCount;
import static org.labkey.api.util.SubstitutionFormat.weeklySampleCount;
import static org.labkey.api.util.SubstitutionFormat.yearlySampleCount;

public class NameGenerator
{
    /**
     * full expression: ${NamePrefix:withCounter(counterStartIndex?: number, counterNumberFormat?: string, extraparam: enum)}
     * use regex to match the content inside the outer ${}
     * Examples:
     *  ${AliquotedFrom}-:withCounter   : parentSample-1
     *  ${AliquotedFrom}-${SourceParent/Property}-:withCounter  : parentSample-parentSource-1
     *  ${AliquotedFrom}.:withCounter() : parentSample.1
     *  ${AliquotedFrom}-:withCounter(1000) : parentSample-1000
     *  ${AliquotedFrom}-:withCounter(1, '000') : parentSample-001
     *  ${AliquotedFrom}-:withCounter(1, '000', NoGap) : parentSample-001
     */
    public static final String WITH_COUNTER_REGEX = "(.+):withCounter\\(?(\\d*)?,?\\s*'?(\\d*)?'?,?\\s*'?([a-zA-Z]*)?'?\\)?";
    public static final Pattern WITH_COUNTER_PATTERN = Pattern.compile(WITH_COUNTER_REGEX, Pattern.CASE_INSENSITIVE);
    public static final String WITH_COUNTER_NO_GAP_PARAM = "NoGap"; // named parameter to enforce continuity in sequence

    public static final String EXPERIMENTAL_ALLOW_GAP_COUNTER = "AllowCounterGap";

    /**
     * Examples:
     *  ${genId:minValue(100)}
     *  ${genId:minValue('100')}
     *  ${sampleCount:minValue(10)}
     */
    public static final String WITH_START_IND_REGEX = ".*\\$\\{%s:minValue\\('?(\\d*)?'?\\).*";

    /**
     * Ancestor lookup example:
     * ..[MaterialInputs]
     * ..[DataInputs]
     * ..[MaterialInputs/SampleType1]
     * ..[MaterialInputs::SampleType1]
     */
    public static final String ANCESTOR_INPUT_PREFIX_MATERIAL = "..[MaterialInputs/";
    public static final String ANCESTOR_INPUT_PREFIX_MATERIAL_NOSLASH = "..[MaterialInputs::";
    public static final String ANCESTOR_INPUT_PREFIX_DATA = "..[DataInputs/";
    public static final String ANCESTOR_INPUT_PREFIX_DATA_NOSLASH = "..[DataInputs::";
    public static final String ANCESTOR_INPUT_REGEX = "\\.\\.\\[((Material|Data)Inputs(/|::)?(.*))]";
    public static final Pattern ANCESTOR_INPUT_PATTERN = Pattern.compile(ANCESTOR_INPUT_REGEX);

    public static Date PREVIEW_DATETIME_VALUE;
    public static java.sql.Date PREVIEW_DATE_VALUE;
    public static java.sql.Time PREVIEW_TIME_VALUE;
    public static Date PREVIEW_MODIFIED_DATE_VALUE;

    static
    {
        try
        {
            PREVIEW_DATETIME_VALUE = new SimpleDateFormat("yyyy/MM/dd HH:mm").parse("2021/04/28 08:30");
            PREVIEW_DATE_VALUE = new java.sql.Date(PREVIEW_DATETIME_VALUE.getTime());
            PREVIEW_TIME_VALUE = new java.sql.Time(PREVIEW_DATETIME_VALUE.getTime());
            PREVIEW_MODIFIED_DATE_VALUE = new SimpleDateFormat("yyyy/MM/dd").parse("2021/05/11");
        }
        catch (ParseException e)
        {
            PREVIEW_DATE_VALUE = null;
            PREVIEW_DATETIME_VALUE = null;
            PREVIEW_TIME_VALUE = null;
            PREVIEW_MODIFIED_DATE_VALUE = null;
        }
    }

    public static final String COUNTER_SEQ_PREFIX = "NameGenCounter-";

    public enum EntityCounter
    {
       genId,
       rootSampleCount,
       sampleCount
    }

    public enum SubstitutionValue
    {
        AliquotedFrom("Sample112"),
        DataInputs("Data101"),
        Inputs("Parent101"),
        MaterialInputs("Sample101"),
        batchRandomId(3294),
        containerPath("containerPathValue"),
        contextPath("contextPathValue"),
        sampleCount(240),
        rootSampleCount(124),
        dailySampleCount(14), // sample counts can both be SubstitutionValue as well as modifiers
        dataRegionName("dataRegionNameValue"),
        genId(1001),
        monthlySampleCount(150),
        now(null)
                {
                    @Override
                    public Object getPreviewValue()
                    {
                        return PREVIEW_DATETIME_VALUE;
                    }
                },
        queryName("queryNameValue"),
        randomId(3294),
        schemaName("schemaNameValue"),
        schemaPath("schemaPathValue"),
        selectionKey("selectionKeyValue"),
        weeklySampleCount(25),
        withCounter(null), // see CounterExpressionPart.getValue
        yearlySampleCount(412),
        folderPrefix("folderPrefixValue");

        private final Object _previewValue;

        SubstitutionValue(Object previewValue)
        {
            _previewValue = previewValue;
        }

        public Object getPreviewValue()
        {
            return _previewValue;
        }

        public static Map<String, Object> getValuesMap()
        {
            Map<String, Object> values = new CaseInsensitiveHashMap<>();
            for (SubstitutionValue substitutionValue : SubstitutionValue.values())
            {
                values.put(substitutionValue.name(), substitutionValue.getPreviewValue());
            }

            return values;
        }

    }

    public static final Set<String> SAMPLE_COUNTER_SUBSTITUTIONS = new HashSet<>(Arrays.asList(SubstitutionValue.dailySampleCount.name(), SubstitutionValue.weeklySampleCount.name(), SubstitutionValue.monthlySampleCount.name(), SubstitutionValue.yearlySampleCount.name()));

    private final TableInfo _parentTable;

    public FieldKeyStringExpression getParsedNameExpression()
    {
        return _parsedNameExpression;
    }

    private final FieldKeyStringExpression _parsedNameExpression;


    public record SampleNameExpressionSummary(boolean hasProjectSampleCounter, boolean hasProjectSampleRootCounter, long minProjectSampleCounter, long minProjectSampleRootCounter) {};

    public record ExpressionSummary(SampleNameExpressionSummary sampleSummary, boolean hasDateBasedSampleCounter, boolean hasLineageInputs, boolean hasLineageLookup) {};

    // extracted from name expression after parsing
    private ExpressionSummary _expressionSummary;
    private Map<FieldKey, TableInfo> _exprLookups = Collections.emptyMap();
    private Map<String, List<String>> _expLineageLookupFields = new CaseInsensitiveHashMap<>();
    private Map<String/*part field key*/, NameExpressionAncestorPartOption> _partAncestorOptions;

    private final Map<String, ExpSampleType> _sampleTypes = new HashMap<>();
    private final Map<String, ExpDataClass> _dataClasses = new HashMap<>();
    private final Map<Integer, ExpMaterial> materialCache = new HashMap<>();
    private final Map<Integer, ExpData> dataCache = new HashMap<>();
    private RemapCache renameCache;
    private final Map<String, Map<String, Object>> objectPropertiesCache = new HashMap<>();

    private final Container _container;

    private final boolean _validateSyntax;
    private final List<String> _syntaxErrors = new ArrayList<>();
    private final List<String> _syntaxWarnings = new ArrayList<>();
    private String _previewName;

    private final List<? extends GWTPropertyDescriptor> _domainProperties; // used for name expression validation at creation time, before the tableInfo is available

    private final String _currentDataTypeName; // used for name expression validation/preview at creation time, before the SampleType or DataClass is created

    public NameGenerator(@NotNull String nameExpression, @Nullable TableInfo parentTable, boolean allowSideEffects, @Nullable Map<String, String> importAliases, @Nullable Container container, Function<String, Long> getNonConflictCountFn, String counterSeqPrefix, boolean validateSyntax, @Nullable List<? extends GWTPropertyDescriptor> domainProperties, String currentDataTypeName, boolean allBulkRemapCache)
    {
        _parentTable = parentTable;
        _container = container;
        _parsedNameExpression = NameGenerationExpression.create(nameExpression, false, NullValueBehavior.ReplaceNullWithBlank, allowSideEffects, container, getNonConflictCountFn, counterSeqPrefix, validateSyntax);
        _validateSyntax = validateSyntax;
        _domainProperties = domainProperties;
        _currentDataTypeName = currentDataTypeName;
        renameCache = new RemapCache(allBulkRemapCache);
        initialize(importAliases);
    }

    public NameGenerator(@NotNull String nameExpression, @Nullable TableInfo parentTable, boolean allowSideEffects, @Nullable Map<String, String> importAliases, @Nullable Container container, Function<String, Long> getNonConflictCountFn, String counterSeqPrefix, boolean validateSyntax, @Nullable List<? extends GWTPropertyDescriptor> domainProperties, String currentDataTypeName)
    {
        this(nameExpression, parentTable, allowSideEffects, importAliases, container, getNonConflictCountFn, counterSeqPrefix, validateSyntax, domainProperties, currentDataTypeName, false);
    }

    public NameGenerator(@NotNull String nameExpression, @Nullable TableInfo parentTable, boolean allowSideEffects, @Nullable Map<String, String> importAliases, @Nullable Container container, Function<String, Long> getNonConflictCountFn, String counterSeqPrefix, boolean validateSyntax, @Nullable List<? extends GWTPropertyDescriptor> domainProperties)
    {
        this(nameExpression, parentTable, allowSideEffects, importAliases, container, getNonConflictCountFn, counterSeqPrefix, validateSyntax, domainProperties, null);
    }

    public NameGenerator(@NotNull String nameExpression, @Nullable TableInfo parentTable, boolean allowSideEffects, @Nullable Map<String, String> importAliases, @Nullable Container container, Function<String, Long> getNonConflictCountFn, String counterSeqPrefix)
    {
        this(nameExpression, parentTable, allowSideEffects, importAliases, container, getNonConflictCountFn, counterSeqPrefix, false, null);
    }

    public NameGenerator(@NotNull String nameExpression, @Nullable TableInfo parentTable, boolean allowSideEffects, Container container, Function<String, Long> getNonConflictCountFn, String counterSeqPrefix)
    {
        this(nameExpression, parentTable, allowSideEffects, null, container, getNonConflictCountFn, counterSeqPrefix);
    }

    public NameGenerator(@NotNull FieldKeyStringExpression nameExpression, @Nullable TableInfo parentTable, @Nullable Container container)
    {
        _parentTable = parentTable;
        _parsedNameExpression = nameExpression;
        _container = container;
        _validateSyntax = false;
        _domainProperties = null;
        _currentDataTypeName = null;
        renameCache = new RemapCache(true);
        initialize(null);
    }

    public NameGenerator(@NotNull FieldKeyStringExpression nameExpression, @Nullable TableInfo parentTable)
    {
        this(nameExpression, parentTable, null);
    }

    public ExpressionSummary getExpressionSummary()
    {
        return _expressionSummary;
    }

    public void setExpressionSummary(ExpressionSummary expressionSummary)
    {
        _expressionSummary = expressionSummary;
    }

    public List<String> getSyntaxErrors()
    {
        return _syntaxErrors;
    }

    public List<String> getSyntaxWarnings()
    {
        return _syntaxWarnings;
    }

    public String getPreviewName()
    {
        return _previewName;
    }

    public void setPreviewName(String previewName)
    {
        _previewName = previewName;
    }

    public static NameExpressionValidationResult getValidationMessages(@Nullable String currentDataTypeName, @NotNull String nameExpression, @Nullable List<? extends GWTPropertyDescriptor> properties, @Nullable Map<String, String> importAliases, @NotNull Container container)
    {
        List<String> errorMessages = getMismatchedTagErrors(nameExpression);
        Pair<List<String>, List<String>> reservedFieldResults = getReservedFieldValidationResults(nameExpression);
        errorMessages.addAll(reservedFieldResults.first);
        List<String> warningMessages = new ArrayList<>(reservedFieldResults.second);
        if (!errorMessages.isEmpty())
            return new NameExpressionValidationResult(errorMessages, warningMessages, null);

        warningMessages.addAll(getFieldMissingBracesWarnings(nameExpression, properties, importAliases));

        NameExpressionValidationResult fieldMessages = getSubstitutionPartValidationResults(nameExpression, properties, importAliases, container, currentDataTypeName);
        errorMessages.addAll(fieldMessages.errors());
        warningMessages.addAll(fieldMessages.warnings());
        return new NameExpressionValidationResult(errorMessages, warningMessages, fieldMessages.previews());
    }

    static NameExpressionValidationResult getSubstitutionPartValidationResults(@NotNull String nameExpression, @Nullable List<? extends GWTPropertyDescriptor> properties, @Nullable Map<String, String> importAliases, @NotNull Container container, @Nullable String currentDataTypeName)
    {
        NameGenerator generator = new NameGenerator(nameExpression, null,true, importAliases, container, null, null, true, properties, currentDataTypeName);
        return new NameExpressionValidationResult(generator.getSyntaxErrors(), generator.getSyntaxWarnings(), generator.getPreviewName() != null ? Collections.singletonList(generator.getPreviewName()) : null);
    }

    static List<String> getFieldMissingBracesWarnings(@NotNull String nameExpression, @Nullable List<? extends GWTPropertyDescriptor> properties, @Nullable Map<String, String> importAliases)
    {
        Set<String> substitutionFields = new CaseInsensitiveHashSet();
        if (importAliases != null)
            substitutionFields.addAll(importAliases.keySet());

        if (properties != null)
            properties.forEach(field -> substitutionFields.add(field.getName()));

        substitutionFields.remove(null);

        if (substitutionFields.isEmpty())
            return Collections.emptyList();

        List<String> warningMessages = new ArrayList<>();
        String lcExpression = nameExpression.toLowerCase();
        String allFieldsLc = StringUtils.join(substitutionFields, "\n").toLowerCase();
        for (String subField : substitutionFields)
        {
            String lcSub = subField.toLowerCase();
            int lcIndex = lcExpression.indexOf(lcSub);

            if (lcIndex != -1)
            {
                if (lcIndex > 0)
                {
                    char preChar = lcExpression.charAt(lcIndex - 1);
                    if (Character.isLetter(preChar))
                        continue;
                    else
                    {
                        // Check if expression is substring of another expression, which is enclosed by ${}.
                        // If both 'Exp Name' and 'Name' fields are present, ${Exp Name} should by pass check on 'Name' field.
                        if (StringUtils.countMatches(allFieldsLc, lcSub) >= 2)
                        {
                            String prevStr = nameExpression.substring(0, lcIndex);
                            int prevOpenCount = StringUtils.countMatches(prevStr, "${");
                            int prevCloseCount = StringUtils.countMatches(prevStr, "}");
                            if ((prevOpenCount - prevCloseCount) == 1)
                                continue;
                        }

                    }
                }
                if (lcExpression.length() > (lcIndex + lcSub.length()))
                {
                    char postChar = lcExpression.charAt(lcIndex + lcSub.length());
                    if (Character.isLetter(postChar))
                        continue;
                }

                warningMessages.addAll(SubstitutionFormat.validateNonFunctionalSyntax(subField, nameExpression, lcIndex, "field", true));
            }
        }

        return warningMessages;

    }

    static Pair<List<String>, List<String>> getReservedFieldValidationResults(String nameExpression)
    {
        // For each substitution format, find its location in the string
        // validate punctuation and arguments.

        List<String> warningMessages = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();
        String lcExpression = nameExpression.toLowerCase();
        SubstitutionFormat.getFormatNames().forEach(formatName -> {
            String lcFormatName = formatName.toLowerCase();
            int lcIndex = lcExpression.indexOf(":" + lcFormatName);
            if (lcIndex > -1)
                errorMessages.addAll(SubstitutionFormat.validateSyntax(formatName, nameExpression, lcIndex));
        });
        for (SubstitutionValue subValue : SubstitutionValue.values())
        {
            String lcSub = subValue.name().toLowerCase();
            int lcIndex = lcExpression.indexOf(lcSub);
            if (lcIndex != -1)
            {
                 /*
                 * also check that the part is not preceded by an alphabetic letter
                 * - "unknown" contains "now", but should bypass reserved key word check
                 * - "DataInputs" contains "Input", but should bypass "Input" check
                 */
                if (lcIndex > 0)
                {
                    char preChar = lcExpression.charAt(lcIndex - 1);
                    if (Character.isLetter(preChar))
                        continue;
                }
                if (lcExpression.length() > (lcIndex + lcSub.length()))
                {
                    char postChar = lcExpression.charAt(lcIndex + lcSub.length());
                    if (Character.isLetter(postChar))
                        continue;
                }

                if (subValue.equals(SubstitutionValue.withCounter))
                {
                    Pair<List<String>, List<String>> withCounterResults = validateWithCounterSyntax(nameExpression, lcIndex);
                    errorMessages.addAll(withCounterResults.first);
                    warningMessages.addAll(withCounterResults.second);
                }
                else
                {
                    warningMessages.addAll(SubstitutionFormat.validateNonFunctionalSyntax(subValue.name(), nameExpression, lcIndex));
                }
            }
        }
        return new Pair<>(errorMessages, warningMessages);
    }

    static Pair<List<String>, List<String>> validateWithCounterSyntax(String nameExpression, int index)
    {
        List<String> warningMessages = new ArrayList<>();
        List<String> errorMessages = new ArrayList<>();
        int start = index;

        // check withCount is inside ${}
        String prevStr = nameExpression.substring(0, index);
        int prevOpenCount = StringUtils.countMatches(prevStr, "${");
        int prevCloseCount = StringUtils.countMatches(prevStr, "}");
        String postStr = nameExpression.substring(index);
        int postOpenCount = StringUtils.countMatches(postStr, "${");
        int postCloseCount = StringUtils.countMatches(postStr, "}");
        if ((prevOpenCount - prevCloseCount) != 1 || (postCloseCount - postOpenCount) != 1)
            warningMessages.add(String.format("The '%s' substitution pattern starting at position %d should be enclosed in ${}.", SubstitutionValue.withCounter.name(), start));

        if (nameExpression.charAt(index-1) != ':')
            warningMessages.add(String.format("The '%s' substitution pattern starting at position %d should be preceded by a colon.", SubstitutionValue.withCounter.name(), start));
        else
            start = start-1;

        int startParen = index + SubstitutionValue.withCounter.name().length();
        if (startParen >= nameExpression.length() || nameExpression.charAt(startParen) != '(')
            return new Pair<>(errorMessages, warningMessages);

        int endParen = nameExpression.indexOf(")", start);
        if (endParen == -1)
            errorMessages.add(String.format("No ending parentheses found for the '%s' substitution pattern starting at index %d.", SubstitutionValue.withCounter.name(), start));
        else
        {
            int commaIndex = nameExpression.indexOf(",", start);
            int firstQuoteIndex = nameExpression.indexOf("'", commaIndex + 1);
            int secondQuoteIndex = firstQuoteIndex == -1 ? -1 : nameExpression.indexOf("'", firstQuoteIndex + 1);
            int secondCommaIndex = -1;
            if (secondQuoteIndex > -1)
                secondCommaIndex = nameExpression.indexOf(",", secondQuoteIndex + 1);
            else if (commaIndex > -1)
                secondCommaIndex = nameExpression.indexOf(",", commaIndex + 1);
            String startVal = null;
            String format = null;
            String thirdParam = null;
            try
            {
                if (secondCommaIndex > -1 && secondCommaIndex < endParen)
                {
                    // 3 arguments
                    startVal = nameExpression.substring(startParen + 1, commaIndex).trim();
                    if (firstQuoteIndex > -1 && secondQuoteIndex > firstQuoteIndex)
                        format = nameExpression.substring(firstQuoteIndex, secondQuoteIndex + 1).trim();
                    thirdParam = nameExpression.substring(secondCommaIndex + 1, endParen).trim();
                }
                else if (commaIndex > startParen && commaIndex < endParen)
                {
                    // two arguments
                    startVal = nameExpression.substring(startParen + 1, commaIndex).trim();
                    format = nameExpression.substring(commaIndex + 1, endParen).trim();
                }
                else
                {
                    startVal = nameExpression.substring(startParen + 1, endParen).trim();
                }
            }
            catch (StringIndexOutOfBoundsException e)
            {
                errorMessages.add(String.format("Invalid 'withCounter' expression starting at position %d", index));
            }
            // find the value of the first argument, if any, and validate it is an integer
            if (!StringUtils.isEmpty(startVal))
            {
                try
                {
                    Integer.parseInt(startVal);
                }
                catch (NumberFormatException e)
                {
                    errorMessages.add(String.format("Invalid starting value %s for 'withCounter' starting at position %d.", startVal, index));
                }
            }
            if (!StringUtils.isEmpty(format))
            {
                if (format.charAt(0) != '\'' || format.charAt(format.length()-1) != '\'')
                    errorMessages.add(String.format("Format string starting at position %d for 'withCounter' substitution pattern should be enclosed in single quotes.", commaIndex + 1));
            }
            if (!StringUtils.isEmpty(thirdParam))
            {
                if (!(WITH_COUNTER_NO_GAP_PARAM.equalsIgnoreCase(thirdParam)))
                    errorMessages.add(String.format("Param at position %d for 'withCounter' substitution pattern is invalid. Supported params include: " + WITH_COUNTER_NO_GAP_PARAM + ".", commaIndex + 1));
            }
        }
        return new Pair<>(errorMessages, warningMessages);
    }

    static List<String> getMismatchedTagErrors(String nameExpression)
    {
        int start = 0;
        int openIndex;
        final String openTag = "${";
        final char closeTag = '}';
        List<String> errors = new ArrayList<>();
        List<Integer> unmatchedOpen = new ArrayList<>();
        List<Integer> unmatchedClosed = new ArrayList<>();
        LinkedList<Integer> openIndexes = new LinkedList<>();

        while (start < nameExpression.length() && (openIndex = nameExpression.indexOf(openTag, start)) >= 0)
        {
            openIndexes.clear();
            openIndexes.push(openIndex);
            int subInd = openIndex + 2;

            while (subInd < nameExpression.length())
            {
                int nextOpen = nameExpression.indexOf(openTag, subInd);
                int nextClose = nameExpression.indexOf(closeTag, subInd);

                // no more opens or closes
                if (nextOpen == -1 && nextClose == -1)
                    break;

                // more opens but no more closes, continue in order to pick up all the open indexes
                if (nextOpen > 0 && nextClose == -1)
                {
                    openIndexes.add(nextOpen);
                    subInd = nextOpen + 2;
                }
                else if (nextOpen == -1 || nextClose < nextOpen)
                {
                    if (openIndexes.isEmpty()) // Can this actually happen?
                        unmatchedClosed.add(nextClose);
                    else
                    {
                        openIndexes.pop();
                        subInd = nextClose + 1;
                    }
                }
                else if (nextClose > nextOpen)
                {
                    openIndexes.push(nextOpen);
                    subInd = nextOpen + 2;
                }

                if (openIndexes.isEmpty())
                    break;
            }

            if (!openIndexes.isEmpty())
                unmatchedOpen.addAll(openIndexes.stream().map(index -> index+1).collect(Collectors.toList()));
            start = subInd;
        }
        if (!unmatchedOpen.isEmpty())
        {
            if (unmatchedOpen.size() == 1)
                errors.add("No closing brace found for the substitution pattern starting at position " + unmatchedOpen.get(0) + ".");
            else
                errors.add("No closing braces found for the substitution patterns starting at positions " + StringUtils.join(unmatchedOpen, ", ") + ".");
        }
        if (!unmatchedClosed.isEmpty())
        {
            errors.add("Unmatched closing brace found at position" + (unmatchedClosed.size() == 1 ? " " : "s ") + StringUtils.join(unmatchedClosed, ", ") + ".");
        }
        return errors;
    }

    public static Stream<String> parentNames(Object value, String parentColName)
    {
        if (value == null)
            return Stream.empty();

        Stream<String> values;
        if (value instanceof String || value instanceof Number)
        {
            String valueStr = value instanceof String ? (String) value : value.toString();
            if (StringUtils.isEmpty((valueStr).trim()))
                return Stream.empty();

            // Issue 44841: The names of the parents may include commas, so we parse the set of parent names
            // using TabLoader instead of just splitting on the comma.
            try (TabLoader tabLoader = new TabLoader(valueStr))
            {
                tabLoader.setDelimiterCharacter(',');
                tabLoader.setUnescapeBackslashes(false);
                try
                {
                    String[][] parsedValues = tabLoader.getFirstNLines(1);
                    values = Arrays.stream(parsedValues[0]);
                }
                catch (IOException e)
                {
                    throw new IllegalStateException("Unable to parse parent names from " + valueStr, e);
                }
            }
        }
        else if (value instanceof Collection)
        {
            Collection<?> coll = (Collection)value;
            values = coll.stream().map(String::valueOf);
        }
        else if (value instanceof JSONArray jsonArray)
        {
            values = jsonArray.toList().stream().map(String::valueOf);
        }
        else
        {
            throw new IllegalStateException("For parent values in naming pattern, expected string or collection for '" + parentColName + "': " + value);
        }

        return values
                .map(String::trim)
                .filter(s -> !s.isEmpty());
    }

    public static boolean isLineageInput(Object token, @Nullable Map<String, String> importAliases, @Nullable String currentDataTypeName, Container container, User user)
    {
        return isLineageToken(token, importAliases) || isLineageInputWithDataType(token.toString().split("/"), currentDataTypeName, container, user);
    }

    public static boolean isLineageLookup(List<String> fieldParts, @Nullable Map<String, String> importAliases, @Nullable String currentDataTypeName, Container container, User user)
    {
        if (!isLineageToken(fieldParts.get(0), importAliases))
            return false;

        if (fieldParts.size() == 2 && isLineageInputWithDataType(fieldParts.toArray(String[]::new), currentDataTypeName, container, user))
        {
            return false;
        }

        return true;
    }

    public static boolean isProjectSampleCountToken(FieldKey token)
    {
        return SubstitutionFormat.sampleCount.name().equalsIgnoreCase(token.toString());
    }

    public static boolean isProjectRootSampleCountToken(FieldKey token)
    {
        return SubstitutionFormat.rootSampleCount.name().equalsIgnoreCase(token.toString());
    }

    public static boolean isLineageToken(Object token, @Nullable Map<String, String> importAliases)
    {
        String sTok = token.toString();
        Map<String, String> aliasesInsensitive = new CaseInsensitiveHashMap<>();
        if (importAliases != null)
            aliasesInsensitive.putAll(importAliases);

        return INPUT_PARENT.equalsIgnoreCase(sTok)
                || ExpData.DATA_INPUT_PARENT.equalsIgnoreCase(sTok)
                || ExpMaterial.MATERIAL_INPUT_PARENT.equalsIgnoreCase(sTok)
                || aliasesInsensitive.containsKey(sTok);
    }

    public static boolean isLineageInputWithDataType(String[] parts, @Nullable String currentDataTypeName, Container container, User user)
    {
        if (parts.length != 2)
            return false;

        String inputToken = parts[0];
        String dataType = parts[1];

        boolean isInput = INPUT_PARENT.equalsIgnoreCase(inputToken);
        boolean isData = ExpData.DATA_INPUT_PARENT.equalsIgnoreCase(inputToken);
        boolean isMaterial = ExpMaterial.MATERIAL_INPUT_PARENT.equalsIgnoreCase(inputToken);

        if (!(isInput || isData || isMaterial))
            return false;

        if (dataType.equalsIgnoreCase(currentDataTypeName))
            return true;

        if (isMaterial || isInput)
        {
            if (SampleTypeService.get().getSampleType(container, user, dataType) != null)
                return true;

            if (isMaterial)
                return false;
        }

        return ExperimentService.get().getDataClass(container, user, dataType) != null;
    }

    private Object getLineageLookupTokenPreview(String currentDataType, FieldKey fkTok, String inputPrefix, @Nullable String inputDataType, List<Pair<ExpLineageOptions.LineageExpType, String>> ancestorPaths, String lookupField, User user, Map<String, String> dataClassNames, Map<String, String> sampleTypeNames)
    {
        boolean isMaterial = inputPrefix.toLowerCase().startsWith("materialinputs") || inputPrefix.toLowerCase().startsWith("inputs");
        boolean isData = inputPrefix.toLowerCase().startsWith("datainputs") || inputPrefix.toLowerCase().startsWith("inputs");
        boolean isAncestor = ancestorPaths != null && !ancestorPaths.isEmpty();
        if (isAncestor)
        {
            Pair<ExpLineageOptions.LineageExpType, String> ancestorType = ancestorPaths.get(ancestorPaths.size() - 1);
            isMaterial = ExpLineageOptions.LineageExpType.Material == ancestorType.first;
            isData = ExpLineageOptions.LineageExpType.Data == ancestorType.first;
            if (!StringUtils.isEmpty(ancestorType.second))
                inputDataType = isMaterial ? sampleTypeNames.get(ancestorType.second) : dataClassNames.get(ancestorType.second);
            else
                inputDataType = null;
        }
        switch (lookupField.toLowerCase())
        {
            case "rowid":
            case "createdby":
            case "modifiedby":
                return 1005;
            case "name":
            case "lsid":
            case "description":
                return (isAncestor ? "ancestor" : "parent") + lookupField;
            case "created":
                return PREVIEW_DATETIME_VALUE;
            case "modified":
                return PREVIEW_MODIFIED_DATE_VALUE;
        }

        List<ExpObject> dataTypes = new ArrayList<>();
        if (isMaterial)
        {
            if (!StringUtils.isEmpty(inputDataType))
            {
                ExpSampleType sampleType = SampleTypeService.get().getSampleType(_container, user, inputDataType);
                if (sampleType != null)
                    dataTypes.add(sampleType);
            }
            else
                dataTypes.addAll(SampleTypeService.get().getSampleTypes(_container, user, true));
        }
        if (isData)
        {
            if (!StringUtils.isEmpty(inputDataType))
            {
                ExpDataClass dataClass = ExperimentService.get().getDataClass(_container, user, inputDataType);
                if (dataClass != null)
                    dataTypes.add(dataClass);
            }
            else
                dataTypes.addAll(ExperimentService.get().getDataClasses(_container, user, true));
        }

        boolean isCurrentDataType = inputDataType != null && inputDataType.equals(currentDataType);

        if (inputDataType != null && dataTypes.isEmpty())
        {
            if (!isCurrentDataType)
            {
                _syntaxErrors.add("Invalid lineage lookup: " + fkTok.toString() + ".");
                return null;
            }
        }

        for (ExpObject dataType : dataTypes)
        {
            Domain domain = null;
            if (dataType instanceof ExpSampleType)
            {
                domain = ((ExpSampleType)dataType).getDomain();
            }
            else if (dataType instanceof ExpDataClass)
            {
                domain = ((ExpDataClass)dataType).getDomain();
            }
            if (domain != null)
            {
                List<? extends DomainProperty> domainProperties = domain.getProperties();
                for (DomainProperty domainProperty : domainProperties)
                {
                    if (domainProperty.getName().equalsIgnoreCase(lookupField))
                    {
                        Object result = getNamePartPreviewValue(domainProperty.getPropertyType(), lookupField);
                        if (result instanceof String)
                            return (isAncestor ? "ancestor" : "parent") + result;
                        return result;
                    }
                }
            }

        }

        if ((isCurrentDataType || inputDataType == null) && _domainProperties != null && !_domainProperties.isEmpty())
        {
            Map<String, GWTPropertyDescriptor> domainFields = new CaseInsensitiveHashMap<>();
            _domainProperties.forEach(prop -> domainFields.put(prop.getName(), prop));

            GWTPropertyDescriptor col = domainFields.get(lookupField);
            if (col != null)
            {
                PropertyType pt = null;
                if (col.getConceptURI() != null || col.getRangeURI() != null)
                    pt = PropertyType.getFromURI(col.getConceptURI(), col.getRangeURI(), null);

                if (pt != null)
                    return getNamePartPreviewValue(pt, lookupField);
                else
                {
                    _syntaxErrors.add("Invalid lineage lookup: " + fkTok.toString() + ".");
                    return null;
                }
            }
        }

        _syntaxErrors.add("Lineage lookup field does not exist: " + fkTok.toString());
        return null;
    }

    // Inspect the expression looking for:
    //   (a) any sample counter formats bound to a column, e.g. ${column:dailySampleCount}
    //   (b) any lineage input tokens
    //   (c) any replacement tokens that include a lookup, e.g., ${foo/bar}
    private void initialize(@Nullable Map<String, String> importAliases)
    {
        assert _parsedNameExpression != null;

        boolean hasDateBasedSampleCounterFormat = false;
        boolean hasLineageInputs = false;
        boolean hasLineageLookup = false;
        boolean hasProjectSampleCounter = false;
        boolean hasProjectSampleRootCounter = false;
        List<FieldKey> lookups = new ArrayList<>();
        Map<String, List<String>> lineageLookupFields = new CaseInsensitiveHashMap<>();
        Set<String> substitutionValues = new CaseInsensitiveHashSet();
        for (SubstitutionValue value : SubstitutionValue.values())
        {
            substitutionValues.add(value.name());
        }

        Map<String, GWTPropertyDescriptor> domainFields = new CaseInsensitiveHashMap<>();
        if (_domainProperties != null && !_domainProperties.isEmpty())
            _domainProperties.forEach(prop -> domainFields.put(prop.getName(), prop));
        User user = User.getSearchUser();

        // check for all parts, including those in sub nested expressions
        List<StringExpressionFactory.StringPart> parts = _parsedNameExpression.getDeepParsedExpression();

        Map<String, Object> previewCtx = new CaseInsensitiveHashMap<>();
        if (_validateSyntax)
        {
            previewCtx.putAll(SubstitutionValue.getValuesMap());
            if (importAliases != null)
            {
                for (String alias : importAliases.keySet())
                {
                    previewCtx.put(alias, SubstitutionValue.Inputs.getPreviewValue());
                }
            }
        }

        Map<String, NameExpressionAncestorPartOption> partAncestorOptions = new HashMap<>();

        Map<String, String> dataClassLSIDs = new CaseInsensitiveHashMap<>();
        Map<String, String> dataClassNames = new CaseInsensitiveHashMap<>();
        Map<String, String> sampleTypeLSIDs = new CaseInsensitiveHashMap<>();
        Map<String, String> sampleTypeNames = new CaseInsensitiveHashMap<>();

        if (_container != null)
        {
            for (ExpDataClass dataClass : ExperimentService.get().getDataClasses(_container, user, true))
            {
                dataClassLSIDs.put(dataClass.getName(), dataClass.getLSID());
                dataClassNames.put(dataClass.getLSID(), dataClass.getName());
            }


            for (ExpSampleType sampleType : SampleTypeService.get().getSampleTypes(_container, user, true))
            {
                sampleTypeLSIDs.put(sampleType.getName(), sampleType.getLSID());
                sampleTypeNames.put(sampleType.getLSID(), sampleType.getName());
            }
        }

        for (StringExpressionFactory.StringPart part : parts)
        {
            boolean isLineagePart = false;
            if (!part.isConstant())
            {
                Object token = part.getToken();
                Collection<SubstitutionFormat> formats = part.getFormats();
                for (SubstitutionFormat format : formats)
                {
                    if (format == dailySampleCount || format == weeklySampleCount || format == monthlySampleCount || format == yearlySampleCount)
                        hasDateBasedSampleCounterFormat = true;
                }

                String sTok = token.toString().toLowerCase();
                if (isLineageInput(sTok, importAliases, _currentDataTypeName, _container, user))
                {
                    isLineagePart = true;
                    hasLineageInputs = true;
                }

                if (token instanceof FieldKey fkTok)
                {
                    int previousErrorCount = _syntaxErrors.size();
                    List<String> fieldParts = processFieldParts(fkTok, partAncestorOptions, user, dataClassLSIDs, sampleTypeLSIDs, importAliases);
                    if (!_syntaxErrors.isEmpty() && _syntaxErrors.size() > previousErrorCount) // if ancestor lookup syntax error, continue
                        continue;
                    List<Pair<ExpLineageOptions.LineageExpType, String>> ancestorPaths = null;
                    if (partAncestorOptions.containsKey(fkTok.encode()))
                        ancestorPaths = partAncestorOptions.get(fkTok.encode()).ancestorPaths();

                    // for simple token with no lookups, e.g. ${genId}, don't need to do anything special
                    if (fieldParts.size() == 1)
                    {
                        if (isProjectSampleCountToken(fkTok))
                            hasProjectSampleCounter = true;

                        if (isProjectRootSampleCountToken(fkTok))
                            hasProjectSampleRootCounter = true;

                        if (_validateSyntax)
                        {
                            String fieldName = fieldParts.get(0);
                            if (!substitutionValues.contains(fieldName) && !isLineagePart)
                            {
                                boolean isColPresent = false;
                                PropertyType pt = null;
                                if (_parentTable != null)
                                {
                                    ColumnInfo col = _parentTable.getColumn(fieldName);
                                    isColPresent = col != null;
                                    if (isColPresent)
                                        pt = col.getPropertyType();
                                }
                                else if (!domainFields.isEmpty())
                                {
                                    GWTPropertyDescriptor col = domainFields.get(fieldName);
                                    isColPresent = col != null;
                                    if (isColPresent)
                                    {
                                        if (col.getConceptURI() != null || col.getRangeURI() != null)
                                            pt = PropertyType.getFromURI(col.getConceptURI(), col.getRangeURI(), null);
                                    }
                                }

                                if (!isColPresent)
                                    _syntaxErrors.add("Invalid substitution token: ${" + token.toString() + "}.");
                                else if (pt != null)
                                    previewCtx.put(fieldName, getNamePartPreviewValue(pt, fieldName));
                            }
                        }
                        continue;
                    }

                    boolean isLineageLookup = isLineageLookup(fieldParts, importAliases, _currentDataTypeName, _container, user);

                    if (isLineageLookup)
                    {

                        String alias = fieldParts.get(0);
                        boolean isParentAlias = importAliases != null && importAliases.containsKey(alias);

                        Object lookupValuePreview = null;
                        hasLineageLookup = true;
                        if (isParentAlias && fieldParts.size() == 2)
                        {
                            String lookupField = fieldParts.get(1);
                            // alias/lookup
                            String dataTypeToken = importAliases.get(alias);
                            lineageLookupFields.computeIfAbsent(dataTypeToken, (s) -> new ArrayList<>()).add(fieldParts.get(1));

                            String[] inputParts = dataTypeToken.split("/");
                            lookupValuePreview = getLineageLookupTokenPreview(_currentDataTypeName, fkTok, inputParts[0], inputParts[1], ancestorPaths, lookupField, user, dataClassNames, sampleTypeNames);
                        }
                        else if (!isParentAlias && fieldParts.size() <= 3)
                        {
                            if (fieldParts.size() == 2)
                            {
                                // Inputs/lookup, MaterialInputs/lookup, DataInputs/lookup, MaterialInputs/SampleType1
                                lineageLookupFields.computeIfAbsent(fieldParts.get(0), (s) -> new ArrayList<>()).add(fieldParts.get(1));

                                lookupValuePreview = getLineageLookupTokenPreview(_currentDataTypeName, fkTok, fieldParts.get(0), null, ancestorPaths, fieldParts.get(1), user, dataClassNames, sampleTypeNames);
                            }
                            else if (fieldParts.size() == 3)
                            {
                                // MaterialInputs/SampleType/lookup, DataInputs/DataClass/lookup
                                lineageLookupFields.computeIfAbsent(fieldParts.get(0) + "/" + fieldParts.get(1), (s) -> new ArrayList<>()).add(fieldParts.get(2));
                                lookupValuePreview = getLineageLookupTokenPreview(_currentDataTypeName, fkTok, fieldParts.get(0), fieldParts.get(1), ancestorPaths, fieldParts.get(2), user, dataClassNames, sampleTypeNames);
                            }
                        }
                        else
                        {
                            String errorMsg = "Only one level of lookup is supported for lineage input: " + fkTok + ".";
                            if (_validateSyntax)
                                _syntaxErrors.add(errorMsg);
                            else
                                throw new UnsupportedOperationException(errorMsg);
                        }

                        if (lookupValuePreview != null)
                        {
                            String fkTokStr = fkTok.toString();
                            if (ancestorPaths != null && !ancestorPaths.isEmpty())
                            {
                                fkTokStr = fkTok.encode();
                            }

                            previewCtx.put(fkTokStr, lookupValuePreview);
                        }

                        continue;
                    }
                    else if (fieldParts.size() > 2)
                    {
                        // for now, we only support one level of lookup: ${ingredient/name}
                        // future versions could support multiple levels
                        String errorMsg = "Only one level of lookup is supported: " + fkTok + ".";
                        if (_validateSyntax)
                            _syntaxErrors.add(errorMsg);
                        else
                            throw new UnsupportedOperationException(errorMsg);
                    }

                    if (isLineagePart)
                    {
                        boolean isInput = sTok.startsWith(INPUT_PARENT.toLowerCase());
                        boolean isMaterial = sTok.startsWith(ExpMaterial.MATERIAL_INPUT_PARENT.toLowerCase());
                        Object preview = isInput ? SubstitutionValue.Inputs.getPreviewValue() : (isMaterial ? SubstitutionValue.MaterialInputs.getPreviewValue() : SubstitutionValue.DataInputs.getPreviewValue());
                        previewCtx.put(fkTok.toString(), preview);
                    }
                    else
                    {
                        if (_parentTable == null && domainFields.isEmpty())
                        {
                            String errorMsg = "Parent table is required for name expressions with lookups: " + fkTok + ".";
                            if (_validateSyntax)
                                _syntaxErrors.add(errorMsg);
                            else
                                throw new UnsupportedOperationException(errorMsg);
                        }

                        lookups.add(fkTok);
                    }
                }
            }
        }

        if (!_syntaxErrors.isEmpty())
            return;

        // for each token with a lookup, get the lookup table and stash it for later
        if (!lookups.isEmpty())
        {
            Map<FieldKey, TableInfo> fieldKeyLookup = new HashMap<>();
            for (FieldKey fieldKey : lookups)
            {
                List<String> fieldParts = fieldKey.getParts();

                if (hasLineageInputs && fieldParts.size() == 3)
                    continue;;

                assert _validateSyntax || fieldParts.size() == 2;

                // find column matching the root
                String root = fieldParts.get(0);
                String lookupFieldName = fieldParts.get(1);
                boolean lookupExist = false;

                PropertyType pt = null;
                if (_parentTable != null)
                {
                    ColumnInfo col = _parentTable.getColumn(root);
                    if (col != null)
                    {
                        ForeignKey fk = col.getFk();
                        if (fk == null)
                            continue;

                        TableInfo lookupTable = fk.getLookupTableInfo();
                        if (lookupTable == null)
                            continue;

                        List<ColumnInfo> pkCols = lookupTable.getPkColumns();
                        if (pkCols.size() != 1)
                        {
                            if (_validateSyntax)
                                _syntaxErrors.add("Lookup field not supported on table with multiple primary key fields: " + root + ".");
                            continue;
                        }

                        fieldKeyLookup.put(fieldKey, lookupTable);

                        ColumnInfo lookupCol = lookupTable.getColumn(lookupFieldName);
                        if (lookupCol != null)
                        {
                            lookupExist = true;
                            pt = lookupCol.getPropertyType();
                        }

                    }
                }
                else if (_validateSyntax && !domainFields.isEmpty())
                {
                    GWTPropertyDescriptor col = domainFields.get(root);
                    if (col != null)
                    {
                        Container lookupContainer = null;
                        String containerId = StringUtils.trimToNull(col.getLookupContainer());
                        if (containerId != null)
                        {
                            if (GUID.isGUID(containerId))
                                lookupContainer = ContainerManager.getForId(containerId);
                            if (null == lookupContainer)
                                lookupContainer = ContainerManager.getForPath(containerId);
                        }
                        UserSchema fkSchema = QueryService.get().getUserSchema(user, lookupContainer == null ? _container : lookupContainer, col.getLookupSchema());

                        if (fkSchema != null)
                        {
                            TableInfo lookupTable = fkSchema.getTable(col.getLookupQuery());
                            if (lookupTable != null)
                            {
                                List<String> pkCols = lookupTable.getPkColumnNames();
                                if (pkCols.size() != 1)
                                {
                                    _syntaxErrors.add("Lookup field not supported on table with multiple primary key fields: " + root + ". ");
                                    continue;
                                }
                                else
                                {
                                    ColumnInfo lookupCol = lookupTable.getColumn(lookupFieldName);
                                    if (lookupCol != null)
                                    {
                                        lookupExist = true;
                                        if (lookupCol.getConceptURI() != null || lookupCol.getRangeURI() != null)
                                            pt = PropertyType.getFromURI(lookupCol.getConceptURI(), lookupCol.getRangeURI(), null);
                                    }

                                }
                            }
                        }
                    }
                }

                if (_validateSyntax)
                {
                    if (!lookupExist)
                    {
                        _syntaxErrors.add("Lookup field does not exist: " + fieldKey.toString());
                    }
                    else if (pt != null)
                    {
                        previewCtx.put(fieldKey.toString(), getNamePartPreviewValue(pt, lookupFieldName));
                    }
                }

            }

            _exprLookups = fieldKeyLookup;
        }

        SampleNameExpressionSummary sampleSummary = new SampleNameExpressionSummary(hasProjectSampleCounter, hasProjectSampleRootCounter, 0/*min value will be set by _GenerateNamesDataIterator*/, 0);
        _expressionSummary = new ExpressionSummary(sampleSummary, hasDateBasedSampleCounterFormat, hasLineageInputs, hasLineageLookup);
        _expLineageLookupFields = lineageLookupFields;
        _partAncestorOptions = partAncestorOptions;
        if (_validateSyntax && _syntaxErrors.isEmpty())
            _previewName = _parsedNameExpression.eval(previewCtx);
    }

    private List<String> processFieldParts(FieldKey fkTok, Map<String, NameExpressionAncestorPartOption> partAncestorOptions, User user, Map<String, String> dataClassLSIDs, Map<String, String> sampleTypeLSIDs, @Nullable Map<String, String> importAliases)
    {
        List<Pair<ExpLineageOptions.LineageExpType, String>> ancestorTypes = new ArrayList<>();
        List<String> allFieldParts = fkTok.getParts();

        List<String> fieldParts = new ArrayList<>();
        int partInd = 0, ancestorLevel = 0;

        String fkTokDisplay = fkTok.toString().replaceAll("\\$P", ".").replaceAll("::", "/");
        boolean hasLookupColumn = false; // needs to specify an explicit ancestor lookup column: ..[MaterialInput/Type]/lookupColumnName
        for (String fPart : allFieldParts)
        {
            if (!StringUtils.isEmpty(fPart) && !isAncestorPart(fPart))
            {
                hasLookupColumn = true;
                fieldParts.add(fPart);
            }
            else
            {
                hasLookupColumn = false;
                ancestorLevel++;

                if (partInd == 0)
                {
                    // Syntax should be ${MaterialInput/..[MaterialInputs]/name} where the first input is the direct parent, instead of ${..[MaterialInputs]/name}.
                    _syntaxErrors.add("Invalid substitution token, parent input must be specified for ancestor lookup: ${" + fkTokDisplay + "}.");
                    return fieldParts;
                }

                if (ancestorLevel > 9) // 1 generation of direct parent + 9 extra generations of ancestors
                {
                    _syntaxErrors.add("Invalid substitution token, a max of 10 generations of ancestor lookup is supported: ${" + fkTokDisplay + "}.");
                    return fieldParts;
                }

                Pair<ExpLineageOptions.LineageExpType, String> ancestorPart = getAncestorPart(fPart, dataClassLSIDs, sampleTypeLSIDs);
                ancestorTypes.add(ancestorPart);
            }

            partInd++;
        }

        if (!ancestorTypes.isEmpty())
        {
            if (!hasLookupColumn || fieldParts.size() < 2 /* should be at least 2 parts, /alias/Name */
                    || fieldParts.size() > 4 /* should be at most 3 parts, for example /MaterialInputs/Type1/Name */)
            {
                _syntaxErrors.add("Invalid substitution token, lookup column name not specified: ${" + fkTokDisplay + "}.");
                return fieldParts;
            }

            ExpLineageOptions options = new ExpLineageOptions();
            options.setForLookup(false);
            options.setParents(true);
            options.setChildren(false);
            options.setDepth((ancestorTypes.size() + 1) * 2); // (ancestor + 1 direct parent) * 2 (1 for data, 1 for run)

            List<String> parentParts = fieldParts.subList(0, fieldParts.size() - 1); // exclude lookup column
            String parentTypeName = null;
            if (parentParts.size() == 1) // alias, or one of Inputs/, MaterialInputs/, DataInputs/
            {
                String alias = parentParts.get(0);
                boolean isParentAlias = importAliases != null && importAliases.containsKey(alias);
                if (isParentAlias)
                {
                    String dataTypeToken = importAliases.get(alias);
                    parentTypeName = dataTypeToken.substring(dataTypeToken.indexOf("/") + 1);
                }
            }
            else
            {
                parentTypeName = parentParts.get(1);
            }

            partAncestorOptions.put(fkTok.encode(), new NameExpressionAncestorPartOption(options, parentTypeName, ancestorTypes, allFieldParts.get(allFieldParts.size() - 1)));
        }

        return fieldParts;
    }

    public static Pair<ExpLineageOptions.LineageExpType, String> getAncestorPart(String part, Map<String, String> dataClassLSIDs, Map<String, String> sampleTypeLSIDs)
    {
        Matcher ancestorTypeMatcher = ANCESTOR_INPUT_PATTERN.matcher(part);
        if (ancestorTypeMatcher.find())
        {
            ExpLineageOptions.LineageExpType expType = ExpLineageOptions.LineageExpType.Material;
            String expTypeStr = ancestorTypeMatcher.group(2);
            if (ExpLineageOptions.LineageExpType.Data.name().equalsIgnoreCase(expTypeStr))
                expType = ExpLineageOptions.LineageExpType.Data;

            String dataType = ancestorTypeMatcher.group(4);

            if (!StringUtils.isEmpty(dataType))
                dataType = expType == ExpLineageOptions.LineageExpType.Data ? dataClassLSIDs.get(dataType) : sampleTypeLSIDs.get(dataType);

            return new Pair<>(expType, dataType);
        }
        return null;
    }

    public static boolean isAncestorPart(String part)
    {
        Matcher ancestorTypeMatcher = ANCESTOR_INPUT_PATTERN.matcher(part);
        return ancestorTypeMatcher.find();
    }

    public static Object getNamePartPreviewValue(PropertyType pt, @Nullable String prefix)
    {
        return pt.getPreviewValue(prefix);
    }

    public static long getCounterStartValue(@Nullable String nameExpression, EntityCounter counterType)
    {
        long startInd = 0;
        if (StringUtils.isEmpty(nameExpression))
            return startInd;

        Pattern pattern = Pattern.compile(String.format(WITH_START_IND_REGEX, counterType.name()));

        Matcher matcher = pattern.matcher(nameExpression);
        if (matcher.find())
        {
            String startIndStr = matcher.group(1);
            if (!StringUtils.isEmpty(startIndStr))
            {
                try
                {
                    startInd = Integer.valueOf(startIndStr);
                }
                catch (NumberFormatException e)
                {
                    // ignore illegal startInd
                }
            }
        }
        return startInd;
    }

    public void generateNames(@NotNull State state,
                              @NotNull List<Map<String, Object>> maps,
                              @Nullable Set<ExpData> parentDatas, @Nullable Set<ExpMaterial> parentSamples,
                              @Nullable List<Supplier<Map<String, Object>>> extraPropsFns,
                              boolean skipDuplicates)
            throws NameGenerationException
    {
        ListIterator<Map<String, Object>> li = maps.listIterator();
        while (li.hasNext())
        {
            Map<String, Object> map = li.next();
            try
            {
                String name = state.nextName(map, parentDatas, parentSamples, extraPropsFns, null);
                map.put("name", name);
            }
            catch (DuplicateNameException dup)
            {
                if (skipDuplicates)
                {
                    // Issue 23384: SampleSet: import should ignore duplicate rows when ignore duplicates is selected
                    li.remove();
                }
                else
                    throw dup;
            }
        }
    }

    /**
     * Create new state object for a batch of names.
     * @param incrementSampleCounts Increment the sample counters for each name generated.
     */
    @NotNull
    public State createState(boolean incrementSampleCounts)
    {
        return new State(incrementSampleCounts, _expressionSummary.sampleSummary, _container);
    }

    public String generateName(@NotNull State state, @NotNull Map<String, Object> rowMap) throws NameGenerationException
    {
        return state.nextName(rowMap, null, null, null, null);
    }

    public String generateName(@NotNull State state, @NotNull Map<String, Object> rowMap,
                               @Nullable Set<ExpData> parentDatas, @Nullable Set<ExpMaterial> parentSamples,
                               @Nullable List<Supplier<Map<String, Object>>> extraPropsFns) throws NameGenerationException
    {
        return state.nextName(rowMap, parentDatas, parentSamples, extraPropsFns, null);
    }

    public String generateName(@NotNull State state, @NotNull Map<String, Object> rowMap,
                               @Nullable Set<ExpData> parentDatas, @Nullable Set<ExpMaterial> parentSamples,
                               @Nullable List<Supplier<Map<String, Object>>> extraPropsFns, @Nullable FieldKeyStringExpression altExpression) throws NameGenerationException
    {
        return state.nextName(rowMap, parentDatas, parentSamples, extraPropsFns, altExpression);
    }

    record ProjectSampleCounters(DbSequence sampleCounterSequence, DbSequence rootCounterSequence)
    {
        public void sync()
        {
            if (sampleCounterSequence != null)
                sampleCounterSequence.sync();
            if (rootCounterSequence != null)
                rootCounterSequence.sync();
        }
    }

    public class State implements AutoCloseable
    {
        private final boolean _incrementSampleCounts;
        private final User _user;
        private final Map<String, Object> _batchExpressionContext;
        private Function<Map<String,Long>,Map<String,Long>> getSampleCountsFunction;
        private final Map<String, Integer> _newNames = new CaseInsensitiveHashMap<>();

        private int _rowNumber = 0;
        private final Map<Tuple3<String, Object, FieldKey>, Object> _lookupCache;
        private final Map<String, ArrayList<Object>> _ancestorCache;
        private final Map<String, Map<String, DbSequence>> _prefixCounterSequences;

        private final ProjectSampleCounters _sampleCounters;
        private boolean _counterSequencesCleaned = false;
        private Container _counterContainer;

        private State(boolean incrementSampleCounts, SampleNameExpressionSummary expressionSummary, Container container)
        {
            _incrementSampleCounts = incrementSampleCounts;
            _counterContainer = container;

            DbSequence sampleCounterSequence;
            DbSequence rootCounterSequence;

            if (incrementSampleCounts) // determine if need to incrementRootSampleCount
            {
                DbSequence sampleCountSeq = SampleTypeService.get().getSampleCountSequence(_counterContainer, false);
                if (expressionSummary.hasProjectSampleCounter || sampleCountSeq.current() > 0) // if ${sampleCount} is present, or if ${sampleCount} was previously evaluated
                {
                    sampleCounterSequence = sampleCountSeq;
                    if (sampleCounterSequence != null)
                    {
                        long expressionMin = expressionSummary.minProjectSampleCounter - 1;
                        if (sampleCounterSequence.current() == 0) // initialize existing count when ${sampleCount} is first encountered for a project
                            sampleCounterSequence.ensureMinimum(Math.max(expressionMin, SampleTypeService.get().getProjectSampleCount(_counterContainer)));
                        else if (sampleCountSeq.current() < expressionMin)
                            sampleCounterSequence.ensureMinimum(expressionMin);
                    }
                }
                else
                    sampleCounterSequence = null;

                DbSequence rootCountSeq = SampleTypeService.get().getSampleCountSequence(_counterContainer, true);
                if (expressionSummary.hasProjectSampleRootCounter || rootCountSeq.current() > 0) // if ${rootSampleCount} is present, or if ${rootSampleCount} was previously evaluated
                {
                    rootCounterSequence = rootCountSeq;
                    if (rootCounterSequence != null)
                    {
                        long expressionMin = expressionSummary.minProjectSampleRootCounter - 1;
                        if (rootCountSeq.current() == 0) // initialize existing count when ${rootSampleCount} is first encountered for a project
                            rootCounterSequence.ensureMinimum(Math.max(expressionMin, SampleTypeService.get().getProjectRootSampleCount(_counterContainer)));
                        else if (rootCounterSequence.current() < expressionMin)
                            rootCounterSequence.ensureMinimum(expressionMin);
                    }
                }
                else
                    rootCounterSequence = null;
            }
            else
            {
                sampleCounterSequence = null;
                rootCounterSequence = null;
            }

            _sampleCounters = new ProjectSampleCounters(sampleCounterSequence, rootCounterSequence);

            // Create the name expression context shared for the entire batch of rows
            Map<String, Object> batchContext = new CaseInsensitiveHashMap<>();
            batchContext.put("BatchRandomId", StringUtilsLabKey.getUniquifier(4));
            batchContext.put("Now", new Date());
            _batchExpressionContext = Collections.unmodifiableMap(batchContext);
            _user = User.getSearchUser();
            _lookupCache = new HashMap<>();
            _ancestorCache = new HashMap<>();
            _prefixCounterSequences = new HashMap<>();
        }

        public boolean isIncrementSampleCounts()
        {
            return _incrementSampleCounts;
        }

        public Map<String, Map<String, DbSequence>> getPrefixCounterSequences()
        {
            return _prefixCounterSequences;
        }

        public void cleanUp()
        {
            if (_counterSequencesCleaned)
                return;

            _sampleCounters.sync();

            for (Map<String, DbSequence> counterSequences : _prefixCounterSequences.values())
            {
                for (DbSequence seq: counterSequences.values())
                    if (seq != null)
                        seq.sync();
            }

            _counterSequencesCleaned = true;
        }

        @Override
        public void close()
        {
            _rowNumber = -1;
            cleanUp();
        }

        private String nextName(Map<String, Object> rowMap,
                                Set<ExpData> parentDatas,
                                Set<ExpMaterial> parentSamples,
                                @Nullable List<Supplier<Map<String, Object>>> extraPropsFns,
                                @Nullable FieldKeyStringExpression altExpression)
                throws NameGenerationException
        {
            if (_rowNumber == -1)
                throw new IllegalStateException("closed");

            _rowNumber++;
            String name;
            try
            {
                name = genName(rowMap, parentDatas, parentSamples, extraPropsFns, altExpression);
            }
            catch (IllegalArgumentException e)
            {
                throw new NameGenerationException(_rowNumber, e);
            }

            if (_newNames.containsKey(name))
            {
                throw new DuplicateNameException(name, _rowNumber);
            }
            else
            {
                _newNames.put(name, 1);
            }

            return name;
        }

        private String genName(@NotNull Map<String, Object> rowMap,
                               @Nullable Set<ExpData> parentDatas,
                               @Nullable Set<ExpMaterial> parentSamples,
                               @Nullable List<Supplier<Map<String, Object>>> extraPropsFns,
                               @Nullable FieldKeyStringExpression altExpression)
                throws IllegalArgumentException
        {
            // If sample counters bound to a column are found, e.g. in the expression "${myDate:dailySampleCount}" the dailySampleCount is bound to myDate column,
            // the sample counters will be incremented for that date when the expression is evaluated -- see SubstitutionFormat.SampleCountSubstitutionFormat.
            // Otherwise, update the sample counters for today's date immediately even if the expression doesn't contain a counter replacement token
            // and put the sample counts into the context so that any sample counters not bound to a column will be replaced; e.g, "${dailySampleCount}".
            // It is important to do this even if a "name" is explicitly provided so the sample counts are accurate.
            Map<String, Long> sampleCounts = null;
            if (_incrementSampleCounts)
            {
                if (!_expressionSummary.hasDateBasedSampleCounter)
                {
                    if (null == getSampleCountsFunction)
                    {
                        Date now = (Date)_batchExpressionContext.get("now");
                        getSampleCountsFunction = SampleTypeService.get().getSampleCountsFunction(now);
                    }
                    sampleCounts = getSampleCountsFunction.apply(null);
                }

                if (_sampleCounters.sampleCounterSequence != null)
                {
                    if (sampleCounts == null)
                        sampleCounts = new HashMap<>();

                    sampleCounts.put("sampleCount", _sampleCounters.sampleCounterSequence.next());
                }

                if (_sampleCounters.rootCounterSequence != null)
                {
                    if (sampleCounts == null)
                        sampleCounts = new HashMap<>();

                    boolean skipRootSampleCount = altExpression != null; // so far altExpression is not null only when generating aliquots
                    if (!skipRootSampleCount)
                        sampleCounts.put("rootSampleCount", _sampleCounters.rootCounterSequence.next());
                    else
                        sampleCounts.put("rootSampleCount", _sampleCounters.rootCounterSequence.current());
                }
            }

            // Always execute the extraPropsFns, if available, to increment the ${genId} counter in the non-QueryUpdateService code path.
            // The DataClass and SampleType DataIterators increment the genId value using SimpleTranslator.addSequenceColumn()
            Map<String, Object> extraProps = new HashMap<>();
            if (extraPropsFns != null)
            {
                for (Supplier<Map<String, Object>> fn : extraPropsFns)
                {
                    Map<String, Object> props = fn.get();
                    if (props != null)
                        extraProps.putAll(props);
                }
            }

            // If a name is already provided, just use it as is
            Object currNameObj = rowMap.get("Name");
            if (currNameObj != null)
            {
                String currName = currNameObj.toString();
                if (StringUtils.isNotBlank(currName))
                    return currName.trim();
            }

            // Add extra context variables
            Map<String, Object> ctx = additionalContext(rowMap, parentDatas, parentSamples, sampleCounts, extraProps);

            // allow using alternative expression for evaluation.
            // for example, use AliquotNameExpression instead of NameExpression if sample is aliquot
            FieldKeyStringExpression expression = altExpression != null ? altExpression : _parsedNameExpression;
            String name = null;
            if (expression instanceof NameGenerationExpression)
                name = ((NameGenerationExpression) expression).eval(ctx, _prefixCounterSequences);
            else
                name = expression.eval(ctx);
            if (name == null || name.length() == 0)
                throw new IllegalArgumentException("The data provided are not sufficient to create a name using the naming pattern '" + expression.getSource() + "'.  Check the pattern syntax and data values.");

            return name;
        }

        private Object getParentFieldValue(ExpObject parentObject, String fieldStr)
        {
            String field = fieldStr.toLowerCase();

            switch (field)
            {
                case "rowid":
                    return parentObject.getRowId();
                case "lsid":
                    return parentObject.getLSID();
                case "name":
                    return parentObject.getName();
                case "description":
                {
                    if (parentObject instanceof ExpMaterial material)
                        return material.getDescription();
                    else if (parentObject instanceof ExpData data)
                        return data.getDescription();
                }
                case "created":
                    return parentObject.getCreated();
                case "modified":
                    return parentObject.getModified();
                case "createdby":
                    return parentObject.getCreatedBy();
                case "modifiedby":
                    return parentObject.getModifiedBy();
                default:
                {
                    if (objectPropertiesCache.containsKey(parentObject.getLSID()))
                        return objectPropertiesCache.get(parentObject.getLSID()).get(field);
                    Map<String, Object> properties = new CaseInsensitiveHashMap<>();
                    parentObject.getObjectProperties(_user).values().forEach(prop -> {
                        PropertyType pt = null;
                        if (prop.getConceptURI() != null || prop.getRangeURI() != null)
                            pt = PropertyType.getFromURI(prop.getConceptURI(), prop.getRangeURI(), null);
                        if (pt != null)
                        {
                            Object rawObj = prop.getObjectValue();
                            if ("Boolean".equals(pt.getXmlName()) && rawObj instanceof Double)
                            {
                                rawObj = (Double) rawObj < 1.0 ? Boolean.FALSE : Boolean.TRUE;
                            }

                            properties.put(prop.getName(), pt.convert(rawObj));
                        }
                        else
                            properties.put(prop.getName(), prop.getObjectValue());
                    });
                    objectPropertiesCache.put(parentObject.getLSID(), properties);
                    return properties.get(field);
                }
            }
        }

        private void addParentLookupValues(String parentTypeName,
                                           boolean isMaterialParent,
                                           @Nullable Map<String, String> parentImportAliases,
                                           ExpObject parentObject,
                                           Map<String, ArrayList<Object>> inputLookupValues)
        {
            String inputType = isMaterialParent ? ExpMaterial.MATERIAL_INPUT_PARENT : ExpData.DATA_INPUT_PARENT;
            String inputCol = inputType + "/" + parentTypeName;

            List<String> fieldNames = new ArrayList<>();
            if (_expLineageLookupFields.containsKey(inputCol))
                fieldNames.addAll(_expLineageLookupFields.get(inputCol));
            if (_expLineageLookupFields.containsKey(inputType))
                fieldNames.addAll(_expLineageLookupFields.get(inputType));
            if (_expLineageLookupFields.containsKey(INPUT_PARENT))
                fieldNames.addAll(_expLineageLookupFields.get(INPUT_PARENT));

            for (String fieldName : fieldNames)
            {
                Object lookupValue = getParentFieldValue(parentObject, fieldName);
                if (lookupValue == null)
                    continue;

                // add to Input/<LookupField>
                inputLookupValues.computeIfAbsent(INPUT_PARENT + "/" + fieldName, (s) -> new ArrayList<>()).add(lookupValue);

                // add to importAlias/<LookupField>
                if (parentImportAliases != null)
                {
                    parentImportAliases
                            .entrySet()
                            .stream()
                            .filter(entry -> inputCol.equalsIgnoreCase(entry.getValue()))
                            .forEach(entry -> inputLookupValues.computeIfAbsent(entry.getKey() + "/" + fieldName, (s) -> new ArrayList<>()).add(lookupValue));
                }

                // add to <Type>Inputs/<LookupField>
                inputLookupValues.computeIfAbsent(inputType + "/" + fieldName, (s) -> new ArrayList<>()).add(lookupValue);
                // add to <Type>Inputs/<TypeName>/<LookupField>
                inputLookupValues.computeIfAbsent(inputCol + "/" + fieldName, (s) -> new ArrayList<>()).add(lookupValue);
            }
        }

        private void addAncestorLookupValues(ExpRunItem parentObject, Map<String, ArrayList<Object>> inputLookupValues)
        {
            String parentLsid = parentObject.getLSID();

            for (String ancestorFieldKey : _partAncestorOptions.keySet())
            {
                NameExpressionAncestorPartOption ancestorOptions = _partAncestorOptions.get(ancestorFieldKey);
                if (ancestorOptions != null)
                {
                    String parentType = ancestorOptions.parentType();
                    if (!StringUtils.isEmpty(parentType))
                    {
                        if (parentObject instanceof ExpMaterial)
                        {
                            if (!((ExpMaterial) parentObject).getSampleType().getName().equalsIgnoreCase(parentType))
                                continue;
                        }
                        else if (parentObject instanceof ExpData)
                        {
                            if (!((ExpData) parentObject).getDataClass(_user).getName().equalsIgnoreCase(parentType))
                                continue;
                        }
                    }
                    String ancestorKey = ancestorFieldKey + "-" + parentObject.getObjectId();

                    ArrayList<Object> ancestorLookupValues = new ArrayList<>();

                    if (_ancestorCache.containsKey(ancestorKey))
                        ancestorLookupValues = _ancestorCache.get(ancestorKey);
                    else
                    {
                        ExpLineageOptions options = ancestorOptions.options();
                        List<Pair<ExpLineageOptions.LineageExpType, String>> ancestorPaths = ancestorOptions.ancestorPaths();
                        String fieldName = ancestorOptions.lookupColumn();
                        Identifiable seed = LsidManager.get().getObject(parentLsid);
                        ExpLineage lineage = ExperimentService.get().getLineage(_container, _user, seed, options);

                        Set<Identifiable> ancestorObjects = lineage.findAncestorObjects(parentObject, ancestorPaths, _user);

                        for (Identifiable ancestorObject : ancestorObjects)
                        {
                            if (ancestorObject instanceof ExpMaterial || ancestorObject instanceof ExpData)
                            {
                                Object lookupValue = getParentFieldValue((ExpObject) ancestorObject, fieldName);
                                if (lookupValue != null)
                                    ancestorLookupValues.add(lookupValue);
                            }
                        }

                        _ancestorCache.put(ancestorFieldKey + "-" + parentObject.getObjectId(), ancestorLookupValues);
                    }

                    if (!ancestorLookupValues.isEmpty())
                        inputLookupValues.put(ancestorFieldKey, ancestorLookupValues);

                }
            }

        }

        private void addLineageLookupContext(String parentTypeName,
                                             String parentName,
                                             boolean isMaterialParent,
                                             @Nullable Map<String, String> parentImportAliases,
                                             Map<String, ArrayList<Object>> inputLookupValues)
        {
            if (!_expressionSummary.hasLineageLookup || StringUtils.isEmpty(parentTypeName) || StringUtils.isEmpty(parentName))
                return;

            boolean hasTypeLookup = _expLineageLookupFields.containsKey(INPUT_PARENT);

            if (!hasTypeLookup)
            {
                if (isMaterialParent)
                {
                    if (_expLineageLookupFields.containsKey(ExpMaterial.MATERIAL_INPUT_PARENT))
                        hasTypeLookup = true;
                    else if (_expLineageLookupFields.containsKey(ExpMaterial.MATERIAL_INPUT_PARENT + "/" + parentTypeName))
                        hasTypeLookup = true;
                }
                else
                {
                    if (_expLineageLookupFields.containsKey(ExpData.DATA_INPUT_PARENT))
                        hasTypeLookup = true;
                    else if (_expLineageLookupFields.containsKey(ExpData.DATA_INPUT_PARENT + "/" + parentTypeName))
                        hasTypeLookup = true;
                }
            }

            if (!hasTypeLookup)
                return;

            ExpObject parentObjectType = isMaterialParent ?
                    _sampleTypes.computeIfAbsent(parentTypeName, (name) -> SampleTypeService.get().getSampleType(_container, _user, name))
                    : _dataClasses.computeIfAbsent(parentTypeName, (name) -> ExperimentService.get().getDataClass(_container, _user, name));
            if (parentObjectType == null)
                throw new RuntimeValidationException("Invalid parent type: " + parentTypeName);

            try
            {
                ExpRunItem parentObject = isMaterialParent ?
                        ExperimentService.get().findExpMaterial(_container, _user, (ExpSampleType) parentObjectType, parentTypeName, parentName, renameCache, materialCache)
                        : ExperimentService.get().findExpData(_container, _user, (ExpDataClass) parentObjectType, parentTypeName, parentName, renameCache, dataCache);

                if (parentObject == null)
                    throw new RuntimeValidationException("Unable to find parent " + parentName);

                addParentLookupValues(parentTypeName, isMaterialParent, parentImportAliases, parentObject, inputLookupValues);

                addAncestorLookupValues(parentObject, inputLookupValues);
            }
            catch (ValidationException validationErrors)
            {
                throw new RuntimeValidationException("Unable to find parent " + parentName);
            }
        }

        private Map<String, Object> additionalContext(@NotNull Map<String, Object> rowMap,
                                                      Set<ExpData> parentDatas,
                                                      Set<ExpMaterial> parentSamples,
                                                      @Nullable Map<String, Long> sampleCounts,
                                                      @Nullable Map<String, Object> extraProps)
        {
            Map<String, Object> ctx = new CaseInsensitiveHashMap<>();
            ctx.putAll(_batchExpressionContext);
            ctx.put("_rowNumber", _rowNumber);
            ctx.put("RandomId", StringUtilsLabKey.getUniquifier(4));
            if (sampleCounts != null)
                ctx.putAll(sampleCounts);
            if (extraProps != null)
                ctx.putAll(extraProps);
            ctx.putAll(rowMap);

            // UploadSamplesHelper uses propertyURIs in the rowMap -- add short column names to the map
            if (_parentTable != null)
            {
                for (ColumnInfo col : _parentTable.getColumns())
                {
                   String propURI = col.getPropertyURI();
                   if (rowMap.containsKey(propURI))
                       ctx.put(col.getName(), rowMap.get(propURI));
                }
            }

            // If needed, add the parent names to the replacement map
            if (_expressionSummary.hasLineageLookup || _expressionSummary.hasLineageInputs)
            {
                Map<String, Set<String>> inputs = new HashMap<>();
                Map<String, ArrayList<Object>> inputLookupValues = new CaseInsensitiveHashMap<>();

                inputs.put(INPUT_PARENT, new LinkedHashSet<>());
                inputs.put(ExpData.DATA_INPUT_PARENT, new LinkedHashSet<>());
                inputs.put(ExpMaterial.MATERIAL_INPUT_PARENT, new LinkedHashSet<>());

                Map<String, String> parentImportAliases = (Map<String, String>) ctx.get(PARENT_IMPORT_ALIAS_MAP_PROP);

                if (parentDatas != null)
                {
                    if (_expressionSummary.hasLineageInputs)
                    {
                        parentDatas.stream().map(ExpObject::getName).forEachOrdered(parentName -> {
                            inputs.get(INPUT_PARENT).add(parentName);
                            inputs.get(ExpData.DATA_INPUT_PARENT).add(parentName);
                        });
                    }

                    if (_expressionSummary.hasLineageLookup)
                    {
                        for (ExpData parentObject : parentDatas)
                        {
                            addParentLookupValues(parentObject.getDataClass(_user).getName(), false, parentImportAliases, parentObject, inputLookupValues);
                            addAncestorLookupValues(parentObject, inputLookupValues);
                        }
                    }
                }

                if (parentSamples != null)
                {
                    if (_expressionSummary.hasLineageInputs)
                    {
                        parentSamples.stream().map(ExpObject::getName).forEachOrdered(parentName -> {
                            inputs.get(INPUT_PARENT).add(parentName);
                            inputs.get(ExpMaterial.MATERIAL_INPUT_PARENT).add(parentName);
                        });
                    }
                    if (_expressionSummary.hasLineageLookup)
                    {
                        for (ExpMaterial parentObject : parentSamples)
                        {
                            addParentLookupValues(parentObject.getSampleType().getName(), true, parentImportAliases, parentObject, inputLookupValues);
                            addAncestorLookupValues(parentObject, inputLookupValues);
                        }
                    }
                }

                for (String colName : rowMap.keySet())
                {
                    Object value = rowMap.get(colName);
                    if (value == null)
                        continue;

                    String[] parts = colName.split("/", 2);

                    if (parts.length == 2)
                    {
                        if (_expressionSummary.hasLineageInputs)
                            addInputs(parts, colName, value, inputs, parentImportAliases);
                        if (_expressionSummary.hasLineageLookup)
                            addLineageLookupInput(parts, colName, value, parentImportAliases, inputLookupValues);
                    }
                    else if (parentImportAliases != null && parentImportAliases.containsKey(colName))
                    {
                        String colNameForAlias = parentImportAliases.get(colName);
                        parts = colNameForAlias.split("/", 2);
                        if (_expressionSummary.hasLineageInputs)
                            addInputs(parts, colNameForAlias, value, inputs, parentImportAliases);
                        if (_expressionSummary.hasLineageLookup)
                            addLineageLookupInput(parts, colName, value, parentImportAliases, inputLookupValues);
                    }
                }

                // if a single input or lookup is found, return the object, not the list
                Map<String, Object> inputValues = new HashMap<>();
                inputs.forEach((key, value) -> {
                    Object inputValue = value;
                    if (value.size() == 1)
                        inputValue = value.iterator().next();
                    else if (value.size() == 0)
                        inputValue = null;
                    inputValues.put(key, inputValue);
                });
                ctx.putAll(inputValues);

                Map<String, Object> lookupValues = new HashMap<>();
                inputLookupValues.forEach((key, value) -> lookupValues.put(key, value.size() > 1 ? value : value.get(0)));
                ctx.putAll(lookupValues);
            }

            // If needed, query to find lookup values
            if (!_exprLookups.isEmpty())
            {
                for (Map.Entry<FieldKey, TableInfo> pair : _exprLookups.entrySet())
                {
                    FieldKey fieldKey = pair.getKey();
                    TableInfo lookupTable = pair.getValue();

                    String rootName = fieldKey.getRootName();
                    Object rootValue = ctx.get(rootName);
                    if (rootValue != null)
                    {
                        List<ColumnInfo> pkCols = lookupTable.getPkColumns();
                        if (pkCols.size() != 1)
                            continue;

                        ColumnInfo pkCol = pkCols.get(0);
                        // convert the rootValue to the target pkColumn type
                        if (rootValue instanceof String && !pkCol.isStringType())
                            rootValue = ConvertUtils.convert((String)rootValue, pkCol.getJavaClass());

                        // Cache lookupValues by (rootName, rootValue, fieldKey) -> lookupValue
                        // CONSIDER: Cache key could be (lookupSchema, lookupQuery, lookupColName, value)
                        // TODO: support for multi-valued FKs
                        Tuple3<String, Object, FieldKey> key = Tuple3.of(rootName, rootValue, fieldKey);
                        Object value = _lookupCache.computeIfAbsent(key, (tuple3) -> {
                            Object rootVal = tuple3.second;
                            SimpleFilter filter = new SimpleFilter();
                            filter.addCondition(pkCol, rootVal);

                            FieldKey relativeFieldKey = fieldKey.removeParent(rootName);
                            Collection<FieldKey> fields = Collections.singleton(relativeFieldKey);
                            Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(lookupTable, fields);

                            var select = QueryService.get().getSelectBuilder(lookupTable)
                                    .columns(cols.values())
                                    .filter(filter);
                            try (Results results = select.select())
                            {
                                if (results.next())
                                {
                                    return results.getFieldKeyRowMap().get(relativeFieldKey);
                                }
                            }
                            catch (SQLException e)
                            {
                                throw new RuntimeSQLException(e);
                            }

                            return null;
                        });

                        ctx.put(fieldKey.toString(), value);
                    }
                }
            }

            return ctx;
        }

        private void addLineageLookupInput(String[] parts,
                                           String colName,
                                           Object value,
                                           @Nullable Map<String, String> parentImportAliases,
                                           Map<String, ArrayList<Object>> inputLookupValues)
        {
            boolean isMaterialParent = parts[0].equalsIgnoreCase(ExpMaterial.MATERIAL_INPUT_PARENT);
            boolean isDataParent = parts[0].equalsIgnoreCase(ExpData.DATA_INPUT_PARENT);
            if (isMaterialParent || isDataParent)
            {
                for (String parent : parentNames(value, colName))
                    addLineageLookupContext(QueryKey.decodePart(parts[1]), parent, isMaterialParent, parentImportAliases, inputLookupValues);
            }
        }

        private Collection<String> parentNames(Object value, String parentColName)
        {
            return NameGenerator.parentNames(value, parentColName).collect(Collectors.toList());
        }

        private void addInputs(String[] parts,
                               String colName,
                               Object value,
                               Map<String, Set<String>> inputs,
                               @Nullable Map<String, String> parentImportAliases)
        {
            if (parts.length == 2)
            {
                String inputsCategory = null;
                if (parts[0].equalsIgnoreCase(ExpData.DATA_INPUT_PARENT))
                    inputsCategory = ExpData.DATA_INPUT_PARENT;
                else if (parts[0].equalsIgnoreCase(ExpMaterial.MATERIAL_INPUT_PARENT))
                    inputsCategory = ExpMaterial.MATERIAL_INPUT_PARENT;
                if (inputsCategory != null)
                {
                    Collection<String> parents = parentNames(value, colName);
                    inputs.get(INPUT_PARENT).addAll(parents);
                    inputs.computeIfAbsent(INPUT_PARENT + "/" + parts[1],  (s) -> new LinkedHashSet<>()).addAll(parents); // add Inputs/SampleType1
                    inputs.get(inputsCategory).addAll(parents);
                    // if import aliases are defined, also add in the inputs under the aliases in case those are used in the name expression
                    if (parentImportAliases != null)
                    {
                        Optional<Map.Entry<String, String>> aliasEntry = parentImportAliases.entrySet().stream().filter(entry -> entry.getValue().equalsIgnoreCase(colName)).findFirst();
                        aliasEntry.ifPresent(entry -> {
                            inputs.computeIfAbsent(entry.getKey(),  (s) -> new LinkedHashSet<>()).addAll(parents);
                        });
                    }

                    if (value instanceof String) // convert "parent1,parent2" to [parent1, parent2]
                        inputs.computeIfAbsent(parts[0] + "/" + parts[1],  (s) -> new LinkedHashSet<>()).addAll(parents);
                }
            }
        }
    }

    /**
     *  Same as FieldKeyExpression, but supports :withCounter syntax
     */
    public static class NameGenerationExpression extends FieldKeyStringExpression
    {
        private final Container _container;
        private Function<String, Long> _getNonConflictCountFn;
        private String _counterSeqPrefix;
        private boolean _validateSyntax;

        private final List<String> _syntaxErrors = new ArrayList<>();

        NameGenerationExpression(String source, boolean urlEncodeSubstitutions, NullValueBehavior nullValueBehavior, boolean allowSideEffects, Container container)
        {
            super(source, urlEncodeSubstitutions, nullValueBehavior, allowSideEffects);
            _container = container;
        }

        NameGenerationExpression(String source, boolean urlEncodeSubstitutions, NullValueBehavior nullValueBehavior, boolean allowSideEffects, Container container, Function<String, Long> getNonConflictCountFn, String counterSeqPrefix, boolean validateSyntax)
        {
            this(source, urlEncodeSubstitutions, nullValueBehavior, allowSideEffects, container);
            _getNonConflictCountFn = getNonConflictCountFn;
            _counterSeqPrefix = counterSeqPrefix;
            _validateSyntax = validateSyntax;
        }

        public static NameGenerationExpression create(String source, boolean urlEncodeSubstitutions)
        {
            return new NameGenerationExpression(source, urlEncodeSubstitutions, NullValueBehavior.ReplaceNullWithBlank, true, null, null, null, false);
        }

        public static NameGenerationExpression create(String source, boolean urlEncodeSubstitutions, NullValueBehavior nullValueBehavior, boolean allowSideEffects, Container container, Function<String, Long> getNonConflictCountFn)
        {
            return new NameGenerationExpression(source, urlEncodeSubstitutions, nullValueBehavior, allowSideEffects, container, getNonConflictCountFn, null, false);
        }
        
        public static NameGenerationExpression create(String source, boolean urlEncodeSubstitutions, NullValueBehavior nullValueBehavior, boolean allowSideEffects, Container container, Function<String, Long> getNonConflictCountFn, String counterSeqPrefix, boolean validateSyntax)
        {
            return new NameGenerationExpression(source, urlEncodeSubstitutions, nullValueBehavior, allowSideEffects, container, getNonConflictCountFn, counterSeqPrefix, validateSyntax);
        }

        public List<String> getSyntaxErrors()
        {
            return _syntaxErrors;
        }

        @Override
        protected StringExpressionFactory.StringPart parsePart(String expression)
        {
            Matcher counterMatcher = WITH_COUNTER_PATTERN.matcher(expression);
            if (counterMatcher.find())
            {
                String namePrefixExpression = counterMatcher.group(1);
                int startInd = 0;
                String startIndStr = counterMatcher.group(2);
                String numberFormat = counterMatcher.group(3);
                String param = counterMatcher.group(4);
                boolean ensureNoGap = false;
                if (!StringUtils.isEmpty(startIndStr))
                {
                    try
                    {
                        startInd = Integer.valueOf(startIndStr);
                    }
                    catch (NumberFormatException e)
                    {
                        // ignore illegal startInd
                    }
                }
                if (!StringUtils.isEmpty(param))
                {
                    ensureNoGap = param.equalsIgnoreCase(WITH_COUNTER_NO_GAP_PARAM);
                }

                return new NameGenerator.CounterExpressionPart(namePrefixExpression, startInd, numberFormat, ensureNoGap, _container, _getNonConflictCountFn, _counterSeqPrefix);
            }

            // if contains ancestor expression, substitute ..[MaterialInputs/type1] with ..[MaterialInputs::type1] before parsing, to avoid splitting at /.
            if (ANCESTOR_INPUT_PATTERN.matcher(expression).find())
            {
                expression = expression.replaceAll(ANCESTOR_INPUT_PREFIX_MATERIAL.replace("[", "\\["), ANCESTOR_INPUT_PREFIX_MATERIAL_NOSLASH);
                expression = expression.replaceAll(ANCESTOR_INPUT_PREFIX_DATA.replace("[", "\\["), ANCESTOR_INPUT_PREFIX_DATA_NOSLASH);
            }

            return super.parsePart(expression);
        }

        @Override
        protected void parse()
        {
            _parsedExpression = new ArrayList<>();
            int start = 0;
            int openIndex;
            int openCount = 0;
            final String openTag = "${";
            final char closeTag = '}';

            while (start < _source.length() && (openIndex = _source.indexOf(openTag, start)) >= 0)
            {
                if (openIndex > 0)
                    _parsedExpression.add(new StringExpressionFactory.ConstantPart(_source.substring(start, openIndex)));

                int subInd = openIndex + 2;
                openCount = 1;

                while (subInd < _source.length())
                {
                    int nextOpen = _source.indexOf(openTag, subInd);
                    int nextClose = _source.indexOf(closeTag, subInd);

                    if (nextOpen == -1 && nextClose == -1)
                        break;

                    if (nextOpen == -1 || nextClose < nextOpen)
                    {
                        openCount--;
                        subInd = nextClose + 1;
                    }
                    else if (nextClose > nextOpen)
                    {
                        openCount++;
                        subInd = nextOpen + 2;
                    }

                    if (openCount == 0)
                        break;

                    if (openCount < 0)
                    {
                        if (_validateSyntax) // unmatched {} are already checked previously
                            return;
                        throw new IllegalArgumentException("Illegal expression: open and close tags are not matched.");
                    }
                }

                if (openCount == 0)
                {
                    String sub = _source.substring(openIndex + 2, subInd - 1);
                    StringExpressionFactory.StringPart part = parsePart(sub);
                    if (_validateSyntax)
                        part.setPreviewMode(true);

                    if (part.hasSideEffects() && !_allowSideEffects)
                    {
                        if (_validateSyntax)
                            return;
                        throw new IllegalArgumentException("Side-effecting expression part not allowed: " + sub);
                    }

                    _parsedExpression.add(part);
                    start = subInd;
                }
                else
                {
                    if (_validateSyntax)
                        return;
                    throw new IllegalArgumentException("Illegal expression: open and close tags are not matched.");
                }
            }

            if (start < _source.length())
                _parsedExpression.add(new StringExpressionFactory.ConstantPart(_source.substring(start)));
        }

        /**
         *
         * @param context The map of values to evaluate from
         * @param prefixCounterSequences if prefixCounterSequences is null, dont' use cache. Otherwise, put counter DBSequence in cache for reuse
         * @return
         */
        public String eval(Map context, @Nullable Map<String, Map<String, DbSequence>> prefixCounterSequences)
        {
            ArrayList<StringExpressionFactory.StringPart> parts = getParsedExpression();
            if (parts.size() == 1)
            {
                StringExpressionFactory.StringPart part = parts.get(0);
                try
                {
                    if (part instanceof CounterExpressionPart counterExpressionPart)
                    {
                        Map<String, DbSequence> counterSequences = prefixCounterSequences == null ? null : prefixCounterSequences.computeIfAbsent(_counterSeqPrefix,  (s) -> new CaseInsensitiveHashMap<>());
                        return nullFilter(counterExpressionPart.getValue(context, counterSequences), part);
                    }
                    return nullFilter(part.getValue(context), part);
                }
                catch (StopIteratingException e)
                {
                    return null;
                }
            }

            try
            {
                StringBuilder builder = new StringBuilder();
                for (StringExpressionFactory.StringPart part : parts)
                {
                    String value;
                    if (part instanceof CounterExpressionPart counterExpressionPart)
                    {
                        Map<String, DbSequence> counterSequences = prefixCounterSequences == null ? null : prefixCounterSequences.computeIfAbsent(_counterSeqPrefix, (s) -> new CaseInsensitiveHashMap<>());
                        value = nullFilter(counterExpressionPart.getValue(context, counterSequences), part);
                    }
                    else
                        value = nullFilter(part.getValue(context), part);
                    builder.append(value);
                }
                return builder.toString();
            }
            catch (StopIteratingException e)
            {
                return null;
            }
        }

    }

    public static class CounterExpressionPart extends StringExpressionFactory.StringPart
    {
        private final static long WITH_COUNTER_PREVIEW_VALUE = 1;

        private final String _prefixExpression;
        private final Integer _startIndex;

        private final FieldKeyStringExpression _parsedNameExpression;

        private final Function<String, Long> _getNonConflictCountFn;

        private final boolean _strictIncremental;
        private SubstitutionFormat _counterFormat;

        private final Container _container;

        private final String _counterSeqPrefix;

        public CounterExpressionPart(String expression, int startIndex, String counterFormatStr, boolean strictIncremental, Container container, Function<String, Long> getNonConflictCountFn, String counterSeqPrefix)
        {
            _prefixExpression = expression;
            _parsedNameExpression = FieldKeyStringExpression.create(expression, false, StringExpressionFactory.AbstractStringExpression.NullValueBehavior.ReplaceNullWithBlank, true);
            _startIndex = startIndex;
            _strictIncremental = strictIncremental;
            _container = container;
            _getNonConflictCountFn = getNonConflictCountFn;
            _counterSeqPrefix = StringUtils.isBlank(counterSeqPrefix) ? COUNTER_SEQ_PREFIX : counterSeqPrefix;
            if (!StringUtils.isEmpty(counterFormatStr))
                _counterFormat = new SubstitutionFormat.NumberSubstitutionFormat(counterFormatStr);
        }

        @Override
        public boolean isConstant() { return false; }

        @Override
        public Object getToken()
        {
            return _prefixExpression;
        }

        @NotNull
        @Override
        public Collection<SubstitutionFormat> getFormats()
        {
            return Collections.emptyList();
        }

        @Override
        public String getValue(Map map)
        {
            return this.getValue(map, null);
        }

        private DbSequence getCounterSeq(String prefixRaw, @Nullable Map<String, DbSequence> counterSequences, boolean noCache)
        {
            String prefix = prefixRaw.trim().toLowerCase(); // Issue 49338: withCounter should be case-insensitive
            DbSequence counterSeq = null;
            if (noCache || !counterSequences.containsKey(prefix) || _strictIncremental)
            {
                long existingCount = -1;

                if (_strictIncremental || !AppProps.getInstance().isExperimentalFeatureEnabled(EXPERIMENTAL_ALLOW_GAP_COUNTER))
                {
                    counterSeq = DbSequenceManager.getReclaimable(_container, _counterSeqPrefix + prefix, 0);
                }
                else
                {
                    if (noCache)
                        counterSeq = DbSequenceManager.get(_container, _counterSeqPrefix + prefix, 0);
                    else
                        counterSeq = DbSequenceManager.getPreallocatingSequence(_container, _counterSeqPrefix + prefix, 0, 100);
                }

                long currentSeqMax = counterSeq.current();

                if (_getNonConflictCountFn != null)
                    existingCount = _getNonConflictCountFn.apply(prefix);

                if (existingCount > currentSeqMax || (_startIndex - 1) > currentSeqMax)
                    counterSeq.ensureMinimum(existingCount > (_startIndex - 1) ? existingCount : (_startIndex - 1));

                if (!noCache)
                    counterSequences.put(prefix, counterSeq);
            }
            else
                counterSeq = counterSequences.get(prefix);

            return counterSeq;
        }

        public String getValue(Map map, @Nullable Map<String, DbSequence> counterSequences)
        {
            String prefix = _parsedNameExpression.eval(map);

            long count;
            if (isPreviewMode())
            {
                count = _startIndex > WITH_COUNTER_PREVIEW_VALUE ? _startIndex : WITH_COUNTER_PREVIEW_VALUE;
            }
            else
            {
                if (StringUtils.isEmpty(prefix))
                    return null;


                boolean noCache = counterSequences == null;
                DbSequence counterSeq = null;

                if (_strictIncremental || !AppProps.getInstance().isExperimentalFeatureEnabled(EXPERIMENTAL_ALLOW_GAP_COUNTER))
                {
                    synchronized (this)
                    {
                        counterSeq = getCounterSeq(prefix, counterSequences, noCache);
                    }
                }
                else
                    counterSeq = getCounterSeq(prefix, counterSequences, noCache);


                count = counterSeq.next();

                if (noCache)
                    counterSeq.sync();
            }


            Object countStr;
            if (_counterFormat != null)
                countStr = _counterFormat.format(count);
            else
                countStr = String.valueOf(count);

            return prefix + countStr;
        }

        public FieldKeyStringExpression getParsedNameExpression()
        {
            return _parsedNameExpression;
        }

        @Override
        public boolean hasSideEffects()
        {
            return false;
        }

        @Override
        public String toString()
        {
            return "${" + _prefixExpression + "}";
        }
    }

    public class NameGenerationException extends Exception
    {
        private final int _rowNumber;

        NameGenerationException(String message, int rowNumber)
        {
            super(message);
            _rowNumber = rowNumber;
        }

        NameGenerationException(int rowNumber, Throwable t)
        {
            super(t);
            _rowNumber = rowNumber;
        }

        public int getRowNumber()
        {
            return _rowNumber;
        }
    }

    public class DuplicateNameException extends NameGenerationException
    {
        private final String _name;

        DuplicateNameException(String name, int rowNumber)
        {
            super("Duplicate name '" + name + "' on row " + rowNumber, rowNumber);
            _name = name;
        }

        public String getName()
        {
            return _name;
        }
    }

    public static class TestCase extends Assert
    {
        final String aliquotedFrom = "S100";
        final String sourceMeta = "mouse1";

        @Test
        public void testDateFormats()
        {
            Date d = new GregorianCalendar(2011, 11, 3, 8, 30, 15).getTime();
            java.sql.Date donly = new java.sql.Date(d.getTime());
            Time t = new Time(d.getTime());
            Map<Object, Object> m = new HashMap<>();
            m.put("d", d);
            m.put("donly", donly);
            m.put("t", t);

            {
                StringExpression se = NameGenerationExpression.create("${d}", false);
                String s = se.eval(m);
                assertEquals("2011-12-03 08:30:15", s);
            }

            {
                StringExpression se = NameGenerationExpression.create("${d:date}", false);
                String s = se.eval(m);
                assertEquals("20111203", s);
            }

            {
                StringExpression se = NameGenerationExpression.create("${d:date('yy-MM-dd')}", false);
                String s = se.eval(m);
                assertEquals("11-12-03", s);
            }

            {
                StringExpression se = NameGenerationExpression.create("${d:date('& yy-MM-dd'):htmlEncode}", false);
                String s = se.eval(m);
                assertEquals("&amp; 11-12-03", s);
            }

            {
                StringExpression se = NameGenerationExpression.create("${d:date('ISO_ORDINAL_DATE')}", false);
                String s = se.eval(m);
                assertEquals("2011-337", s);
            }

            {
                // invalid date format
                StringExpression se = NameGenerationExpression.create("${d:date('yyy-MMMMM-dd')}", false);
                String s = se.eval(m);
                assertEquals("2011-D-03", s);
            }

            {
                StringExpression se = NameGenerationExpression.create("${donly}", false);
                String s = se.eval(m);
                assertEquals("20111203", s);
            }

            {
                StringExpression se = NameGenerationExpression.create("${donly:date('yy-MM-dd')}", false);
                String s = se.eval(m);
                assertEquals("11-12-03", s);
            }

            {
                StringExpression se = NameGenerationExpression.create("${d:date('HHmm')}", false);
                String s = se.eval(m);
                assertEquals("0830", s);
            }

            {
                StringExpression se = NameGenerationExpression.create("${t}", false);
                String s = se.eval(m);
                assertEquals("08:30:15", s);
            }

            {
                StringExpression se = NameGenerationExpression.create("${t:date('HHmm')}", false);
                String s = se.eval(m);
                assertEquals("0830", s);
            }

            {
                StringExpression se = NameGenerationExpression.create("${t:date('HH:mm')}", false);
                String s = se.eval(m);
                assertEquals("08:30", s);
            }

            {
                // parse a non date value
                StringExpression se = NameGenerationExpression.create("${d:date('yyy-MM-dd')}", false);
                Map<Object, Object> m2 = new HashMap<>();
                m.put("d", "Not a date");
                String s = se.eval(m2);
                assertNull(s);
            }

        }

        @Test
        public void testNumberFormats()
        {
            Double d = 123456.789;
            Map<Object, Object> m = new HashMap<>();
            m.put("d", d);

            {
                StringExpression se = NameGenerationExpression.create("${d:number('0.0000')}", false);
                String s = se.eval(m);
                assertEquals("123456.7890", s);
            }

            {
                StringExpression se = NameGenerationExpression.create("${d:number('000000000')}", false);
                String s = se.eval(m);
                assertEquals("000123457", s);
            }

            {
                // invalid number format
                StringExpression se = NameGenerationExpression.create("${d:number('abcde')}", false);
                String s = se.eval(m);
                assertEquals("abcde123457", s);

            }

            {
                // parse a non number
                try
                {
                    Map<Object, Object> m2 = new HashMap<>();
                    m2.put("d", "not a number");

                    StringExpression se = NameGenerationExpression.create("${d:number('0.00')}", false);
                    se.eval(m2);
                    fail("Expected exception");
                }
                catch (IllegalArgumentException e)
                {
                    // ok
                }

            }
        }

        @Test
        public void testStringFormats()
        {
            Map<Object, Object> m = new HashMap<>();
            m.put("a", "A");
            m.put("b", " B ");
            m.put("empty", "");
            m.put("null", null);
            m.put("list", Arrays.asList("a", "b", "c"));

            {
                StringExpression se = NameGenerationExpression.create(
                        "${null:defaultValue('foo')}|${empty:defaultValue('bar')}|${a:defaultValue('blee')}", false);

                String s = se.eval(m);
                assertEquals("foo|bar|A", s);
            }

            {
                StringExpression se = NameGenerationExpression.create(
                        "${b}|${b:trim}|${empty:trim}|${null:trim}", false);
                String s = se.eval(m);
                assertEquals(" B |B||", s);
            }

            {
                StringExpression se = NameGenerationExpression.create(
                        "${a:prefix('!')}|${a:suffix('?')}|${null:suffix('#')}|${empty:suffix('*')}|${empty:defaultValue('foo'):suffix('@')}", false);
                String s = se.eval(m);
                assertEquals("!A|A?|||foo@", s);
            }

            {
                StringExpression se = NameGenerationExpression.create(
                        "${a:join('-')}|${list:join('-')}|${list:join('_'):prefix('['):suffix(']')}|${empty:join('-')}|${null:join('-')}", false);
                String s = se.eval(m);
                assertEquals("A|a-b-c|[a_b_c]||", s);
            }
        }

        @Test
        public void testCollectionFormats()
        {
            Map<Object, Object> m = new HashMap<>();
            m.put("a", "A");
            m.put("empty", Collections.emptyList());
            m.put("null", null);
            m.put("list", Arrays.asList("a", "b", "c"));

            // CONSIDER: We may want to allow empty string to pass through the collection methods untouched
            {
                StringExpression se = NameGenerationExpression.create("${a:first}", false);

                String s = se.eval(m);
                assertEquals("A", s);
            }

            {
                StringExpression se = NameGenerationExpression.create("${null:first}|${empty:first}|${list:first}", false);

                String s = se.eval(m);
                assertEquals("||a", s);
            }

            {
                StringExpression se = NameGenerationExpression.create(
                        "${null:rest}|${empty:rest:join('-')}|${list:rest:join('-')}", false);

                String s = se.eval(m);
                assertEquals("||b-c", s);
            }

            {
                StringExpression se = NameGenerationExpression.create(
                        "${null:last}|${empty:last}|${list:last}", false);

                String s = se.eval(m);
                assertEquals("||c", s);
            }
        }

        private void resetCounter()
        {
            Container c = JunitUtil.getTestContainer();

            DbSequenceManager.delete(c, COUNTER_SEQ_PREFIX + aliquotedFrom + '.');
            DbSequenceManager.delete(c, COUNTER_SEQ_PREFIX + aliquotedFrom + "..");
            DbSequenceManager.delete(c, COUNTER_SEQ_PREFIX + aliquotedFrom + "...");
            DbSequenceManager.delete(c, COUNTER_SEQ_PREFIX + aliquotedFrom + '.' + sourceMeta + ".");
            DbSequenceManager.delete(c, COUNTER_SEQ_PREFIX + aliquotedFrom + ".mouse2.");
            DbSequenceManager.delete(c, COUNTER_SEQ_PREFIX + aliquotedFrom + '-');
            DbSequenceManager.delete(c, COUNTER_SEQ_PREFIX + aliquotedFrom + '-' + sourceMeta + "-");
            DbSequenceManager.delete(c, COUNTER_SEQ_PREFIX + aliquotedFrom + '_');
        }

        @Test
        public void testWithCounterAndCommas()
        {
            Pair<List<String>, List<String>> messages;
            String testPattern = "${${AliquotedFrom},:withCounter(100)}";
            messages = NameGenerator.validateWithCounterSyntax(testPattern, testPattern.indexOf(SubstitutionValue.withCounter.name()));
            assertTrue("Should have no error messages with comma before withCounter", messages.first.isEmpty());
            assertTrue("Should have no warning messages with comma before withCounter", messages.second.isEmpty());

            testPattern = "${NE_:withCounter(200)}_${randomId}-${genId:number('000,000')}_${now:date('yy-MM-dd')}_Some,String";
            messages = NameGenerator.validateWithCounterSyntax(testPattern, testPattern.indexOf(SubstitutionValue.withCounter.name()));
            assertTrue("Should have no error messages with comma after withCounter", messages.first.isEmpty());
            assertTrue("Should have no warning messages with comma after withCounter", messages.second.isEmpty());

            testPattern = "${NE_:withCounter(200,'000,000')}_${randomId}-${genId:number('000,000')}_${now:date('yy-MM-dd')}_Some,String";
            messages = NameGenerator.validateWithCounterSyntax(testPattern, testPattern.indexOf(SubstitutionValue.withCounter.name()));
            assertTrue("Should have no error messages with comma after withCounter", messages.first.isEmpty());
            assertTrue("Should have no warning messages with comma after withCounter", messages.second.isEmpty());

            testPattern = "${NE:withCounter(200,'000,000',NoGap)},${randomId},${AliquotedFrom},_Some,String";
            messages = NameGenerator.validateWithCounterSyntax(testPattern, testPattern.indexOf(SubstitutionValue.withCounter.name()));
            assertTrue("Should have no error messages with three-argument withCounter", messages.first.isEmpty());
            assertTrue("Should have no warning messages with three-argument withCounter", messages.second.isEmpty());
        }


        @Test
        public void testWithCounter()
        {

            Map<Object, Object> m = new HashMap<>();
            m.put(ExpMaterial.ALIQUOTED_FROM_INPUT, aliquotedFrom);
            m.put("SourceMeta", sourceMeta);

            Container c = JunitUtil.getTestContainer();
            resetCounter();

            {
                FieldKeyStringExpression se = NameGenerationExpression.create(
                        "${${AliquotedFrom}.:withCounter}", false, NullValueBehavior.ReplaceNullWithBlank, true, c, null);

                ArrayList<StringExpressionFactory.StringPart> parsedExpressions = se.getParsedExpression();

                assertEquals(1, parsedExpressions.size());
                assertEquals(2, se.getDeepParsedExpression().size());
                assertTrue(parsedExpressions.get(0) instanceof NameGenerator.CounterExpressionPart);

                String s = se.eval(m);
                assertEquals("S100.1", s);
            }

            {
                FieldKeyStringExpression se = NameGenerationExpression.create(
                        "${${AliquotedFrom}.${SourceMeta}.:withCounter}", false, NullValueBehavior.ReplaceNullWithBlank, true, c, null);

                ArrayList<StringExpressionFactory.StringPart> parsedExpressions = se.getParsedExpression();

                assertEquals(1, parsedExpressions.size());
                assertEquals(4, se.getDeepParsedExpression().size());
                assertTrue(parsedExpressions.get(0) instanceof NameGenerator.CounterExpressionPart);

                String s = se.eval(m);
                assertEquals("S100.mouse1.1", s);
                s = se.eval(m);
                assertEquals("S100.mouse1.2", s);

                Map<Object, Object> m2 = new HashMap<>();
                m2.put(ExpMaterial.ALIQUOTED_FROM_INPUT, aliquotedFrom);
                m2.put("SourceMeta", "mouse2");

                s = se.eval(m2);
                assertEquals("S100.mouse2.1", s);
            }

            {
                FieldKeyStringExpression se = NameGenerationExpression.create(
                        "${${AliquotedFrom}-:withCounter}-${SourceMeta}-suffix", false, NullValueBehavior.ReplaceNullWithBlank, true, c, null);

                ArrayList<StringExpressionFactory.StringPart> parsedExpressions = se.getParsedExpression();

                assertEquals(4, parsedExpressions.size());
                assertEquals(5, se.getDeepParsedExpression().size());
                assertTrue(parsedExpressions.get(0) instanceof NameGenerator.CounterExpressionPart);
                assertTrue(parsedExpressions.get(1) instanceof StringExpressionFactory.ConstantPart);
                assertTrue(parsedExpressions.get(2) instanceof StringExpressionFactory.FieldPart);

                String s = se.eval(m);
                assertEquals("S100-1-mouse1-suffix", s);
            }

            {
                FieldKeyStringExpression se = NameGenerationExpression.create(
                        "${${AliquotedFrom}_:withCounter()}", false, NullValueBehavior.ReplaceNullWithBlank, true, c, null);

                ArrayList<StringExpressionFactory.StringPart> parsedExpressions = se.getParsedExpression();

                assertEquals(1, parsedExpressions.size());
                assertEquals(2, se.getDeepParsedExpression().size());
                assertTrue(parsedExpressions.get(0) instanceof NameGenerator.CounterExpressionPart);

                String s = se.eval(m);
                assertEquals("S100_1", s);
                s = se.eval(m);
                assertEquals("S100_2", s);
            }

            {
                FieldKeyStringExpression se = NameGenerationExpression.create(
                        "${${AliquotedFrom}..:withCounter(101)}", false, NullValueBehavior.ReplaceNullWithBlank, true, c, null);

                ArrayList<StringExpressionFactory.StringPart> parsedExpressions = se.getParsedExpression();

                assertEquals(1, parsedExpressions.size());
                assertEquals(2, se.getDeepParsedExpression().size());
                assertTrue(parsedExpressions.get(0) instanceof NameGenerator.CounterExpressionPart);
                NameGenerator.CounterExpressionPart counterPart = (NameGenerator.CounterExpressionPart) parsedExpressions.get(0);
                assertEquals((Integer) 101, counterPart._startIndex);

                String s = se.eval(m);
                assertEquals("S100..101", s);
            }

            {
                FieldKeyStringExpression se = NameGenerationExpression.create(
                        "${${AliquotedFrom}...:withCounter(111, '0000')}", false, NullValueBehavior.ReplaceNullWithBlank, true, c, null);

                ArrayList<StringExpressionFactory.StringPart> parsedExpressions = se.getParsedExpression();

                assertEquals(1, parsedExpressions.size());
                assertEquals(2, se.getDeepParsedExpression().size());
                assertTrue(parsedExpressions.get(0) instanceof NameGenerator.CounterExpressionPart);

                String s = se.eval(m);
                assertEquals("S100...0111", s);
            }

        }

        @Test
        public void testNoMismatchedBraces()
        {
            assertEquals(0, getMismatchedTagErrors("Good-${genId}").size());
            assertEquals(0, getMismatchedTagErrors("No-subs").size());
            assertEquals(0, getMismatchedTagErrors("${genId}-Many-${batchCounter}").size());
            assertEquals(0, getMismatchedTagErrors("${${AliquotedFrom}.:withCounter}").size());
        }

        @Test
        public void testMismatchedOpeningBraces()
        {
            List<String> errors = getMismatchedTagErrors("One-${genId");
            assertArrayEquals(new String[]{"No closing brace found for the substitution pattern starting at position 5."}, errors.toArray());
            errors = getMismatchedTagErrors("${genId{-One");
            assertArrayEquals(new String[]{"No closing brace found for the substitution pattern starting at position 1."}, errors.toArray());
            errors = getMismatchedTagErrors("Bad-${genId ${genId");
            assertArrayEquals(new String[]{"No closing braces found for the substitution patterns starting at positions 5, 13."}, errors.toArray());
            errors = getMismatchedTagErrors("Bad-${${xyz");
            assertArrayEquals(new String[]{"No closing braces found for the substitution patterns starting at positions 5, 7."}, errors.toArray());
            errors = getMismatchedTagErrors("Mixed-${genId-${xyz}-${open");
            assertArrayEquals(new String[]{"No closing braces found for the substitution patterns starting at positions 7, 22."}, errors.toArray());
        }

        private void validateNameResult(String expression, NameExpressionValidationResult expectedResult, @Nullable Map<String, String> importAliases, @Nullable List<GWTPropertyDescriptor> fields)
        {
            Container c = JunitUtil.getTestContainer();
            NameExpressionValidationResult results = getValidationMessages("SampleTypeBeingCreated", expression, fields, importAliases, c);
            assertEquals(expectedResult, results);
        }

        private void validateNameResult(String expression, NameExpressionValidationResult errorMsg, @Nullable Map<String, String> importAliases)
        {
            validateNameResult(expression, errorMsg, importAliases, null);
        }

        private void validateNameResult(String expression, NameExpressionValidationResult errorMsg)
        {
            validateNameResult(expression, errorMsg, null);
        }

        private NameExpressionValidationResult withErrors(String... errors)
        {
            List<String> errorsList = new ArrayList<>();
            Collections.addAll(errorsList, errors);
            return new NameExpressionValidationResult(errorsList, Collections.emptyList(), null);
        }

        private NameExpressionValidationResult withWarnings(String preview, String... warnings)
        {
            List<String> warningsList = new ArrayList<>();
            Collections.addAll(warningsList, warnings);
            return new NameExpressionValidationResult(Collections.emptyList(), warningsList, Collections.singletonList(preview));
        }

        @Test
        public void testNameExpressionTokenFieldErrors()
        {
            validateNameResult("One-${genId", withErrors("No closing brace found for the substitution pattern starting at position 5."));

            validateNameResult("S-${FieldA}-${FieldB}", withErrors("Invalid substitution token: ${FieldA}.", "Invalid substitution token: ${FieldB}."));

            GWTPropertyDescriptor descriptor = new GWTPropertyDescriptor("FieldA", "http://www.w3.org/2001/XMLSchema#string");
            List<GWTPropertyDescriptor> fields = Collections.singletonList(descriptor);
            validateNameResult("S-${FieldA}-${FieldB}", withErrors("Invalid substitution token: ${FieldB}."), null, fields);
        }

        @Test
        public void testNameExpressionLookupFieldErrors()
        {
            validateNameResult("One-${A/B/C}", withErrors("Only one level of lookup is supported: A/B/C.", "Parent table is required for name expressions with lookups: A/B/C."));

            validateNameResult("S-${parentAlias/a/b}", withErrors("Only one level of lookup is supported for lineage input: parentAlias/a/b."), Collections.singletonMap("parentAlias", "MaterialInputs/SampleTypeA"));

            validateNameResult("S-${Inputs/a/b/d}", withErrors("Only one level of lookup is supported for lineage input: Inputs/a/b/d."));

            validateNameResult("S-${Inputs/SampleTypeNotExist}", withErrors("Lineage lookup field does not exist: Inputs/SampleTypeNotExist"));
        }

        @Test
        public void testNameExpressionSubstitutionFormatErrors()
        {
            validateNameResult("${genId:number}", withErrors("No starting parentheses found for the 'number' substitution pattern starting at index 7.",
                    "No ending parentheses found for the 'number' substitution pattern starting at index 7."));

            validateNameResult("${genId:number(000)}", withErrors("Value in parentheses starting at index 14 should be enclosed in single quotes."));

            validateNameResult("${genId:number(}", withErrors("No ending parentheses found for the 'number' substitution pattern starting at index 7.",
                    "Value in parentheses starting at index 14 should be enclosed in single quotes."));

            validateNameResult("${genId:number('000)}", withErrors("No ending quote for the 'number' substitution pattern value starting at index 7."));

            validateNameResult("${genId:minValue}", withErrors("No starting parentheses found for the 'minValue' substitution pattern starting at index 7.", "No ending parentheses found for the 'minValue' substitution pattern starting at index 7."));

            validateNameResult("${genId:minValue(}", withErrors("No ending parentheses found for the 'minValue' substitution pattern starting at index 7."));

            validateNameResult("${sampleCount:minValue(}", withErrors("No ending parentheses found for the 'minValue' substitution pattern starting at index 13."));
        }

        @Test
        public void testNameExpressionReservedTokenWarnings()
        {
            validateNameResult("S-genId", withWarnings("S-genId", "The 'genId' substitution pattern starting at position 2 should be preceded by the string '${'."));

            validateNameResult("S-genId-now-001", withWarnings("S-genId-now-001", "The 'genId' substitution pattern starting at position 2 should be preceded by the string '${'.",
                    "The 'now' substitution pattern starting at position 8 should be preceded by the string '${'."));

            validateNameResult("S-Inputs", withWarnings("S-Inputs", "The 'Inputs' substitution pattern starting at position 2 should be preceded by the string '${'."));

            validateNameResult("S-MaterialInputs/lookupfield", withWarnings("S-MaterialInputs/lookupfield","The 'MaterialInputs' substitution pattern starting at position 2 should be preceded by the string '${'."));

            validateNameResult("AliquotedFrom-001", withWarnings("AliquotedFrom-001", "The 'AliquotedFrom' substitution pattern starting at position 0 should be preceded by the string '${'."));

            validateNameResult("S-rootSampleCount", withWarnings("S-rootSampleCount", "The 'rootSampleCount' substitution pattern starting at position 2 should be preceded by the string '${'."));
        }

        @Test
        public void testNameExpressionAbsentFieldBraceWarnings()
        {
            GWTPropertyDescriptor stringField = new GWTPropertyDescriptor("FieldStr", "http://www.w3.org/2001/XMLSchema#string");
            GWTPropertyDescriptor intField = new GWTPropertyDescriptor("FieldInt", "http://www.w3.org/2001/XMLSchema#int");
            List<GWTPropertyDescriptor> fields = new ArrayList<>();
            fields.add(stringField);
            fields.add(intField);

            Map<String, String> importAliases = Collections.singletonMap("parentAlias", "MaterialInputs/SampleTypeA");

            validateNameResult("S-FieldStr", new NameExpressionValidationResult(Collections.emptyList(), Collections.singletonList("The 'fieldstr' field starting at position 2 should be preceded by the string '${'."), Collections.singletonList("S-FieldStr")), null, fields);

            validateNameResult("S-${FieldStr}-FieldInt", new NameExpressionValidationResult(Collections.emptyList(), Collections.singletonList("The 'fieldint' field starting at position 14 should be preceded by the string '${'."), Collections.singletonList("S-FieldStrValue-FieldInt")), null, fields);

            validateNameResult("S-parentAlias", new NameExpressionValidationResult(Collections.emptyList(), Collections.singletonList("The 'parentalias' field starting at position 2 should be preceded by the string '${'."), Collections.singletonList("S-parentAlias")), importAliases, null);
         }

        @Test
        public void testNameExpressionWithCounterSyntax()
        {
            validateNameResult("${AliquotedFrom}-:withCounter", withWarnings("Sample112-:withCounter", "The 'withCounter' substitution pattern starting at position 18 should be enclosed in ${}."));

            validateNameResult("${genId}-${AliquotedFrom}-:withCounter}", withWarnings("1001-Sample112-:withCounter}", "The 'withCounter' substitution pattern starting at position 27 should be enclosed in ${}."));

            validateNameResult("${${AliquotedFrom}-:withCounter(}", withErrors("No ending parentheses found for the 'withCounter' substitution pattern starting at index 19."));

            validateNameResult("${${AliquotedFrom}-:withCounter(abc)}", withErrors("Invalid starting value abc for 'withCounter' starting at position 20."));

            validateNameResult("${${AliquotedFrom}-:withCounter(100, 000)}", withErrors("Format string starting at position 36 for 'withCounter' substitution pattern should be enclosed in single quotes."));

            validateNameResult("${${AliquotedFrom}-:withCounter(100, '000', badparam)}", withErrors("Param at position 36 for 'withCounter' substitution pattern is invalid. Supported params include: NoGap."));
        }

        @Test
        public void testNameExpressionAncestorLookupFieldErrors()
        {
            validateNameResult("S-${..[MaterialInputs]/name}", withErrors("Invalid substitution token, parent input must be specified for ancestor lookup: ${..[MaterialInputs]/name}."));
            validateNameResult("S-${MaterialInputs/CurrentType/..[MaterialInputs]/..[DataInputs]/..[MaterialInputs]/..[DataInputs]/..[MaterialInputs]/..[DataInputs]/..[MaterialInputs]/..[DataInputs]/..[MaterialInputs]/..[MaterialInputs]/..[MaterialInputs]/name}",
                    withErrors("Invalid substitution token, a max of 10 generations of ancestor lookup is supported: ${MaterialInputs/CurrentType/..[MaterialInputs]/..[DataInputs]/..[MaterialInputs]/..[DataInputs]/..[MaterialInputs]/..[DataInputs]/..[MaterialInputs]/..[DataInputs]/..[MaterialInputs]/..[MaterialInputs]/..[MaterialInputs]/name}."));
            validateNameResult("S-${parentAlias/..[MaterialInputs]/..[DataInputs]/..[MaterialInputs]/..[DataInputs]/..[MaterialInputs]/..[DataInputs]/..[MaterialInputs]/..[DataInputs]/..[MaterialInputs]/..[MaterialInputs]/..[MaterialInputs]/name}",
                    withErrors("Invalid substitution token, a max of 10 generations of ancestor lookup is supported: ${parentAlias/..[MaterialInputs]/..[DataInputs]/..[MaterialInputs]/..[DataInputs]/..[MaterialInputs]/..[DataInputs]/..[MaterialInputs]/..[DataInputs]/..[MaterialInputs]/..[MaterialInputs]/..[MaterialInputs]/name}."));
            validateNameResult("S-${parentAlias/..[MaterialInputs/G1]/..[DataInputs/G2]/..[MaterialInputs/G3]/..[MaterialInputs/G4]/..[MaterialInputs/G5]/..[MaterialInputs/G6]/..[DataInputs/G7]/..[MaterialInputs/G8]/..[MaterialInputs/G9]/..[MaterialInputs/G10]/name}",
                    withErrors("Invalid substitution token, a max of 10 generations of ancestor lookup is supported: ${parentAlias/..[MaterialInputs/G1]/..[DataInputs/G2]/..[MaterialInputs/G3]/..[MaterialInputs/G4]/..[MaterialInputs/G5]/..[MaterialInputs/G6]/..[DataInputs/G7]/..[MaterialInputs/G8]/..[MaterialInputs/G9]/..[MaterialInputs/G10]/name}."));
            validateNameResult("S-${MaterialInputs/CurrentType/..[MaterialInputs]}", withErrors("Invalid substitution token, lookup column name not specified: ${MaterialInputs/CurrentType/..[MaterialInputs]}."));
            validateNameResult("S-${MaterialInputs/CurrentType/..[MaterialInputs]/}", withErrors("Invalid substitution token, lookup column name not specified: ${MaterialInputs/CurrentType/..[MaterialInputs]/}."));

        }

        private void verifyPreview(String expression, String preview, @Nullable Map<String, String> importAliases, @Nullable List<GWTPropertyDescriptor> fields)
        {
            validateNameResult(expression, new NameExpressionValidationResult(Collections.emptyList(), Collections.emptyList(), Collections.singletonList(preview)), importAliases, fields);
        }

        private void verifyPreview(String expression, String preview)
        {
            verifyPreview(expression, preview, null, null);
        }

        @Test
        public void testNameExpressionPreviews()
        {
            // tokens can be substring, examples:  now in snow, genId in genIdentification
            verifyPreview("S-snow-genIdentification", "S-snow-genIdentification");

            // with reserved field
            verifyPreview("S-${genId}", "S-1001");
            verifyPreview("S-${genId:minValue(100)}", "S-1001");
            verifyPreview("S-${genId:minValue(2000)}", "S-2000");
            verifyPreview("S-${genId:minValue('10000')}", "S-10000");
            verifyPreview("S-${weeklySampleCount:number('00000')}", "S-00025");
            verifyPreview("S-${now:date('yyyy.MM.dd')}", "S-2021.04.28");
            verifyPreview("S-${AliquotedFrom}", "S-Sample112");
            verifyPreview("S-${sampleCount}", "S-240");
            verifyPreview("S-${sampleCount:minValue(500)}", "S-500");
            verifyPreview("S-${rootSampleCount:minValue(800)}", "S-800");

            // withCounter
            verifyPreview("${${AliquotedFrom}-:withCounter}", "Sample112-1");
            verifyPreview("${${AliquotedFrom}-:withCounter(123)}", "Sample112-123");
            verifyPreview("${${AliquotedFrom}-:withCounter(11, '000')}", "Sample112-011");
            verifyPreview("${${AliquotedFrom}-:withCounter(11, '000', NoGap)}", "Sample112-011");
            verifyPreview("${${AliquotedFrom},:withCounter(11, '000', NoGap)}", "Sample112,011");
            verifyPreview("${${AliquotedFrom},:withCounter(11, '000,000', NoGap)}", "Sample112,011");
            verifyPreview("${${AliquotedFrom},:withCounter(11, '000,000', NoGap)}_Some,String", "Sample112,011_Some,String");

            // with table columns
            GWTPropertyDescriptor stringField = new GWTPropertyDescriptor("FieldStr", "http://www.w3.org/2001/XMLSchema#string");
            GWTPropertyDescriptor intField = new GWTPropertyDescriptor("FieldInt", "http://www.w3.org/2001/XMLSchema#int");
            GWTPropertyDescriptor dateField = new GWTPropertyDescriptor("FieldDate", "http://www.w3.org/2001/XMLSchema#date");
            List<GWTPropertyDescriptor> fields = new ArrayList<>();
            fields.add(stringField);
            fields.add(intField);
            fields.add(dateField);
            verifyPreview("S-${FieldStr}-${FieldInt:number('00000')}", "S-FieldStrValue-00003", null, fields);
            verifyPreview("S-${FieldStr}-${FieldInt:minValue(1234)}", "S-FieldStrValue-1234", null, fields);
            verifyPreview("S-${FieldStr}-${FieldInt:minValue('5678')}", "S-FieldStrValue-5678", null, fields);

            verifyPreview("S-${FieldStr}-${FieldDate:date('yyyy.MM.dd')}", "S-FieldStrValue-2021.04.28", null, fields);
            verifyPreview("${${FieldStr}-:withCounter}", "FieldStrValue-1", null, fields);

            verifyPreview("S-${FieldStr}-${FieldDate:dailySampleCount}", "S-FieldStrValue-14", null, fields);
            verifyPreview("S-${FieldStr}-${FieldDate:yearlySampleCount}", "S-FieldStrValue-412", null, fields);
            verifyPreview("S-${FieldStr}-${dailySampleCount}", "S-FieldStrValue-14", null, fields);
            verifyPreview("S-${FieldStr}-${yearlySampleCount}", "S-FieldStrValue-412", null, fields);

            // with input
            verifyPreview("S-${Inputs}", "S-Parent101");
            verifyPreview("S-${Inputs/SampleTypeBeingCreated}", "S-Parent101");
            verifyPreview("S-${MaterialInputs/SampleTypeBeingCreated}", "S-Sample101");
            verifyPreview("S-${Inputs/name}", "S-parentname");
            verifyPreview("S-${Inputs/SampleTypeBeingCreated/name}", "S-parentname");
            verifyPreview("S-${parentAlias}", "S-Parent101", Collections.singletonMap("parentAlias", "MaterialInputs/SampleTypeA"), null);
            verifyPreview("S-${parentAlias/name}", "S-parentname", Collections.singletonMap("parentAlias", "MaterialInputs/SampleTypeA"), null);

            // ancestor lookup
            verifyPreview("S-${MaterialInputs/..[MaterialInputs]/name}", "S-ancestorname");
            verifyPreview("S-${MaterialInputs/SampleTypeBeingCreated/..[MaterialInputs/GrandParentSampleType]/name}", "S-ancestorname");
            verifyPreview("S-${MaterialInputs/SampleTypeBeingCreated/..[MaterialInputs/GrandParentSampleType]/..[DataInputs/GreatGrandParentDataType]/name}", "S-ancestorname");
            verifyPreview("S-${parentAlias/..[MaterialInputs/GrandParentSampleType]/name}", "S-ancestorname", Collections.singletonMap("parentAlias", "MaterialInputs/SampleTypeA"), null);
            verifyPreview("S-${MaterialInputs/SampleTypeBeingCreated/..[MaterialInputs]/..[DataInputs]/..[MaterialInputs]/..[MaterialInputs]/name}", "S-ancestorname");
            verifyPreview("S-${MaterialInputs/SampleTypeBeingCreated/..[MaterialInputs/G1]/..[DataInputs/G2]/..[MaterialInputs/G3]/..[MaterialInputs/G4]/name}", "S-ancestorname");
            verifyPreview("S-${parentAlias/..[MaterialInputs/G1]/..[DataInputs/G2]/..[MaterialInputs/G3]/..[MaterialInputs/G4]/name}", "S-ancestorname", Collections.singletonMap("parentAlias", "MaterialInputs/SampleTypeA"), null);
        }

        @After
        public void cleanup()
        {
            resetCounter();
        }

    }

}


