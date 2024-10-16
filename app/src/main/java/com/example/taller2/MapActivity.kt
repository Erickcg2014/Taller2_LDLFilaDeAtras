package com.example.taller2

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.RoadManager
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.*
import android.app.UiModeManager

class MapActivity : AppCompatActivity(), SensorEventListener {

    private lateinit var map: MapView
    private var lastLocation: GeoPoint? = null
    private var currentLocationMarker: Marker? = null
    private var searchMarker: Marker? = null
    private var longClickMarker: Marker? = null
    private var currentRoute: Polyline? = null
    private val MIN_DISTANCE_METERS = 30.0
    private var locationManager: LocationManager? = null
    private val LOCATION_PERMISSION_REQUEST_CODE = 1
    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private val LIGHT_THRESHOLD = 40

    // Campo de texto para la dirección
    private lateinit var addressInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configurar la caché de OSM
        Configuration.getInstance().load(this, androidx.preference.PreferenceManager.getDefaultSharedPreferences(this))

        setContentView(R.layout.activity_map)

        // Inicializar el sensor de luminosidad
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        if (lightSensor == null) {
            Log.e("MapActivity", "El dispositivo no tiene un sensor de luz.")
            Toast.makeText(this, "El dispositivo no tiene un sensor de luz.", Toast.LENGTH_SHORT).show()
        } else {
            Log.d("MapActivity", "Sensor de luz detectado.")
        }

        map = findViewById(R.id.mapView)
        map.setMultiTouchControls(true)

        addressInput = findViewById(R.id.address_input)
        addressInput.setOnEditorActionListener { v, _, _ ->
            val address = v.text.toString()
            if (address.isNotEmpty()) {
                searchLocation(address)
            }
            true
        }

        val mapController: IMapController = map.controller
        mapController.setZoom(15.0)

        if (!hasLocationPermission()) {
            requestLocationPermission()
        } else {
            setupMap()
        }

