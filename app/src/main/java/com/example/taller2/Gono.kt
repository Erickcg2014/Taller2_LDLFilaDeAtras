package com.example.taller2
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.taller2.R
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Gono : AppCompatActivity() {

    private val CAMERA_REQUEST_CODE = 100
    private val GALLERY_REQUEST_CODE = 101
    private val CAMERA_PERMISSION_CODE = 102

    private lateinit var imageView: ImageView
    private lateinit var currentPhotoPath: String
    private lateinit var photoUri: Uri

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        imageView = findViewById(R.id.imageView)
        val buttonSelectImage: Button = findViewById(R.id.button_gallery)
        val buttonTakePhoto: Button = findViewById(R.id.button_camera)


        // Registra el lanzador para el selector de fotos
        val pickMedia = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                Log.d("PhotoPicker", "Selected URI: $uri")
                imageView.setImageURI(uri)
            } else {
                Log.d("PhotoPicker", "No media selected")
            }
        }

        // Registra el lanzador para la cámara
        val takePhoto =
            registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
                if (success) {
                    Log.d("Camera", "Photo taken: $photoUri")
                    imageView.setImageURI(photoUri)
                } else {
                    Log.d("Camera", "Photo not taken")
                }
            }

        buttonSelectImage.setOnClickListener {
            pickMedia.launch("image/*") // Lanza el selector de imágenes
        }

        buttonTakePhoto.setOnClickListener {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                openCamera(takePhoto)
            } else {
                // Solicitar permiso para la cámara
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    CAMERA_PERMISSION_CODE
                )
            }
        }



        fun createImageFile(): File? {
            // Crear un nombre de archivo con la fecha y hora actuales
            val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(
                Date()
            )
            val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)

            return try {
                // Crear el archivo de imagen temporal
                File.createTempFile(
                    "JPEG_${timeStamp}_", // Prefijo del nombre del archivo
                    ".jpg",               // Sufijo del nombre del archivo
                    storageDir            // Directorio donde se guardará el archivo
                )
            } catch (ex: IOException) {
                ex.printStackTrace()
                null // Retorna null si ocurre un error
            }
        }


        fun openCamera(takePhoto: (Uri) -> Unit) {
            // Crea un archivo para almacenar la imagen
            val photoFile = createImageFile()
            photoUri = Uri.fromFile(photoFile)

            // Lanza el intent de la cámara
            takePhoto(photoUri)
        }


        fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<out String>,
            grantResults: IntArray
        ) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            if (requestCode == CAMERA_PERMISSION_CODE) {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openCamera { uri ->
                        // Lanza la cámara usando el uri proporcionado
                        registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
                            if (success) {
                                imageView.setImageURI(uri)
                            }
                        }.launch(uri)
                    }
                } else {
                    Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
                }
            }
        }

        fun guardarEnJSON(datos: JSONObject) {
            try {
                val fileName = "datos_personales.json"
                val file = File(filesDir, fileName)

                FileOutputStream(file).use { fos ->
                    fos.write(datos.toString().toByteArray())
                }

                Toast.makeText(this, "Datos guardados exitosamente", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun openCamera(takePhoto: ActivityResultLauncher<Uri>) {
        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                val takePhoto = null
                takePhoto?.let { openCamera(it) }
            } else {
                Toast.makeText(this, "Permiso de cámara denegado", Toast.LENGTH_SHORT).show()
            }
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) {
            when (requestCode) {
                CAMERA_REQUEST_CODE -> {
                    val imageBitmap = data?.extras?.get("data") as? Bitmap
                    imageBitmap?.let {
                        imageView.setImageBitmap(it) // Muestra la imagen en el ImageView
                    }
                }
                GALLERY_REQUEST_CODE -> {
                    val selectedImageUri = data?.data
                    imageView.setImageURI(selectedImageUri) // Muestra la imagen seleccionada de la galería
                }
            }
        }
    }
}

