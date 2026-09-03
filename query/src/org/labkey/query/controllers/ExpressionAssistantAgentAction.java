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
package org.labkey.query.controllers;

import com.google.genai.errors.ClientException;
import com.google.genai.errors.ServerException;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.markdown.MarkdownService;
import org.labkey.api.mcp.AbstractAgentAction;
import org.labkey.api.mcp.McpContext;
import org.labkey.api.mcp.McpService;
import org.labkey.api.query.RuntimeValidationException;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.query.controllers.QueryController.ParseForm;
import org.labkey.query.controllers.QueryController.PromptResource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.validation.BindException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@RequiresPermission(ReadPermission.class)
@RequiresLogin
public class ExpressionAssistantAgentAction extends AbstractAgentAction<ParseForm>
{
    private static final Logger LOG = LogManager.getLogger(ExpressionAssistantAgentAction.class);

    @Override
    protected String getAgentName()
    {
        return ExpressionAssistantAgentAction.class.getName();
    }

    @Override
    protected String getServicePrompt()
    {
        return PromptResource.ExpressionAssistant.resource() +
            "\n\nBefore starting, load the LabKey SQL documentation using the \"readResource\" tool with the URI \"" + PromptResource.LabKeySql.uri() + "\"\n\n";
    }

    @Override
    public Object execute(ParseForm form, BindException errors) throws Exception
    {
        try (var _ = McpContext.withContext(getViewContext()))
        {
            boolean firstTurn = isBlank(form.getConversationId());
            String prompt = form.getPrompt();
            String composedPrompt = composePrompt(firstTurn, prompt, form.getField(), form.getDomainFields(), form.getFieldExpression(), form.getFieldError());

            if (isBlank(composedPrompt))
            {
                return new JSONObject(Map.of(
                        "contentType", "text/plain",
                        "text", "🤷",
                        "success", Boolean.TRUE));
            }

            ChatClient chatSession = getChat(true);
            List<McpService.MessageResponse> responses;

            try
            {
                // Context for the QueryMcp.validateCalculatedColumnExpression() tool
                McpContext.get()
                        .put("columnMap", form.getColumnMap())
                        .put("phiColumns", form.getPhiColumns());

                LOG.info("Expression assistant prompt: {}", prompt);
                responses = McpService.get().sendMessageEx(chatSession, composedPrompt);
            }
            catch (ServerException x)
            {
                return new JSONObject(Map.of(
                        "error", x.getMessage(),
                        "text", "ERROR: " + x.getMessage(),
                        "success", Boolean.FALSE));
            }

            JSONArray segments = buildSegments(responses);
            return new JSONObject(Map.of(
                    "success", Boolean.TRUE,
                    "conversationId", getConversationId(),
                    "segments", segments));
        }
        catch (ClientException x)
        {
            return errorResponse(x);
        }
    }

    /**
     * Combines the user's prompt with any first-turn context supplied by the client (the catalog
     * of available fields, the current expression, and an error message when auto-evaluating an
     * invalid expression). When {@code firstTurn} is false the context fields are ignored, and the
     * user's prompt is returned verbatim.
     */
    static String composePrompt(boolean firstTurn, String userPrompt, JSONObject field, JSONArray domainFields, String fieldExpression, String fieldError)
    {
        if (!firstTurn)
            return StringUtils.defaultString(userPrompt);

        boolean autoEvaluate = isBlank(userPrompt) && isNotBlank(fieldError);
        if (isBlank(userPrompt) && !autoEvaluate)
            return "";

        StringBuilder sb = new StringBuilder();
        if (domainFields != null && !domainFields.isEmpty())
        {
            sb.append("The following enumerates the available columns and their types:\n");
            sb.append(fence(domainFields.toString(), "json"));
        }

        if (field != null)
        {
            sb.append("The current column that is having its expression evaluated and the one you are assisting with is:\n");
            sb.append(fence(field.toString(), "json"));
        }

        if (autoEvaluate)
        {
            if (isNotBlank(fieldExpression))
            {
                sb.append("The user already has the following calculated column expression:\n");
                sb.append(fence(fieldExpression));
            }
            sb.append("This expression contains an error:\n");
            sb.append(fence(fieldError));
            sb.append("Evaluate this expression and see if you can determine how to fix this error. If you can, point them out and propose corrections.");
        }
        else if (sb.isEmpty())
        {
            sb.append(userPrompt);
        }
        else
        {
            sb.append("Generate a calculated column expression that matches the following description:\n");
            sb.append(userPrompt);
        }

        return sb.toString();
    }

