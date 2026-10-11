<?xml version="1.0" encoding="UTF-8"?>
<!-- LYR-05 Territorio comunal (gis.md §2.2 y §7.2). Colores: model/styles/palette.json -->
<StyledLayerDescriptor version="1.0.0" xmlns="http://www.opengis.net/sld" xmlns:ogc="http://www.opengis.net/ogc"
  xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://www.opengis.net/sld http://schemas.opengis.net/sld/1.0.0/StyledLayerDescriptor.xsd">
  <NamedLayer>
    <Name>sgspe-lyr-05</Name>
    <UserStyle>
      <Name>sgspe-lyr-05</Name>
      <Title>LYR-05 Territorio comunal</Title>
      <FeatureTypeStyle>
        <Rule>
          <Title>Territorio comunal</Title>
          <PolygonSymbolizer>
            <Fill>
              <CssParameter name="fill">#3f7d3a</CssParameter>
              <CssParameter name="fill-opacity">0.12</CssParameter>
            </Fill>
            <Stroke>
              <CssParameter name="stroke">#3f7d3a</CssParameter>
              <CssParameter name="stroke-width">1.4</CssParameter>
            </Stroke>
          </PolygonSymbolizer>
        </Rule>
        <Rule>
          <Title>Nombre</Title>
          <MaxScaleDenominator>250000</MaxScaleDenominator>
          <TextSymbolizer>
            <Label>
              <ogc:PropertyName>name</ogc:PropertyName>
            </Label>
            <Font>
              <CssParameter name="font-family">DejaVu Sans</CssParameter>
              <CssParameter name="font-size">11</CssParameter>
              <CssParameter name="font-style">italic</CssParameter>
            </Font>
            <LabelPlacement>
              <PointPlacement>
                <AnchorPoint>
                  <AnchorPointX>0.5</AnchorPointX>
                  <AnchorPointY>0.5</AnchorPointY>
                </AnchorPoint>
              </PointPlacement>
            </LabelPlacement>
            <Halo>
              <Radius>1.5</Radius>
              <Fill>
                <CssParameter name="fill">#ffffff</CssParameter>
              </Fill>
            </Halo>
            <Fill>
              <CssParameter name="fill">#24461f</CssParameter>
            </Fill>
              <VendorOption name="conflictResolution">true</VendorOption>
              <VendorOption name="autoWrap">80</VendorOption>
              <VendorOption name="goodnessOfFit">0.3</VendorOption>
          </TextSymbolizer>
        </Rule>
      </FeatureTypeStyle>
    </UserStyle>
  </NamedLayer>
</StyledLayerDescriptor>
