<%
/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.view.template.ClientDependencies" %>
<%@ page extends="org.labkey.api.jsp.JspBase" %>
<%!
    @Override
    public void addClientDependencies(ClientDependencies dependencies)
    {
        dependencies.add("Ext4");
    }
%>
<%
    Container c = getContainer();
%>
<style>
.level-inherited {
  font-style: italic;
  cursor: pointer
}
.level-configured {
  cursor: pointer
}

.level-OFF {
  color: black
}
.level-FATAL {
  color: red
}
.level-ERROR {
  color: orangered
}
.level-WARN {
  color: orange
}
.level-INFO {
  color: dodgerblue
}
.level-DEBUG {
  color: olivedrab
}
.level-TRACE {
  color: blue
}
.level-ALL {
  color: black
}

</style>

<h2>Log4j Loggers</h2>

<input id="search" type="text" size="120">

<p style='padding-left:0.75em; margin:0.5em;'>
<label for="showLevel">Show Level:</label>
<select id="showLevel" value="">
  <option value="">
  <option value="OFF">OFF
  <option value="FATAL">FATAL
  <option value="ERROR">ERROR
  <option value="WARN">WARN
  <option value="INFO">INFO
  <option value="DEBUG">DEBUG
  <option value="TRACE">TRACE
  <option value="ALL">ALL
</select>

&nbsp;
<label for="showInherited">Show inherited:</label>
<input id="showInherited" type="checkbox" checked>
</p>

<p style='padding-left:0.75em; margin:0.5em;'>
<a id='refresh' class='labkey-button'><span>Refresh</span></a>
<a id='reset' class='labkey-button'><span>Reset</span></a>
</p>

<p>
<table id='loggerTable' class='labkey-data-region-legacy labkey-show-borders' style='margin-left:0.75em;'>
  <thead>
    <tr>
      <td width=80 class='labkey-column-header'>Level</td>
      <td width=500 class='labkey-column-header'>Name</td>
      <td width=100 class='labkey-column-header'>Parent</td>
    </tr>
    <tr>
      <td colspan=3 id='loggerTableMessage' class='labkey-message'></td>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td colspan=3>Loading...</td>
    </tr>
  </tbody>
</table>

<%-- HTML5 datalist: https://developer.mozilla.org/en-US/docs/Web/HTML/Element/datalist --%>
<datalist id="levelsList">
  <option value="OFF">OFF
  <option value="FATAL">FATAL
  <option value="ERROR">ERROR
  <option value="WARN">WARN
  <option value="INFO">INFO
  <option value="DEBUG">DEBUG
  <option value="TRACE">TRACE
  <option value="ALL">ALL
</datalist>

