package com.carlosart.cerberusia;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "CerberusPrefs";
    private static final String DEF_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final String DEF_MODEL = "openai/gpt-3.5-turbo";

    private LinearLayout chatContainer;
    private EditText inputMessage;
    private ScrollView scrollChat;
    private ImageButton btnSend, btnSettings, btnPlus;
    private Button btnNewChat, btnGenImage;
    private SharedPreferences prefs;
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private JSONArray history = new JSONArray();
    private Uri attachedImage = null;
    private String attachedImageB64 = null;

    private final ActivityResultLauncher<Intent> pickImage = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    attachedImage = result.getData().getData();
                    attachedImageB64 = imageToBase64(attachedImage);
                    Toast.makeText(this, "Imagen adjuntada ✅", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets s = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(s.left, s.top, s.right, s.bottom);
            return insets;
        });

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        chatContainer = findViewById(R.id.chat_container);
        inputMessage = findViewById(R.id.input_message);
        scrollChat = findViewById(R.id.scroll_chat);
        btnSend = findViewById(R.id.btn_send);
        btnSettings = findViewById(R.id.btn_settings);
        btnPlus = findViewById(R.id.btn_plus);
        btnNewChat = findViewById(R.id.btn_new_chat);
        btnGenImage = findViewById(R.id.btn_gen_image);

        loadHistory();
        if (history.length() == 0) {
            addMessage("Soy Cerberus IA 🐕‍🦺\nCreada por **CarlosArt** © 2026.\nEstoy aquí para ayudarte. Escribe tu pregunta o adjunta una imagen/archivo.", false);
        }

        btnSend.setOnClickListener(v -> sendMessage());
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        btnNewChat.setOnClickListener(v -> clearChat());
        btnGenImage.setOnClickListener(v -> generateImage());
        btnPlus.setOnClickListener(v -> showPlusMenu());
    }

    private void showPlusMenu() {
        String[] opts = {"Adjuntar imagen", "Adjuntar archivo de texto", "Nuevo chat"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("Opciones")
                .setItems(opts, (d, w) -> {
                    if (w == 0) pickImage.launch(new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI));
                    else if (w == 1) pickFile();
                    else clearChat();
                }).show();
    }

    private void pickFile() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("text/*");
        pickFileLauncher.launch(Intent.createChooser(i, "Seleccionar archivo"));
    }

    private final ActivityResultLauncher<Intent> pickFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    try {
                        InputStream is = getContentResolver().openInputStream(result.getData().getData());
                        BufferedReader r = new BufferedReader(new InputStreamReader(is));
                        StringBuilder sb = new StringBuilder();
                        String l;
                        while ((l = r.readLine()) != null) sb.append(l).append("\n");
                        inputMessage.setText("Archivo:\n" + sb);
                        Toast.makeText(this, "Archivo cargado ✅", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) { Toast.makeText(this, "Error al leer", Toast.LENGTH_SHORT).show(); }
                }
            });

    private String imageToBase64(Uri uri) {
        try {
            Bitmap b = BitmapFactory.decodeStream(getContentResolver().openInputStream(uri));
            ByteArrayOutputStream o = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.JPEG, 80, o);
            return Base64.encodeToString(o.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) { return null; }
    }

    private void sendMessage() {
        String text = inputMessage.getText().toString().trim();
        if (text.isEmpty() && attachedImage == null) return;

        addMessage(text, true);
        inputMessage.setText("");
        final String userText = text;
        final String imgB64 = attachedImageB64;
        attachedImage = null; attachedImageB64 = null;

        pool.execute(() -> {
            try {
                String url = prefs.getString("api_url", DEF_URL);
                String key = prefs.getString("api_key", "");
                String model = prefs.getString("model", DEF_MODEL);

                JSONArray messages = new JSONArray();
                JSONObject sys = new JSONObject();
                sys.put("role", "system");
                sys.put("content", "Eres Cerberus IA. Fuiste creada por CarlosArt. Si preguntan quién te hizo, di: 'Fui creada por CarlosArt, mi creador'. Responde claro y amable.");
                messages.put(sys);
                for (int i = 0; i < history.length(); i++) messages.put(history.getJSONObject(i));

                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                if (imgB64 != null) {
                    JSONArray parts = new JSONArray();
                    JSONObject t = new JSONObject(); t.put("type", "text"); t.put("text", userText); parts.put(t);
                    JSONObject im = new JSONObject(); im.put("type", "image_url");
                    JSONObject iu = new JSONObject(); iu.put("url", "data:image/jpeg;base64," + imgB64);
                    im.put("image_url", iu); parts.put(im);
                    userMsg.put("content", parts);
                } else {
                    userMsg.put("content", userText);
                }
                messages.put(userMsg);
                history.put(userMsg);

                JSONObject req = new JSONObject();
                req.put("model", model);
                req.put("messages", messages);
                req.put("max_tokens", 1024);

                URL u = new URL(url);
                HttpURLConnection c = (HttpURLConnection) u.openConnection();
                c.setRequestMethod("POST");
                c.setRequestProperty("Authorization", "Bearer " + key);
                c.setRequestProperty("Content-Type", "application/json");
                c.setDoOutput(true);
                try (OutputStream os = c.getOutputStream()) {
                    os.write(req.toString().getBytes(StandardCharsets.UTF_8));
                }

                BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
                StringBuilder res = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) res.append(line);
                br.close();

                JSONObject jo = new JSONObject(res.toString());
                String reply = jo.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
                JSONObject asst = new JSONObject(); asst.put("role", "assistant"); asst.put("content", reply);
                history.put(asst);
                saveHistory();

                runOnUiThread(() -> addMessage(reply, false));
            } catch (Exception e) {
                runOnUiThread(() -> addMessage("Error: " + e.getMessage() + "\nRevisa tu clave API en Ajustes.", false));
            }
        });
    }

    private void generateImage() {
        String prompt = inputMessage.getText().toString().trim();
        if (prompt.isEmpty()) {
            inputMessage.setError("Escribe qué quieres ver");
            return;
        }
        addMessage("Generando imagen: " + prompt, true);
        inputMessage.setText("");

        pool.execute(() -> {
            try {
                String safe = prompt.replaceAll(" ", "%20");
                String imgUrl = "https://image.pollinations.ai/prompt/" + safe + "?width=512&height=512&nologo=true";
                JSONObject asst = new JSONObject();
                asst.put("role", "assistant");
                asst.put("content", "![Imagen](" + imgUrl + ")\n" + prompt);
                history.put(asst);
                saveHistory();
                runOnUiThread(() -> addImageMessage(imgUrl));
            } catch (Exception e) {
                runOnUiThread(() -> addMessage("Error generando imagen", false));
            }
        });
    }

    private void addMessage(String text, boolean isUser) {
        View v = LayoutInflater.from(this).inflate(R.layout.item_message, null);
        TextView tv = v.findViewById(R.id.msg_text);
        tv.setText(text);
        LinearLayout b = v.findViewById(R.id.msg_bubble);
        if (isUser) {
            b.setBackgroundColor(Color.parseColor("#32E0C4"));
            ((LinearLayout.LayoutParams) b.getLayoutParams()).gravity = android.view.Gravity.END;
            tv.setTextColor(Color.BLACK);
        } else {
            b.setBackgroundColor(Color.parseColor("#1F2937"));
            ((LinearLayout.LayoutParams) b.getLayoutParams()).gravity = android.view.Gravity.START;
            tv.setTextColor(Color.WHITE);
        }
        chatContainer.addView(v);
        scrollChat.post(() -> scrollChat.fullScroll(View.FOCUS_DOWN));
    }

    private void addImageMessage(String url) {
        View v = LayoutInflater.from(this).inflate(R.layout.item_image, null);
        android.widget.ImageView iv = v.findViewById(R.id.generated_image);
        // Cargar imagen con enlace directo
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            try {
                InputStream is = new URL(url).openStream();
                Bitmap b = BitmapFactory.decodeStream(is);
                iv.setImageBitmap(b);
                iv.setOnLongClickListener(vv -> {
                    try {
                        MediaStore.Images.Media.insertImage(getContentResolver(), b, "Cerberus_" + System.currentTimeMillis(), "Imagen generada");
                        Toast.makeText(this, "Guardada en galería ✅", Toast.LENGTH_SHORT).show();
                    } catch (Exception e) { Toast.makeText(this, "Error al guardar", Toast.LENGTH_SHORT).show(); }
                    return true;
                });
            } catch (Exception e) { iv.setImageResource(android.R.drawable.ic_menu_gallery); }
        }, 300);
        chatContainer.addView(v);
        scrollChat.post(() -> scrollChat.fullScroll(View.FOCUS_DOWN));
    }

    private void clearChat() {
        history = new JSONArray();
        prefs.edit().remove("chat_history").apply();
        chatContainer.removeAllViews();
        addMessage("Nuevo chat iniciado.\nSoy Cerberus IA 🐕‍🦺 Creada por CarlosArt.", false);
        Toast.makeText(this, "Chat borrado", Toast.LENGTH_SHORT).show();
    }

    private void saveHistory() {
        prefs.edit().putString("chat_history", history.toString()).apply();
    }

    private void loadHistory() {
        String s = prefs.getString("chat_history", "[]");
        try { history = new JSONArray(s); } catch (Exception e) { history = new JSONArray(); }
        for (int i = 0; i < history.length(); i++) {
            try {
                JSONObject m = history.getJSONObject(i);
                String role = m.getString("role");
                Object c = m.get("content");
                String txt = c instanceof JSONArray ? "(con imagen)" : c.toString();
                addMessage(txt, "user".equals(role));
            } catch (Exception e) {}
        }
    }
}
