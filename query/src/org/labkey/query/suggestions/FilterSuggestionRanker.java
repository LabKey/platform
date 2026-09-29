/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.query.suggestions;

import com.fasterxml.jackson.databind.JsonNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.suggestions.FilterSuggestion;
import org.labkey.api.query.suggestions.FilterSuggestion.ComposeField;
import org.labkey.api.query.suggestions.SuggestionColumn;
import org.labkey.api.util.DateUtil.MonthDayOption;
import org.labkey.api.util.JsonUtil;
import org.labkey.api.util.JunitUtil;
import org.labkey.query.QueryModule;
import org.labkey.query.suggestions.SuggestionTerm.DateSpan;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Ranks targeted filters for a search term from what is known about each column. Direct filters are ordered by tier
 * (numeric key, identity, exact value, partial value, integer value, shape), then come the compose options, and the
 * Q filter is always last. Port of buildSuggestions in @labkey/components; filterSuggestionCases.json keeps them in step.
 */
public class FilterSuggestionRanker
{
    public static final int DEFAULT_MAX_SUGGESTIONS = 10;
    private static final int MAX_PARTIAL_VALUE_SUGGESTIONS = 5;
    private static final int MAX_INTEGER_VALUE_SUGGESTIONS = 3;
    private static final int MIN_PARTIAL_MATCH_LENGTH = 2;
    private static final int MAX_CACHED_PATTERNS = 1000;
    // Generated unique IDs are zero-padded to 9 digits; shorter digit runs are far more likely to be quantities
    private static final Pattern BARCODE_PATTERN = Pattern.compile("^\\d{6,}$");
    private static final Pattern LETTER_PATTERN = Pattern.compile("[A-Za-z]");
    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d");
    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("[A-Za-z0-9]");

    private static final Map<String, Optional<Pattern>> NAME_EXPRESSION_PATTERNS = new ConcurrentHashMap<>();

    private record Partial(SuggestionColumn column, boolean isPrefix, String value) {}

