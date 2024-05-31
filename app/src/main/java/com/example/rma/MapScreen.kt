package com.example.rma

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.AlertDialog
import androidx.compose.material.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.MapsInitializer
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.UiSettings
import com.google.firebase.firestore.FirebaseFirestore
import androidx.navigation.NavController
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Marker

@Composable
fun MapScreen(navController:NavController) {
    val context = LocalContext.current
    var marker: Marker? = null
    val markers = mutableListOf<Marker?>()

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasLocationPermission = granted
        }
    )

    LaunchedEffect(key1 = true) {
        if (!hasLocationPermission) {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    BackHandler {
        (context as ComponentActivity).finish()
    }
    if (hasLocationPermission) {
        AndroidView(
            factory = { context ->
                MapView(context).apply {
                    onCreate(null)
                    onResume()
                    getMapAsync { googleMap ->


                        googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(CameraBounds.getCameraPosition()))
                        googleMap.setOnCameraMoveListener { CameraBounds.setCameraPosition(googleMap.cameraPosition) }

                        // Enable location layer if permission granted
                        googleMap.isMyLocationEnabled = true

                        googleMap.setMapStyle(
                            MapStyleOptions(
                                """
                                [
                                    {
                                        "featureType": "poi",
                                        "elementType": "all",
                                        "stylers": [
                                            { "visibility": "off" }
                                        ]
                                    },
                                    {
                                        "featureType": "road",
                                        "elementType": "geometry",
                                        "stylers": [
                                            { "visibility": "simplified" }
                                        ]
                                    }
                                ]
                                """.trimIndent()
                            )
                        )

                        // Adjust UI settings as needed
                        val uiSettings: UiSettings = googleMap.uiSettings
                        uiSettings.isZoomControlsEnabled = true
                        val locations = mutableListOf<MapMarker>()
                        // Fetch locations from Firestore
                        FirebaseFirestore.getInstance().collection("locations")
                            .get()
                            .addOnSuccessListener { documents ->
                                for (document in documents.documents) {
                                    val coordinates = LatLng(
                                        document.data!!["latitude"].toString().toDouble(),
                                        document.data!!["longitude"].toString().toDouble()
                                    )
                                    locations.add(MapMarker(document.id, coordinates))
                                }
                            }
                            .addOnCompleteListener {
                                for (location in locations) {
                                    val myMarker = googleMap.addMarker(MarkerOptions().position(location.cordinates))
                                    myMarker!!.tag = location.id
                                    markers.add(myMarker)
                                }

                                if (CameraBounds.showSpecifiedLocationOnMap) {

                                    marker = googleMap.addMarker(
                                        MarkerOptions().position(
                                            LatLng(
                                                CameraBounds.latitude,
                                                CameraBounds.longitude
                                            )
                                        )
                                            .icon(
                                                BitmapDescriptorFactory.defaultMarker(
                                                    BitmapDescriptorFactory.HUE_AZURE))
                                            .title("Its here!")
                                                /* TODO:  Marker Title is not showing*/

                                    )

                                    for (mark in markers) {
                                        if (marker!!.position == mark?.position)
                                            marker!!.tag = mark.tag

                                    }
                                    googleMap.setOnMapClickListener {
                                        marker!!.remove()
                                    }

                                    CameraBounds.showSpecifiedLocationOnMap = false
                                    marker?.showInfoWindow()

                                }



                                googleMap.setOnMarkerClickListener { marker ->

                                    val documentId = marker.tag as? String
                                    if (documentId != null) {
                                        navController.navigate("locationDetail/$documentId")
                                    }
                                    true
                                }



                            }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        // Handle the case when permission is not granted
        Text(
            text = "Permission not granted for accessing location.",
            modifier = Modifier.fillMaxSize(),
            textAlign = TextAlign.Center
        )
    }
}



data class MapMarker(
    var id: String,
    var cordinates: LatLng
)

