<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <xsl:template match="/">
    <xsl:variable name="dummy" />
    <xsl:value-of select="$<caret>" />
  </xsl:template>

</xsl:stylesheet>