/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

package org.labkey.api.reports.report.r;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.reader.Readers;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.ScriptOutput;
import org.labkey.api.reports.report.r.view.HrefOutput;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.writer.PrintWriters;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.startsWith;
import static org.labkey.api.reports.report.ScriptEngineReport.INPUT_FILE_TSV;

/**
 * User: Karl Lum
 * Date: May 5, 2008
 */
public class ParamReplacementSvc
{
    private static final Logger _log = Logger.getLogger(ParamReplacementSvc.class);
    private static final ParamReplacementSvc _instance = new ParamReplacementSvc();
    private static final Map<String, String> _outputSubstitutions = new HashMap<>();

    /* Inline substitution parameters "${}" are deprecated, use comment substitution "#${}" instead. */
    // the default inline param replacement pattern : ${}
    private static final String REPLACEMENT_PARAM = "\\$\\{(.*?)\\}"; /* Deprecated */
    private static final Pattern DEFAULT_INLINE_SCRIPT_PATTERN = Pattern.compile(REPLACEMENT_PARAM); /* Deprecated */
    // Enable ${} or equivalent escape sequences for {}.  Note that this
    // makes the match group 2 for the token name instead of 1
    private static final String REPLACEMENT_PARAM_ESC = "\\$(\\{|%7[bB])(.*?)(\\}|%7[dD])"; /* Deprecated */
    private static final Pattern ESC_INLINE_SCRIPT_PATTERN = Pattern.compile(REPLACEMENT_PARAM_ESC); /* Deprecated */

    private static final String REGEX_PARAM_REGEX = "(regex\\()(.*)(\\))"; /* Deprecated */
    private static final Pattern REGEX_PARAM_PATTERN = Pattern.compile(REGEX_PARAM_REGEX); /* Deprecated */

    // the default comment param replacement pattern : #${:} \n
    public static final String COMMENT_LINE_REGEX = "#\\p{Space}*" + REPLACEMENT_PARAM + "\\p{Space}+";
    public static final Pattern COMMENT_LINE_PATTERN = Pattern.compile(COMMENT_LINE_REGEX);
    public static final String COMMENT_BLOCK_PARAM = COMMENT_LINE_REGEX + "((\\p{Graph}+)|(\"\\p{Print}\"))\\p{Space}*";
    public static final Pattern DEFAULT_COMMENT_PATTERN = Pattern.compile(COMMENT_BLOCK_PARAM);
    // Enable #${} or equivalent escape sequences for {}.  Note that this
    // makes the match group 2 for the token name instead of 1
    public static final String ESC_COMMENT_LINE_REGEX = "#\\p{Space}*" + REPLACEMENT_PARAM_ESC + "\\p{Space}+";
    public static final Pattern ESC_COMMENT_LINE_PATTERN = Pattern.compile(ESC_COMMENT_LINE_REGEX);
    public static final String ESC_COMMENT_BLOCK_PARAM = ESC_COMMENT_LINE_REGEX + "((\\p{Graph}+)|(\"\\p{Print}\"))\\p{Space}*";
    public static final Pattern ESC_COMMENT_PATTERN = Pattern.compile(ESC_COMMENT_BLOCK_PARAM);

    private ParamReplacementSvc(){}

    public enum SubstitutionPattern
    {
        COMMENT(DEFAULT_COMMENT_PATTERN, ESC_COMMENT_PATTERN)
                {
                    public String getReplacementStr(String replacementStr, String fullMatchedString, String paramName, boolean useEscaped)
                    {
                        if (fullMatchedString == null)
                            return null;

                        Pattern pattern = useEscaped ? ESC_COMMENT_LINE_PATTERN : COMMENT_LINE_PATTERN;
                        Matcher matcher = pattern.matcher(fullMatchedString);
                        if (matcher.find())
                        {
                            String commentLine = matcher.group(0);
                            if (!StringUtils.isEmpty(commentLine))
                                return fullMatchedString.replace(commentLine, "").replace(paramName, replacementStr);
                        }
                        return null;
                    }

                    @Override
                    public String getReplacementStr(String replacementStr, String fullMatchedString, String paramName)
                    {
                        return getReplacementStr(replacementStr, fullMatchedString, paramName, false);
                    }

                    @Override
                    public String getEscapedReplacementStr(String replacementStr, String fullMatchedString, String paramName)
                    {
                        return getReplacementStr(replacementStr, fullMatchedString, paramName, true);
                    }
                },
        /**
         * Deprecated
         */
        INLINE(DEFAULT_INLINE_SCRIPT_PATTERN, ESC_INLINE_SCRIPT_PATTERN);

        private final Pattern _matchPattern;
        private final Pattern _escapedMatchPattern;

