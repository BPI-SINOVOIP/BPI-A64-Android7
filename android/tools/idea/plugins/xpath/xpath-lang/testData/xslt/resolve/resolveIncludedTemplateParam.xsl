<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:include href="included.xsl" />

  <xsl:template match="/">
    <xsl:call-template name="dummy">
      <xsl:with-param name="foo<caret>" />
    </xsl:call-template>
  </xsl:template>
</xsl:stylesheet>