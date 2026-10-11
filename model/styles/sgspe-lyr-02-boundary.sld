<?xml version="1.0" encoding="UTF-8"?>
<!-- LYR-02 Centros poblados y localidades, su polígono (gis.md §2.2 y §7.2). Colores: model/styles/palette.json -->
<StyledLayerDescriptor version="1.0.0" xmlns="http://www.opengis.net/sld" xmlns:ogc="http://www.opengis.net/ogc"
  xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://www.opengis.net/sld http://schemas.opengis.net/sld/1.0.0/StyledLayerDescriptor.xsd">
  <NamedLayer>
    <Name>sgspe-lyr-02-boundary</Name>
    <UserStyle>
      <Name>sgspe-lyr-02-boundary</Name>
      <Title>LYR-02 Límite de la localidad</Title>
      <FeatureTypeStyle>
        <Rule>
          <Title>Límite de la localidad</Title>
          <PolygonSymbolizer>
            <Fill>
              <CssParameter name="fill">#b8452e</CssParameter>
              <CssParameter name="fill-opacity">0.08</CssParameter>
            </Fill>
            <Stroke>
              <CssParameter name="stroke">#b8452e</CssParameter>
              <CssParameter name="stroke-width">1</CssParameter>
            </Stroke>
          </PolygonSymbolizer>
        </Rule>
        <Rule>
          <Title>Nombre</Title>
          <MaxScaleDenominator>100000</MaxScaleDenominator>
          <TextSymbolizer>
            <Label>
              <ogc:PropertyName>name</ogc:PropertyName>
            </Label>
            <Font>
              <CssParameter name="font-family">DejaVu Sans</CssParameter>
              <CssParameter name="font-size">10</CssParameter>
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
              <CssParameter name="fill">#3a2a24</CssParameter>
            </Fill>
              <VendorOption name="conflictResolution">true</VendorOption>
              <VendorOption name="autoWrap">60</VendorOption>
          </TextSymbolizer>
        </Rule>
      </FeatureTypeStyle>
    </UserStyle>
  </NamedLayer>
</StyledLayerDescriptor>
