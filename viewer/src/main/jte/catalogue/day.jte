@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.web.view.DayView
@param DayView day
@param String id = "on"
<%-- Shows the day control of a page: one field that sets the day of the page, or its two days.
     DayView supplies the data. Without scripts it is a GET form with one date box, or with two
     date boxes where the page has two days. The element <day-picker> (daypicker.js) hides the
     boxes and shows one field with a calendar. The calendar opens on one day. Where the page has
     an address for two days (data-range), a switch in the calendar lets the reader select two
     days. The calendar puts a dot on each day with a change (data-changes) and names the acts of
     the day. A hidden box keeps the provision that a page with two days is limited to. --%>
<day-picker data-single="${day.single()}" data-range="${day.range()}" data-changes="${day.changes()}">
  <form class="on" method="get" action="${day.from() == null ? day.single() : day.range()}">
    @if(day.from() == null)
      <label for="${id}">The law on</label>
      <input id="${id}" name="on" type="date" value="${day.on()}" required>
    @else
      <label for="${id}-from">The law on</label>
      <input id="${id}-from" name="from" type="date" value="${day.from()}" required>
      <label for="${id}">compared with</label>
      <input id="${id}" name="to" type="date" value="${day.on()}" required>
    @endif
    @if(day.occurrence() != null)<input type="hidden" name="occurrence" value="${day.occurrence()}">@endif
    <button>Go</button>
  </form>
</day-picker>
