@import dev.groknull.kludgeworks.eu.legislation.viewer.site.Frame
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.ArrivingView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.CompareView
@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.MastheadView
@param Frame frame
@param String title
@param MastheadView masthead
@param CompareView compare
@param gg.jte.Content changes
@param boolean arrived = false
@param ArrivingView arriving = null
@param String view = null
<%-- Shows the Compare page of an act: the law on two days. It has the head of the act, the two
     days with the amending acts between them (compare/days.jte), and then the changes. The changes
     are the comparison module's: a slot loads them from that module. For one provision, the slot
     loads the wording of the provision on the two days, and the steps between the days come after
     it (compare/steps.jte).
     Its links are boosted, as on the Read page: each swaps in the #page of the page it links to.
     Only hx-boost is inherited, with its target, selection and swap. The slot loads with htmx
     attributes of its own. The page's view (view) is the comparison module's code, as the reader
     gave it: while the next page loads, the page shows the shape of the view that is coming.
     When the reader comes from the preparing page (arrived), the days and the text fade in. If
     texts appeared after the act was prepared and are being prepared now, a notice says so
     (arriving). --%>
@template.layout.page(frame = frame, title = title, content = @`
  <div id="page" class="${arrived ? "arrived" : null}" data-view="${view != null ? view : "changes"}" hx-boost:inherited="target:#page select:#page swap:outerHTML">
    @template.catalogue.skeletons()
    @template.catalogue.masthead(masthead = masthead)
    @if(arriving != null)
      @template.catalogue.arriving(arriving = arriving)
    @endif
    @template.catalogue.compare.days(compare = compare)
    ${changes}
    @if(!compare.steps().isEmpty())
      @template.catalogue.compare.steps(compare = compare)
    @endif
  </div>
`)
