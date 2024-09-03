/*
 * This file is part of "SnipSnap Radeox Rendering Engine".
 *
 * Copyright (c) 2002 Stephan J. Schmidt, Matthias L. Jugel
 * All Rights Reserved.
 *
 * Please visit http://radeox.org/ for updates and contact.
 *
 * --LICENSE NOTICE--
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 * --LICENSE NOTICE--
 */

package org.radeox.filter;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.radeox.engine.context.BaseInitialRenderContext;
import org.radeox.api.engine.context.InitialRenderContext;
import org.radeox.api.engine.context.RenderContext;
import org.radeox.filter.context.FilterContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/*
 * FilterPipe is a collection of Filters which are
 * applied one by one to an input to generate output
 *
 * @author stephan
 * @team sonicteam
 * @version $Id: FilterPipe.java,v 1.20 2003/10/07 08:20:24 stephan Exp $
 */

public class FilterPipe {
  private static Logger log = LogManager.getLogger(FilterPipe.class);

  public final static String FIRST_IN_PIPE = "all";
  public final static String LAST_IN_PIPE = "none";
  public final static String[] EMPTY_BEFORE = new String[]{};
  public final static String[] NO_REPLACES = new String[]{};
  public final static String[] FIRST_BEFORE = new String[]{ FIRST_IN_PIPE };

  private InitialRenderContext initialContext;

  private List filterList = null;
  private static Object[] noArguments = new Object[]{};

  public FilterPipe() {
     this(new BaseInitialRenderContext());
  }

  public FilterPipe(InitialRenderContext context) {
    filterList = new ArrayList();
    initialContext = context;
  }

  public void init() {
    Iterator iterator = new ArrayList(filterList).iterator();
    while (iterator.hasNext()) {
      Filter filter = (Filter) iterator.next();
      String[] replaces = filter.replaces();
      for (int i = 0; i < replaces.length; i++) {
        String replace = replaces[i];
        removeFilter(replace);
      }
    }
  }

  public void removeFilter(String filterClass) {
    Iterator iterator = filterList.iterator();
    while (iterator.hasNext()) {
      Filter filter = (Filter) iterator.next();
      if (filter.getClass().getName().equals(filterClass)) {
        iterator.remove();
      }
    }
  }

  /**
   * Add a filter to the pipe
   *
   * @param filter Filter to add
   */
  public void addFilter(Filter filter) {
    filter.setInitialContext(initialContext);

    int minIndex = Integer.MAX_VALUE;
    String[] before = filter.before();
    for (int i = 0; i < before.length; i++) {
      String s = before[i];
      int index = index(filterList, s);
      if (index < minIndex) {
        minIndex = index;
      }
    }
    if (minIndex == Integer.MAX_VALUE) {
      // -1 is more usable for not-found than MAX_VALUE
      minIndex = -1;
    }

    if (contains(filter.before(), FIRST_IN_PIPE)) {
      filterList.add(0, filter);
    } else if (minIndex != -1) {
      filterList.add(minIndex, filter);
//    } else if (contains(filter.before(), LAST_IN_PIPE)) {
//      filterList.add(-1, filter);
    } else {
      filterList.add(filter);
    }
  }

  public int index(String filterName) {
    return FilterPipe.index(filterList, filterName);
  }

  public static int index(List list, final String filterName) {
    for (int i = 0; i < list.size(); i++) {
      if (filterName.equals(
          list.get(i).getClass().getName()))
        return i;
    }
    return -1;
  }

  public static boolean contains(Object[] array, Object value) {
    return (Arrays.binarySearch(array, value) != -1);
  }

  /**
   * Filter some input and generate ouput. FilterPipe pipes the
   * string input through every filter in the pipe and returns
   * the resulting string.
   *
   * @param input Input string which should be transformed
   * @param context FilterContext with information about the environment
   * @return result Filtered output
   */
  public String filter(String input, FilterContext context) {
    //Logger.debug("FilterPipe.filter: context = "+context);
    String output = input;
    Iterator filterIterator = filterList.iterator();
    RenderContext renderContext = context.getRenderContext();

    // Apply every filter in filterList to input string
    while (filterIterator.hasNext()) {
      Filter f = (Filter) filterIterator.next();
      try {
        // assume all filters non cacheable
        if (f instanceof CacheFilter) {
          renderContext.setCacheable(true);
        } else {
          renderContext.setCacheable(false);
        }
        String tmp = f.filter(output, context);
        if (output.equals(tmp)) {
          renderContext.setCacheable(true);
        }
        if (null == tmp) {
          log.warn("FilterPipe.filter: error while filtering: " + f);
        } else {
          output = tmp;
        }
        renderContext.commitCache();
      } catch (Exception e) {
        log.warn("Filtering exception: " + f + " in " + context.getRenderContext(), e);
      }
    }
    return output;
  }

  public Filter getFilter(int index) {
    return (Filter) filterList.get(index);
  }
}