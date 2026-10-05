package com.carlosrafaelsart.cerberusia;


import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {

    private EditText etApiUrl, etApiKey, etModel;
    private SharedPreferences prefs;
    private static final String DEF_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String DEF_MODEL = "openai/gpt-3.5-turbo";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("CerberusPrefs", MODE_PRIVATE);
        etApiUrl = findViewById(R.id.et_api_url);
        etApiKey = findViewById(R.id.et_api_key);
        etModel = findViewById(R.id.et_model);
        Button btnSave = findViewById(R.id.btn_save);
        Button btnReset = findViewById(R.id.btn_reset);

        etApiUrl.setText(prefs.getString("api_url", DEF_URL));
        etApiKey.setText(prefs.getString("api_key", ""));
        etModel.setText(prefs.getString("model", DEF_MODEL));

        btnSave.setOnClickListener(v -> {
            prefs.edit()
                .putString("api_url", etApiUrl.getText().toString().trim())
                .putString("api_key", etApiKey.getText().toString().trim())
                .putString("model", etModel.getText().toString().trim())
                .apply();
            Toast.makeText(this, "Guardado ✅", Toast.LENGTH_SHORT).show();
            finish();
        });

        btnReset.setOnClickListener(v -> {
            etApiUrl.setText(DEF_URL);
            etModel.setText(DEF_MODEL);
            Toast.makeText(this, "Restablecido ✅", Toast.LENGTH_SHORT).show();
        });
    }
}
