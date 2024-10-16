package com.example.taller2

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        var botonContactos = findViewById<ImageButton>(R.id.botoncontactos)
        val intentContactos = Intent(this, ContactosActivity::class.java)
        botonContactos.setOnClickListener(){
            startActivity(intentContactos)
        }
        var botonMapa = findViewById<ImageButton>(R.id.mapa)
        val intentMapa = Intent(this, MapActivity::class.java)
        botonMapa.setOnClickListener(){
            startActivity(intentMapa)
        }

        val buttonCamara = findViewById<ImageButton>(R.id.camara)
        buttonCamara.setOnClickListener {
            val intent = Intent(this, Gono::class.java)
            startActivity(intent)
        }


    }
}