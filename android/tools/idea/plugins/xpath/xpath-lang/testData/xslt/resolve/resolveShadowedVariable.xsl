<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:variable name="a" />

  <xsl:template match="/">
    <xsl:variable name="a" />

    <xsl:value-of select="$<caret>a" />
  </xsl:template>

</xsl:stylesheet>