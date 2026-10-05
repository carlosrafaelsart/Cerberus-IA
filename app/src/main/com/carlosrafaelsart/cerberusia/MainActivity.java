package com.carlosrafaelsart.cerberusia;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Conectar elementos
        EditText inputMessage = findViewById(R.id.input_message);
        Button btnSend = findViewById(R.id.btn_send);
        Button btnSettings = findViewById(R.id.btn_settings);

        // Botón enviar
        btnSend.setOnClickListener(v -> {
            String texto = inputMessage.getText().toString();
            if (!texto.isEmpty()) {
                // Aquí va tu lógica
                inputMessage.setText("");
            }
        });

        // Botón ajustes
        btnSettings.setOnClickListener(v -> {
            startActivity(new Intent(this, SettingsActivity.class));
        });
    }
}
