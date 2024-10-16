package com.example.taller2

import android.Manifest
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import android.widget.ListView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class ContactosActivity : AppCompatActivity() {
    var mProjection: Array<String>? = null
    var mCursor: Cursor? = null
    var mContactsAdapter: ContactsAdapter? = null
    var mlista: ListView? = null

    private lateinit var contactsPermissionLauncher: androidx.activity.result.ActivityResultLauncher<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contactos)
        mlista = findViewById<ListView>(R.id.listview)
        mProjection = arrayOf(ContactsContract.Profile._ID, ContactsContract.Profile.DISPLAY_NAME_PRIMARY)
        mContactsAdapter = ContactsAdapter(this, null, 0)
        mlista?.adapter = mContactsAdapter
        // Ajustar los márgenes de las barras del sistema
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Registrar el lanzador para solicitar permisos de contactos
        contactsPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                Log.d("ContactsActivity", "Permiso de contactos concedido")
                openContactsActivity()
            } else {
                Log.d("ContactsActivity", "Permiso de contactos denegado")
                Toast.makeText(this, "Permiso de contactos denegado", Toast.LENGTH_SHORT).show()
                openRestrictedContactsActivity()
            }
        }

        // Verificar y solicitar permiso de contactos
        requestContactsPermission()
    }

    private fun requestContactsPermission() {
        Log.d("ContactsActivity", "Verificando permisos de contactos")
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            // Si no tenemos el permiso, lo solicitamos
            Log.d("ContactsActivity", "Permiso de contactos no concedido, solicitando")
            contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
        } else {
            // Si ya tenemos el permiso, abrir la actividad de contactos
            Log.d("ContactsActivity", "Permiso de contactos ya concedido, abriendo ContactsActivity")
            openContactsActivity()
        }
    }

    private fun openContactsActivity() {
        // Aquí puedes abrir una actividad que maneje los contactos
        Log.d("ContactsActivity", "Abriendo actividad de contactos")
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                mCursor = contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI, mProjection, null, null, null
                )
                mContactsAdapter?.changeCursor(mCursor)
            }

    }

    private fun openRestrictedContactsActivity() {
        // Si el permiso es denegado, puedes manejarlo de forma diferente
        Log.d("ContactsActivity", "Abriendo actividad restringida por falta de permiso de contactos")

    }
}
