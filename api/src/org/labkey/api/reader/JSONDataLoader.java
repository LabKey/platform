/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
package org.labkey.api.reader;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonLocation;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.ObjectCodec;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.action.ApiJsonWriter;
import org.labkey.api.action.ExtendedApiQueryResponse;
import org.labkey.api.action.ExtendedApiQueryResponse.ColMapEntry;
import org.labkey.api.data.Container;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.dataiterator.DataIterator;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.iterator.CloseableIterator;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.FileType;
import org.labkey.api.util.JsonUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.util.JsonUtil.expectArrayEnd;
import static org.labkey.api.util.JsonUtil.expectArrayStart;
import static org.labkey.api.util.JsonUtil.expectObjectEnd;
import static org.labkey.api.util.JsonUtil.expectObjectStart;
import static org.labkey.api.util.JsonUtil.isArrayEnd;
import static org.labkey.api.util.JsonUtil.skipValue;

/**
 * Reads data from an JSON table that matches the selectRows 8.3, 9.1, and 13.2 response formats.
 * The Jackson parser is used to stream the results one row at a time to handle large datasets.
 * If available, the name and type information will be read from the metaData fields array, otherwise
 * all the values will be converted into Strings.
 *
 * Example:
 * <pre>
 * {
 *   metaData: [
 *     fields: [{
 *       name: "Column Name 1", type: "string",
 *       name: "Column Name 2", type: "int"
 *     },{
 *     }]
 *   ],
 *   rows: [{
 *      "Column Name 1": "Row 1 Value 1",
 *      "Column Name 2": "Row 1 Value 2",
 *      ...
 *   },{
 *      "Column Name 1": "Row 2 Value 1",
 *      "Column Name 2": "Row 2 Value 2",
 *   },{
 *     ...
 *   }]
 * }
 * </pre>
 *
 * User: kevink
 * Date: 10/1/12
 */
public class JSONDataLoader extends DataLoader
{
    public static final FileType FILE_TYPE = new FileType(Arrays.asList(".json"), ".json", Arrays.asList(ApiJsonWriter.CONTENT_TYPE_JSON))
    {
        /**
         * Looks one of the following at the top-level of the json object to consider it a match:
         * - 'schemaName' and 'queryName' strings
         * - 'metaData' object
         * - 'rows' array
         *
         * @param header First few K of the file.
         * @return True if the header looks like it will be a JSON selectRows response.
         */
        @Override
        public boolean isHeaderMatch(@NotNull byte[] header)
        {
            boolean foundSchemaName = false;
            boolean foundQueryName = false;

            JsonFactory factory = new JsonFactory();
            try (JsonParser parser = factory.createParser(header))
            {
                int depth = 0;
                JsonToken tok;
                while (null != (tok = parser.nextToken()))
                {
                    switch (tok)
                    {
                        case START_OBJECT:
                            depth++;
                            break;

                        case END_OBJECT:
                            depth--;
                            break;

                        case FIELD_NAME:
                            if (depth == 1)
                            {
                                String fieldName = parser.getCurrentName();

                                if (fieldName.equals("schemaName"))
                                {
                                    tok = parser.nextToken();
                                    if (tok == JsonToken.VALUE_STRING)
                                    {
                                        foundSchemaName = true;
                                        if (foundQueryName)
                                            return true;
                                    }
                                    else
                                        return false;
                                }
                                else if (fieldName.equals("queryName"))
                                {
                                    tok = parser.nextToken();
                                    if (tok == JsonToken.VALUE_STRING)
                                    {
                                        foundQueryName = true;
                                        if (foundSchemaName)
                                            return true;
                                    }
                                    else
                                        return false;
                                }
                                else if (fieldName.equals("metaData"))
                                {
                                    // value of metaData must be an object
                                    tok = parser.nextToken();
                                    return tok == JsonToken.START_OBJECT;
                                }
                                else if (fieldName.equals("rows"))
                                {
                                    // value of rows must be an array
                                    tok = parser.nextToken();
                                    return tok == JsonToken.START_ARRAY;
                                }
                            }
                            break;
                    }
                }

                return false;
            }
            catch (IOException e)
            {
                return false;
            }
        }
    };

