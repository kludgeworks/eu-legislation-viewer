@param String eli
<%-- Says that no act has the address: the Publications Office has no act with the ELI. The act's
     page shows it when the lookup ends so, with 404 when it is loaded again. --%>
<section class="failure" role="alert" aria-labelledby="no-act">
  <div class="failure-head">
    <h2 id="no-act">No act has this address</h2>
    <p>The Publications Office has no act with the ELI ${eli}.</p>
  </div>
  <div class="failure-actions">
    <a class="button" href="/">Find an act</a>
  </div>
</section>
