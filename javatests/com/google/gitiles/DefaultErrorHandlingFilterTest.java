package com.google.gitiles;

import static com.google.common.truth.Truth.assertThat;
import static javax.servlet.http.HttpServletResponse.SC_BAD_REQUEST;

import com.google.common.collect.ImmutableList;
import com.google.gitiles.GitilesRequestFailureException.FailureReason;
import java.net.URL;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jgit.http.server.glue.MetaFilter;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DefaultErrorHandlingFilterTest {
  private final MetaFilter mf = new MetaFilter();

  @Before
  public void setUp() {
    mf.serve("*")
        .through(
            new DefaultErrorHandlingFilter(
                new DefaultRenderer(
                    GitilesServlet.STATIC_PREFIX, ImmutableList.<URL>of(), "test site")))
        .with(new TestServlet());
  }

  @Test
  public void renderError() throws Exception {
    FakeHttpServletRequest req = FakeHttpServletRequest.newRequest();
    req.setPathInfo("/");
    FakeHttpServletResponse resp = new FakeHttpServletResponse();
    mf.doFilter(req, resp, (unusedReq, unusedResp) -> {});

    assertThat(resp.getStatus()).isEqualTo(SC_BAD_REQUEST);
    assertThat(resp.getHeader(DefaultErrorHandlingFilter.GITILES_ERROR))
        .isEqualTo("INCORECT_PARAMETER");
  }

  private static class TestServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) {
      throw new GitilesRequestFailureException(FailureReason.INCORECT_PARAMETER);
    }
  }
}