    static String fence(String body)
    {
        return fence(body, "");
    }

    /** Wraps {@code body} in a markdown fenced code block, optionally tagged with a language. */
    static String fence(String body, String tag)
    {
        return "```" + tag + "\n" + body + "\n```\n";
    }

    /**
     * Walks the markdown of each MessageResponse and produces an ordered list of segments.
     * Each segment is either a rendered-HTML span or a fenced SQL block. SQL blocks fenced as
     * `expression` are tagged "expression" (the model's assertion that this SQL has been validated
     * and is safe to apply); blocks fenced as `sql` are tagged "sql" (illustrative / unvalidated).
     * For `expression` blocks the body is expected to be the JSON returned by
     * validateCalculatedColumnExpression — at minimum {@code {"expression": "..."}}, optionally
     * with {@code "jdbcType"}.
     */
    private static JSONArray buildSegments(List<McpService.MessageResponse> responses)
    {
        JSONArray segments = new JSONArray();
        MarkdownService md = MarkdownService.get();
        StringBuilder htmlBuf = new StringBuilder();

        // Scan the turns as one document: tool calling can split a single assistant turn so that a
        // fence opens in one MessageResponse and closes in the next.
        String text = responses.stream()
                .map(McpService.MessageResponse::text)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining("\n"));

        LOG.debug("Expression assistant raw response:\n{}", text);

        String[] lines = text.split("\n", -1);
        int i = 0;
        while (i < lines.length)
        {
            Fence f = readFence(lines, i);
            if (f != null && f.terminated && ("sql".equals(f.tag) || "expression".equals(f.tag)))
            {
                flushHtmlSegment(segments, htmlBuf, md);
                segments.put(buildSqlSegment(f.tag, f.body));
                i = f.nextIndex;
            }
            else if (f != null && !f.terminated)
            {
                // Unterminated fence — skip only the opening line, so the user doesn't see a stray
                // "```expression" rendered as a code marker, and keep scanning. The body lands in prose
                // line by line via the branch below, and a well-formed fence later in the turn still parses.
                i++;
            }
            else
            {
                // Either not a fence opener or an unknown tag (e.g., python). In the unknown-tag
                // case we leave the fence intact in prose so the Markdown renderer turns it into
                // a code block.
                if (!htmlBuf.isEmpty()) htmlBuf.append("\n");
                htmlBuf.append(lines[i]);
                i++;
            }
        }

        flushHtmlSegment(segments, htmlBuf, md);

        // A payload the user can read means the model broke the fence protocol, so it renders as raw JSON
        // instead of a working Apply Expression action. Log the response that caused it.
        if (hasLeakedPayload(segments))
            LOG.warn("Expression assistant leaked a validator payload into its reply:\n{}", text);

