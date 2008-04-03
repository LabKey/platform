<%
    String contextPath = request.getContextPath();
%>

<div id="filterDiv" style="display:none;border: 1px solid black;padding:4px; display:none;position:absolute;background-color:white">
  <table border="0" cellpadding="0" cellspacing="0">
    <tr class="wpHeader">
      <td title="Filter" nowrap>
        <div class="wpTitle">Show Rows Where <span id="filterDivFieldName">Field</span></div>
      </td>
      <td align="right">
      <img alt="close" border=0 src="<%=contextPath%>/_images/partdelete.gif" onclick="hideFilterDiv()">
      </td>
     </tr>
    <tr>
      <td colspan=2 class="normal">

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
