<xsl:stylesheet version="1.1" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:variable name="foo" />

  <xsl:template name="abc">
    <x>
      <xsl:variable name="<warning>foo</warning>" />
      <xsl:value-of select="$foo" />
    </x>
  </xsl:template>
</xsl:stylesheet>