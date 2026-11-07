@import dev.groknull.kludgeworks.eu.legislation.viewer.content.web.JournalHeader
@param JournalHeader header
<%-- Shows the head of the Official Journal page on which the published act starts (oj-hd-*). It
     shows the date, the language, the Journal, and the issue and page. --%><header class="journal-header"><span class="journal-date">${header.date()}</span><span class="journal-language">${header.language()}</span><span class="journal-name">${header.journal()}</span><span class="journal-page">${header.issue()}</span></header>