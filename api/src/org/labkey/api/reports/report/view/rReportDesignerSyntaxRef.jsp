<%
/*
 * Copyright (c) 2007-2012 LabKey Corporation
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
%>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%@ page isELIgnored="true" %>

<table>
    <tr class="labkey-wp-header"><th align="left">Syntax Reference</th></tr>
    <tr><td><i>Your R script uses input substitution parameters to generate the names of input files and to import data
        from your chosen Dataset Grid. It then uses output substitution parameters to either directly place image/data
        files in your View or to include download links to these files in your View. Substitutions take the form
        of: <%="${param}"%> where 'param' is the substitution.</i></td>
    </tr>
    <tr><td>&nbsp;</td></tr>
    <tr><td><i>Valid substitutions:</i></td>
    </tr>
    <tr><td><table id="validSubstitutions">
        <tr><td class="labkey-form-label">input_data</td>
            <td>The input dataset, a tab-delimited table. LabKey Server automatically reads your chosen dataset into
                a data frame called: <code>labkey.data</code>. If you desire tighter control over the method of data upload, you
                can perform the data upload yourself:<br/>
        <pre>
        labkey.data <- read.table("<%="${input_data}"%>", header=TRUE, sep="\t")
        labkey.data
        </pre>
            </td></tr>
        <tr><td class="labkey-form-label">imgout:&lt;name&gt;</td>
            <td>An image output file (such as jpg, png, etc.) that will be displayed as a Section of a View on the
                LabKey Server.  The 'imgout:' prefix indicates that the output file is an image and the &lt;name&gt;
                substitution identifies the unique image produced after you call <code>dev.off()</code>.  The following script
                displays a .png image in a View:<br/>
        <pre>
        png(filename="<%="${imgout:labkeyl_png}"%>")
        plot(c(rep(25,100), 26:75), c(1:100, rep(1, 50)), ylab= "L", xlab="LabKey",
           xlim= c(0, 100), ylim=c(0, 100), main="LabKey in R")
        dev.off()
        </pre>
            </td></tr>
        <tr><td class="labkey-form-label">tsvout:&lt;name&gt;</td>
            <td>A TSV text file that is displayed on LabKey Server as a Section within a View.  No downloadable
                file is created.  For example:<br/>
        <pre>
        write.table(labkey.data, file = "<%="${tsvout:tsvfile}"%>", sep = "\t",
            qmethod = "double", col.names=NA)
        </pre>
            </td></tr>
        <tr><td class="labkey-form-label">txtout:&lt;name&gt;</td>
            <td>A text file that is displayed on LabKey Server as a Section within a View.
                No downloadable file is created.   A CSV example:<br/>
        <pre>
        write.csv(labkey.data, file = "<%="${txtout:csvfile}"%>")
        </pre>
            </td></tr>
        <tr><td class="labkey-form-label">pdfout:&lt;name&gt;</td>
            <td>A PDF output file that can be downloaded from the LabKey Server.  The 'pdfout:' prefix indicates that
                the expected output is a pdf file.  The &lt;name&gt; substitution identifies the unique file produced
                after you call <code>dev.off()</code>.<br/>
        <pre>
        pdf(file="<%="${pdfout:labkeyl_pdf}"%>")
        plot(c(rep(25,100), 26:75), c(1:100, rep(1, 50)), ylab= "L", xlab="LabKey",
            xlim= c(0, 100), ylim=c(0, 100), main="LabKey in R")
        dev.off()
        </pre>
            </td></tr>
        <tr><td class="labkey-form-label">psout:&lt;name&gt;</td>
            <td>A postscript output file that can be downloaded from the LabKey Server.  The 'psout:' prefix indicates
                that the expected output is a postscript file.  The &lt;name&gt; substitution identifies the unique
                file produced after you call <code>dev.off()</code>.<br/>
        <pre>
        postscript(file="<%="${psout:labkeyl_eps}"%>", horizontal=FALSE, onefile=FALSE)
        plot(c(rep(25,100), 26:75), c(1:100, rep(1, 50)), ylab= "L", xlab="LabKey",
            xlim= c(0, 100), ylim=c(0, 100), main="LabKey in R")
        dev.off()
        </pre>
            </td></tr>
        <tr><td class="labkey-form-label">fileout:&lt;name&gt;</td>
            <td>A text output file that can be downloaded from the LabKey Server.  For example, use <code>fileout</code>
                in the place of tsvout to print a table to a downloadable file:<br/>
        <pre>
        write.table(labkey.data, file = "<%="${fileout:tsvfile}"%>", sep = "\t",
            qmethod = "double", col.names=NA)
        </pre>
            </td></tr>
        <tr><td class="labkey-form-label">htmlout:&lt;name&gt;</td>
            <td>A text file that is displayed on LabKey Server as a Section within a View. The output is different
                from the <code>txtout:</code> replacement in that no html escaping is done. This is useful when
                you have a report that produces html output. No downloadable file is created:
        <pre>
        txt <- paste("&lt;i&gt;Click on the link to visit LabKey:&lt;/i&gt;
            &lt;a target='blank' href='http://www.labkey.org'&gt;LabKey&lt;/a&gt;")
        write(txt, file="<%="${htmlout:output}"%>")
        </pre>
            </td></tr>
        <tr><td class="labkey-form-label">svgout:&lt;name&gt;</td>
            <td>An svg file that is displayed on LabKey Server as a Section within a View. <code>htmlout</code> can be
                used to render svg outputs as well, however, using <code>svgout</code> will generate a more appropriate
                thumbnail image for the report. No downloadable file is created:
        <pre>
        svg("<%="${svgout:svg}"%>", width= 4, height=3)
        plot(x=1:10,y=(1:10)^2, type='b')
        dev.off()
        </pre>
            </td></tr>
    </table></td></tr>

    <tr><td>&nbsp;</td></tr>
    <tr><td><i>Each R script contains implicit variables that are inserted before your source script. Implicit variables
        are R data types and may contain information that can be used by the source script.</i></td></tr>
    <tr><td>&nbsp;</td></tr>
    <tr><td><i>Implicit Variables:</i></td></tr>
    <tr><td><table id="implicitVariables">
        <tr><td class="labkey-form-label">labkey.data</td>
            <td>The data frame which the input dataset is automatically read into. The code to generate the
                data frame is: <br/>
            <pre>
        labkey.data <- read.table("<%="${input_data}"%>", header=TRUE, sep="\t",
            quote="", comment.char="")
            </pre></td></tr>
        <tr><td class="labkey-form-label">labkey.url.path</td>
            <td>The path portion of the current URL which omits the base context path, action and URL parameters. 
                The path portion of the URL:
                <code>http://localhost:8080/labkey/study/home/test/begin.view</code> would be:
                <code>/home/test/</code></td></tr>
        <tr><td class="labkey-form-label">labkey.url.base</td>
            <td>The base portion of the current URL. The base portion of the URL:
                <code>http://localhost:8080/labkey/study/home/test/begin.view</code> would be:
                <code>http://localhost:8080/labkey/</code></td></tr>
        <tr><td class="labkey-form-label">labkey.url.params</td>
            <td>The list of parameters on the current URL. The parameters are represented as a list of key /
                value pairs.</td></tr>
        <tr><td class="labkey-form-label">labkey.user.email</td>
            <td>The email address of the current user</td></tr>
    </table></td></tr>

    <tr><td>&nbsp;</td></tr>
    <tr class="labkey-wp-header"><th align="left">Using regular expressions with replacement token names</th></tr>
    <tr><td><i>Sometimes it can be useful to have flexibility when binding token names to replacement parameters. This can be the case when
        a script generates file artifacts but does not know the file names in advance. Using the syntax: regex() in the place of a token name
        (where LabKey server controls the token name to file mapping) will result the following actions:
        <ul>
            <li>any script generated files not mapped to a replacement will be evaluated against the file's name using the regex</li>
            <li>if a file matches the regex, it will be assigned to the replacement and rendered accordingly</li>
        </ul>
    </i></td></tr>
    <tr><td>&nbsp;</td></tr>
    <tr><td><table id="regexSyntax">
        <tr><td class="labkey-form-label">&lt;replacement&gt;:regex(&lt;expression&gt;)</td>
            <td>The following example will find all files generated by the script with the extension : '.gct'. If any are found<br/>
                they will be assigned and rendered to the replacement parameter (in this case as a download link).<br/>
            <pre>
        ${fileout:regex(.*?(\.gct))}
            </pre></td></tr>
    </table></td></tr>

    <tr><td><i>Documentation and tutorials about the R language can be found at
        the <a target="_blank" href="http://www.r-project.org/">R Project website</a>.</i>
    </tr>
</table>
