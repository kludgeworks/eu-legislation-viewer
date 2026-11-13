@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.NoticeView
@param NoticeView notice
<%-- Makes one message of the stream of notices of an act's page (NoticeController). htmx swaps it
     in for the empty notice in the masthead (hx-partial). It says what changed. The page itself
     does not change. --%>
<hx-partial hx-target="#notice" hx-swap="outerHTML"><div id="notice" class="arriving" role="status"><p>${notice.notice()}</p></div></hx-partial>