<script type="text/javascript">
(function ()
{
    var searchInput = document.getElementById('search');
    var showInheritedInput = document.getElementById('showInherited');
    var showLevelSelect = document.getElementById('showLevel');

    var loggerTable = document.getElementById('loggerTable');
    var loggerTableMessage = document.getElementById('loggerTableMessage');

    var refreshButton = document.getElementById('refresh');
    var resetButton = document.getElementById('reset');

    var levels = {
        "OFF": "OFF",
        "FATAL": "FATAL",
        "ERROR": "ERROR",
        "WARN": "WARN",
        "INFO": "INFO",
        "DEBUG": "DEBUG",
        "TRACE": "TRACE",
        "ALL": "ALL"
    };

    //
    // refresh
    //

    refreshLoggers();

    refreshButton.addEventListener('click', onRefreshClick, false);
    function onRefreshClick()
    {
        refreshLoggers();
    }

    function refreshLoggers()
    {
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL("logger", "list.api"),
            success: LABKEY.Utils.getCallbackWrapper(function (response)
            {
                if (response.success)
                    updateDisplay(response["data"]);
            }, this)
        });
    }

    function updateDisplay(loggers)
    {
        loggers.sort(function (a, b)
        {
            return a.name.localeCompare(b.name);
        });
        if (loggers.length == 0)
        {
            loggerTableMessage.innerHTML = 'No loggers in response';
            loggerTableMessage.style.display = '';
        }
        else
        {
            var isVisible = createVisibleFilter();
            var rows = [];
            for (var i = 0, len = loggers.length; i < len; i++)
            {
                var logger = loggers[i];
                var visible = isVisible(logger);
                rows.push(renderLogger(logger, visible));
            }

            loggerTableMessage.style.display = 'none';
            loggerTable.tBodies[0].innerHTML = rows.join("");
        }
    }

    // The logger info is stored on the <tr> as a dataset using the 'data-*' attributes
    //   https://developer.mozilla.org/en-US/docs/Web/Guide/HTML/Using_data_attributes
    //   https://developer.mozilla.org/en-US/docs/Web/API/HTMLElement.dataset
    function renderLogger(logger, visible)
    {
        var inherited = logger.inherited === true || logger.inherited === "true"; // convert to boolean

        return "<tr class='logger-row' " + (visible ? "" : " style='display:none;'") +
                "data-name='" + logger.name + "' data-level='" + logger.level + "' data-inherited='" + logger.inherited + "' data-parent='" + logger.parent + "'>" +
                "<td class='" + (inherited ? 'level-inherited' : 'level-configured') + " level-" + logger.level + "'>" +
                logger.level +
                "</td>" +
                "<td>" + (logger.name == 'null' ? '&lt;root&gt;' : logger.name) + "</td>" +
                "<td>" + logger.parent + "</td>" +
                "</tr>";
    }

    //
    // reset
    //

    resetButton.addEventListener('click', onResetClick, false);
    function onResetClick()
    {
        reset();
    }

    function reset()
    {
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL("logger", "reset.api"),
            success: LABKEY.Utils.getCallbackWrapper(function (response)
            {
                if (response.success) {
                    // Reset inputs back to default state
                    searchInput.value = '';
                    showLevelSelect.value = '';
                    showInheritedInput.checked = true;

                    // Reload the list of loggers
                    refreshLoggers();
                }
            })
        });
    }

    //
    // filtering
    //

    searchInput.addEventListener('input', updateVisibleRows, false);
    showLevelSelect.addEventListener('change', updateVisibleRows, false);
    showInheritedInput.addEventListener('change', updateVisibleRows, false);

    function updateVisibleRows()
    {
        var isVisible = createVisibleFilter();

        var visibleRows = 0;
        var tbody = loggerTable.tBodies[0];
        var nl = tbody.querySelectorAll("tr");
        for (var i = 0, len = nl.length; i < len; i++)
        {
            var row = nl[i];
            var visible = isVisible(row.dataset);

            if (visible)
            {
                row.style.display = "";
                visibleRows++;
            }
            else
            {
                row.style.display = "none";
            }
        }

        if (visibleRows > 0)
        {
            loggerTableMessage.style.display = "none";
        }
        else
        {
            loggerTableMessage.innerHTML = "No rows visible.";
            loggerTableMessage.style.display = "";
        }
    }

    function createVisibleFilter()
    {
        var filterName = searchInput.value.toLowerCase();
        var filterLevel = showLevelSelect.value;
        var filterInherited = showInheritedInput.checked;

        return function (logger)
        {
            var name = logger.name.toLowerCase();
            var inherited = logger.inherited === true || logger.inherited === "true"; // convert to boolean

            var visible = true;
            if (filterName && name.indexOf(filterName) == -1)
                visible = false;

            if (filterLevel && logger.level != filterLevel)
                visible = false;

            if (!filterInherited && inherited)
                visible = false;

            return visible;
        }
    }

    //
    // level select
    //

    loggerTable.addEventListener('click', onTableClick, false);

    function onTableClick(evt)
    {
        var target = evt.target;
        if (target.tagName == "TD" && target.classList.contains("level-configured") || target.classList.contains("level-inherited"))
        {
            var initialValue = target.parentNode.dataset.level;

            var input = document.createElement("input");
            input.type = "text";
            input.setAttribute("list", "levelsList");
            input.size = 7;
            input.value = initialValue;
            input.addEventListener('blur', onLevelInputBlur, false);
            input.style.fontSize = '11px';

            // remove level classes
            target.classList.remove('level-inherited');
            target.classList.remove('level-configured');
            target.classList.remove('level-' + initialValue);

            // remove all content and add the <select>
            target.innerHTML = "";
            target.appendChild(input);
        }
    }

    function onLevelInputBlur(evt)
    {
        var target = evt.target;
        var td = target.parentNode;
        var tr = td.parentNode;
        var newLevel = target.value;

        // update the new level value if it is valid
        var updated = false;
        if (levels[newLevel] && newLevel != tr.dataset.level)
        {
            updated = true;
            tr.dataset.level = newLevel;
            tr.dataset.inherited = false;
        }

        tr.innerHTML = renderLogger(tr.dataset, true);

        if (updated)
        {
            save(tr);
        }
    }

    function save(row)
    {
        LABKEY.Ajax.request({
            url: LABKEY.ActionURL.buildURL("logger", "update.api"),
            jsonData: {
                name: row.dataset.name,
                level: row.dataset.level
            }
        });
    }

})();

</script>
