@import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Chrome
@import dev.groknull.kludgeworks.eu.legislation.viewer.chrome.Theme
@param Chrome chrome
!{var current = chrome.theme();}
<%-- Shows the theme switch: light, the device's theme, or dark. It is a plain form and is not
     boosted. The theme is set on the html element, and a boosted swap of the body would not change
     it. <theme-switch> applies the theme in place. Without JavaScript, the form posts. --%>
<theme-switch>
<form class="theme" method="post" action="/theme" role="group" aria-label="Colour theme">
  <input type="hidden" name="back" value="${chrome.here()}">
  <button name="set" value="light" title="Remembered on this device" aria-label="Light" aria-pressed="${String.valueOf(current == Theme.LIGHT)}">
    <svg viewBox="0 0 16 16" aria-hidden="true"><circle cx="8" cy="8" r="3"/><path d="M8 1v2M8 13v2M1 8h2M13 8h2M3 3l1.4 1.4M11.6 11.6 13 13M3 13l1.4-1.4M11.6 4.4 13 3"/></svg>
  </button>
  <button name="set" value="system" title="Remembered on this device" aria-label="Follow the system" aria-pressed="${String.valueOf(current == Theme.SYSTEM)}">
    <svg viewBox="0 0 16 16" aria-hidden="true"><rect x="1.5" y="2.5" width="13" height="9" rx="1"/><path d="M5.5 14.5h5M8 11.5v3"/></svg>
  </button>
  <button name="set" value="dark" title="Remembered on this device" aria-label="Dark" aria-pressed="${String.valueOf(current == Theme.DARK)}">
    <svg viewBox="0 0 16 16" aria-hidden="true"><path d="M13.5 9.5A5.5 5.5 0 0 1 6.5 2.5a5.5 5.5 0 1 0 7 7z"/></svg>
  </button>
</form>
</theme-switch>
