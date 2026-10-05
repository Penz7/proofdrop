package com.penz7.proofdrop.feature.fleet

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.penz7.proofdrop.core.model.CourierPosition
import com.penz7.proofdrop.core.model.CourierStatus
import com.penz7.proofdrop.core.model.DemoData
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** Raster OpenStreetMap tiles: free, no API key. Attribution is shown by MapLibre's (i) button. */
private const val OSM_STYLE = """
{
  "version": 8,
  "sources": {
    "osm": {
      "type": "raster",
      "tiles": ["https://tile.openstreetmap.org/{z}/{x}/{y}.png"],
      "tileSize": 256,
      "maxzoom": 19,
      "attribution": "© OpenStreetMap contributors"
    }
  },
  "layers": [{ "id": "osm", "type": "raster", "source": "osm" }]
}
"""

private const val SOURCE_ID = "couriers"

internal fun CourierStatus.hex(): String = when (this) {
    CourierStatus.IDLE -> "#8A94A6"
    CourierStatus.EN_ROUTE -> "#3B82C4"
    CourierStatus.DELIVERING -> "#2E9E5B"
    CourierStatus.OFFLINE -> "#4B5563"
}

/** MapLibre's MapView wrapped for Compose: lifecycle-aware, couriers drawn as a GeoJSON circle layer. */
@Composable
internal fun FleetMap(couriers: List<CourierPosition>, selfId: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).apply { onCreate(null) }
    }
    var style by remember { mutableStateOf<Style?>(null) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            val start = couriers.firstOrNull { it.courierId == selfId }
            map.cameraPosition = CameraPosition.Builder()
                .target(LatLng(start?.latitude ?: DemoData.HUB_LAT, start?.longitude ?: DemoData.HUB_LNG))
                .zoom(14.0)
                .build()
            map.setStyle(Style.Builder().fromJson(OSM_STYLE)) { loaded ->
                loaded.addSource(GeoJsonSource(SOURCE_ID))
                loaded.addLayer(
                    CircleLayer("courier-dots", SOURCE_ID).withProperties(
                        PropertyFactory.circleColor(Expression.toColor(Expression.get("color"))),
                        PropertyFactory.circleRadius(
                            Expression.switchCase(Expression.get("self"), Expression.literal(11f), Expression.literal(8f)),
                        ),
                        PropertyFactory.circleStrokeWidth(
                            Expression.switchCase(Expression.get("self"), Expression.literal(4f), Expression.literal(2f)),
                        ),
                        PropertyFactory.circleStrokeColor(
                            Expression.switchCase(Expression.get("self"), Expression.literal("#FFC72C"), Expression.literal("#FFFFFF")),
                        ),
                    ),
                )
                style = loaded
            }
        }
    }

    LaunchedEffect(style, couriers) {
        val source = style?.getSourceAs<GeoJsonSource>(SOURCE_ID) ?: return@LaunchedEffect
        source.setGeoJson(
            FeatureCollection.fromFeatures(
                couriers.map { c ->
                    Feature.fromGeometry(Point.fromLngLat(c.longitude, c.latitude)).apply {
                        addStringProperty("color", c.status.hex())
                        addBooleanProperty("self", c.courierId == selfId)
                    }
                },
            ),
        )
    }

    AndroidView(factory = { mapView }, modifier = modifier)
}