    public static @NotNull List<FilterSuggestion> rank(@NotNull SuggestionTerm term, @NotNull List<SuggestionColumn> columns, int maxSuggestions)
    {
        String raw = term.getRaw();
        if (raw.isEmpty())
            return List.of();

        String lcRaw = lower(raw);
        List<FilterSuggestion> direct = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        // Columns the term plausibly belongs to, mapped to the operator to seed when composing a string filter
        Map<String, String> stringSignals = new LinkedHashMap<>();
        BigDecimal number = term.getNumber();
        boolean isCount = number != null && term.isInteger() && !term.hasLeadingZero();

        if (isCount)
        {
            for (SuggestionColumn column : columns)
                if (column.isKey() && column.getType() == SuggestionColumn.Type.INT)
                    addFilter(direct, seen, column, raw, "key");
        }

        for (SuggestionColumn column : columns)
        {
            if (column.getType() == SuggestionColumn.Type.STRING && matchesIdentity(column, raw))
            {
                addFilter(direct, seen, column, raw, "identity");
                stringSignals.putIfAbsent(column.getFieldKey(), "eq");
            }
        }

        for (SuggestionColumn column : columns)
        {
            if (column.getType() == SuggestionColumn.Type.INT || column.getValues() == null)
                continue;
            column.getValues().stream().filter(value -> lower(value).equals(lcRaw)).findFirst().ifPresent(exact -> {
                addFilter(direct, seen, column, exact, "exactValue");
                if (column.getType() == SuggestionColumn.Type.STRING)
                    stringSignals.putIfAbsent(column.getFieldKey(), "contains");
            });
        }

        if (raw.length() >= MIN_PARTIAL_MATCH_LENGTH)
        {
            List<Partial> partials = new ArrayList<>();
            for (SuggestionColumn column : columns)
            {
                if (column.getType() != SuggestionColumn.Type.STRING || column.getValues() == null)
                    continue;
                for (String value : column.getValues())
                {
                    String lcValue = lower(value);
                    if (!lcValue.equals(lcRaw) && lcValue.contains(lcRaw))
                    {
                        partials.add(new Partial(column, lcValue.startsWith(lcRaw), value));
                        stringSignals.putIfAbsent(column.getFieldKey(), "contains");
                    }
                }
            }

            partials.stream()
                .sorted(Comparator.comparing((Partial p) -> !p.isPrefix()).thenComparingInt(p -> p.value().length()))
                .limit(MAX_PARTIAL_VALUE_SUGGESTIONS)
                .forEach(p -> addFilter(direct, seen, p.column(), p.value(), "partialValue"));
        }

        if (isCount)
        {
            String value = number.toPlainString();
            columns.stream()
                .filter(column -> column.getType() == SuggestionColumn.Type.INT && !column.isKey() && column.getValues() != null && column.getValues().contains(value))
                .limit(MAX_INTEGER_VALUE_SUGGESTIONS)
                .forEach(column -> addFilter(direct, seen, column, value, "integerValue"));
        }

        String shape = getValueShape(raw);
        for (SuggestionColumn column : columns)
        {
            if (column.getType() == SuggestionColumn.Type.STRING && column.getShapes() != null && column.getShapes().contains(shape))
            {
                addFilter(direct, seen, column, raw, "shape");
                stringSignals.putIfAbsent(column.getFieldKey(), "eq");
            }
        }

        List<FilterSuggestion> compose = new ArrayList<>();

        if (!stringSignals.isEmpty())
        {
            List<ComposeField> fields = stringSignals.entrySet().stream().map(e -> new ComposeField(e.getKey(), e.getValue())).toList();
            compose.add(createCompose(columns, fields, raw, "string", raw));
        }

        if (number != null && !term.hasLeadingZero())
        {
            List<ComposeField> fields = columns.stream()
                .filter(column -> (column.getType() == SuggestionColumn.Type.FLOAT || (column.getType() == SuggestionColumn.Type.INT && term.isInteger()))
                    && !column.isKey() && inNumberRange(column, number))
                .map(column -> new ComposeField(column.getFieldKey(), "eq"))
                .toList();
            if (!fields.isEmpty())
                compose.add(createCompose(columns, fields, raw, "number", raw));
        }

        DateSpan dateSpan = term.getDateSpan();
        if (dateSpan != null)
        {
            boolean isDay = dateSpan.precision() == SuggestionTerm.Precision.DAY;
            String op = isDay ? "dateeq" : "between";
            List<ComposeField> fields = columns.stream()
                .filter(column -> column.getType() == SuggestionColumn.Type.DATE && overlapsDateRange(column, dateSpan))
                .map(column -> new ComposeField(column.getFieldKey(), op))
                .toList();
            String value = isDay ? dateSpan.start().toString() : dateSpan.start() + "," + dateSpan.end();
            if (!fields.isEmpty())
                compose.add(createCompose(columns, fields, value, "date", raw));
        }

        // A term with no string signal that also parses as a number or date belongs to those tiers, not to every string
        if (stringSignals.isEmpty() && number == null && dateSpan == null)
        {
            List<ComposeField> fields = columns.stream()
                .filter(column -> column.getType() == SuggestionColumn.Type.STRING)
                .sorted(Comparator.comparing((SuggestionColumn column) -> !column.isTitle()))
                .map(column -> new ComposeField(column.getFieldKey(), "contains"))
                .toList();
            if (!fields.isEmpty())
                compose.add(createCompose(columns, fields, raw, "string", raw));
        }

        int directLimit = Math.max(0, maxSuggestions - compose.size() - 1);
        List<FilterSuggestion> suggestions = new ArrayList<>(direct.subList(0, Math.min(directLimit, direct.size())));
        suggestions.addAll(compose);
        suggestions.add(FilterSuggestion.search(raw));
        return suggestions;
    }

    private static String lower(String s)
    {
        return s.toLowerCase(Locale.ROOT);
    }

    private static void addFilter(List<FilterSuggestion> direct, Set<String> seen, SuggestionColumn column, String value, String source)
    {
        if (seen.add(lower(column.getFieldKey() + "|" + value)))
            direct.add(FilterSuggestion.filter(column.getFieldKey(), "eq", value, column.getCaption() + " is " + value, source));
    }

