<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="/WEB-INF/struts-html.tld" prefix="html" %>

<html:xhtml/>

  <div class="geneInformation">
    <h3 class="overlapping">SNPs to overlapping Genes within 10.0kb</h3>

    <table id="snpToGenes" cellspacing="0">
      <tr>
        <th>Gene Primary Identifier</th>
        <th>Gene name</th>
        <th>Gene Symbol</th>
        <th colspan="2">Relative to Gene</th>
      </tr>
      <c:forEach var="row" items="${list}" varStatus="status">
        <tr class="${status.count mod 2 == 0 ? 'odd' : 'even'}">
          <c:forEach var="column" items="${row}" varStatus="columnStatus">
            <c:choose>
              <%-- distance --%>
              <c:when test="${columnStatus.count == 4}">
                <td class="distance">${column}</td>
              </c:when>
              <%-- direction --%>
              <c:when test="${columnStatus.count == 5}">
                <td class="direction">${column}</td>
              </c:when>
              <c:otherwise>
                <td>${column}</td>
              </c:otherwise>
            </c:choose>
          </c:forEach>
        </tr>
      </c:forEach>
    </table>
  </div>

<script type="text/javascript">
jQuery(document).ready(function() {
    // no value...
    jQuery("table#snpToGenes td").each(function() {
      if (jQuery(this).text() == "[no value]") jQuery(this).text("");
    });

    // distance formatting
    jQuery("table#snpToGenes td.distance").each(function() {
        var distance = parseInt(jQuery(this).text());
        // under 1kb
        if (distance < 1000) {
          if (distance == 0) {
            jQuery(this).text("genic");
          } else {
            jQuery(this).text(distance + "b");
          }
        } else {
          jQuery(this).text(distance/1000 + "kb");
        }
    });
});
</script>

<style> div.geneInformation table td.distance { border-right:none; } </style>