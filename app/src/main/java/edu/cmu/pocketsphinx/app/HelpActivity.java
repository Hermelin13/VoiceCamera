package edu.cmu.pocketsphinx.app;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;

public class HelpActivity extends AppCompatActivity {

    ImageButton back;

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        setContentView(R.layout.activity_main2);
        back = findViewById(R.id.back);

        back.setOnClickListener(v -> closeHelp());
    }

    public void closeHelp() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        finish();
    }
}