    private static FilterSuggestion createCompose(List<SuggestionColumn> columns, List<ComposeField> fields, String value, String valueType, String raw)
    {
        String label;
        if (fields.size() == 1)
        {
            String fieldKey = fields.getFirst().fieldKey();
            String caption = columns.stream().filter(column -> column.getFieldKey().equals(fieldKey)).findFirst().map(SuggestionColumn::getCaption).orElse(fieldKey);
            label = "Compose a filter on " + caption + "…";
        }
        else
        {
            label = "Compose a " + valueType + " filter for \"" + raw + "\"… (" + fields.size() + " columns)";
        }

        return FilterSuggestion.compose(fields, value, valueType, label);
    }

    private static boolean matchesIdentity(SuggestionColumn column, String raw)
    {
        if (column.isUniqueId() && BARCODE_PATTERN.matcher(raw).matches())
            return true;

        return column.getNameExpressions().stream()
            .map(FilterSuggestionRanker::getNameExpressionPattern)
            .anyMatch(pattern -> pattern != null && pattern.matcher(raw).matches());
    }

    private static @Nullable Pattern getNameExpressionPattern(String expression)
    {
        Optional<Pattern> pattern = NAME_EXPRESSION_PATTERNS.get(expression);
        if (pattern == null)
        {
            if (NAME_EXPRESSION_PATTERNS.size() >= MAX_CACHED_PATTERNS)
                NAME_EXPRESSION_PATTERNS.clear();
            pattern = Optional.ofNullable(nameExpressionToPattern(expression));
            NAME_EXPRESSION_PATTERNS.put(expression, pattern);
        }
        return pattern.orElse(null);
    }

    private static @Nullable BigDecimal toBigDecimal(@Nullable Object value)
    {
        if (value instanceof BigDecimal bd)
            return bd;
        if (value instanceof Number n)
        {
            try
            {
                return new BigDecimal(n.toString());
            }
            catch (NumberFormatException e)
            {
                return null;
            }
        }
        return null;
    }

    private static boolean inNumberRange(SuggestionColumn column, BigDecimal value)
    {
        if (column.isRangeUnknown())
            return true;
        BigDecimal min = toBigDecimal(column.getMin());
        BigDecimal max = toBigDecimal(column.getMax());
        if (min == null || max == null)
            return false;
        return value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
    }

    private static boolean overlapsDateRange(SuggestionColumn column, DateSpan span)
    {
        if (column.isRangeUnknown())
            return true;
        if (!(column.getMin() instanceof LocalDate min) || !(column.getMax() instanceof LocalDate max))
            return false;
        return !span.start().isAfter(max) && !span.end().isBefore(min);
    }

    /** Letters become "A" and digits "9", so "SUBJ-028504" and "SUBJ-044304" share the shape "AAAA-999999". */
    public static @NotNull String getValueShape(@NotNull String value)
    {
        return DIGIT_PATTERN.matcher(LETTER_PATTERN.matcher(value).replaceAll("A")).replaceAll("9");
    }