    public static class Factory extends AbstractDataLoaderFactory
    {
        @NotNull
        @Override
        public DataLoader createLoader(InputStream is, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
        {
            return new JSONDataLoader(is, hasColumnHeaders, mvIndicatorContainer);
        }

        @NotNull
        @Override
        public DataLoader createLoader(File file, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
        {
            return new JSONDataLoader(file, hasColumnHeaders, mvIndicatorContainer);
        }

        @NotNull
        @Override
        public FileType getFileType()
        {
            return FILE_TYPE;
        }
    }

    private ObjectMapper _mapper;
    private JsonParser _parser;
    private List<Map<String, Map<ColMapEntry, Object>>> _firstRows;

    public JSONDataLoader(File inputFile, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
    {
        super(mvIndicatorContainer);
        setSource(inputFile);
        setScrollable(true);
        //setHasColumnHeaders(hasColumnHeaders);
        setHasColumnHeaders(false);
        setInferTypes(false);
        setScanAheadLineCount(1);

        init(new FileInputStream(inputFile));
    }

    public JSONDataLoader(InputStream is, boolean hasColumnHeaders, Container mvIndicatorContainer) throws IOException
    {
        super(mvIndicatorContainer);
        setScrollable(false);
        //setHasColumnHeaders(hasColumnHeaders);
        setHasColumnHeaders(false);
        setInferTypes(false);
        setScanAheadLineCount(1);

        init(is);
    }

    // For unit testing only
    private JSONDataLoader(JsonParser parser)
    {
        setHasColumnHeaders(false);
        setInferTypes(false);
        setScanAheadLineCount(1);

        _parser = parser;
    }

    protected void init(InputStream is) throws IOException
    {
        // TODO: JsonFactory is threadsafe and can be configured once and shared across the application.
        JsonFactory factory = new JsonFactory();
        _mapper = new ObjectMapper(factory);
        _parser = factory.createParser(is);
        enterTopLevel();
    }

    protected void enterTopLevel() throws IOException
    {
        if (_parser == null)
            throw new IllegalStateException("Parser not initialized");

        if (!_parser.getParsingContext().inRoot())
            throw new IllegalStateException("Parser should be at top-level");

        JsonToken tok = _parser.nextToken();
        if (tok != JsonToken.START_OBJECT)
            throw new IllegalStateException("Expected start object");
    }

    /**
     * PRECONDITION: Parser is at START_OBJECT at top-level.
     * POSTCONDITION: Parser is positioned at START_ARRAY token of 'rows', or throws an exception if 'rows' wasn't found.
     *
     * We will skip forward until we find a 'metaData' field and parse the contents.
     * The parser will continue forward until the 'rows' field is found.
     */
    protected void parseMetadata() throws IOException
    {
        expectObjectStart(_parser);

        while (_parser.getCurrentToken() == JsonToken.FIELD_NAME)
        {
            String fieldName = _parser.getCurrentName();
            _parser.nextToken();

            if ("metaData".equals(fieldName) && _parser.getCurrentToken() == JsonToken.START_OBJECT)
            {
                parseMetadataContents();
            }
            else if ("rows".equals(fieldName) && _parser.getCurrentToken() == JsonToken.START_ARRAY)
            {
                // stop parsing
                break;
            }
            else
            {
                skipValue(_parser);
            }
        }

        if (!(_parser.getCurrentToken() == JsonToken.START_ARRAY && _parser.getCurrentName().equals("rows")))
            throw new JsonParseException("Expected 'rows' field", _parser.getTokenLocation());
    }

    /**
     * PRECONDITION: Parser is on START_OBJECT of metaData
     * POSTCONDITION: Parser is on END_OBJECT of the metaData value
     *
     * Parse the metaData fields and construct the ColumnDescriptor array.
     */
    protected void parseMetadataContents() throws IOException
    {
        expectObjectStart(_parser);

        while (_parser.getCurrentToken() == JsonToken.FIELD_NAME)
        {
            String fieldName = _parser.getCurrentName();
            _parser.nextToken();

            if ("fields".equals(fieldName) && _parser.getCurrentToken() == JsonToken.START_ARRAY && _columns == null)
            {
                _columns = parseFields(_parser, _mvIndicatorContainer);
            }
            else
            {
                // ignore other fields
                skipValue(_parser);
            }
        }

        expectObjectEnd(_parser);
    }

    /**
     * PRECONDITION: At start of 'fields' array
     * POSTCONDITION: At end of 'fields' array.
     *
     * Creates the list of ColumnDescriptors for this DataIterator.
     *
     * @throws IOException
     */
    protected static ColumnDescriptor[] parseFields(JsonParser parser, Container mvIndicatorContainer) throws IOException
    {
        JsonLocation loc = expectArrayStart(parser);

        if (isArrayEnd(parser))
            throw new JsonParseException("Expected array of fields, got empty array", loc);

        List<ColumnDescriptor> cols = new ArrayList<>();

        while (!isArrayEnd(parser))
        {
            ColumnDescriptor col = parseField(parser, mvIndicatorContainer);
            if (col != null)
                cols.add(col);
        }

        expectArrayEnd(parser);

        return cols.toArray(new ColumnDescriptor[cols.size()]);
    }

    // For testing only
    private static ColumnDescriptor parseField(JsonParser parser) throws IOException
    {
        return parseField(parser, null);
    }

    /**
     * PRECONDITION: At start object for item in 'fields' array.
     * POSTCONDITION: At end object.
     *
     * Reads enough of a single metadata field to create a {@link ColumnDescriptor} for loading purposes.
     * The 'fieldKey' property is a JSON array in 13.2 format and is a string in earlier formats
     * while the 'fieldKeyArray' property is a JSON array earlier formats.
     *
     * The part of the field read are (in BNF):
     *
     * (fieldKey | fieldKeyArray), type?, mvEnabled?
     *
     * One of fieldKey or fieldKeyArray is required.
     * Type and mvEnabled are optional.
     *
     * {
     *     fieldKey: [ "parts" ],
     *     type: "string",
     *     mvEnabled: boolean
     * }
     *
     * @return Returns a ColumnDescriptor if the fieldKey is present.
     */
    protected static ColumnDescriptor parseField(JsonParser parser, Container mvIndicatorContainer) throws IOException
    {
        JsonLocation loc = expectObjectStart(parser);

        FieldKey fieldKey = null;
        String type = null;
        Boolean mvEnabled = Boolean.FALSE;

        while (parser.getCurrentToken() == JsonToken.FIELD_NAME)
        {
            String fieldName = parser.getCurrentName();
            parser.nextToken();

            if ((fieldName.equals("fieldKey") || fieldName.equals("fieldKeyArray")) && JsonToken.START_ARRAY == parser.getCurrentToken() && fieldKey == null)
            {
                fieldKey = parser.readValueAs(FieldKey.class);
                if (fieldKey == null)
                    throw new JsonParseException("Failed to parse " + fieldName + " property", loc);

                parser.nextToken();
            }
            else if (fieldName.equals("type"))
            {
                type = parser.getValueAsString();
                if (type == null)
                    throw new JsonParseException("Failed to parse type property", loc);

                parser.nextToken();
            }
            else if (fieldName.equals("mvEnabled"))
            {
                mvEnabled = parser.getValueAsBoolean();
                parser.nextToken();
            }
            else
            {
                // ignore field
                skipValue(parser);
            }
        }

        ColumnDescriptor col = null;
        if (fieldKey != null)
        {
            col = new ColumnDescriptor(fieldKey.toString());
            col.clazz = DisplayColumn.getClassFromJsonTypeName(type);
            if (mvEnabled)
                col.setMvEnabled(mvIndicatorContainer);
        }

        expectObjectEnd(parser);
        return col;
    }

    /**
     * PRECONDITION: At start object for item in 'rows' array.
     * POSTCONDITION: At end object.
     *
     * Reads a single row and returns the raw values (not display values) for the row as strings.
     * If not null, the values will be ordered according to the <code>cols</code> ColumnDescriptor array
     * and any values not in the ColumnDescriptor array will be ignored.
     *
     * @return The values of the next row or null if no more rows are available.
     */
    protected Object[] parseRow() throws IOException
    {
        Map<String, Map<ColMapEntry, Object>> row;

        // Use previously parsed rows if they exist
        if (_firstRows != null)
        {
            row = _firstRows.remove(0);
            if (_firstRows.isEmpty())
                _firstRows = null;
        }
        else
        {
            row = parseRow(_parser);
        }

        Object[] values = null;
        if (row != null)
        {
            // create the value array
            values = new Object[_columns.length];
            for (int i = 0; i < _columns.length; i++)
            {
                Map<ColMapEntry, Object> col = row.get(_columns[i].name);
                if (col != null)
                    values[i] = col.get(ColMapEntry.value);
            }
        }

        return values;
    }

    /**
     * PRECONDITION: At start object for item in 'rows' array.
     * POSTCONDITION: At end object.
     *
     * Reads a single row and returns a map of columns (ColMap).
     *
     * 8.3 format:
     *  {
     *      "field": 100
     *      "_labkeyurl_field": "http://..."
     *  }
     *
     * 9.1 format:
     *  {
     *      "field": {
     *          "value": 100,
     *          "displayValue": "ignored",
     *          "url": "http://...",
     *          "mvValue": "ignored"
     *      }
     *  }
     *
     * 13.2 format:
     *  {
     *      "links: {
     *          "title": {
     *              "href": "http://...",
     *              "title": "title"
     *          }
     *      },
     *      "data": {
     *          "field": {
     *              "value": 100,
     *              "displayValue": "ignored",
     *              "mvValue": "ignored"
     *          }
     *      }
     *  }
     *
     * @return Array of JSON value types 'Number', 'String', 'Boolean' for the row or null if no row is available.
     * @throws IOException
     */
    protected static Map<String, Map<ColMapEntry, Object>> parseRow(JsonParser parser) throws IOException
    {
        if (parser.getCurrentToken() != JsonToken.START_OBJECT)
            return null;

        expectObjectStart(parser);

        Map<String, Map<ColMapEntry, Object>> row;

        // Read the entire row object structure into memory
        JsonNode node = parser.readValueAsTree();

        // Check for 13.2 format first (data node has a field that is an object)
        if (isFormat13_2(node))
            row = parseRow91(parser, node.get("data"));
        else if (isFormat9_1(node))
            row = parseRow91(parser, node);
        else
            row = parseRow83(node);

        // NOTE: parser.readValueAsTree() advances the parser past the end object token
        //expectObjectEnd(parser);
        return row;
    }

    /**
     * Checks for 13.2 format by looking for a 'data' object with a child field that is also an object.
     *  {
     *      "data": {
     *          "column": { }
     *      }
     *  }
     */
    protected static boolean isFormat13_2(TreeNode node)
    {
        TreeNode dataNode = node.get("data");
        if (dataNode != null)
        {
            Iterator<String> names = dataNode.fieldNames();
            if (isFormat9_1(dataNode))
                return true;
        }

        return false;
    }

    /**
     * Checks for 9.1 format by looking for an object with a child field that is also an object.
     * You MUST check that this isn't 13.2 format first.
     *  {
     *      "column": { }
     *  }
     */
    protected static boolean isFormat9_1(TreeNode node)
    {
        Iterator<String> names = node.fieldNames();
        if (names != null && names.hasNext())
        {
            String name = names.next();
            TreeNode child = node.get(name);
            if (child != null && child.isObject())
                return true;
        }

        return false;
    }

    protected static Map<String, Map<ColMapEntry, Object>> parseRow83(JsonNode dataNode) throws JsonParseException
    {
        Map<String, Map<ColMapEntry, Object>> row = new HashMap<>();

        Iterator<Map.Entry<String, JsonNode>> iter = dataNode.fields();
        while (iter.hasNext())
        {
            Map.Entry<String, JsonNode> field = iter.next();
            String fieldName = field.getKey();
            if (fieldName.startsWith("_labkeyurl_"))
                continue;

            JsonNode valueNode = field.getValue();
            Object value = null;
            if (valueNode != null)
                value = JsonUtil.valueOf(valueNode);

            Map<ColMapEntry, Object> rowField = Collections.singletonMap(ColMapEntry.value, value);
            row.put(fieldName, rowField);
        }

        return row;
    }

    private static TypeReference rowType = new TypeReference<Map<String, Map<ColMapEntry, Object>>>() { };

    protected static Map<String, Map<ColMapEntry, Object>> parseRow91(JsonParser parser, JsonNode dataNode) throws IOException
    {
//        Map<String, Map<ColMapEntry, Object>> row = new HashMap<>();
//
//        Iterator<String> iter = dataNode.fieldNames();
//        while (iter.hasNext())
//        {
//            String fieldName = iter.next();
//            Map<ColMapEntry, Object> rowField = parseColumn(parser, dataNode.get(fieldName));
//
//            row.put(fieldName, rowField);
//        }
//
//        return row;

        ObjectCodec codec = parser.getCodec();
        JsonParser subParser = dataNode.traverse(codec);
        Map<String, Map<ColMapEntry, Object>> row = subParser.readValueAs(rowType);
        return row;
    }

    protected static Map<ColMapEntry, Object> parseColumn(JsonParser parser, JsonNode rowFieldNode) throws JsonProcessingException
    {
        ObjectCodec codec = parser.getCodec();
        Map<ColMapEntry, Object> col = codec.treeToValue(rowFieldNode, ExtendedApiQueryResponse.ColMap.class);
        return col;
    }


    /**
     * I hate this function.  We must implement it because of how inferColumns() behaves.
     * Reads the first JSON rows and returns and array of keys and an array of values.  No other rows will be read.
     */
    @Override
    public String[][] getFirstNLines(int n) throws IOException
    {
        if (_firstRows != null)
            throw new IllegalStateException("First n rows can only be called once");

        List<Map<String, Map<ColMapEntry, Object>>> firstRows = new ArrayList<>(n);
        Set<String> keys = new LinkedHashSet<>();
        for (int i = 0; i < n; i++)
        {
            // advance to the next row start object
            _parser.nextToken();
            Map<String, Map<ColMapEntry, Object>> row = parseRow(_parser);
            if (row == null)
                break;

            keys.addAll(row.keySet());
            firstRows.add(row);
        }

        // No rows found
        if (firstRows.isEmpty())
            return new String[0][];

        _firstRows = firstRows;

        String[][] lines = new String[_firstRows.size()+1][];

        // Create the header line
        String[] header = new String[keys.size()];
        lines[0] = header;
        int col = 0;
        for (String key : keys)
            header[col] = key;

        // Create the remaining lines
        int lineNum = 1;
        for (Map<String, Map<ColMapEntry, Object>> row : _firstRows)
        {
            String[] line = new String[header.length];
            for (col = 0; col < header.length; col++)
            {
                Map<ColMapEntry, Object> column = row.get(header[col]);
                if (column != null)
                {
                    Object value = column.get(ColMapEntry.value);
                    if (value != null)
                        line[col] = String.valueOf(value);
                }
            }

            lines[lineNum] = line;
            lineNum++;
        }

        return lines;
    }

    /**
     * NOTE: We don't call super.initializeColumns() which uses inferColumnInfo() from the first N lines.
     * @throws IOException
     */
    @Override
    protected void initializeColumns() throws IOException
    {
        // Find 'metaData' and create ColumnDescriptors
        parseMetadata();

        // If no 'metaData' found, fallback on returning the first N rows of data and using super.inferColumnInfo()
        if (_columns == null && _scanAheadLineCount > 0)
        {
            String[][] lines = getFirstNLines(_scanAheadLineCount);
            if (lines != null && lines.length > 0)
            {
                String[] headers = lines[0];
                ColumnDescriptor[] cols = new ColumnDescriptor[headers.length];
                for (int i = 0; i < headers.length; i++)
                {
                    cols[i] = new ColumnDescriptor(headers[i]);
                }
                _columns = cols;
            }
            else
            {
                _columns = new ColumnDescriptor[0];
            }
        }
    }

    @Override
    public CloseableIterator<Map<String, Object>> iterator()
    {
        try
        {
            ensureInitialized();
            return new Iter();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close()
    {
        try
        {
            if (_parser != null)
            {
                _parser.close();
                _parser = null;
            }
        }
        catch (IOException e)
        {
            Logger.getLogger(JSONDataLoader.class).error("Error closing JSON stream", e);
        }
    }

    @Override
    protected DataIterator createDataIterator(DataIteratorContext context) throws IOException
    {
        return new _DataIterator(context, getColumns(), false);
    }

    private class Iter extends DataLoaderIterator
    {
        protected Iter() throws IOException
        {
            super(_skipLines);
            assert _skipLines != -1;

            int start = _firstRows == null ? 0 : _firstRows.size();
            for (int i = start; i < lineNum(); i++)
            {
                // Move the parser to the next row object
                if (_firstRows == null)
                    _parser.nextToken();

                JSONDataLoader.this.parseRow();
            }
        }

        @Override
        public void close() throws IOException
        {
            try
            {
                JSONDataLoader.this.close();
            }
            finally
            {
                super.close();
            }
        }

        @Override
        protected Object[] readFields() throws IOException
        {
            // Move the parser to the next row object
            if (_firstRows == null)
                _parser.nextToken();

            // Read the next row or null of no more rows are present.
            return JSONDataLoader.this.parseRow();
        }

        @Override
        public boolean hasNext()
        {
            if (_parser == null)
                return false;
            return super.hasNext();
        }
    }


    public static class HeaderMatchTest extends Assert
    {
        @Test
        public void notJSON()
        {
            assertFalse(FILE_TYPE.isHeaderMatch("".getBytes()));
            assertFalse(FILE_TYPE.isHeaderMatch("hello".getBytes()));
        }

        @Test
        public void badJSON()
        {
            assertFalse(FILE_TYPE.isHeaderMatch("{".getBytes()));
            assertFalse(FILE_TYPE.isHeaderMatch("[".getBytes()));
            assertFalse(FILE_TYPE.isHeaderMatch("{}".getBytes()));
            assertFalse(FILE_TYPE.isHeaderMatch("{}}".getBytes()));
            assertFalse(FILE_TYPE.isHeaderMatch("[]".getBytes()));
            assertFalse(FILE_TYPE.isHeaderMatch("\"foo\"".getBytes()));
            assertFalse(FILE_TYPE.isHeaderMatch("3".getBytes()));
            assertFalse(FILE_TYPE.isHeaderMatch("null".getBytes()));
            assertFalse(FILE_TYPE.isHeaderMatch("true".getBytes()));
        }

        @Test
        public void noMatch()
        {
            assertFalse(FILE_TYPE.isHeaderMatch("{\"foo\": 3}".getBytes()));

            // metaData must be an object
            assertFalse(FILE_TYPE.isHeaderMatch("{\"metaData\": []}".getBytes()));
            assertFalse(FILE_TYPE.isHeaderMatch("{\"metaData\": \"foo\"}".getBytes()));

            // metaData must be at the top-level
            assertFalse(FILE_TYPE.isHeaderMatch("{\"foo\": {\"metaData\": []}}".getBytes()));

            // rows must be an array
            assertFalse(FILE_TYPE.isHeaderMatch("{\"rows\": {}}".getBytes()));
            assertFalse(FILE_TYPE.isHeaderMatch("{\"rows\": true}".getBytes()));

            // need both schemaName and queryName
            assertFalse(FILE_TYPE.isHeaderMatch("{\"schemaName\": \"foo\"}".getBytes()));
            assertFalse(FILE_TYPE.isHeaderMatch("{\"queryName\": \"foo\"}".getBytes()));

            // schemaName and queryName must be strings
            assertFalse(FILE_TYPE.isHeaderMatch("{\"schemaName\": false, \"queryName\": 3}".getBytes()));
        }

        @Test
        public void matches()
        {
            assertTrue(FILE_TYPE.isHeaderMatch("{\"schemaName\": \"foo\", \"queryName\": \"bar\"}".getBytes()));
            assertTrue(FILE_TYPE.isHeaderMatch("{\"metaData\": {}}".getBytes()));
            assertTrue(FILE_TYPE.isHeaderMatch("{\"rows\": []}".getBytes()));

            // leading junk is ignored
            assertTrue(FILE_TYPE.isHeaderMatch("    {\"foo\": [{\"q\": 1}, 3], \"rows\": []}".getBytes()));
        }

        @Test
        public void partialContent()
        {
            assertTrue(FILE_TYPE.isHeaderMatch("{ \"metaData\": {".getBytes()));
            assertTrue(FILE_TYPE.isHeaderMatch("{ \"rows\": [".getBytes()));
        }
    }

    public abstract static class JsonBaseTest extends Assert
    {
        JsonParser createParser(String json) throws IOException
        {
            ObjectMapper mapper = new ObjectMapper();
            JsonFactory factory = new JsonFactory(mapper);
            JsonParser parser = factory.createParser(json);
            parser.nextToken();
            return parser;
        }
    }

    public static class MetadataTest extends JsonBaseTest
    {
        @Test
        public void notObject() throws IOException
        {
            JsonParser parser = createParser("[ ]");
            try
            {
                JSONDataLoader.parseField(parser);
                fail("Expected JsonParseException");
            }
            catch (JsonParseException pex)
            {
                // expected
            }
        }

        @Test
        public void empty() throws IOException
        {
            JsonParser parser = createParser("{ }");
            assertNull(JSONDataLoader.parseField(parser));
        }

        @Test
        public void ignoreOtherProperties() throws IOException
        {
            JsonParser parser = createParser("{ \"foo\": [1, 2], \"bar\": {\"a\": \"A\"}, \"quux\": 3 }");
            assertNull(JSONDataLoader.parseField(parser));
        }

        @Test
        public void badFieldKey() throws IOException
        {
            JsonParser parser = createParser("{ \"fieldKey\": 3 }");
            assertNull(JSONDataLoader.parseField(parser));
        }

        @Test
        public void emptyFieldKey() throws IOException
        {
            JsonParser parser = createParser("{ \"fieldKey\": [] }");
            try
            {
                JSONDataLoader.parseField(parser);
                fail("Expected JsonParseException");
            }
            catch (JsonParseException pex)
            {
                // expected
            }
        }

        @Test
        public void fieldKey() throws IOException
        {
            JsonParser parser = createParser("{ \"fieldKey\": [\"one\", \"two\"] }");
            ColumnDescriptor col = JSONDataLoader.parseField(parser);
            assertEquals(col.name, "one/two");

            parser = createParser("{ \"fieldKey\": [\"one\", \"two\"], \"foo\": 3 }");
            col = JSONDataLoader.parseField(parser);
            assertEquals(col.name, "one/two");
        }

        @Test
        public void fieldKeyArray() throws IOException
        {
            JsonParser parser = createParser("{ \"fieldKeyArray\": [\"one\", \"two\"] }");
            ColumnDescriptor col = JSONDataLoader.parseField(parser);
            assertEquals(col.name, "one/two");
        }

        @Test
        public void bothFieldKeys() throws IOException
        {
            JsonParser parser = createParser("{ \"fieldKey\": [\"one\", \"two\"], \"fieldKeyArray\": [\"three\", \"four\"] }");
            ColumnDescriptor col = JSONDataLoader.parseField(parser);
            assertEquals(col.name, "one/two");
        }

        @Test
        public void firstFieldKey() throws IOException
        {
            JsonParser parser = createParser("{ \"fieldKey\": [\"one\", \"two\"], \"fieldKey\": [\"three\", \"four\"] }");
            ColumnDescriptor col = JSONDataLoader.parseField(parser);
            assertEquals(col.name, "one/two");
        }

        @Test
        public void fieldKeyAndType() throws IOException
        {
            JsonParser parser = createParser("{ \"fieldKey\": [\"A\"], \"type\": \"int\" }");
            ColumnDescriptor col = JSONDataLoader.parseField(parser);
            assertEquals(col.name, "A");
            assertEquals(col.clazz, Integer.class);
        }

        @Test
        public void emptyFields() throws IOException
        {
            JsonParser parser = createParser("[ ]");
            try
            {
                JSONDataLoader.parseFields(parser, null);
                fail("Expected JsonParseException");
            }
            catch (JsonParseException e)
            {
                assertTrue(e.getMessage().startsWith("Expected array of fields, got empty array"));
            }
        }

        @Test
        public void fields() throws IOException
        {
            JsonParser parser = createParser(
                "[" +
                    "{ \"fieldKey\": [\"A\"], \"type\": \"int\" }, " +
                    "{ \"fieldKey\": [\"B\"], \"type\": \"unknown\" }, " +
                    "{ \"fieldKey\": [\"C\"] } " +
                "]"
            );
            ColumnDescriptor[] cols = JSONDataLoader.parseFields(parser, null);
            assertEquals(3, cols.length);

            assertEquals(cols[0].name, "A");
            assertEquals(cols[0].clazz, Integer.class);

            assertEquals(cols[1].name, "B");
            assertEquals(cols[1].clazz, String.class);

            assertEquals(cols[2].name, "C");
            assertEquals(cols[2].clazz, String.class);
        }

        @Test
        public void metadataContents() throws IOException
        {
            JsonParser parser = createParser(
                    "{\n" +
                    "  \"id\": \"test\",\n" +
                    "  \"fields\": [\n" +
                    "    { \"fieldKey\": [\"A\"], \"type\": \"int\" },\n" +
                    "    { \"fieldKey\": [\"B\"], \"type\": \"unknown\" },\n" +
                    "    { \"fieldKey\": [\"C\"] }\n" +
                    "  ],\n" +
                    "  \"other\": \"test\"\n" +
                    "}"
            );

            JSONDataLoader loader = new JSONDataLoader(parser);
            loader.parseMetadataContents();

            ColumnDescriptor[] cols = loader._columns;
            assertEquals(3, cols.length);

            assertEquals(cols[0].name, "A");
            assertEquals(cols[0].clazz, Integer.class);

            assertEquals(cols[1].name, "B");
            assertEquals(cols[1].clazz, String.class);

            assertEquals(cols[2].name, "C");
            assertEquals(cols[2].clazz, String.class);
        }

        @Test
        public void metadata() throws IOException
        {
            JsonParser parser = createParser(
                    "{\n" +
                    "  \"metaData\": {\n" +
                    "    \"fields\": [\n" +
                    "      { \"fieldKey\": [\"A\"], \"type\": \"int\" }\n" +
                    "    ]\n" +
                    "  },\n" +
                    "  \"rows\": [ ]\n" +
                    "}"
            );

            JSONDataLoader loader = new JSONDataLoader(parser);
            loader.parseMetadata();

            ColumnDescriptor[] cols = loader._columns;
            assertEquals(1, cols.length);

            assertEquals(cols[0].name, "A");
            assertEquals(cols[0].clazz, Integer.class);

            assertEquals("rows", parser.getCurrentName());
            assertEquals(JsonToken.START_ARRAY, parser.getCurrentToken());
        }

        @Test
        public void noMetadata() throws IOException
        {
            JsonParser parser = createParser(
                    "{\n" +
                    "  \"foo\": { },\n" +
                    "  \"rows\": [ ],\n" +
                    "  \"metaData\": {\n" +
                    "    \"fields\": [\n" +
                    "      { \"fieldKey\": [\"A\"], \"type\": \"int\" }\n" +
                    "    ]\n" +
                    "  },\n" +
                    "}"
            );

            JSONDataLoader loader = new JSONDataLoader(parser);
            loader.parseMetadata();

            assertNull(loader._columns);

            assertEquals("rows", parser.getCurrentName());
            assertEquals(JsonToken.START_ARRAY, parser.getCurrentToken());
        }

        @Test
        public void noRows() throws IOException
        {
            JsonParser parser = createParser(
                    "{\n" +
                    "  \"foo\": { },\n" +
                    "  \"metaData\": {\n" +
                    "    \"fields\": [\n" +
                    "      { \"fieldKey\": [\"A\"], \"type\": \"int\" }\n" +
                    "    ]\n" +
                    "  }\n" +
                    "}"
            );

            JSONDataLoader loader = new JSONDataLoader(parser);
            try
            {
                loader.parseMetadata();
                fail("Expected JsonParseException");
            }
            catch (JsonParseException e)
            {
                assertTrue(e.getMessage().startsWith("Expected 'rows' field"));
            }
        }

    }

    public static class RowTest extends JsonBaseTest
    {
        @Test
        public void emptyRows() throws IOException
        {
            JsonParser parser = createParser("{ \"rows\": [ ] }");

            JSONDataLoader loader = new JSONDataLoader(parser);
            List<Map<String, Object>> rows = loader.load();
            assertEquals(0, rows.size());
        }

        @Test
        public void row83() throws IOException
        {
            JsonParser parser = createParser(
                    "{\n" +
                    "  \"one\": true\n," +
                    "  \"_labkeyurl_one\": \"http://...\",\n" +
                    "  \"two\": \"TWO\"\n," +
                    "  \"three\": 3\n" +
                    "}"
            );

            Map<String, Map<ColMapEntry, Object>> row = JSONDataLoader.parseRow(parser);
            assertEquals(3, row.size());

            Map<ColMapEntry, Object> col = row.get("one");
            assertEquals(true, col.get(ColMapEntry.value));

            col = row.get("two");
            assertEquals("TWO", col.get(ColMapEntry.value));

            col = row.get("three");
            assertEquals(3, col.get(ColMapEntry.value));
        }

        @Test
        public void row91() throws IOException
        {
            JsonParser parser = createParser(
                    "{\n" +
                    "  \"one\": {\n" +
                    "    \"value\": true,\n" +
                    "    \"displayValue\": \"yes\",\n" +
                    "    \"url\": \"http://...\"\n" +
                    "  },\n" +
                    "  \"two\": {\n" +
                    "    \"value\": \"TWO\"\n" +
                    "  },\n" +
                    "  \"three\": {\n" +
                    "    \"value\": 3\n" +
                    "  }\n" +
                    "}"
            );

            Map<String, Map<ColMapEntry, Object>> row = JSONDataLoader.parseRow(parser);
            assertEquals(3, row.size());

            Map<ColMapEntry, Object> col = row.get("one");
            assertEquals(true, col.get(ColMapEntry.value));
            assertEquals("yes", col.get(ColMapEntry.displayValue));
            assertEquals("http://...", col.get(ColMapEntry.url));

            col = row.get("two");
            assertEquals("TWO", col.get(ColMapEntry.value));

            col = row.get("three");
            assertEquals(3, col.get(ColMapEntry.value));
        }

        @Test
        public void row91_dataColumn() throws IOException
        {
            // Try to be sneaky by handling a 9.1 format row with a 'data' and 'links' column
            JsonParser parser = createParser(
                    "{\n" +
                    "  \"links\": {\n" +
                    "    \"value\": null\n" +
                    "  },\n" +
                    "  \"data\": {\n" +
                    "    \"value\": \"sneaky\"\n" +
                    "  }\n" +
                    "}"
            );

            Map<String, Map<ColMapEntry, Object>> row = JSONDataLoader.parseRow(parser);
            assertEquals(2, row.size());

            Map<ColMapEntry, Object> col = row.get("links");
            assertNull(col.get(ColMapEntry.value));

            col = row.get("data");
            assertEquals("sneaky", col.get(ColMapEntry.value));
        }

        @Test
        public void row13_2() throws IOException
        {
            JsonParser parser = createParser(
                    "{\n" +
                    "  \"links\": {\n" +
                    "    \"details\": {\n" +
                    "      \"title\": \"details\",\n" +
                    "      \"href\": \"http://...\"\n" +
                    "    }\n" +
                    "  },\n" +
                    "  \"data\": {\n" +
                    "    \"one\": {\n" +
                    "      \"value\": true,\n" +
                    "      \"displayValue\": \"yes\",\n" +
                    "      \"url\": \"http://...\"\n" +
                    "    },\n" +
                    "    \"two\": {\n" +
                    "      \"value\": \"TWO\"\n" +
                    "    },\n" +
                    "    \"three\": {\n" +
                    "      \"value\": 3\n" +
                    "    }\n" +
                    "  }\n" +
                    "}"
            );

            Map<String, Map<ColMapEntry, Object>> row = JSONDataLoader.parseRow(parser);
            assertEquals(3, row.size());

            Map<ColMapEntry, Object> col = row.get("one");
            assertEquals(true, col.get(ColMapEntry.value));
            assertEquals("yes", col.get(ColMapEntry.displayValue));
            assertEquals("http://...", col.get(ColMapEntry.url));

            col = row.get("two");
            assertEquals("TWO", col.get(ColMapEntry.value));

            col = row.get("three");
            assertEquals(3, col.get(ColMapEntry.value));
        }

        @Test
        public void row91_threeRows() throws IOException
        {
            JsonParser parser = createParser(
                    "{\n" +
                    "  \"metaData\": {\n" +
                    "    \"fields\": [ {\n" +
                    "      \"fieldKey\": [\"A\"],\n" +
                    "      \"type\": \"int\"\n" +
                    "    } ]\n" +
                    "  },\n" +
                    "  \"rows\": [ {\n" +
                    "    \"A\": {\n" +
                    "      \"value\": 1\n" +
                    "    }\n" +
                    "  }, {\n" +
                    "    \"A\": {\n" +
                    "      \"value\": 2\n" +
                    "    }\n" +
                    "  }, {\n" +
                    "    \"A\": {\n" +
                    "      \"value\": 3\n" +
                    "    },\n" +
                    "    \"B\": {\n" +
                    "      \"value\": \"ignored\"\n" +
                    "    }\n" +
                    "  } ]\n" +
                    "}"
            );

            JSONDataLoader loader = new JSONDataLoader(parser);
            List<Map<String, Object>> rows = loader.load();
            assertEquals(3, rows.size());

            Map<String, Object> row = rows.get(0);
            assertEquals(1, row.get("A"));

            row = rows.get(1);
            assertEquals(2, row.get("A"));

            row = rows.get(2);
            assertEquals(3, row.get("A"));
            assertFalse(row.containsKey("B"));
        }

        @Test
        public void row91_inferColumns() throws IOException
        {
            JsonParser parser = createParser(
                    "{\n" +
                    "  \"rows\": [ {\n" +
                    "    \"A\": {\n" +
                    "      \"value\": 1\n" +
                    "    }\n" +
                    "  }, {\n" +
                    "    \"A\": {\n" +
                    "      \"value\": 2\n" +
                    "    }\n" +
                    "  }, {\n" +
                    "    \"A\": {\n" +
                    "      \"value\": 3\n" +
                    "    }\n" +
                    "  } ]\n" +
                    "}"
            );

            JSONDataLoader loader = new JSONDataLoader(parser);
            List<Map<String, Object>> rows = loader.load();
            assertEquals(3, rows.size());

            Map<String, Object> row = rows.get(0);
            // NOTE: the values are Strings instead of Integers
            assertEquals("1", row.get("A"));

            row = rows.get(1);
            assertEquals("2", row.get("A"));

            row = rows.get(2);
            assertEquals("3", row.get("A"));
        }
    }

}

