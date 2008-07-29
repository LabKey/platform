<%
/*
 * Copyright (c) 2006-2008 LabKey Corporation
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
<%
    String contextPath = request.getContextPath();
%>

<div id="filterDiv" class="labkey-row-filter labkey-filter">
  <table>
    <tr class="labkey-wp-header">
      <td title="Filter" nowrap>
        <div class="labkey-wp-title">Show Rows Where <span id="filterDivFieldName">Field</span></div>
      </td>
      <td align="right">
      <img alt="close" src="<%=contextPath%>/_images/partdelete.gif" onclick="hideFilterDiv()">
      </td>
     </tr>
    <tr>
      <td colspan=2>

        <select id="compare_1" name="compare_1" onchange="doChange(this)">
            <option value="">&lt;has any value></option>
        </select><br>
        <input disabled id="value_1" style="visibility:hidden" type=text name=value_1 onkeypress="if(event.keyCode==13)doFilter();"><br>
        <span id="compareSpan_2" style="visibility:hidden">and<br>
        <select id="compare_2" name="compare_2" onchange="doChange(this)">
            <option value="">&lt;no other filter></option>
        </select><br>
        <input disabled style="visibility:hidden" id="value_2" type="text" name="value_2" onkeypress="if(event.keyCode==13)doFilter();"><br><br>
        </span>
        <input type="submit" style="cursor:hand;background-color:#eeeeee;font-family:verdana,arial,sans-serif;font-size:8pt;border:1px solid black" value="OK" onclick="doFilter();return false;">
        <input type="submit" value="Clear" style="cursor:hand;background-color:#eeeeee;font-family:verdana,arial,sans-serif;font-size:8pt;border:1px solid black" onclick="clearFilter();return false;">
        <input type="submit" value="Clear All" style="cursor:hand;background-color:#eeeeee;font-family:verdana,arial,sans-serif;font-size:8pt;border:1px solid black" onclick="clearAllFilters();return false;">

      </td>
    </tr>
  </table>
</div>