    /**
     * Compiles a name expression into a pattern where each ${...} substitution matches anything. Returns null when the
     * constant parts carry no letter or digit, since a bare separator (e.g. "${Parent}-${genId}") matches too much.
     */
    public static @Nullable Pattern nameExpressionToPattern(@Nullable String expression)
    {
        if (expression == null || expression.isEmpty())
            return null;

        StringBuilder pattern = new StringBuilder("^");
        StringBuilder literal = new StringBuilder();
        StringBuilder run = new StringBuilder();
        int i = 0;
        while (i < expression.length())
        {
            if (expression.startsWith("${", i))
            {
                int depth = 1;
                int j = i + 2;
                while (j < expression.length() && depth > 0)
                {
                    if (expression.startsWith("${", j))
                    {
                        depth++;
                        j += 2;
                    }
                    else
                    {
                        if (expression.charAt(j) == '}')
                            depth--;
                        j++;
                    }
                }
                appendLiteral(pattern, run);
                pattern.append(".+?");
                i = j;
            }
            else
            {
                literal.append(expression.charAt(i));
                run.append(expression.charAt(i));
                i++;
            }
        }
        appendLiteral(pattern, run);

        if (!ALPHANUMERIC_PATTERN.matcher(literal).find())
            return null;

        return Pattern.compile(pattern.append("$").toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private static void appendLiteral(StringBuilder pattern, StringBuilder run)
    {
        if (!run.isEmpty())
        {
            pattern.append(Pattern.quote(run.toString()));
            run.setLength(0);
        }
    }

    public static class TestCase extends Assert
    {
        private static final String CASES_PATH = "suggestions/filterSuggestionCases.json";

        private static List<SuggestionColumn> toColumns(JsonNode columnSet)
        {
            List<SuggestionColumn> columns = new ArrayList<>();
            for (JsonNode node : columnSet)
            {
                SuggestionColumn.Type type = SuggestionColumn.Type.valueOf(node.get("type").asText().toUpperCase(Locale.ROOT));
                SuggestionColumn column = new SuggestionColumn(node.get("fieldKey").asText(), node.get("caption").asText(), type, null);
                column.setKey(node.path("isKey").asBoolean(false));
                column.setTitle(node.path("isTitle").asBoolean(false));
                column.setUniqueId(node.path("isUniqueId").asBoolean(false));
                column.setRangeUnknown(node.path("rangeUnknown").asBoolean(false));
                column.addNameExpression(node.path("nameExpression").asText(null));
                column.setValues(toStrings(node.get("values")));
                column.setShapes(toStrings(node.get("shapes")));
                if (node.has("min") && node.has("max"))
                {
                    if (type == SuggestionColumn.Type.DATE)
                        column.setRange(LocalDate.parse(node.get("min").asText()), LocalDate.parse(node.get("max").asText()));
                    else
                        column.setRange(node.get("min").decimalValue(), node.get("max").decimalValue());
                }
                columns.add(column);
            }
            return columns;
        }

        private static @Nullable List<String> toStrings(@Nullable JsonNode array)
        {
            if (array == null)
                return null;
            List<String> strings = new ArrayList<>();
            array.forEach(value -> strings.add(value.asText()));
            return strings;
        }

        @Test
        public void testSharedCases() throws IOException
        {
            File file = JunitUtil.getSampleData(ModuleLoader.getInstance().getModule(QueryModule.class), CASES_PATH);
            JsonNode root = JsonUtil.DEFAULT_MAPPER.readTree(file);
            JsonNode columnSets = root.get("columnSets");
            JsonNode cases = root.get("cases");
            assertFalse("No cases found in " + CASES_PATH, cases.isEmpty());

            for (JsonNode suggestionCase : cases)
            {
                String name = suggestionCase.get("name").asText();
                List<SuggestionColumn> columns = toColumns(columnSets.get(suggestionCase.get("columnSet").asText()));
                int max = suggestionCase.path("maxSuggestions").asInt(DEFAULT_MAX_SUGGESTIONS);
                SuggestionTerm term = SuggestionTerm.parse(suggestionCase.get("term").asText(), MonthDayOption.MONTH_DAY);
                JsonNode actual = JsonUtil.DEFAULT_MAPPER.valueToTree(rank(term, columns, max));
                assertEquals(name, suggestionCase.get("expected"), actual);
            }
        }

        @Test
        public void testNameExpressionPattern()
        {
            Pattern pattern = nameExpressionToPattern("PL-${genId}");
            assertNotNull(pattern);
            assertTrue(pattern.matcher("PL-5000000").matches());
            assertTrue(pattern.matcher("pl-5000000-1").matches());
            assertFalse(pattern.matcher("PL-").matches());
            assertFalse(pattern.matcher("XPL-1").matches());

            Pattern nested = nameExpressionToPattern("S-${${AliquotedFrom}-:defaultValue('x')}.${genId}");
            assertNotNull(nested);
            assertTrue(nested.matcher("S-abc.1").matches());

            assertNull(nameExpressionToPattern("${Parent}-${genId}"));
            assertNull(nameExpressionToPattern(""));
            Pattern literalDot = nameExpressionToPattern("a.b${x}");
            assertNotNull(literalDot);
            assertTrue(literalDot.matcher("a.b1").matches());
            assertFalse(literalDot.matcher("aXb1").matches());
        }

        @Test
        public void testValueShape()
        {
            assertEquals("AAAA-999999", getValueShape("SUBJ-028504"));
            assertEquals("99.9 AA", getValueShape("12.5 mL"));
        }
    }
}
