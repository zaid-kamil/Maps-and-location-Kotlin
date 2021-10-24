package com.podium.mapsandlocationkotlin

import android.animation.ObjectAnimator
import android.animation.TypeEvaluator
import android.animation.ValueAnimator
import android.animation.ValueAnimator.AnimatorUpdateListener
import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.appcompat.app.AppCompatActivity
import com.mapbox.geojson.Feature
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponent
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.LocationComponentOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.expressions.Expression.*
import com.mapbox.mapboxsdk.style.layers.FillExtrusionLayer
import com.mapbox.mapboxsdk.style.layers.FillLayer
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.*
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.mapbox.mapboxsdk.style.sources.VectorSource
import pub.devrel.easypermissions.EasyPermissions


class MainActivity : AppCompatActivity(), EasyPermissions.PermissionCallbacks {

    private var mapView: MapView? = null
    private lateinit var mapboxMapItem: MapboxMap
    private var currentPosition: LatLng = LatLng(64.900932, -18.167040)
    private var geoJsonSource: GeoJsonSource? = null
    private var animator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Mapbox.getInstance(this, getString(R.string.mapbox_access_token))
        setContentView(R.layout.activity_main)

        var perms = arrayOf(
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        )
        if (!EasyPermissions.hasPermissions(this, *perms)) {
            EasyPermissions.requestPermissions(this, "location permission", 2893, *perms)
        } else {
            updateUI(savedInstanceState)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        updateUI(null)
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        Toast.makeText(this, "App wont work without permission", Toast.LENGTH_LONG).show()
    }

    private fun updateUI(savedInstanceState: Bundle?) {
        mapView = findViewById(R.id.mapView)
        mapView?.onCreate(savedInstanceState)
        mapView?.getMapAsync { mapboxMap ->
            mapboxMapItem = mapboxMap
            geoJsonSource = GeoJsonSource(
                "source-id",
                Feature.fromGeometry(
                    Point.fromLngLat(
                        currentPosition.getLongitude(),
                        currentPosition.getLatitude()
                    )
                )
            )
            mapboxMap.setStyle(Style.MAPBOX_STREETS) { style ->
                enableLocationComponent(style)
                style.addSource(
                    VectorSource("population", "mapbox://peterqliu.d0vin3el")
                )
                style.addImage(
                    "marker_icon", BitmapFactory.decodeResource(
                        resources, R.drawable.red_marker
                    )
                )

                style.addSource(geoJsonSource!!)

                style.addLayer(
                    SymbolLayer("layer-id", "source-id")
                        .withProperties(
                            PropertyFactory.iconImage("marker_icon"),
                            PropertyFactory.iconIgnorePlacement(true),
                            PropertyFactory.iconAllowOverlap(true)
                        )
                )

                addFillsLayer(style)
                addExtrusionsLayer(style)
                mapboxMap.addOnMapClickListener {
//                    val position: CameraPosition = CameraPosition.Builder()
//                        .target(it) // Sets the new camera position
//                        .zoom(17.0) // Sets the zoom
//                        .bearing(360.0) // Rotate the camera
//                        .tilt(30.0) // Set the camera tilt
//                        .build()
//
//                    mapboxMap.animateCamera(CameraUpdateFactory.newCameraPosition(position))
                    if (animator != null && animator!!.isStarted) {
                        currentPosition = animator!!.animatedValue as LatLng
                        animator!!.cancel()
                    }

                    animator = ObjectAnimator.ofObject(latLngEvaluator, currentPosition, it)
                        .setDuration(2000)
                    with(animator) {
                        this?.addUpdateListener(animatorUpdateListener)
                        this?.start()
                    }

                    currentPosition = it
                    true
                }


            }

        }
    }

    private val animatorUpdateListener =
        AnimatorUpdateListener { valueAnimator ->
            val animatedPosition = valueAnimator.animatedValue as LatLng
            geoJsonSource!!.setGeoJson(
                Point.fromLngLat(
                    animatedPosition.longitude,
                    animatedPosition.latitude
                )
            )
        }

    // Class is used to interpolate the marker animation.
    private val latLngEvaluator: TypeEvaluator<LatLng?> = object : TypeEvaluator<LatLng?> {
        private val latLng = LatLng()
        override fun evaluate(fraction: Float, startValue: LatLng?, endValue: LatLng?): LatLng? {
            latLng.latitude =
                (startValue!!.latitude + (endValue!!.latitude - startValue.latitude) * fraction)
            latLng.longitude =
                (startValue.longitude + (endValue.longitude - startValue.longitude) * fraction)
            return latLng
        }
    }

    private fun addFillsLayer(loadedMapStyle: Style) {
        val fillsLayer = FillLayer("fills", "population")
        fillsLayer.sourceLayer = "outgeojson"
        fillsLayer.setFilter(all(lt(get("pkm2"), literal(300000))))
        fillsLayer.withProperties(
            fillColor(
                interpolate(
                    exponential(1f), get("pkm2"),
                    stop(0, rgb(22, 14, 35)),
                    stop(14500, rgb(0, 97, 127)),
                    stop(145000, rgb(85, 223, 255))
                )
            )
        )
        loadedMapStyle.addLayerBelow(fillsLayer, "water")
    }

    private fun addExtrusionsLayer(loadedMapStyle: Style) {
        val fillExtrusionLayer = FillExtrusionLayer("extrusions", "population")
        fillExtrusionLayer.sourceLayer = "outgeojson"
        fillExtrusionLayer.setFilter(all(gt(get("p"), 1), lt(get("pkm2"), 300000)))
        fillExtrusionLayer.withProperties(
            fillExtrusionColor(
                interpolate(
                    exponential(1f), get("pkm2"),
                    stop(0, rgb(22, 14, 35)),
                    stop(14500, rgb(0, 97, 127)),
                    stop(145000, rgb(85, 233, 255))
                )
            ),
            fillExtrusionBase(0f),
            fillExtrusionHeight(
                interpolate(
                    exponential(1f), get("pkm2"),
                    stop(0, 0f),
                    stop(1450000, 20000f)
                )
            )
        )
        loadedMapStyle.addLayerBelow(fillExtrusionLayer, "airport-label")
    }

    @SuppressLint("MissingPermission")
    private fun enableLocationComponent(@NonNull loadedMapStyle: Style) {

        // Enable the most basic pulsing styling by ONLY using
        // the `.pulseEnabled()` method
        val customLocationComponentOptions: LocationComponentOptions =
            LocationComponentOptions.builder(this)
                .pulseEnabled(true)
                .build()

        // Get an instance of the component
        val locationComponent: LocationComponent = mapboxMapItem.locationComponent

        // Activate with options
        locationComponent.activateLocationComponent(
            LocationComponentActivationOptions.builder(this, loadedMapStyle)
                .locationComponentOptions(customLocationComponentOptions)
                .build()
        )

        // Enable to make component visible
        locationComponent.isLocationComponentEnabled = true

        // Set the component's camera mode
        locationComponent.cameraMode = CameraMode.TRACKING_GPS_NORTH

        // Set the component's render mode
        locationComponent.renderMode = RenderMode.GPS

    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView?.onDestroy()
    }
}