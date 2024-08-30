package org.radeox.test.filter.mock;

import org.radeox.api.engine.context.InitialRenderContext;
import org.radeox.filter.Filter;
import org.radeox.filter.context.FilterContext;

public class MockReplacedFilter implements Filter {
  @Override
  public String filter(String input, FilterContext context) {
    return input;
  }

  @Override
  public void setInitialContext(InitialRenderContext context) {
  }

  @Override
  public String[] replaces() {
    return new String[0];
  }

  @Override
  public String[] before() {
    return new String[0];
  }

  @Override
  public String getDescription() {
    return "";
  }
}
