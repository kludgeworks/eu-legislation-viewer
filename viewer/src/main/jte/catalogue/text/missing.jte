@import dev.groknull.kludgeworks.eu.legislation.viewer.catalogue.Version
@import dev.groknull.kludgeworks.eu.legislation.viewer.language.Language
@param Version version
@param String eurLex
<div id="text">
  <p class="status">
    This version has no text in this language that the viewer reads. It is in:
    @for(Language language : version.languages())${language.code()} @endfor
  </p>
  <p><a href="${eurLex}">EUR-Lex</a> may have it in other formats.</p>
</div>
