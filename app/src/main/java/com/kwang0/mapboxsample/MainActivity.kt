package com.kwang0.mapboxsample

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.mapbox.android.core.permissions.PermissionsListener
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.LocationComponentOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.expressions.Expression
import com.mapbox.mapboxsdk.style.expressions.Expression.*
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.*
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.layers.TransitionOptions
import com.mapbox.mapboxsdk.style.sources.GeoJsonOptions
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.mapbox.mapboxsdk.utils.BitmapUtils
import kotlinx.android.synthetic.main.activity_main.*
import java.net.URI
import java.net.URISyntaxException


class MainActivity : AppCompatActivity(), OnMapReadyCallback, PermissionsListener {
    private val TAG = MainActivity::class.simpleName

    private val CLUSTER_EARTHQUAKE_TRIANGLE_ICON_ID = "quake-triangle-icon-id"
    private val SINGLE_EARTHQUAKE_TRIANGLE_ICON_ID = "single-quake-icon-id"
    private val EARTHQUAKE_SOURCE_ID = "earthquakes"
    private val POINT_COUNT = "point_count"

    private var permissionsManager: PermissionsManager = PermissionsManager(this)
    private lateinit var mapboxMap: MapboxMap

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Mapbox Access token
        Mapbox.getInstance(applicationContext, getString(R.string.mapbox_access_token))

        setContentView(R.layout.activity_main)

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)
    }

    override fun onMapReady(mapboxMap: MapboxMap) {
        this.mapboxMap = mapboxMap

        // Hide compass
        mapboxMap.uiSettings.isCompassEnabled = false

//        mapboxMap.setStyle(Style.Builder().fromUri(
//                "mapbox://styles/mapbox/cjerxnqt3cgvp2rmyuxbeqme7")) {
//
//            // Map is set up and the style has loaded. Now you can add data or make other map adjustments
//            enableLocationComponent(it)
//        }

        mapboxMap.setStyle(Style.LIGHT) { style ->
            // Disable any type of fading transition when icons collide on the map. This enhances the visual
            // look of the data clustering together and breaking apart.
            style.transition = TransitionOptions(0, 0, false)
            initLayerIcons(style)
            addClusteredGeoJsonSource(style)
            Toast.makeText(this@MainActivity, R.string.zoom_map_in_and_out_instruction,
                    Toast.LENGTH_SHORT).show()
        }
    }

    private fun initLayerIcons(loadedMapStyle: Style) {
        BitmapUtils.getBitmapFromDrawable(
                resources.getDrawable(R.drawable.single_quake_icon, null))?.let {
            loadedMapStyle.addImage(SINGLE_EARTHQUAKE_TRIANGLE_ICON_ID, it)
        }
        BitmapUtils.getBitmapFromDrawable(
                resources.getDrawable(R.drawable.earthquake_triangle, null))?.let {
            loadedMapStyle.addImage(CLUSTER_EARTHQUAKE_TRIANGLE_ICON_ID, it)
        }
    }

    private fun addClusteredGeoJsonSource(loadedMapStyle: Style) {
        // Add a new source from the GeoJSON data and set the 'cluster' option to true.
        try {
            loadedMapStyle.addSource( // Point to GeoJSON data. This example visualizes all M1.0+ earthquakes from
                    // 12/22/15 to 1/21/16 as logged by USGS' Earthquake hazards program.
                    GeoJsonSource(EARTHQUAKE_SOURCE_ID,
                            URI("https://www.mapbox.com/mapbox-gl-js/assets/earthquakes.geojson"),
                            GeoJsonOptions()
                                    .withCluster(true)
                                    .withClusterMaxZoom(14)
                                    .withClusterRadius(50)
                    )
            )
        } catch (uriSyntaxException: URISyntaxException) {
            Log.e("","Check the URL ${uriSyntaxException.message}")
        }
        val unclusteredSymbolLayer = SymbolLayer("unclustered-points", EARTHQUAKE_SOURCE_ID).withProperties(
                iconImage(SINGLE_EARTHQUAKE_TRIANGLE_ICON_ID),
                iconSize(
                        division(
                                get("mag"), literal(4.0f)
                        )
                )
        )
        unclusteredSymbolLayer.setFilter(has("mag"))

        //Creating a SymbolLayer icon layer for single data/icon points
        loadedMapStyle.addLayer(unclusteredSymbolLayer)

        // Use the earthquakes GeoJSON source to create three point ranges.
        val layers = intArrayOf(150, 20, 0)
        for (i in layers.indices) {
            //Add clusters' SymbolLayers images
            val symbolLayer = SymbolLayer("cluster-$i", EARTHQUAKE_SOURCE_ID)
            symbolLayer.setProperties(
                    iconImage(CLUSTER_EARTHQUAKE_TRIANGLE_ICON_ID)
            )
            val pointCount: Expression = toNumber(get(POINT_COUNT))

            // Add a filter to the cluster layer that hides the icons based on "point_count"
            symbolLayer.setFilter(
                    if (i == 0) all(has(POINT_COUNT),
                            gte(pointCount, literal(layers[i]))
                    ) else all(has(POINT_COUNT),
                            gte(pointCount, literal(layers[i])),
                            lt(pointCount, literal(layers[i - 1]))
                    )
            )
            loadedMapStyle.addLayer(symbolLayer)
        }

        //Add a SymbolLayer for the cluster data number point count
        loadedMapStyle.addLayer(SymbolLayer("count", EARTHQUAKE_SOURCE_ID).withProperties(
                textField(Expression.toString(get(POINT_COUNT))),
                textSize(12f),
                textColor(Color.BLACK),
                textIgnorePlacement(true),  // The .5f offset moves the data numbers down a little bit so that they're
                // in the middle of the triangle cluster image
                textOffset(arrayOf(0f, .5f)),
                textAllowOverlap(true)
        ))
    }

    @SuppressLint("MissingPermission")
    private fun enableLocationComponent(loadedMapStyle: Style) {
        // Check if permissions are enabled and if not request
        if (PermissionsManager.areLocationPermissionsGranted(this)) {

            // Create and customize the LocationComponent's options
            val customLocationComponentOptions = LocationComponentOptions.builder(this)
                    .trackingGesturesManagement(true)
                    .accuracyColor(ContextCompat.getColor(this, R.color.mapboxGreen))
                    .build()

            val locationComponentActivationOptions = LocationComponentActivationOptions.builder(this, loadedMapStyle)
                    .locationComponentOptions(customLocationComponentOptions)
                    .build()

            // Get an instance of the LocationComponent and then adjust its settings
            mapboxMap.locationComponent.apply {

                // Activate the LocationComponent with options
                activateLocationComponent(locationComponentActivationOptions)

                // Enable to make the LocationComponent visible
                isLocationComponentEnabled = true

                // Set the LocationComponent's camera mode
                cameraMode = CameraMode.TRACKING

                // Set the LocationComponent's render mode
                renderMode = RenderMode.COMPASS
            }
        } else {
            permissionsManager = PermissionsManager(this)
            permissionsManager.requestLocationPermissions(this)
        }
    }

    override fun onExplanationNeeded(permissionsToExplain: List<String>) {
        Toast.makeText(this, R.string.user_location_permission_explanation, Toast.LENGTH_LONG).show()
    }

    override fun onPermissionResult(granted: Boolean) {
        if (granted) {
            enableLocationComponent(mapboxMap.style!!)
        } else {
            Toast.makeText(this, R.string.user_location_permission_not_granted, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        permissionsManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }
}