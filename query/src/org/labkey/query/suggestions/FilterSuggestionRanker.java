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
import org.apache.commons.lang3.Strings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.suggestions.FilterSuggestion;
import org.labkey.api.query.suggestions.FilterSuggestion.ColumnMatch;
import org.labkey.api.query.suggestions.FilterSuggestion.ComposeField;
import org.labkey.api.query.suggestions.FilterSuggestion.Match;
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
 * Q filter is always last.
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

    private static final Map<String, Optional<NameExpressionMatcher>> NAME_EXPRESSION_MATCHERS = new ConcurrentHashMap<>();

    private record Partial(SuggestionColumn column, boolean isPrefix, String value) {}

    /**
     * Constant parts of a name expression, with gaps[i] the minimum length of the substitutions between literals[i] and
     * literals[i + 1]. Matched without regex so a term can't trigger backtracking.
     */
    record NameExpressionMatcher(List<String> literals, int[] gaps)
    {
        boolean matches(String value)
        {
            if (!Strings.CI.startsWith(value, literals.getFirst()))
                return false;
            int pos = literals.getFirst().length();
            if (gaps.length == 0)
                return value.length() == pos;

            // Substitutions match anything, so placing each literal at its earliest position never rules out a match
            for (int i = 1; i < literals.size() - 1; i++)
            {
                int idx = Strings.CI.indexOf(value, literals.get(i), pos + gaps[i - 1]);
                if (idx < 0)
                    return false;
                pos = idx + literals.get(i).length();
            }

            String suffix = literals.getLast();
            return value.length() - suffix.length() >= pos + gaps[gaps.length - 1] && Strings.CI.endsWith(value, suffix);
        }
    }

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
                    addFilter(direct, seen, column, "eq", raw, "key", raw);
        }

        for (SuggestionColumn column : columns)
        {
            if (column.getType() != SuggestionColumn.Type.STRING)
                continue;
            boolean nameMatch = matchesNameExpression(column, raw);
            if (nameMatch || (column.isUniqueId() && BARCODE_PATTERN.matcher(raw).matches()))
            {
                addFilter(direct, seen, column, valueOp(column), raw, "identity", raw);
                // Any prefix of a generated name also matches its pattern, and users often type only a prefix
                if (nameMatch)
                    addFilter(direct, seen, column, "contains", raw, "identityContains", raw);
                stringSignals.putIfAbsent(column.getFieldKey(), valueOp(column));
            }
        }

        for (SuggestionColumn column : columns)
        {
            if (column.getType() == SuggestionColumn.Type.INT || column.getValues() == null)
                continue;
            column.getValues().stream().filter(value -> lower(value).equals(lcRaw)).findFirst().ifPresent(exact -> {
                addFilter(direct, seen, column, valueOp(column), exact, "exactValue", raw);
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
                .forEach(p -> addFilter(direct, seen, p.column(), valueOp(p.column()), p.value(), "partialValue", raw));
        }

        if (isCount)
        {
            String value = number.toPlainString();
            columns.stream()
                .filter(column -> column.getType() == SuggestionColumn.Type.INT && !column.isKey() && column.getValues() != null && column.getValues().contains(value))
                .limit(MAX_INTEGER_VALUE_SUGGESTIONS)
                .forEach(column -> addFilter(direct, seen, column, "eq", value, "integerValue", raw));
        }

        String shape = getValueShape(raw);
        for (SuggestionColumn column : columns)
        {
            if (column.getType() == SuggestionColumn.Type.STRING && column.getShapes() != null && column.getShapes().contains(shape))
            {
                addFilter(direct, seen, column, valueOp(column), raw, "shape", raw);
                // Titles entered by hand don't follow the name expression, but users still search them by prefix
                if (column.isTitle())
                    addFilter(direct, seen, column, "contains", raw, "shapeContains", raw);
                stringSignals.putIfAbsent(column.getFieldKey(), valueOp(column));
            }
        }

        // Ranked below every specific match, but given a reserved slot below so those can't crowd it out
        List<FilterSuggestion> partialMatches = new ArrayList<>();
        if (raw.length() >= MIN_PARTIAL_MATCH_LENGTH)
        {
            columns.stream()
                .filter(column -> column.isPartialMatch() && column.getType() == SuggestionColumn.Type.STRING)
                .forEach(column -> addFilter(partialMatches, seen, column, "contains", raw, "partialMatch", raw));
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

        // Numbers and dates belong to their own tiers, not to every string column; zero-padded numbers are identifiers
        if ((number == null || term.hasLeadingZero()) && dateSpan == null)
        {
            List<ComposeField> fields = columns.stream()
                .filter(column -> column.getType() == SuggestionColumn.Type.STRING)
                .sorted(Comparator.comparing((SuggestionColumn column) -> !column.isTitle()))
                .map(column -> new ComposeField(column.getFieldKey(), "contains"))
                .toList();
            Set<String> signalKeys = stringSignals.keySet();
            boolean sameAsSignals = fields.size() == signalKeys.size() && fields.stream().allMatch(field -> signalKeys.contains(field.fieldKey()));
            if (!fields.isEmpty() && !sameAsSignals)
                compose.add(createCompose(columns, fields, raw, "string", raw));
        }

        int directLimit = Math.max(0, maxSuggestions - compose.size() - 1);
        // Never reserve the only direct slot, which belongs to the strongest specific match
        int otherLimit = directLimit;
        if (directLimit > partialMatches.size())
            otherLimit -= partialMatches.size();
        List<FilterSuggestion> suggestions = new ArrayList<>(direct.subList(0, Math.min(otherLimit, direct.size())));
        suggestions.addAll(partialMatches.subList(0, Math.min(partialMatches.size(), directLimit - suggestions.size())));
        suggestions.addAll(compose);
        suggestions.add(FilterSuggestion.search(raw));
        return suggestions;
    }

    private static String lower(String s)
    {
        return s.toLowerCase(Locale.ROOT);
    }

    /** Equals never matches a multi-valued column, whose rows hold their values joined into one string */
    private static String valueOp(SuggestionColumn column)
    {
        return column.isMultiValued() ? "contains" : "eq";
    }

    private static void addFilter(List<FilterSuggestion> direct, Set<String> seen, SuggestionColumn column, String op, String value, String source, String raw)
    {
        String verb = "contains".equals(op) ? " contains " : " is ";
        if (seen.add(lower(column.getFieldKey() + "|" + op + "|" + value)))
            direct.add(FilterSuggestion.filter(column, op, value, verb, source, raw));
    }

    private static FilterSuggestion createCompose(List<SuggestionColumn> columns, List<ComposeField> fields, String value, String valueType, String raw)
    {
        if (fields.size() == 1)
        {
            String fieldKey = fields.getFirst().fieldKey();
            String prefix = "Filter ";
            SuggestionColumn column = columns.stream().filter(c -> c.getFieldKey().equals(fieldKey)).findFirst().orElse(null);
            if (column == null)
                return FilterSuggestion.compose(fields, value, valueType, prefix + fieldKey + "…", null, null);
            ColumnMatch colMatch = new ColumnMatch(prefix.length(), prefix.length() + column.getCaption().length(), column.getGridFieldKey());
            return FilterSuggestion.compose(fields, value, valueType, prefix + column.getCaption() + "…", null, colMatch);
        }

        String prefix = "Filter a " + valueType + " field for ";
        String label = prefix + raw + " (" + fields.size() + " fields)";
        return FilterSuggestion.compose(fields, value, valueType, label, new Match(prefix.length(), prefix.length() + raw.length()), null);
    }

    private static boolean matchesNameExpression(SuggestionColumn column, String raw)
    {
        return column.getNameExpressions().stream()
            .map(FilterSuggestionRanker::getNameExpressionMatcher)
            .anyMatch(matcher -> matcher != null && matcher.matches(raw));
    }

    private static @Nullable NameExpressionMatcher getNameExpressionMatcher(String expression)
    {
        Optional<NameExpressionMatcher> matcher = NAME_EXPRESSION_MATCHERS.get(expression);
        if (matcher == null)
        {
            if (NAME_EXPRESSION_MATCHERS.size() >= MAX_CACHED_PATTERNS)
                NAME_EXPRESSION_MATCHERS.clear();
            matcher = Optional.ofNullable(compileNameExpression(expression));
            NAME_EXPRESSION_MATCHERS.put(expression, matcher);
        }
        return matcher.orElse(null);
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
     * Compiles a name expression into a matcher where each ${...} substitution matches anything. Returns null when the
     * constant parts carry no letter or digit, since a bare separator (e.g. "${Parent}-${genId}") matches too much.
     */
    static @Nullable NameExpressionMatcher compileNameExpression(@Nullable String expression)
    {
        if (expression == null || expression.isEmpty())
            return null;

        List<String> literals = new ArrayList<>();
        List<Integer> gaps = new ArrayList<>();
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
                // Adjacent substitutions widen the current gap rather than adding an empty literal
                if (run.isEmpty() && !gaps.isEmpty())
                    gaps.set(gaps.size() - 1, gaps.getLast() + 1);
                else
                {
                    literals.add(run.toString());
                    run.setLength(0);
                    gaps.add(1);
                }
                i = j;
            }
            else
            {
                literal.append(expression.charAt(i));
                run.append(expression.charAt(i));
                i++;
            }
        }
        literals.add(run.toString());

        if (!ALPHANUMERIC_PATTERN.matcher(literal).find())
            return null;

        return new NameExpressionMatcher(List.copyOf(literals), gaps.stream().mapToInt(Integer::intValue).toArray());
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
                column.setGridFieldKey(node.path("gridFieldKey").asText(column.getFieldKey()));
                column.setKey(node.path("isKey").asBoolean(false));
                column.setTitle(node.path("isTitle").asBoolean(false));
                column.setUniqueId(node.path("isUniqueId").asBoolean(false));
                column.setRangeUnknown(node.path("rangeUnknown").asBoolean(false));
                column.setMultiValued(node.path("multiValued").asBoolean(false));
                column.setPartialMatch(node.path("partialMatch").asBoolean(false));
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
        public void testNameExpressionMatcher()
        {
            NameExpressionMatcher matcher = compileNameExpression("PL-${genId}");
            assertNotNull(matcher);
            assertTrue(matcher.matches("PL-5000000"));
            assertTrue(matcher.matches("pl-5000000-1"));
            assertFalse(matcher.matches("PL-"));
            assertFalse(matcher.matches("XPL-1"));

            NameExpressionMatcher nested = compileNameExpression("S-${${AliquotedFrom}-:defaultValue('x')}.${genId}");
            assertNotNull(nested);
            assertTrue(nested.matches("S-abc.1"));
            assertFalse(nested.matches("S-.1"));
            assertFalse(nested.matches("S-abc."));

            assertNull(compileNameExpression("${Parent}-${genId}"));
            assertNull(compileNameExpression(""));
            NameExpressionMatcher literalDot = compileNameExpression("a.b${x}");
            assertNotNull(literalDot);
            assertTrue(literalDot.matches("a.b1"));
            assertFalse(literalDot.matches("aXb1"));

            NameExpressionMatcher adjacent = compileNameExpression("${a}${b}x");
            assertNotNull(adjacent);
            assertFalse(adjacent.matches("ax"));
            assertTrue(adjacent.matches("abx"));

            NameExpressionMatcher constant = compileNameExpression("Fixed");
            assertNotNull(constant);
            assertTrue(constant.matches("fixed"));
            assertFalse(constant.matches("Fixed1"));

            // Suffix may not overlap the last middle literal
            NameExpressionMatcher overlap = compileNameExpression("${a}-x${b}x-");
            assertNotNull(overlap);
            assertFalse(overlap.matches("1-x-"));
            assertTrue(overlap.matches("1-x2x-"));
        }

        @Test
        public void testValueShape()
        {
            assertEquals("AAAA-999999", getValueShape("SUBJ-028504"));
            assertEquals("99.9 AA", getValueShape("12.5 mL"));
        }
    }
}
