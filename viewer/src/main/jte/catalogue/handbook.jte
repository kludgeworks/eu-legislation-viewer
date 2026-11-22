@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ApiResourceView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ApiTermView
@import dev.groknull.kludgeworks.eu.legislation.viewer.site.Frame
@import java.util.List
@param Frame frame
@param String title
@param boolean api
@param List<ApiResourceView> resources
@param List<ApiTermView> problems
@param gg.jte.Content content
<%-- Lays out a page of reference, About and the API's documentation, as a handbook: a column of
     contents that stays in view, beside a column of reading width. The two pages have the same
     contents in the same place, so the page does not move when the reader goes from one to the
     other. A link to the page itself is #name, and the contents mark the section the reader is in
     (page-contents, js/outline.js). A link to the other page is its address. api is true on the
     API's documentation. --%>
!{var about = api ? "/about" : "";}
!{var docs = api ? "" : "/api/docs";}
@template.layout.page(frame = frame, title = title, content = @`
  <div class="handbook">
    <page-contents>
      <nav class="handbook-contents" aria-label="Contents">
        <h2>About</h2>
        <ul>
          <li><a href="${about}#what">What this is</a></li>
          <li><a href="${about}#privacy">Privacy</a></li>
          <li><a href="${about}#build">Build</a></li>
        </ul>
        <h2>API</h2>
        <ul>
          <li><a href="${docs}#conventions">Conventions</a></li>
          <li><a href="${docs}#resources">Resources</a>
            <ul>
              @for(var resource : resources)
                <li><a href="${docs}#${resource.id()}">${resource.title()}</a></li>
              @endfor
            </ul>
          </li>
          <li><a href="${docs}#problems">Problem types</a>
            <ul class="handbook-terms">
              @for(var problem : problems)
                <li><a class="mono" href="${docs}#${problem.name()}">${problem.name()}</a></li>
              @endfor
            </ul>
          </li>
        </ul>
      </nav>
    </page-contents>
    <article>${content}</article>
  </div>
`)