        return segments;
    }

    /**
     * True when a validateCalculatedColumnExpression payload reached the user as text rather than being unpacked
     * into an `expression` segment. Prose is one surface; the others are an illustrative `sql` block and an
     * `expression` block whose body didn't yield an expression, which shows the raw JSON behind an Apply button.
     * A well-formed `expression` segment carries jdbcType as its own key, so only the displayed text is checked.
     */
    private static boolean hasLeakedPayload(JSONArray segments)
    {
        for (int i = 0; i < segments.length(); i++)
        {
            JSONObject segment = segments.getJSONObject(i);
            String displayed = "html".equals(segment.optString("type"))
                    ? segment.optString("html", "")
                    : segment.optString("sql", "");
            if (displayed.contains("jdbcType"))
                return true;
        }
        return false;
    }

    /**
     * Build a segment JSON object for an `sql` or `expression` fenced block. For `expression`
     * blocks the body is expected to be the JSON returned by validateCalculatedColumnExpression;
     * we pull "expression" into "sql" and pass through "jdbcType".
     */
    private static JSONObject buildSqlSegment(String tag, String body)
    {
        Map<String, Object> segData = new LinkedHashMap<>();
        segData.put("type", tag);

        if ("expression".equals(tag))
        {
            String sql = null;
            String jdbcType = null;
            try
            {
                JSONObject payload = new JSONObject(body);
                if (payload.has("expression"))
                {
                    sql = payload.optString("expression", null);
                    if (payload.has("jdbcType"))
                        jdbcType = payload.optString("jdbcType", null);
                }
            }
            catch (org.json.JSONException x)
            {
                LOG.debug("Unable to parse JSON expression: " + body, x);
            }

            segData.put("sql", sql != null ? sql : body);
            if (sql != null && jdbcType != null)
                segData.put("jdbcType", jdbcType);
        }
        else
            segData.put("sql", body);

        return new JSONObject(segData);
    }

    private record Fence(String tag, String body, int nextIndex, boolean terminated) {}

    /** Length of the leading backtick run if {@code trimmed} is long enough to delimit a fence, else 0. */
    private static int backtickRun(String trimmed)
    {
        int n = 0;
        while (n < trimmed.length() && trimmed.charAt(n) == '`')
            n++;
        return n >= 3 ? n : 0;
    }

    /** Per CommonMark a closing fence is backticks only, and at least as long as the opener. */
    private static boolean isFenceClose(String line, int openLength)
    {
        String trimmed = line.trim();
        int n = backtickRun(trimmed);
        return n == trimmed.length() && n >= openLength;
    }

    private static Fence readFence(String[] lines, int i)
    {
        String trimmed = lines[i].trim();
        int openLength = backtickRun(trimmed);
        if (openLength == 0)
            return null;
        String rest = trimmed.substring(openLength).trim();
        if (rest.isEmpty())
            return null;
        // Only the first word of the info string is the tag; models pad it ("expression json").
        String tag = rest.split("\\s+", 2)[0].toLowerCase();

        int j = i + 1;
        StringBuilder body = new StringBuilder();
        while (j < lines.length && !isFenceClose(lines[j], openLength))
        {
            if (!body.isEmpty()) body.append("\n");
            body.append(lines[j]);
            j++;
        }
        boolean terminated = j < lines.length;
        return new Fence(tag, body.toString(), terminated ? j + 1 : lines.length, terminated);
    }

    private static void flushHtmlSegment(JSONArray segments, StringBuilder buf, MarkdownService md)
    {
        if (buf.isEmpty())
            return;

        String raw = buf.toString().strip();
        buf.setLength(0);
        if (raw.isEmpty())
            return;

        String html;
        try
        {
            html = md != null ? md.toHtml(raw) : raw;
        }
        catch (Exception x)
        {
            html = raw;
        }

        var validateErrors = new ArrayList<String>();
        PageFlowUtil.validateHtml(html, validateErrors, validateErrors);
        if (!validateErrors.isEmpty())
            throw new RuntimeValidationException("Invalid HTML markup. " + String.join("\n", validateErrors));

        segments.put(new JSONObject(Map.of("type", "html", "html", html)));
    }

    public static class TestCase extends Assert
    {
        private static McpService.MessageResponse markdownResponse(String md)
        {
            return new McpService.MessageResponse("text/markdown", md, HtmlString.of(md));
        }

        private static JSONObject segment(JSONArray segments, int i)
        {
            return segments.getJSONObject(i);
        }

        /** The JSON body the model is expected to echo verbatim from validateCalculatedColumnExpression. */
        private static String expressionPayload(String sql, String jdbcType)
        {
            JSONObject json = new JSONObject();
            json.put("jdbcType", jdbcType);
            json.put("expression", sql);
            return json.toString();
        }

        private static void assertExpressionSegment(JSONArray segments, int i, String sql, String jdbcType)
        {
            assertEquals("expression", segment(segments, i).getString("type"));
            assertEquals(sql, segment(segments, i).getString("sql"));
            assertEquals(jdbcType, segment(segments, i).getString("jdbcType"));
        }

        /** The validator payload is transport between the tool and this action; it must never reach the user as text. */
        private static void assertNoPayloadInProse(JSONArray segments)
        {
            for (int i = 0; i < segments.length(); i++)
            {
                JSONObject seg = segments.getJSONObject(i);
                if ("html".equals(seg.optString("type")))
                    assertFalse("validator payload leaked into prose: " + seg, seg.getString("html").contains("jdbcType"));
            }
        }

        @Test
        public void emptyResponseList()
        {
            JSONArray segments = buildSegments(List.of());
            assertEquals(0, segments.length());
        }

        @Test
        public void blankMessageProducesNoSegments()
        {
            JSONArray segments = buildSegments(List.of(markdownResponse(""), markdownResponse("   ")));
            assertEquals(0, segments.length());
        }

        @Test
        public void proseOnlyProducesSingleHtmlSegment()
        {
            JSONArray segments = buildSegments(List.of(markdownResponse("Hello, here is some advice.")));
            assertEquals(1, segments.length());
            assertEquals("html", segment(segments, 0).getString("type"));
            assertTrue(segment(segments, 0).getString("html").contains("Hello, here is some advice."));
        }

        @Test
        public void expressionFenceProducesExpressionSegment()
        {
            String md = "```expression\n" + expressionPayload("SELECT 1", "INTEGER") + "\n```";
            JSONArray segments = buildSegments(List.of(markdownResponse(md)));
            assertEquals(1, segments.length());
            assertEquals("expression", segment(segments, 0).getString("type"));
            assertEquals("SELECT 1", segment(segments, 0).getString("sql"));
            assertEquals("INTEGER", segment(segments, 0).getString("jdbcType"));
        }

        @Test
        public void expressionFenceWithRawSqlBodyFallsBackToSqlOnly()
        {
            // Legacy / model-misbehavior fallback: if the body isn't JSON, treat it as raw SQL and omit jdbcType.
            String md = "```expression\nSELECT 1\n```";
            JSONArray segments = buildSegments(List.of(markdownResponse(md)));
            assertEquals(1, segments.length());
            assertEquals("expression", segment(segments, 0).getString("type"));
            assertEquals("SELECT 1", segment(segments, 0).getString("sql"));
            assertFalse("jdbcType should be absent when body isn't JSON",
                    segment(segments, 0).has("jdbcType"));
        }

        @Test
        public void sqlFenceProducesSqlSegment()
        {
            String md = "```sql\nSELECT 1\n```";
            JSONArray segments = buildSegments(List.of(markdownResponse(md)));
            assertEquals(1, segments.length());
            assertEquals("sql", segment(segments, 0).getString("type"));
            assertEquals("SELECT 1", segment(segments, 0).getString("sql"));
        }

        @Test
        public void interleavedProseAndFencesProduceOrderedSegments()
        {
            String md = String.join("\n",
                "Here are two options.",
                "",
                "Option A (illustrative):",
                "```sql",
                "SELECT a FROM t",
                "```",
                "",
                "Option B (ready to apply):",
                "```expression",
                expressionPayload("SELECT b FROM t", "VARCHAR"),
                "```",
                "Pick whichever fits."
            );
            JSONArray segments = buildSegments(List.of(markdownResponse(md)));
            assertEquals(5, segments.length());
            assertEquals("html", segment(segments, 0).getString("type"));
            assertEquals("sql", segment(segments, 1).getString("type"));
            assertEquals("SELECT a FROM t", segment(segments, 1).getString("sql"));
            assertEquals("html", segment(segments, 2).getString("type"));
            assertEquals("expression", segment(segments, 3).getString("type"));
            assertEquals("SELECT b FROM t", segment(segments, 3).getString("sql"));
            assertEquals("VARCHAR", segment(segments, 3).getString("jdbcType"));
            assertEquals("html", segment(segments, 4).getString("type"));
        }

        @Test
        public void multipleExpressionFencesEachBecomeOwnSegment()
        {
            String md = String.join("\n",
                "```expression",
                expressionPayload("SELECT 1", "INTEGER"),
                "```",
                "```expression",
                expressionPayload("SELECT 2", "BIGINT"),
                "```"
            );
            JSONArray segments = buildSegments(List.of(markdownResponse(md)));
            assertEquals(2, segments.length());
            assertEquals("expression", segment(segments, 0).getString("type"));
            assertEquals("SELECT 1", segment(segments, 0).getString("sql"));
            assertEquals("INTEGER", segment(segments, 0).getString("jdbcType"));
            assertEquals("expression", segment(segments, 1).getString("type"));
            assertEquals("SELECT 2", segment(segments, 1).getString("sql"));
            assertEquals("BIGINT", segment(segments, 1).getString("jdbcType"));
        }

        @Test
        public void unterminatedFenceFallsBackToHtml()
        {
            String payload = expressionPayload("SELECT 1", "INTEGER");
            String md = "Here's an expression:\n```expression\n" + payload + "\n(no closing fence)";
            JSONArray segments = buildSegments(List.of(markdownResponse(md)));
            assertEquals(1, segments.length());
            assertEquals("html", segment(segments, 0).getString("type"));
            String html = segment(segments, 0).getString("html");
            assertTrue("body should survive: " + html, html.contains("SELECT 1"));
            assertTrue("leading prose should survive: " + html, html.contains("Here&#39;s an expression:") || html.contains("Here's an expression:"));
            assertFalse("opening fence must be stripped: " + html, html.contains("```"));
        }

        @Test
        public void unterminatedFenceDoesNotDiscardLaterResponses()
        {
            // An unterminated fence must cost only its own block — the four-backtick opener below is never
            // closed (its closer is shorter), and the well-formed block after it still has to parse.
            var r1 = markdownResponse("````expression\n" + expressionPayload("SELECT 1", "INTEGER") + "\n```");
            var r2 = markdownResponse("Corrected:\n```expression\n" + expressionPayload("SELECT 2", "BIGINT") + "\n```");
            JSONArray segments = buildSegments(List.of(r1, r2));
            assertEquals(2, segments.length());
            assertEquals("html", segment(segments, 0).getString("type"));
            assertExpressionSegment(segments, 1, "SELECT 2", "BIGINT");
        }

        @Test
        public void unknownFenceLanguageIsTreatedAsProse()
        {
            String md = "```python\nprint('hi')\n```";
            JSONArray segments = buildSegments(List.of(markdownResponse(md)));
            // Only sql/expression are split out; other fenced blocks stay in the html segment.
            assertEquals(1, segments.length());
            assertEquals("html", segment(segments, 0).getString("type"));
        }

        @Test
        public void fenceTagIsCaseInsensitive()
        {
            String md = "```EXPRESSION\n" + expressionPayload("SELECT 1", "INTEGER") + "\n```";
            JSONArray segments = buildSegments(List.of(markdownResponse(md)));
            assertEquals(1, segments.length());
            assertEquals("expression", segment(segments, 0).getString("type"));
            assertEquals("SELECT 1", segment(segments, 0).getString("sql"));
            assertEquals("INTEGER", segment(segments, 0).getString("jdbcType"));
        }

        @Test
        public void expressionFenceIsRecognizedAtAnyBacktickLength()
        {
            for (int n = 3; n <= 6; n++)
            {
                String delim = "`".repeat(n);
                String md = delim + "expression\n" + expressionPayload("Int7 + Int6", "INTEGER") + "\n" + delim;
                JSONArray segments = buildSegments(List.of(markdownResponse(md)));
                assertEquals("fence length " + n, 1, segments.length());
                assertExpressionSegment(segments, 0, "Int7 + Int6", "INTEGER");
                assertNoPayloadInProse(segments);
            }
        }

        @Test
        public void longerClosingFenceTerminatesShorterOpener()
        {
            String md = "```expression\n" + expressionPayload("Int7 + Int6", "INTEGER") + "\n````";
            JSONArray segments = buildSegments(List.of(markdownResponse(md)));
            assertEquals(1, segments.length());
            assertExpressionSegment(segments, 0, "Int7 + Int6", "INTEGER");
        }

        @Test
        public void shorterClosingFenceDoesNotTerminateLongerOpener()
        {
            // Per CommonMark the short line is fence content, not a closer, so the block never terminates.
            String md = "````expression\n" + expressionPayload("Int7 + Int6", "INTEGER") + "\n```";
            JSONArray segments = buildSegments(List.of(markdownResponse(md)));
            assertEquals(1, segments.length());
            assertEquals("html", segment(segments, 0).getString("type"));
        }

        @Test
        public void leakedPayloadDetectedWhenFenceIsMissing()
        {
            // No opening fence, so the payload lands in prose and the user gets no Apply affordance.
            String md = "Adding Int7 and Int6.\n\n" + expressionPayload("Int7 + Int6", "INTEGER") + "\n```";
            JSONArray segments = buildSegments(List.of(markdownResponse(md)));
            assertEquals(1, segments.length());
            assertEquals("html", segment(segments, 0).getString("type"));
            assertTrue("prose payload should be reported as a leak", hasLeakedPayload(segments));
        }

        @Test
        public void leakedPayloadDetectedInSqlFence()
        {
            // The model reached for the illustrative `sql` tag, so the payload renders as a read-only code block.
            String md = "```sql\n" + expressionPayload("Int7 + Int6", "INTEGER") + "\n```";
            JSONArray segments = buildSegments(List.of(markdownResponse(md)));
            assertEquals(1, segments.length());
            assertEquals("sql", segment(segments, 0).getString("type"));
            assertTrue("payload in a sql block should be reported as a leak", hasLeakedPayload(segments));
        }

        @Test
        public void leakedPayloadDetectedWhenExpressionBodyIsNotJson()
        {
            // The model narrated inside the fence, so the body doesn't parse and Apply would write JSON to the field.
            String body = "Here is the payload: " + expressionPayload("Int7 + Int6", "INTEGER");
            JSONArray segments = buildSegments(List.of(markdownResponse("```expression\n" + body + "\n```")));
            assertEquals(1, segments.length());
            assertEquals("expression", segment(segments, 0).getString("type"));
            assertEquals(body, segment(segments, 0).getString("sql"));
            assertTrue("unparsable payload should be reported as a leak", hasLeakedPayload(segments));
        }

        @Test
        public void leakedPayloadDetectedWhenExpressionKeyIsMissing()
        {
            // Parses, but nothing to unpack, so buildSqlSegment falls back to the raw body.
            String body = "{\"jdbcType\":\"INTEGER\",\"sql\":\"Int7 + Int6\"}";
            JSONArray segments = buildSegments(List.of(markdownResponse("```expression\n" + body + "\n```")));
            assertEquals(body, segment(segments, 0).getString("sql"));
            assertTrue("payload without an expression key should be reported as a leak", hasLeakedPayload(segments));
        }

        @Test
        public void wellFormedExpressionSegmentIsNotALeak()
        {
            // "jdbcType" is a key on the expression segment itself, not part of the SQL the user sees.
            String md = "```expression\n" + expressionPayload("Int7 + Int6", "INTEGER") + "\n```";
            JSONArray segments = buildSegments(List.of(markdownResponse(md)));
            assertExpressionSegment(segments, 0, "Int7 + Int6", "INTEGER");
            assertFalse("a parsed expression segment is not a leak", hasLeakedPayload(segments));
        }

        @Test
        public void proseWithoutPayloadIsNotALeak()
        {
            JSONArray segments = buildSegments(List.of(markdownResponse("Which date field should be used?")));
            assertEquals("html", segment(segments, 0).getString("type"));
            assertFalse(hasLeakedPayload(segments));
        }

        @Test
        public void expressionFenceWithExtraInfoStringTokenIsRecognized()
        {
            // Only the info string's first word is the tag, so "expression json" still resolves to "expression".
            String md = "```expression json\n" + expressionPayload("Int7 + Int6", "INTEGER") + "\n```";
            JSONArray segments = buildSegments(List.of(markdownResponse(md)));
            assertEquals(1, segments.length());
            assertExpressionSegment(segments, 0, "Int7 + Int6", "INTEGER");
            assertNoPayloadInProse(segments);
        }

        @Test
        public void expressionFenceSplitAcrossResponsesIsRecognized()
        {
            // Tool calling can split one assistant turn mid-fence; the scan joins the turns so the block still closes.
            String payload = expressionPayload("Int7 + Int6", "INTEGER");
            var r1 = markdownResponse("Adding Int7 and Int6.\n\n```expression\n" + payload);
            var r2 = markdownResponse("```");
            JSONArray segments = buildSegments(List.of(r1, r2));
            assertEquals(2, segments.length());
            assertEquals("html", segment(segments, 0).getString("type"));
            assertExpressionSegment(segments, 1, "Int7 + Int6", "INTEGER");
            assertNoPayloadInProse(segments);
        }

        @Test
        public void preservesMultilineSqlBody()
        {
            String multiline = "SELECT a,\n       b\nFROM t";
            String md = "```expression\n" + expressionPayload(multiline, "VARCHAR") + "\n```";
            JSONArray segments = buildSegments(List.of(markdownResponse(md)));
            assertEquals(1, segments.length());
            assertEquals(multiline, segment(segments, 0).getString("sql"));
            assertEquals("VARCHAR", segment(segments, 0).getString("jdbcType"));
        }

        @Test
        public void multipleMessageResponsesAreConcatenatedInOrder()
        {
            McpService.MessageResponse r1 = markdownResponse("First response prose.");
            McpService.MessageResponse r2 = markdownResponse("```expression\n" + expressionPayload("SELECT 1", "INTEGER") + "\n```");
            JSONArray segments = buildSegments(List.of(r1, r2));
            assertEquals(2, segments.length());
            assertEquals("html", segment(segments, 0).getString("type"));
            assertEquals("expression", segment(segments, 1).getString("type"));
            assertEquals("SELECT 1", segment(segments, 1).getString("sql"));
            assertEquals("INTEGER", segment(segments, 1).getString("jdbcType"));
        }

        @Test
        public void flushHtmlSegmentRejectsScriptTag()
        {
            JSONArray segments = new JSONArray();
            StringBuilder buf = new StringBuilder("<div>Here is a greeting:</div><script>alert('xss')</script>");
            RuntimeValidationException ex = assertThrows(RuntimeValidationException.class,
                    () -> flushHtmlSegment(segments, buf, null));
            assertTrue("expected validation error message, got: " + ex.getMessage(),
                    ex.getMessage().contains("Illegal element <script>"));
            assertEquals("no segment should be emitted when validation fails", 0, segments.length());
        }

        @Test
        public void testFence()
        {
            assertEquals("```json\n{\"a\":1}\n```\n", fence("{\"a\":1}", "json"));
            assertEquals("```\nSELECT 1\n```\n", fence("SELECT 1", ""));
            assertEquals("```sql\nline1\nline2\n```\n", fence("line1\nline2", "sql"));
            assertEquals("```json\n\n```\n", fence("", "json"));
        }

        private static JSONArray fields(String json)
        {
            return new JSONArray(json);
        }

        @Test
        public void composePromptFollowUpTurnIgnoresContextAndReturnsUserPromptVerbatim()
        {
            // Once a conversation is underway, the catalog/expression/error are already in chat history.
            assertEquals("more please", composePrompt(false, "more please", null, fields("[{\"name\":\"A\"}]"), "SELECT 1", "boom"));
        }

        @Test
        public void composePromptFirstTurnWrapsWithFieldsCatalogAndInstruction()
        {
            JSONObject field = new JSONObject(Map.of("name", "MyCalc"));
            String composed = composePrompt(true, "sum A and B", field, fields("[{\"name\":\"A\"}]"), null, null);
            assertTrue(composed.contains("available columns"));
            assertTrue(composed.contains("```json\n[{\"name\":\"A\"}]\n```"));
            assertTrue("current-column preamble missing: " + composed,
                    composed.contains("current column that is having its expression evaluated"));
            assertTrue("current-column JSON missing: " + composed,
                    composed.contains("```json\n" + field + "\n```"));
            assertTrue(composed.contains("Generate a calculated column expression"));
            assertTrue(composed.endsWith("sum A and B"));
        }

        @Test
        public void composePromptAutoEvaluateIncludesExpressionAndError()
        {
            String composed = composePrompt(true, "", null, fields("[{\"name\":\"A\"}]"), "SELECT bad", "syntax error");
            assertTrue(composed.contains("available columns"));
            assertTrue(composed.contains("```\nSELECT bad\n```"));
            assertTrue(composed.contains("```\nsyntax error\n```"));
            assertTrue(composed.contains("Evaluate this expression"));
            assertFalse("auto-evaluate must not include the change/new instruction line",
                    composed.contains("Generate a calculated column expression"));
        }

        @Test
        public void composePromptAutoEvaluateWithoutExpressionStillIncludesError()
        {
            String composed = composePrompt(true, "", null, null, null, "boom");
            assertTrue(composed.contains("```\nboom\n```"));
            assertFalse(composed.contains("user already has the following"));
        }

        @Test
        public void composePromptEmptyWhenNothingToSay()
        {
            // First turn with no user prompt and no error context — caller renders the no-op shrug response.
            assertEquals("", composePrompt(true, "", null, null, null, null));
            assertEquals("", composePrompt(true, null, null, fields("[]"), "expr", null));
        }

        @Test
        public void composePromptFirstTurnWithoutDomainFieldsSkipsWrapping()
        {
            // No catalog to inject — don't bother prepending the "Generate a calculated column" preamble.
            assertEquals("just do it", composePrompt(true, "just do it", null, null, null, null));
        }
    }
}