        SubstitutionPattern(Pattern matchPattern, Pattern escapedMatchPattern)
        {
            this._matchPattern = matchPattern;
            this._escapedMatchPattern = escapedMatchPattern;
        }

        String getReplacementStr(String replacementStr, String fullMatchedString, String paramName)
        {
            return replacementStr;
        }

        String getEscapedReplacementStr(String replacementStr, String fullMatchedString, String paramName)
        {
            return replacementStr;
        }

        public Pattern getMatchPattern()
        {
            return _matchPattern;
        }

        public int getTokenGroupIndex()
        {
            return 1;
        }

        public Pattern getEscapedMatchPattern()
        {
            return _escapedMatchPattern;
        }

        public int getEscapedTokenGroupIndex()
        {
            return 2;
        }
    }

    public static ParamReplacementSvc get()
    {
        return _instance;
    }

    public void registerHandler(ParamReplacement rout)
    {
        if (_outputSubstitutions.containsKey(rout.getId()))
            throw new IllegalStateException("Replacement parameter type: " + rout.getId() + " has previously been registered");

        if (!rout.getId().endsWith(":"))
            throw new IllegalArgumentException("The ID of a replacement parameter must end with a ':'");

        _outputSubstitutions.put(rout.getId(), rout.getClass().getName());
    }

    public boolean isScriptWithValidReplacements(String text, List<String> errors)
    {
        return isScriptWithValidReplacements(text, errors, SubstitutionPattern.COMMENT) && isScriptWithValidReplacements(text, errors, SubstitutionPattern.INLINE);
    }

    public boolean isScriptWithValidReplacements(String text, List<String> errors, SubstitutionPattern pattern)
    {
        Matcher m = pattern.getMatchPattern().matcher(text);

        while (m.find())
        {
            String value = m.group(pattern.getTokenGroupIndex());

            if (!isValidReplacement(value, pattern))
            {
                errors.add("Invalid template, the replacement parameter: " + value + " is unknown.");
                return false;
            }
        }
        return true;
    }

    protected boolean isValidReplacement(String value, SubstitutionPattern pattern)
    {
        if (INPUT_FILE_TSV.equals(value)) return true;

        return fromToken(value) != null;
    }

    /**
     * Returns a ParamReplacement from a replacement parameter id
     */
    public ParamReplacement getHandlerInstance(String id)
    {
        if (_outputSubstitutions.containsKey(id))
        {
            try {
                String className = _outputSubstitutions.get(id);
                return (ParamReplacement)Class.forName(className).newInstance();
            }
            catch (Exception e)
            {
                _log.error("Unable to create report output handler", e);
            }
        }
        return null;
    }

    /**
     * Finds all the replacement parameters for a given script block
     */
    public List<ParamReplacement> getParamReplacements(String script)
    {
        List<ParamReplacement> params = getParamReplacements(script, SubstitutionPattern.COMMENT);
        params.addAll(getParamReplacements(script, SubstitutionPattern.INLINE));
        return params;
    }

    /**
     * Finds all tokens for a given script block
     */
    public Set<String> tokens(String script)
    {
        // Preserving the order the tokens found is important
        // TODO for script with both inline and comment params, ordering of tokens aren't guaranteed..
        Set<String> tokens = getTokens(script, SubstitutionPattern.COMMENT);
        tokens.addAll(getTokens(script, SubstitutionPattern.INLINE));

        return tokens;
    }

    public Set<String> getTokens(String script, SubstitutionPattern pattern)
    {
        // Preserving the order the tokens found is important -- ${input1.txt} must be found before ${input2.txt}
        Set<String> tokens = new LinkedHashSet<>();
        Matcher m = pattern.getMatchPattern().matcher(script);

        while (m.find())
        {
            String token = m.group(pattern.getTokenGroupIndex());
            if (token != null && token.length() > 0)
                tokens.add(token);
        }

        return tokens;
    }

    /**
     * Finds all the replacement parameters for a given script block
     *
     * @param pattern - the regular expression pattern for the replacements
     */
    public List<ParamReplacement> getParamReplacements(String script, SubstitutionPattern pattern)
    {
        List<ParamReplacement> params = new ArrayList<>();
        if (script != null)
        {
            Matcher m = pattern.getMatchPattern().matcher(script);

            while (m.find())
            {
                ParamReplacement param = fromToken(m.group(pattern.getTokenGroupIndex()));
                if (param != null)
                    params.add(param);
            }
        }
        return params;
    }