        // Evento de "LongClick" usando un Overlay
        val longClickOverlay = object : Overlay() {
            override fun onLongPress(e: android.view.MotionEvent, mapView: MapView): Boolean {
                val projection = mapView.projection
                val geoPoint = projection.fromPixels(e.x.toInt(), e.y.toInt()) as GeoPoint
                createLongClickMarker(geoPoint) // Crea el marcador en la posición del evento
                return true
            }
        }
        map.overlays.add(longClickOverlay)
    }

    private fun searchLocation(address: String) {
        val geocoder = Geocoder(this, Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocationName(address, 1)
            if (addresses != null && addresses.isNotEmpty()) {
                val location = addresses[0]
                val geoPoint = GeoPoint(location.latitude, location.longitude)

                // Mover la cámara al punto encontrado
                map.controller.setZoom(18.0)
                map.controller.animateTo(geoPoint)

                updateSearchMarker(geoPoint)

                // Calcular y mostrar la distancia
                calculateDistanceToMarker(geoPoint)

                createRouteToMarker(lastLocation!!, geoPoint)
            } else {
                Toast.makeText(this, "Dirección no encontrada", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            Toast.makeText(this, "Error al buscar la dirección", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun updateSearchMarker(location: GeoPoint) {
        if (searchMarker == null) {
            // Si no existe el marcador de búsqueda, crear uno nuevo
            searchMarker = Marker(map)
            searchMarker?.title = "Resultado de búsqueda"
            val searchIcon = ContextCompat.getDrawable(this, R.mipmap.icon_marker2)
            searchIcon?.let {
                searchMarker?.icon = it
            }
            map.overlays.add(searchMarker)
        }
        // Actualizar la posición del marcador de búsqueda
        searchMarker?.position = location
        map.invalidate()
    }

    private fun createLongClickMarker(geoPoint: GeoPoint) {
        val geocoder = Geocoder(this, Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocation(geoPoint.latitude, geoPoint.longitude, 1)
            val address = if (addresses != null && addresses.isNotEmpty()) {
                addresses[0].getAddressLine(0) // Obtener la dirección completa
            } else {
                "Dirección no encontrada"
            }

            // Si ya existe un marcador de long click, eliminarlo antes de crear uno nuevo
            longClickMarker?.let { map.overlays.remove(it) }

            // Crear y agregar un nuevo marcador para el evento de LongClick
            longClickMarker = Marker(map)
            longClickMarker?.position = geoPoint
            longClickMarker?.title = address // Usar la dirección como título del marcador

            map.overlays.add(longClickMarker)
            map.invalidate()
        } catch (e: IOException) {
            Toast.makeText(this, "Error al obtener la dirección", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }

    private fun calculateDistanceToMarker(markerLocation: GeoPoint) {
        lastLocation?.let { userLocation ->
            val distance = userLocation.distanceToAsDouble(markerLocation)
            val message = "Distancia entre tu ubicación y el marcador: %.2f metros".format(distance)
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        } ?: run {
            Toast.makeText(this, "No se pudo obtener tu ubicación", Toast.LENGTH_SHORT).show()
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupMap()
            } else {
                Toast.makeText(this, "Permiso de ubicación denegado", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupMap() {
        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
            locationManager?.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, 5000, 10f, locationListener
            )
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val newGeoPoint = GeoPoint(location.latitude, location.longitude)
            handleLocationChange(newGeoPoint)
        }

        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    private fun handleLocationChange(newGeoPoint: GeoPoint) {
        if (lastLocation == null || newGeoPoint.distanceToAsDouble(lastLocation!!) > MIN_DISTANCE_METERS) {
            lastLocation = newGeoPoint
            updateCurrentLocationMarker(newGeoPoint)
            saveLocationToJson(newGeoPoint) // Guardar la ubicación en JSON cada vez que cambia
        }
    }

    private fun updateCurrentLocationMarker(location: GeoPoint) {
        if (currentLocationMarker == null) {
            currentLocationMarker = Marker(map)
            currentLocationMarker?.title = "Ubicación actual"
            val customIcon = ContextCompat.getDrawable(this, R.mipmap.icon_marker3)
            currentLocationMarker?.icon = customIcon
            map.overlays.add(currentLocationMarker)
        }
        currentLocationMarker?.position = location
        map.controller.animateTo(location)
        map.invalidate()
    }

    // Función para crear la ruta de punto a punto, o solo la ruta xd
    private fun createRouteToMarker(startPoint: GeoPoint, endPoint: GeoPoint) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val roadManager = OSRMRoadManager(this@MapActivity, "Taller2App")
                val waypoints = ArrayList<GeoPoint>().apply {
                    add(startPoint)
                    add(endPoint)
                }
                val road = roadManager.getRoad(waypoints)

                withContext(Dispatchers.Main) {
                    // Si ya existe una ruta, eliminarla antes de agregar una nueva
                    currentRoute?.let {
                        map.overlays.remove(it)
                    }

                    val roadOverlay = RoadManager.buildRoadOverlay(road)
                    roadOverlay.outlinePaint.strokeWidth = 10f
                    roadOverlay.outlinePaint.color = ContextCompat.getColor(this@MapActivity, R.color.red)

                    // Guardar la nueva ruta en la variable currentRoute
                    currentRoute = roadOverlay

                    // Agregar la nueva ruta al mapa
                    map.overlays.add(roadOverlay)
                    map.invalidate()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MapActivity, "Error al obtener la ruta", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun saveLocationToJson(location: GeoPoint) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val currentTime = sdf.format(Date())
        val locationData = JSONObject()
        locationData.put("latitude", location.latitude)
        locationData.put("longitude", location.longitude)
        locationData.put("timestamp", currentTime)

        val locationsFile = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "locations.json")
        try {
            if (!locationsFile.exists()) {
                locationsFile.createNewFile()
            }
            val jsonArray = if (locationsFile.length() > 0) {
                JSONArray(locationsFile.readText())
            } else {
                JSONArray()
            }
            jsonArray.put(locationData)

            FileWriter(locationsFile).use { writer ->
                writer.write(jsonArray.toString())
                writer.flush()
            }

            Log.d("MapActivity", "Ubicación guardada en JSON: ${locationsFile.absolutePath}")

            runOnUiThread {
                Toast.makeText(this, "Ubicación guardada en JSON", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            Log.e("MapActivity", "Error guardando ubicación", e)
        }
    }

    // Función para el modo oscuro y claro
    override fun onResume() {
        super.onResume()
        map.onResume()

        val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        if (uiModeManager.nightMode == UiModeManager.MODE_NIGHT_YES) {
            map.setTileSource(XYTileSource("DarkMode", 0, 18, 256, ".png", arrayOf("https://tile.osmand.net/dark/")))
        } else {
            map.setTileSource(TileSourceFactory.MAPNIK)
        }
        map.invalidate()

        // Registrar el sensor de luz si está disponible
        lightSensor?.let { sensor ->
            Log.d("MapActivity", "Registrando el sensor de luz.")
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        map.onPause()

        // Desregistrar el sensor de luz
        Log.d("MapActivity", "Desregistrando el sensor de luz.")
        sensorManager.unregisterListener(this)
    }

    // Cambiar el estilo del mapa según el sensor de luz
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
            val lightLevel = event.values[0]
            Log.d("MapActivity", "Nivel de luz detectado: $lightLevel")

            if (lightLevel < LIGHT_THRESHOLD) {
                Log.d("MapActivity", "Activando modo oscuro.")
                map.setTileSource(XYTileSource("DarkMode", 0, 18, 256, ".png", arrayOf("https://tile.osmand.net/dark/")))
            } else {
                Log.d("MapActivity", "Activando modo claro.")
                map.setTileSource(TileSourceFactory.MAPNIK)
            }

            map.invalidate() // Refrescar el mapa
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No es necesario manejar la precisión en este caso
    }
}
