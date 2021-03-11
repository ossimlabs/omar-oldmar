package omar.oldmar.app

import org.springframework.beans.factory.annotation.Value

class WmsProxyService
{
  @Value('${oldmar.wms.endpoint}')
  String wmsEndpoint

  def handleRequest(def params)
  {
    println params

    // def contentType = params.find { it.key.toUpperCase() == 'FORMAT' }?.value ?: 'image/png'

    def newParams = params.inject( [:] ) { a, b ->
      switch ( b.key?.toUpperCase() )
      {
      case 'LAYERS':
        def layers = b?.value?.split( ',' )

        if ( layers?.every { it ==~ /\d+/ } )
        {
          a['LAYERS'] = layers?.collect { "omar:raster_entry.${it}" }.join( ',' )
        }
        else if ( layers?.every { it ==~ /[A-Fa-f0-9]{64}/ } )
        {
          a['LAYERS'] = 'omar:raster_entry'
          a['FILTER'] = "index_id in ( ${layers.collect { "'${it}'" }.join( ',' )} )"
        }
        break
      case 'CONTROLLER':
        break
      default:
        a[b.key] = b.value
      }
      a
    }
    
    newParams['STYLES'] = '{"bands":"default","brightness":0,"contrast":1,"histCenterTile":false,"histLinearNormClip":"0,1","histOp":"auto-minmax","nullPixelFlip":true,"resampler_filter":"bilinear","sharpen_percent":0,"gamma":1,"histCenterClip":0.5}'
    
    def newQuery = newParams.collect { "${it.key}=${URLEncoder.encode( it.value as String, 'UTF-8' )}" }.join( '&' )
    def url = "${wmsEndpoint}?${newQuery}".toURL()
    def contentType = url.openConnection().contentType

    println url

    [contentType: contentType, file: url.bytes]
  }
}