    private ParamReplacement fromToken(String value)
    {
        int idx = value.indexOf(':');
        if (idx != -1)
        {
            String id = value.substring(0, idx+1);
            String name = value.substring(idx+1);

            ParamReplacement param = getHandlerInstance(id);
            Matcher m = REGEX_PARAM_PATTERN.matcher(name);
            if (param != null)
            {
                if (m.find())
                {
                    String regex = m.group(2);
                    if (regex != null)
                    {
                        param.setRegex(regex);
                    }
                }
                else if (name.indexOf('?') != -1)
                {
                    String[] parts = name.split("\\?");
                    if (parts.length == 2)
                    {
                        param.setName(parts[0]);
                        param.setProperties(PageFlowUtil.mapFromQueryString(parts[1]));
                    }
                }
                else
                    param.setName(name);
                return param;
            }
        }
        return null;
    }

    /**
     * Replaces an input replacement symbol with the full path name of the specified input file.
     */
    public String processInputReplacement(String script, String replacementParam, String value) throws Exception
    {
        String commentProcessedScript = processInputReplacement(script, replacementParam, value, SubstitutionPattern.COMMENT);
        return processInputReplacement(commentProcessedScript, replacementParam, value, SubstitutionPattern.INLINE);
    }

    /**
     * Replaces an input replacement symbol with the full path name of the specified input file.
     *
     * @param pattern - the regular expression pattern for the replacements
     */
    public String processInputReplacement(String script, String replacementParam, String replacementValue, SubstitutionPattern pattern) throws Exception
    {
        Matcher m = pattern.getMatchPattern().matcher(script);
        StringBuffer sb = new StringBuffer();

        while (m.find())
        {
            String value = m.group(pattern.getTokenGroupIndex());
            if (replacementParam.equals(value))
            {
                m.appendReplacement(sb, replacementValue);
            }
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /**
     * Removes any replacement param sequences '${}' from the specified String, this is useful when a replacement
     * parameter is conditional and you need to remove any unprocessed sequences before the command is executed
     */
    public String clearUnusedReplacements(String script) throws Exception
    {
        return script.replaceAll(SubstitutionPattern.INLINE.getMatchPattern().pattern(), "");
    }

    /**
     * Finds and processes all replacement parameters for a given script block. The
     * returned string will have all valid replacement references converted.
     *
     * @param parentDirectory - the parent directory to create the output files for each param replacement.
     * @param outputReplacements - the list of processed replacements found in the source script.
     */
    public String processParamReplacement(String script, File parentDirectory, String remoteParentDirectoryPath, List<ParamReplacement> outputReplacements, boolean isRStudio) throws Exception
    {
        if (isRStudio)
        {
            return script; //TODO transform inline to comment
        }
        String commentProcessedScript = processParamReplacement(script, parentDirectory, remoteParentDirectoryPath, outputReplacements, SubstitutionPattern.COMMENT);
        return processParamReplacement(commentProcessedScript, parentDirectory, remoteParentDirectoryPath, outputReplacements, SubstitutionPattern.INLINE);
    }

    /**
     * Finds and processes all replacement parameters for a given script block. The
     * returned string will have all valid replacement references converted.
     *
     * @param parentDirectory - the parent directory to create the output files for each param replacement.
     * @param remoteParentDirectoryPath - the remote reference to this path if specified; may be null
     * @param outputReplacements - the list of processed replacements found in the source script.
     */
    public String processParamReplacement(String script, File parentDirectory, String remoteParentDirectoryPath, List<ParamReplacement> outputReplacements, SubstitutionPattern pattern) throws Exception
    {
        Matcher m = pattern.getMatchPattern().matcher(script);
        StringBuffer sb = new StringBuffer();

        while (m.find())
        {
            ParamReplacement param = fromToken(m.group(pattern.getTokenGroupIndex()));
            if (param != null)
            {
                File resultFile = param.convertSubstitution(parentDirectory);
                String resultFileName = "";

                if (resultFile != null)
                {
                    if (!StringUtils.isEmpty(remoteParentDirectoryPath))
                    {
                        //
                        // now that we've created the resultFile locally, replace the parameter with the remote
                        // machines view of it
                        //
                        String slash = remoteParentDirectoryPath.endsWith("/") ? "" : "/";
                        resultFileName = remoteParentDirectoryPath + slash + resultFile.getName();
                        param.setRemote(true);
                    }
                    else
                    {
                        resultFileName = resultFile.getAbsolutePath().replaceAll("\\\\", "/");
                    }
                    _log.debug("Found output parameter '" + param.getName() + "'.  Mapping local file '" + resultFile.getAbsolutePath() + "' to '" + resultFileName + "'");
                }
                String replacementStr = pattern.getReplacementStr(resultFileName, m.group(0), param.getName());
                outputReplacements.add(param);
                m.appendReplacement(sb, replacementStr);
            }
        }
        m.appendTail(sb);

        return sb.toString();
    }

    public String processHrefParamReplacement(Report report, String script, File parentDirectory) throws Exception
    {
        String commentProcessedScript = processHrefParamReplacement(report, script, parentDirectory, SubstitutionPattern.COMMENT);
        return processHrefParamReplacement(report, commentProcessedScript, parentDirectory, SubstitutionPattern.INLINE);

    }

    /**
     * Finds and processes all replacement parameters for a given script block. The
     * returned string will have all valid replacement references converted.  Note that for this overload
     * the files have already been created and we are replacing with a valid URL inline here.
     *
     * @param script - the script upon which to replace the Href parameters
     * @param parentDirectory - the parent directory to create the output files for each param replacement.
     * @param pattern - the remote reference to this path if specified; may be null
     */
    public String processHrefParamReplacement(Report report, String script, File parentDirectory, SubstitutionPattern pattern) throws Exception
    {
        Matcher m = pattern.getEscapedMatchPattern().matcher(script);
        StringBuffer sb = new StringBuffer();

        while (m.find())
        {
            ParamReplacement param = fromToken(m.group(pattern.getEscapedTokenGroupIndex()));
            if (param != null && HrefOutput.class.isInstance(param))
            {
                HrefOutput href = (HrefOutput) param;
                href.setReport(report);
                File file = new File(parentDirectory, href.getName());
                if (file.exists())
                {
                    href.addFile(file);
                    ScriptOutput o = href.renderAsScriptOutput(file);
                    if (null != o)
                    {
                        String outputValue = o.getValue();
                        String replacementStr = pattern.getEscapedReplacementStr(outputValue, m.group(0), param.getName());
                        m.appendReplacement(sb, replacementStr);
                    }

                }
            }
        }
        m.appendTail(sb);

        return sb.toString();
    }

    private boolean isRelativeHref(String href)
    {
        return !(startsWith(href,"$") || startsWith(href,"http:") || startsWith(href,"https:") || startsWith(href,"/"));
    }

    /**
     * Finds and processes all replacement parameters for a given script block. The
     * returned string will have all valid replacement references converted.  Note that for this overload
     * the files have already been created and we are replacing with a valid URL inline here.
     *
     * @param script - the script upon which to replace the Href parameters
     * @param parentDirectory - the parent directory to create the output files for each param replacement.
     */
    public String processRelativeHrefReplacement(Report report, String script, File parentDirectory) throws Exception
    {
        Matcher m = Pattern.compile("(href|src)=\"([^\"]*)\"").matcher(script);
        StringBuffer sb = new StringBuffer();

        while (m.find())
        {
            String path = m.group(2);

            if (!isRelativeHref(path))
            {
                m.appendReplacement(sb, "");
                sb.append(m.group(0));
                continue;
            }

            HrefOutput href = new HrefOutput();
            href.setName(path);
            href.setReport(report);
            File file = new File(parentDirectory, href.getName());
            ScriptOutput so = null;
            if (file.exists())
            {
                href.addFile(file);
                so = href.renderAsScriptOutput(file);
            }
            m.appendReplacement(sb,"");
            if (null != so)
                sb.append(m.group(1)).append("=\"").append(so.getValue()).append("\"");
            else
                sb.append(m.group(0));
        }
        m.appendTail(sb);

        return sb.toString();
    }

    public void toFile(List<ParamReplacement> outputSubst, File file) throws Exception
     {
         try (PrintWriter bw = PrintWriters.getPrintWriter(file))
         {
             outputSubst.stream().
                     filter(output -> output.getName() != null).
                     forEach(output -> output.getFiles().stream().filter(outputFile -> outputFile != null).
                     forEach(outputFile -> bw.write(output.getId() + '\t' + output.getName() + '\t' + outputFile.getAbsolutePath() + '\t' + PageFlowUtil.toQueryString(output.getProperties().entrySet()) + '\n')));
         }
     }

     public Collection<ParamReplacement> fromFile(File file) throws Exception
     {
         Map<String, ParamReplacement> outputSubstMap = new HashMap<>();
         if (file.exists())
         {
             try (BufferedReader br = Readers.getReader(file))
             {
                 String l;
                 while ((l = br.readLine()) != null)
                 {
                     String[] parts = l.split("\\t");
                     if (parts.length == 4)
                     {
                         String id = parts[0];
                         if (!outputSubstMap.containsKey(id))
                             outputSubstMap.put(id, getHandlerInstance(id));

                         ParamReplacement handler = outputSubstMap.get(id);
                         if (handler != null)
                         {
                             handler.setName(parts[1]);
                             handler.addFile(new File(parts[2]));
                             handler.setProperties(PageFlowUtil.mapFromQueryString(parts[3]));
                         }
                     }
                 }
             }
         }
         return outputSubstMap.values();
     }
}
